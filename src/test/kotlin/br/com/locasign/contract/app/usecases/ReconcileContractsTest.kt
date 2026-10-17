package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingContractRepository
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.HistoryOutcome
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.defaultSigners
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.RecordingMetricsPort
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconcileContractsTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val clock = FixedBusinessClock(now)
    private val settings = ContractSettings(
        signatureDeadline = Duration.ofDays(7),
        reminderAfter = Duration.ofDays(3),
        reconciliationStaleAfter = Duration.ofMinutes(10),
        jobBatchSize = 50,
    )

    private val contracts = RecordingContractRepository()
    private val provider = ScriptedStateProvider()
    private val metrics = RecordingMetricsPort()

    private val reconcile = ReconcileContracts(
        contracts = contracts,
        provider = provider,
        applyUpdate = ApplyProviderUpdate(
            contracts = contracts,
            reconciliationMarker = contracts,
            persister = ContractPersister(contracts, RecordingContractEventPublisher()),
            clock = clock,
            settings = settings,
            metrics = metrics,
            transactions = ImmediateTransactionRunner,
        ),
        clock = clock,
        settings = settings,
        transactions = ImmediateTransactionRunner,
    )

    /** Contrato parado há 1 hora, portanto candidato à reconciliação (limite de 10 minutos). */
    private fun staleContract(status: ContractStatus, documentId: String, signersDone: Set<SignerRole> = emptySet()) =
        restoreContract(
            status = status,
            signers = defaultSigners().map { if (it.role in signersDone) it.copy(completedAt = now) else it },
            now = now.minus(Duration.ofHours(1)),
            providerDocumentId = ProviderDocumentId.of(documentId),
        ).also(contracts.storage::save)

    private fun document(raw: String) = ProviderDocumentId.of(raw)

    @Test
    fun `sem candidatos não consulta o provedor`() {
        assertEquals(0, reconcile.executeStale())

        assertTrue(provider.consulted.isEmpty())
    }

    @Test
    fun `consulta o candidato parado e aplica o estado do provedor`() {
        val contract = staleContract(ContractStatus.GENERATED, "doc-1")
        provider.respond("doc-1", ProviderDocumentStatus.SENT)

        val consulted = reconcile.executeStale()

        assertEquals(1, consulted)
        assertEquals(listOf(document("doc-1")), provider.consulted)
        assertEquals(ContractStatus.SENT, contract.status)
        assertEquals(now, contract.lastReconciledAt)
    }

    @Test
    fun `não carrega o agregado só para ler o documento do provedor (RC1)`() {
        staleContract(ContractStatus.GENERATED, "doc-1")
        staleContract(ContractStatus.GENERATED, "doc-2")
        provider.respond("doc-1", ProviderDocumentStatus.SENT)
        provider.respond("doc-2", ProviderDocumentStatus.SENT)

        assertEquals(2, reconcile.executeStale())

        assertEquals(0, contracts.findByIdCalls)
    }

    @Test
    fun `para no primeiro RateLimited e deixa os demais candidatos para o próximo ciclo (RL1)`() {
        staleContract(ContractStatus.GENERATED, "doc-1")
        staleContract(ContractStatus.GENERATED, "doc-2")
        staleContract(ContractStatus.GENERATED, "doc-3")
        provider.respond("doc-1", ProviderDocumentStatus.SENT)
        provider.fail(
            "doc-2",
            ProviderException.RateLimited("Limite local de 8 requisições/min atingido para 'details'")
        )
        provider.respond("doc-3", ProviderDocumentStatus.SENT)

        val consulted = reconcile.executeStale()

        assertEquals(1, consulted)
        assertEquals(listOf(document("doc-1"), document("doc-2")), provider.consulted)
    }

    @Test
    fun `provedor indisponível para um candidato não impede os demais`() {
        staleContract(ContractStatus.GENERATED, "doc-1")
        val second = staleContract(ContractStatus.GENERATED, "doc-2")
        provider.fail("doc-1", ProviderException.Unavailable("PandaDoc fora do ar"))
        provider.respond("doc-2", ProviderDocumentStatus.SENT)

        val consulted = reconcile.executeStale()

        assertEquals(1, consulted)
        assertEquals(listOf(document("doc-1"), document("doc-2")), provider.consulted)
        assertEquals(ContractStatus.SENT, second.status)
    }

    @Test
    fun `falha inesperada em um candidato não impede os demais`() {
        staleContract(ContractStatus.GENERATED, "doc-1")
        val second = staleContract(ContractStatus.GENERATED, "doc-2")
        provider.fail("doc-1", IllegalStateException("falha inesperada"))
        provider.respond("doc-2", ProviderDocumentStatus.SENT)

        val consulted = reconcile.executeStale()

        assertEquals(1, consulted)
        assertEquals(ContractStatus.SENT, second.status)
    }

    @Test
    fun `consulta que não muda o contrato só marca a reconciliação sem regravar o agregado (RC2a)`() {
        val contract = staleContract(ContractStatus.SENT, "doc-1")
        provider.respond("doc-1", ProviderDocumentStatus.SENT)

        reconcile.executeStale()

        assertEquals(0, contracts.saveCalls)
        assertEquals(listOf(contract.id to now), contracts.reconciliationMarks)
        assertEquals(now, contract.lastReconciledAt)
        assertTrue(contract.pullHistory().isEmpty())
    }

    @Test
    fun `reconciliar duas vezes contrato PARTIALLY_SIGNED com o provedor em sent não registra regressão nem regrava (RC2b)`() {
        val contract = staleContract(ContractStatus.PARTIALLY_SIGNED, "doc-1", signersDone = setOf(SignerRole.TENANT))
        provider.respond("doc-1", ProviderDocumentStatus.SENT, completedRoles = setOf(SignerRole.TENANT))

        reconcile.executeStale()
        clock.instant = now.plus(Duration.ofMinutes(15))
        reconcile.executeStale()

        assertEquals(2, provider.consulted.size)
        assertEquals(ContractStatus.PARTIALLY_SIGNED, contract.status)
        assertEquals(clock.instant, contract.lastReconciledAt)
        assertEquals(0, contract.pullHistory().count { it.outcome == HistoryOutcome.IGNORED_TRANSITION })
        assertEquals(0, contracts.saveCalls)
        assertEquals(listOf(contract.id to now, contract.id to clock.instant), contracts.reconciliationMarks)
        assertTrue(metrics.counted.isEmpty())
    }

    @Test
    fun `reconciliação manual de contrato final continua registrando a rejeição (R5)`() {
        val contract = staleContract(ContractStatus.COMPLETED, "doc-1")
        provider.respond("doc-1", ProviderDocumentStatus.SENT)

        val summary = reconcile.executeFor(contract.id)

        assertEquals(contract.leaseId, summary.leaseId)
        assertEquals(ContractStatus.COMPLETED, summary.status)
        assertEquals(ContractStatus.COMPLETED, contract.status)
        val history = contract.pullHistory().single()
        assertEquals(HistoryOutcome.IGNORED_TRANSITION, history.outcome)
    }

    @Test
    fun `reconciliação forçada de contrato sem documento no provedor devolve status atual e não consulta provedor (ADR-018)`() {
        val contract = restoreContract(
            status = ContractStatus.DRAFT,
            providerDocumentId = null,
            now = now,
        ).also(contracts.storage::save)

        val summary = reconcile.executeFor(contract.id)

        assertEquals(contract.leaseId, summary.leaseId)
        assertEquals(ContractStatus.DRAFT, summary.status)
        assertTrue(provider.consulted.isEmpty())
    }

    @Test
    fun `reconciliação forçada com transição devolve status atualizado (ADR-018)`() {
        val contract = staleContract(ContractStatus.SENT, "doc-1")
        provider.respond("doc-1", ProviderDocumentStatus.COMPLETED)

        val summary = reconcile.executeFor(contract.id)

        assertEquals(contract.leaseId, summary.leaseId)
        assertEquals(ContractStatus.COMPLETED, summary.status)
        assertEquals(ContractStatus.COMPLETED, contract.status)
    }

    @Test
    fun `reconciliação forçada de contrato inexistente lança NotFound`() {
        val unknownId = br.com.locasign.contract.domain.valueobjects.ContractId.new()

        kotlin.test.assertFailsWith<br.com.locasign.shared.domain.DomainException.NotFound> {
            reconcile.executeFor(unknownId)
        }
    }

    /** Provedor que responde à consulta de estado por documento e registra a ordem das consultas. */
    private class ScriptedStateProvider : SignatureProviderPort {
        val consulted = mutableListOf<ProviderDocumentId>()
        private val states = mutableMapOf<ProviderDocumentId, ProviderDocumentState>()
        private val failures = mutableMapOf<ProviderDocumentId, RuntimeException>()

        fun respond(
            documentId: String,
            status: ProviderDocumentStatus,
            completedRoles: Set<SignerRole> = emptySet(),
        ) {
            val id = ProviderDocumentId.of(documentId)
            states[id] = ProviderDocumentState(id, status, completedRoles, modifiedAt = null)
        }

        fun fail(documentId: String, failure: RuntimeException) {
            failures[ProviderDocumentId.of(documentId)] = failure
        }

        override fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState {
            consulted += documentId
            failures[documentId]?.let { throw it }
            return checkNotNull(states[documentId]) { "Sem resposta configurada para $documentId" }
        }

        override fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId = unused()

        override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) = unused()

        override fun cancelDocument(documentId: ProviderDocumentId) = unused()

        override fun downloadSigned(documentId: ProviderDocumentId): SignedDocument = unused()

        private fun unused(): Nothing = error("A reconciliação só consulta o estado do documento")
    }
}

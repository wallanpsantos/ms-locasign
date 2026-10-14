package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.domain.events.ContractGenerationFailed
import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.HistoryOutcome
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.RecordingMetricsPort
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.domain.valueobjects.Email
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplyProviderUpdateTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()
    private val metrics = RecordingMetricsPort()
    private val document = ProviderDocumentId.of("doc-1")

    private val applyProviderUpdate = ApplyProviderUpdate(
        contracts = contracts,
        persister = ContractPersister(contracts, publisher),
        clock = FixedBusinessClock(now),
        settings = ContractSettings(
            signatureDeadline = Duration.ofDays(7),
            reminderAfter = Duration.ofDays(3),
            reconciliationStaleAfter = Duration.ofHours(1),
            jobBatchSize = 50,
        ),
        metrics = metrics,
        transactions = ImmediateTransactionRunner,
    )

    private val signers = listOf(
        Signer(SignerRole.TENANT, "Juliana Mendes", Email.of("juliana@example.com"), SigningOrder.of(1)),
        Signer(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), SigningOrder.of(2)),
    )

    private fun contractIn(status: ContractStatus, providerLastModifiedAt: Instant? = null): Contract = restoreContract(
        status = status,
        leaseId = LeaseId.new(),
        signers = signers,
        now = now.minusSeconds(3600),
        providerDocumentId = document,
        providerLastModifiedAt = providerLastModifiedAt,
    ).also(contracts::save)

    private fun receive(signal: ProviderSignal) =
        applyProviderUpdate.execute(ApplyProviderUpdateCommand(listOf(signal), ChangeSource.WEBHOOK, "evt-1"))

    private fun documentError(modifiedAt: Instant?) =
        ProviderSignal.StatusChanged(document, null, ProviderDocumentStatus.ERROR, modifiedAt)

    private fun transition(metric: String, target: ContractStatus) = metric to listOf("target", target.name)

    @Test
    fun `falha de criação cancela o contrato e conta a transição aplicada para CANCELLED`() {
        val contract = contractIn(ContractStatus.DRAFT)

        receive(ProviderSignal.CreationFailed(document, null, "Modelo sem o campo Locatario.CPF."))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(CancelReason.GenerationFailed("Modelo sem o campo Locatario.CPF."), contract.cancelReason)
        val event = assertIs<ContractGenerationFailed>(publisher.published.single())
        assertEquals("Modelo sem o campo Locatario.CPF.", event.detail)
        val history = contract.pullHistory().single()
        assertEquals("Modelo sem o campo Locatario.CPF.", history.note)
        assertEquals(ChangeSource.WEBHOOK, history.source)
        assertEquals("evt-1", history.sourceEventId)
        assertEquals(listOf(transition(Metrics.TRANSITION_APPLIED, ContractStatus.CANCELLED)), metrics.counted)
    }

    @Test
    fun `falha de criação sem detalhe cancela com a nota padrão e sem data de modificação do provedor`() {
        val contract = contractIn(ContractStatus.DRAFT)

        receive(ProviderSignal.CreationFailed(document, null, null))

        assertEquals(CancelReason.GenerationFailed(null), contract.cancelReason)
        assertNull(contract.providerLastModifiedAt)
        assertNull(assertIs<ContractGenerationFailed>(publisher.published.single()).detail)
        assertEquals("O provedor reportou falha na criação do documento.", contract.pullHistory().single().note)
        assertEquals(listOf(transition(Metrics.TRANSITION_APPLIED, ContractStatus.CANCELLED)), metrics.counted)
    }

    @Test
    fun `document error em DRAFT cancela com o detalhe padrão e grava a data de modificação do provedor`() {
        val contract = contractIn(ContractStatus.DRAFT)
        val modifiedAt = now.minusSeconds(30)

        receive(documentError(modifiedAt))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(
            CancelReason.GenerationFailed("O provedor reportou document.error na criação do documento."),
            contract.cancelReason,
        )
        assertEquals(modifiedAt, contract.providerLastModifiedAt)
        assertEquals(listOf(transition(Metrics.TRANSITION_APPLIED, ContractStatus.CANCELLED)), metrics.counted)
    }

    @Test
    fun `document error em DRAFT mais antigo que a última atualização aplicada é ignorado (ADR-010)`() {
        val contract = contractIn(ContractStatus.DRAFT, providerLastModifiedAt = now.minusSeconds(10))

        receive(documentError(now.minusSeconds(30)))

        assertEquals(ContractStatus.DRAFT, contract.status)
        assertEquals(HistoryOutcome.IGNORED_TRANSITION, contract.pullHistory().single().outcome)
        assertEquals(listOf(transition(Metrics.TRANSITION_IGNORED, ContractStatus.CANCELLED)), metrics.counted)
    }

    @Test
    fun `document error fora de DRAFT registra anomalia sem mudar o status nem contar métrica`() {
        val contract = contractIn(ContractStatus.SENT)

        receive(documentError(now.minusSeconds(30)))

        assertEquals(ContractStatus.SENT, contract.status)
        val history = contract.pullHistory().single()
        assertEquals(HistoryOutcome.INFO, history.outcome)
        assertEquals("ANOMALIA: provedor reportou document.error sem pedido do LocaSign.", history.note)
        assertTrue(metrics.counted.isEmpty())
    }

    @Test
    fun `falha de criação em contrato concluído é ignorada e conta a transição ignorada (R5)`() {
        val contract = contractIn(ContractStatus.COMPLETED)

        receive(ProviderSignal.CreationFailed(document, null, null))

        assertEquals(ContractStatus.COMPLETED, contract.status)
        assertEquals(HistoryOutcome.IGNORED_TRANSITION, contract.pullHistory().single().outcome)
        assertEquals(listOf(transition(Metrics.TRANSITION_IGNORED, ContractStatus.CANCELLED)), metrics.counted)
    }

    @Test
    fun `falha de criação em contrato já cancelado não muda nada nem conta métrica`() {
        val contract = contractIn(ContractStatus.CANCELLED)

        receive(ProviderSignal.CreationFailed(document, null, null))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertTrue(contract.pullHistory().isEmpty())
        assertTrue(publisher.published.isEmpty())
        assertTrue(metrics.counted.isEmpty())
    }
}

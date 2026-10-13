package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.events.ContractCompleted
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.events.ContractSent
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Email
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContractTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val leaseId = LeaseId.new()

    private val tenantSigner = Signer(
        role = SignerRole.TENANT,
        name = "João da Silva",
        email = Email.of("joao@example.com"),
        order = SigningOrder.of(1),
    )

    private val agencySigner = Signer(
        role = SignerRole.AGENCY,
        name = "Imobiliária Central",
        email = Email.of("contato@imobiliaria.com"),
        order = SigningOrder.of(2),
    )

    private val signers = listOf(tenantSigner, agencySigner)

    /** Prazo de assinatura do R4. */
    private val signatureDeadline = Duration.ofDays(7)

    private fun restoreWithStatus(status: ContractStatus): Contract = restoreContract(
        status = status,
        leaseId = leaseId,
        signers = signers,
        now = now,
        providerDocumentId = ProviderDocumentId.of("doc-abc"),
        providerLastModifiedAt = now,
        sentAt = now,
        expiresAt = now.plus(signatureDeadline),
    )

    private fun webhookChange(target: ContractStatus, eventId: String) = StatusChange(
        target = target,
        source = ChangeSource.WEBHOOK,
        sourceEventId = eventId,
    )

    @Test
    fun `solicitação de contrato exige locatário em primeiro e imobiliária em segundo (R3)`() {
        val contract = Contract.request(leaseId, 1, signers, now)

        assertEquals(ContractStatus.DRAFT, contract.status)
        assertEquals(1, contract.versionNumber)
        assertEquals(leaseId, contract.leaseId)

        val events = contract.pullEvents()
        assertEquals(1, events.size)
        assertIs<ContractRequested>(events.first())

        val history = contract.pullHistory()
        assertEquals(1, history.size)
        assertEquals(ContractStatus.DRAFT, history.first().toStatus)
        assertEquals(HistoryOutcome.APPLIED, history.first().outcome)
    }

    @Test
    fun `solicitação rejeita signatários com ordem invertida ou incompleta (R3)`() {
        val invertedSigners = listOf(
            agencySigner.copy(order = SigningOrder.of(1)),
            tenantSigner.copy(order = SigningOrder.of(2)),
        )

        assertFailsWith<DomainException.BusinessRuleViolation> {
            Contract.request(leaseId, 1, invertedSigners, now)
        }

        val onlyTenant = listOf(tenantSigner)
        assertFailsWith<DomainException.BusinessRuleViolation> {
            Contract.request(leaseId, 1, onlyTenant, now)
        }
    }

    @Test
    fun `transição para SENT define expiração para 7 dias e emite ContractSent (R4)`() {
        val contract = Contract.request(leaseId, 1, signers, now)
        contract.pullEvents()
        contract.pullHistory()

        val docId = ProviderDocumentId.of("doc-abc")
        contract.attachProviderDocument(docId, ChangeSource.WEBHOOK, "evt-doc-created", now)
        contract.apply(webhookChange(ContractStatus.SENT, "evt-sent"), now, signatureDeadline)

        assertEquals(ContractStatus.SENT, contract.status)
        assertEquals(now.plus(signatureDeadline), contract.expiresAt)

        val events = contract.pullEvents()
        assertTrue(events.any { it is ContractSent })
    }

    @Test
    fun `transição para COMPLETED emite ContractCompleted (R5)`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        contract.apply(
            webhookChange(ContractStatus.COMPLETED, "evt-completed"),
            now.plusSeconds(3600),
            signatureDeadline
        )

        assertEquals(ContractStatus.COMPLETED, contract.status)
        val events = contract.pullEvents()
        assertEquals(1, events.size)
        assertIs<ContractCompleted>(events.first())
    }

    @Test
    fun `transição com status anterior ou inválido registra IGNORED_TRANSITION na auditoria (R5)`() {
        val contract = restoreWithStatus(ContractStatus.VIEWED)

        // De VIEWED para SENT é regressão de status
        contract.apply(webhookChange(ContractStatus.SENT, "evt-old-sent"), now.plusSeconds(60), signatureDeadline)

        assertEquals(ContractStatus.VIEWED, contract.status)
        assertTrue(contract.pullEvents().isEmpty())

        val history = contract.pullHistory()
        assertEquals(1, history.size)
        assertEquals(HistoryOutcome.IGNORED_TRANSITION, history.first().outcome)
    }
}

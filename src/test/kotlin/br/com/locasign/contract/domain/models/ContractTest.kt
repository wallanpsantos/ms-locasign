package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.events.ContractCompleted
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.events.ContractSent
import br.com.locasign.contract.domain.valueobjects.ContractId
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
import kotlin.test.assertNotNull
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

    @Test
    fun `solicitação de contrato exige locatário em primeiro e imobiliária em segundo (R3)`() {
        val validSigners = listOf(tenantSigner, agencySigner)
        val contract = Contract.request(leaseId, 1, validSigners, now)

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
        val contract = Contract.request(leaseId, 1, listOf(tenantSigner, agencySigner), now)
        contract.pullEvents()
        contract.pullHistory()

        val docId = ProviderDocumentId.of("doc-abc")
        contract.attachProviderDocument(docId, ChangeSource.WEBHOOK, "evt-doc-created", now)
        contract.apply(
            StatusChange(
                target = ContractStatus.SENT,
                source = ChangeSource.WEBHOOK,
                sourceEventId = "evt-sent",
            ),
            now,
            Duration.ofDays(7),
        )

        assertEquals(ContractStatus.SENT, contract.status)
        assertNotNull(contract.expiresAt)
        assertEquals(now.plus(Duration.ofDays(7)), contract.expiresAt)

        val events = contract.pullEvents()
        assertTrue(events.any { it is ContractSent })
    }

    @Test
    fun `transição para COMPLETED emite ContractCompleted (R5)`() {
        val contract = Contract.restore(
            id = ContractId.new(),
            leaseId = leaseId,
            versionNumber = 1,
            status = ContractStatus.SENT,
            providerDocumentId = ProviderDocumentId.of("doc-abc"),
            providerLastModifiedAt = now,
            sentAt = now,
            expiresAt = now.plus(Duration.ofDays(7)),
            reminderSentAt = null,
            lastReconciledAt = null,
            cancelReason = null,
            signedDocumentRef = null,
            signers = listOf(tenantSigner, agencySigner),
            createdAt = now,
            updatedAt = now,
            rowVersion = 1L,
        )

        contract.apply(
            StatusChange(
                target = ContractStatus.COMPLETED,
                source = ChangeSource.WEBHOOK,
                sourceEventId = "evt-completed",
            ),
            now.plusSeconds(3600),
            Duration.ofDays(7),
        )

        assertEquals(ContractStatus.COMPLETED, contract.status)
        val events = contract.pullEvents()
        assertEquals(1, events.size)
        assertIs<ContractCompleted>(events.first())
    }

    @Test
    fun `transição com status anterior ou inválido registra IGNORED_TRANSITION na auditoria (R5)`() {
        val contract = Contract.restore(
            id = ContractId.new(),
            leaseId = leaseId,
            versionNumber = 1,
            status = ContractStatus.VIEWED,
            providerDocumentId = ProviderDocumentId.of("doc-abc"),
            providerLastModifiedAt = now,
            sentAt = now,
            expiresAt = now.plus(Duration.ofDays(7)),
            reminderSentAt = null,
            lastReconciledAt = null,
            cancelReason = null,
            signedDocumentRef = null,
            signers = listOf(tenantSigner, agencySigner),
            createdAt = now,
            updatedAt = now,
            rowVersion = 1L,
        )

        contract.apply(
            StatusChange(
                target = ContractStatus.SENT, // Regressão de status
                source = ChangeSource.WEBHOOK,
                sourceEventId = "evt-old-sent",
            ),
            now.plusSeconds(60),
            Duration.ofDays(7),
        )

        assertEquals(ContractStatus.VIEWED, contract.status)
        assertTrue(contract.pullEvents().isEmpty())

        val history = contract.pullHistory()
        assertEquals(1, history.size)
        assertEquals(HistoryOutcome.IGNORED_TRANSITION, history.first().outcome)
    }
}

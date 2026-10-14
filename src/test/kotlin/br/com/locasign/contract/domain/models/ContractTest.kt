package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.events.ContractCancelled
import br.com.locasign.contract.domain.events.ContractCompleted
import br.com.locasign.contract.domain.events.ContractExpired
import br.com.locasign.contract.domain.events.ContractGenerationFailed
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")

    /** Instante da operação sob teste, distinto do `now` usado para montar o contrato. */
    private val later = now.plusSeconds(60)

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

    /** Confere a única linha de histórico pendente, campo a campo (o `id` é aleatório). */
    private fun assertSingleHistory(
        contract: Contract,
        from: ContractStatus?,
        to: ContractStatus,
        outcome: HistoryOutcome,
        source: ChangeSource,
        sourceEventId: String?,
        note: String?,
        occurredAt: Instant = later,
    ) {
        val entry = contract.pullHistory().single()
        val expected = StatusHistoryEntry(
            id = entry.id,
            contractId = contract.id,
            fromStatus = from,
            toStatus = to,
            outcome = outcome,
            source = source,
            sourceEventId = sourceEventId,
            note = note,
            occurredAt = occurredAt,
        )
        assertEquals(expected, entry)
    }

    /** Fato que não muda o status: origem e destino são o status atual e o resultado é INFO. */
    private fun assertSingleInfo(
        contract: Contract,
        status: ContractStatus,
        source: ChangeSource,
        sourceEventId: String?,
        note: String,
    ) = assertSingleHistory(contract, status, status, HistoryOutcome.INFO, source, sourceEventId, note)

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

    @Test
    fun `solicitação registra a criação em DRAFT no histórico com a versão na nota`() {
        val contract = Contract.request(leaseId, 3, signers, later)

        assertSingleHistory(
            contract,
            from = null,
            to = ContractStatus.DRAFT,
            outcome = HistoryOutcome.APPLIED,
            source = ChangeSource.API,
            sourceEventId = null,
            note = "Contrato v3 solicitado.",
        )
    }

    @Test
    fun `transição aplicada registra origem, destino, fonte e nota da mudança no histórico`() {
        val contract = restoreWithStatus(ContractStatus.SENT)
        val change = webhookChange(ContractStatus.VIEWED, "evt-viewed").copy(note = "Documento visualizado.")

        contract.apply(change, later, signatureDeadline)

        assertSingleHistory(
            contract,
            from = ContractStatus.SENT,
            to = ContractStatus.VIEWED,
            outcome = HistoryOutcome.APPLIED,
            source = ChangeSource.WEBHOOK,
            sourceEventId = "evt-viewed",
            note = "Documento visualizado.",
        )
    }

    @Test
    fun `transição rejeitada pela política grava o motivo da rejeição como nota (R5)`() {
        val contract = restoreWithStatus(ContractStatus.VIEWED)

        contract.apply(webhookChange(ContractStatus.SENT, "evt-old-sent"), later, signatureDeadline)

        assertSingleHistory(
            contract,
            from = ContractStatus.VIEWED,
            to = ContractStatus.SENT,
            outcome = HistoryOutcome.IGNORED_TRANSITION,
            source = ChangeSource.WEBHOOK,
            sourceEventId = "evt-old-sent",
            note = "A transição VIEWED -> SENT regrediria o status.",
        )
    }

    @Test
    fun `atualização do provedor mais antiga que a última aplicada é rejeitada no histórico (ADR-010)`() {
        val contract = restoreWithStatus(ContractStatus.SENT)
        val change = webhookChange(ContractStatus.VIEWED, "evt-late").copy(providerModifiedAt = now.minusSeconds(1))

        contract.apply(change, later, signatureDeadline)

        assertEquals(ContractStatus.SENT, contract.status)
        assertSingleHistory(
            contract,
            from = ContractStatus.SENT,
            to = ContractStatus.VIEWED,
            outcome = HistoryOutcome.IGNORED_TRANSITION,
            source = ChangeSource.WEBHOOK,
            sourceEventId = "evt-late",
            note = "Atualização do provedor mais antiga que a última aplicada.",
        )
    }

    @Test
    fun `documento criado no provedor é registrado como fato INFO sem mudar o status`() {
        val contract = restoreContract(ContractStatus.DRAFT, leaseId, signers, now)

        contract.attachProviderDocument(ProviderDocumentId.of("doc-1"), ChangeSource.WEBHOOK, "evt-doc", later)

        assertSingleInfo(
            contract,
            ContractStatus.DRAFT,
            ChangeSource.WEBHOOK,
            "evt-doc",
            "Documento criado no provedor.",
        )
    }

    @Test
    fun `assinatura de um signatário é registrada como fato INFO com o papel na nota`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        contract.recordSignerCompleted(SignerRole.TENANT, ChangeSource.WEBHOOK, "evt-signer", later)

        assertSingleInfo(
            contract,
            ContractStatus.SENT,
            ChangeSource.WEBHOOK,
            "evt-signer",
            "Signatário TENANT assinou.",
        )
    }

    @Test
    fun `lembrete de assinatura é registrado como fato INFO do agendador (R4)`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        contract.markReminderSent(later)

        assertSingleInfo(
            contract,
            ContractStatus.SENT,
            ChangeSource.SCHEDULER,
            null,
            "Lembrete de assinatura registrado.",
        )
    }

    @Test
    fun `arquivamento do PDF assinado é registrado como fato INFO com o tipo de armazenamento`() {
        val contract = restoreWithStatus(ContractStatus.COMPLETED)

        contract.markSignedDocumentArchived("FILE", "contratos/doc-abc.pdf", ChangeSource.WEBHOOK, "evt-pdf", later)

        assertSingleInfo(
            contract,
            ContractStatus.COMPLETED,
            ChangeSource.WEBHOOK,
            "evt-pdf",
            "PDF assinado arquivado (FILE).",
        )
    }

    @Test
    fun `ativação da locação é registrada como fato INFO da API (R8)`() {
        val contract = restoreWithStatus(ContractStatus.COMPLETED)

        contract.recordLeaseActivated(later)

        assertSingleInfo(contract, ContractStatus.COMPLETED, ChangeSource.API, null, "Locação ativada.")
    }

    @Test
    fun `anomalia é registrada como fato INFO com o prefixo ANOMALIA na nota`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        contract.recordAnomaly("documento removido no provedor", ChangeSource.RECONCILIATION, "evt-rec", later)

        assertSingleInfo(
            contract,
            ContractStatus.SENT,
            ChangeSource.RECONCILIATION,
            "evt-rec",
            "ANOMALIA: documento removido no provedor",
        )
    }

    @Test
    fun `cancelamento pedido grava o motivo, emite ContractCancelled e não abre prazo de assinatura`() {
        val contract = restoreContract(ContractStatus.DRAFT, leaseId, signers, now)

        assertEquals(TransitionResult.APPLIED, contract.cancel("motivo", later))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(CancelReason.Requested("motivo"), contract.cancelReason)
        assertNull(contract.sentAt)
        assertNull(contract.expiresAt)
        val event = assertIs<ContractCancelled>(contract.pullEvents().single())
        assertEquals(ContractCancelled(contract.id, "motivo", later, event.eventId), event)
        assertSingleHistory(
            contract,
            from = ContractStatus.DRAFT,
            to = ContractStatus.CANCELLED,
            outcome = HistoryOutcome.APPLIED,
            source = ChangeSource.API,
            sourceEventId = null,
            note = "motivo",
        )
    }

    @Test
    fun `falha de geração sem detalhe grava a nota padrão e emite ContractGenerationFailed sem detalhe`() {
        val contract = restoreContract(ContractStatus.DRAFT, leaseId, signers, now)

        contract.failGeneration(null, ChangeSource.WEBHOOK, "evt-fail", later)

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(CancelReason.GenerationFailed(null), contract.cancelReason)
        assertNull(contract.expiresAt)
        val event = assertIs<ContractGenerationFailed>(contract.pullEvents().single())
        assertEquals(ContractGenerationFailed(contract.id, null, later, event.eventId), event)
        assertSingleHistory(
            contract,
            from = ContractStatus.DRAFT,
            to = ContractStatus.CANCELLED,
            outcome = HistoryOutcome.APPLIED,
            source = ChangeSource.WEBHOOK,
            sourceEventId = "evt-fail",
            note = "O provedor reportou falha na criação do documento.",
        )
    }

    @Test
    fun `falha de geração com detalhe usa o detalhe como nota e no evento`() {
        val contract = restoreContract(ContractStatus.GENERATED, leaseId, signers, now)

        contract.failGeneration("modelo inválido", ChangeSource.WEBHOOK, "evt-fail", later)

        assertEquals(CancelReason.GenerationFailed("modelo inválido"), contract.cancelReason)
        val event = assertIs<ContractGenerationFailed>(contract.pullEvents().single())
        assertEquals("modelo inválido", event.detail)
        assertEquals("modelo inválido", contract.pullHistory().single().note)
    }

    @Test
    fun `cancelamento sem motivo nem nota grava o texto padrão Cancelado e não grava nota no histórico`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        contract.apply(StatusChange(ContractStatus.CANCELLED, ChangeSource.API), later, signatureDeadline)

        assertEquals(CancelReason.Requested("Cancelado."), contract.cancelReason)
        val event = assertIs<ContractCancelled>(contract.pullEvents().single())
        assertEquals(ContractCancelled(contract.id, "Cancelado.", later, event.eventId), event)
        assertNull(contract.pullHistory().single().note)
    }

    @Test
    fun `cancelamento sem motivo explícito usa a nota da mudança como motivo`() {
        val contract = restoreWithStatus(ContractStatus.SENT)
        val change = StatusChange(ContractStatus.CANCELLED, ChangeSource.RECONCILIATION, note = "Anulado no provedor.")

        contract.apply(change, later, signatureDeadline)

        assertEquals(CancelReason.Requested("Anulado no provedor."), contract.cancelReason)
        val event = assertIs<ContractCancelled>(contract.pullEvents().single())
        assertEquals("Anulado no provedor.", event.reason)
        assertEquals("Anulado no provedor.", contract.pullHistory().single().note)
    }

    @Test
    fun `motivo explícito prevalece sobre a nota no cancelamento e a nota fica só no histórico`() {
        val contract = restoreWithStatus(ContractStatus.SENT)
        val change = StatusChange(
            target = ContractStatus.CANCELLED,
            source = ChangeSource.API,
            note = "Nota do operador.",
            cancelReason = CancelReason.Requested("Desistência do locatário."),
        )

        contract.apply(change, later, signatureDeadline)

        assertEquals(CancelReason.Requested("Desistência do locatário."), contract.cancelReason)
        val event = assertIs<ContractCancelled>(contract.pullEvents().single())
        assertEquals("Desistência do locatário.", event.reason)
        assertEquals("Nota do operador.", contract.pullHistory().single().note)
    }

    @Test
    fun `prazo vencido expira o contrato sem alterar a data de expiração (R4)`() {
        val deadline = now
        val contract = restoreContract(
            status = ContractStatus.SENT,
            leaseId = leaseId,
            signers = signers,
            now = now,
            sentAt = now.minus(signatureDeadline),
            expiresAt = deadline,
        )

        assertEquals(TransitionResult.APPLIED, contract.expireIfOverdue(later))

        assertEquals(ContractStatus.EXPIRED, contract.status)
        assertEquals(deadline, contract.expiresAt)
        val event = assertIs<ContractExpired>(contract.pullEvents().single())
        assertEquals(ContractExpired(contract.id, later, event.eventId), event)
        assertSingleHistory(
            contract,
            from = ContractStatus.SENT,
            to = ContractStatus.EXPIRED,
            outcome = HistoryOutcome.APPLIED,
            source = ChangeSource.SCHEDULER,
            sourceEventId = null,
            note = "Prazo de assinatura vencido em 2026-10-09T10:00:00Z.",
        )
    }

    @Test
    fun `prazo ainda aberto não expira o contrato (R4)`() {
        val contract = restoreWithStatus(ContractStatus.SENT)

        assertEquals(TransitionResult.NO_CHANGE, contract.expireIfOverdue(later))

        assertEquals(ContractStatus.SENT, contract.status)
        assertTrue(contract.pullEvents().isEmpty())
        assertTrue(contract.pullHistory().isEmpty())
    }
}

package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.events.ContractCancelled
import br.com.locasign.contract.domain.events.ContractCompleted
import br.com.locasign.contract.domain.events.ContractDeclined
import br.com.locasign.contract.domain.events.ContractDocumentCreated
import br.com.locasign.contract.domain.events.ContractEvent
import br.com.locasign.contract.domain.events.ContractExpired
import br.com.locasign.contract.domain.events.ContractGenerated
import br.com.locasign.contract.domain.events.ContractGenerationFailed
import br.com.locasign.contract.domain.events.ContractReminderSent
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.events.ContractSent
import br.com.locasign.contract.domain.events.ContractSignerCompleted
import br.com.locasign.contract.domain.events.ContractViewed
import br.com.locasign.contract.domain.events.LeaseActivated
import br.com.locasign.contract.domain.events.SignedDocumentArchived
import br.com.locasign.contract.domain.services.ContractTransitionPolicy
import br.com.locasign.contract.domain.services.TransitionDecision
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import java.time.Duration
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Raiz de agregação do contrato de locação, atuando como a única fonte da verdade sobre o status do documento.
 *
 * **Responsabilidade:**
 * - Centralizar e proteger as invariantes de negócio e ciclo de vida do contrato (princípio 1 da arquitetura e regras R1 a R10).
 * - Submeter qualquer intenção de mutação de estado à política de transições ([ContractTransitionPolicy]) antes de efetivá-la.
 * - Gerenciar a sequência estrita de assinaturas (R3), prazos de expiração (R4), lembretes de pendência (R7) e idempotência/reconciliação (R5, R6, R8).
 * - Acumular eventos de domínio ([ContractEvent]) e entradas de histórico ([StatusHistoryEntry]) para descarregamento transacional atômico pelos casos de uso.
 */
class Contract private constructor(
    val id: ContractId,
    val leaseId: LeaseId,
    val versionNumber: Int,
    status: ContractStatus,
    providerDocumentId: ProviderDocumentId?,
    providerLastModifiedAt: Instant?,
    sentAt: Instant?,
    expiresAt: Instant?,
    reminderSentAt: Instant?,
    lastReconciledAt: Instant?,
    cancelReason: CancelReason?,
    signedDocumentRef: String?,
    signers: List<Signer>,
    val createdAt: Instant,
    updatedAt: Instant,
    val rowVersion: Long?,
) {
    var status: ContractStatus = status
        private set

    var providerDocumentId: ProviderDocumentId? = providerDocumentId
        private set

    var providerLastModifiedAt: Instant? = providerLastModifiedAt
        private set

    var sentAt: Instant? = sentAt
        private set

    var expiresAt: Instant? = expiresAt
        private set

    var reminderSentAt: Instant? = reminderSentAt
        private set

    var lastReconciledAt: Instant? = lastReconciledAt
        private set

    var cancelReason: CancelReason? = cancelReason
        private set

    var signedDocumentRef: String? = signedDocumentRef
        private set

    var signers: List<Signer> = signers
        private set

    var updatedAt: Instant = updatedAt
        private set

    val pendingEvents: List<ContractEvent>
        field = mutableListOf<ContractEvent>()

    val pendingHistory: List<StatusHistoryEntry>
        field = mutableListOf<StatusHistoryEntry>()

    /** Entrega e limpa os eventos pendentes, para publicação pelo outbox. */
    fun pullEvents(): List<ContractEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    /** Entrega e limpa as linhas de auditoria pendentes, para persistência. */
    fun pullHistory(): List<StatusHistoryEntry> = pendingHistory.toList().also { pendingHistory.clear() }

    fun signer(role: SignerRole): Signer? = signers.firstOrNull { it.role == role }

    val hasProviderDocument: Boolean get() = providerDocumentId != null

    /** Há eventos ou linhas de auditoria esperando para serem persistidos. */
    val hasPendingChanges: Boolean get() = pendingEvents.isNotEmpty() || pendingHistory.isNotEmpty()

    /** Guarda o identificador do documento criado no provedor. Não muda o status. */
    fun attachProviderDocument(
        documentId: ProviderDocumentId,
        source: ChangeSource,
        sourceEventId: String?,
        now: Instant,
    ): Boolean {
        if (providerDocumentId != null) return false
        providerDocumentId = documentId
        touch(now)
        pendingEvents += ContractDocumentCreated(id, documentId, now)
        recordHistory(status, HistoryOutcome.INFO, source, sourceEventId, "Documento criado no provedor.", now)
        return true
    }

    /**
     * Aplica uma mudança de status pedida por uma origem (API, webhook, reconciliação ou job).
     * A política decide: aplicar, ignorar silenciosamente (mesmo status) ou rejeitar com registro.
     */
    fun apply(change: StatusChange, now: Instant, signatureDeadline: Duration): TransitionResult {
        val modifiedAt = change.providerModifiedAt
        val lastApplied = providerLastModifiedAt
        if (modifiedAt != null && lastApplied != null && modifiedAt.isBefore(lastApplied)) {
            recordRejected(change, "Atualização do provedor mais antiga que a última aplicada.", now)
            return TransitionResult.IGNORED
        }
        return when (val decision = ContractTransitionPolicy.decide(status, change.target)) {
            TransitionDecision.NoOp -> TransitionResult.NO_CHANGE
            is TransitionDecision.Reject -> {
                recordRejected(change, decision.reason, now)
                TransitionResult.IGNORED
            }

            TransitionDecision.Apply -> {
                applyTransition(change, now, signatureDeadline)
                TransitionResult.APPLIED
            }
        }
    }

    /** Marca a assinatura de um signatário (idempotente). Ignorada em contrato final (R5). */
    fun recordSignerCompleted(
        role: SignerRole,
        source: ChangeSource,
        sourceEventId: String?,
        now: Instant,
    ): Boolean {
        if (status.isFinal) return false
        val signer = signer(role) ?: return false
        if (signer.hasCompleted) return false
        signers = signers.map { if (it.role == role) it.copy(completedAt = now) else it }
        touch(now)
        pendingEvents += ContractSignerCompleted(id, role, now)
        recordHistory(status, HistoryOutcome.INFO, source, sourceEventId, "Signatário $role assinou.", now)
        return true
    }

    /** Cancelamento pedido pelo corretor. Falha com [DomainException.ContractFinal] se já for final. */
    fun cancel(reason: String, now: Instant): TransitionResult {
        if (status.isFinal) throw DomainException.ContractFinal(id.toString(), status.name)
        return apply(
            StatusChange(
                target = ContractStatus.CANCELLED,
                source = ChangeSource.API,
                note = reason,
                cancelReason = CancelReason.Requested(reason),
            ),
            now,
            Duration.ZERO,
        )
    }

    /** O documento não pôde ser gerado nem enviado: cancela o contrato com o motivo de falha de geração. */
    fun failGeneration(
        detail: String?,
        source: ChangeSource,
        sourceEventId: String?,
        now: Instant,
        providerModifiedAt: Instant? = null,
    ): TransitionResult = apply(
        StatusChange(
            target = ContractStatus.CANCELLED,
            source = source,
            sourceEventId = sourceEventId,
            providerModifiedAt = providerModifiedAt,
            note = detail ?: DEFAULT_GENERATION_FAILURE_NOTE,
            cancelReason = CancelReason.GenerationFailed(detail),
        ),
        now,
        Duration.ZERO,
    )

    /** R4: expira o contrato cujo prazo venceu. Não faz nada se ele já mudou de estado. */
    fun expireIfOverdue(now: Instant): TransitionResult {
        val deadline = expiresAt
        if (!status.isAwaitingSignatures || deadline == null || deadline.isAfter(now)) {
            return TransitionResult.NO_CHANGE
        }
        return apply(
            StatusChange(
                target = ContractStatus.EXPIRED,
                source = ChangeSource.SCHEDULER,
                note = "Prazo de assinatura vencido em $deadline.",
            ),
            now,
            Duration.ZERO,
        )
    }

    /** Lembrete do 3º dia (R4): no máximo um por contrato. */
    fun markReminderSent(now: Instant): Boolean {
        if (!status.isAwaitingSignatures || reminderSentAt != null) return false
        reminderSentAt = now
        touch(now)
        pendingEvents += ContractReminderSent(id, now)
        recordHistory(
            status,
            HistoryOutcome.INFO,
            ChangeSource.SCHEDULER,
            null,
            "Lembrete de assinatura registrado.",
            now
        )
        return true
    }

    /** Registra o arquivamento do PDF assinado (ou apenas a referência, no sandbox). */
    fun markSignedDocumentArchived(
        storage: String,
        location: String,
        source: ChangeSource,
        sourceEventId: String?,
        now: Instant,
    ): Boolean {
        if (status != ContractStatus.COMPLETED || signedDocumentRef != null) return false
        signedDocumentRef = location
        touch(now)
        pendingEvents += SignedDocumentArchived(id, storage, location, now)
        recordHistory(status, HistoryOutcome.INFO, source, sourceEventId, "PDF assinado arquivado ($storage).", now)
        return true
    }

    /** Registra que a ação pós-assinatura ativou a locação (R8: só com o contrato concluído). */
    fun recordLeaseActivated(now: Instant): Boolean {
        if (status != ContractStatus.COMPLETED) return false
        touch(now)
        pendingEvents += LeaseActivated(id, leaseId, now)
        recordHistory(status, HistoryOutcome.INFO, ChangeSource.API, null, "Locação ativada.", now)
        return true
    }

    /** Registra uma anomalia (por exemplo, documento removido no provedor sem pedido nosso). */
    fun recordAnomaly(note: String, source: ChangeSource, sourceEventId: String?, now: Instant) {
        touch(now)
        recordHistory(status, HistoryOutcome.INFO, source, sourceEventId, "ANOMALIA: $note", now)
    }

    /** Marca que a reconciliação consultou o provedor, para não repetir a consulta a cada ciclo. */
    fun markReconciled(now: Instant) {
        lastReconciledAt = now
    }

    private fun applyTransition(change: StatusChange, now: Instant, signatureDeadline: Duration) {
        val from = status
        status = change.target
        change.providerModifiedAt?.let { modified ->
            providerLastModifiedAt = providerLastModifiedAt?.let { maxOf(it, modified) } ?: modified
        }
        when (change.target) {
            ContractStatus.SENT, ContractStatus.VIEWED, ContractStatus.PARTIALLY_SIGNED -> {
                // Cobre também saltos para frente (webhook de envio perdido): o prazo sempre existe.
                val sent = sentAt ?: now
                sentAt = sent
                expiresAt = expiresAt ?: sent.plus(signatureDeadline)
            }

            ContractStatus.COMPLETED -> signers = signers.map { it.copy(completedAt = it.completedAt ?: now) }
            ContractStatus.CANCELLED ->
                cancelReason = change.cancelReason ?: CancelReason.Requested(change.note ?: "Cancelado.")

            ContractStatus.DRAFT, ContractStatus.GENERATED, ContractStatus.DECLINED, ContractStatus.EXPIRED -> Unit
        }
        touch(now)
        eventFor(change, now)?.let { pendingEvents += it }
        pendingHistory += StatusHistoryEntry(
            id = Uuid.random(),
            contractId = id,
            fromStatus = from,
            toStatus = change.target,
            outcome = HistoryOutcome.APPLIED,
            source = change.source,
            sourceEventId = change.sourceEventId,
            note = change.note,
            occurredAt = now,
        )
    }

    private fun eventFor(change: StatusChange, now: Instant): ContractEvent? = when (change.target) {
        ContractStatus.DRAFT, ContractStatus.PARTIALLY_SIGNED -> null
        ContractStatus.GENERATED -> ContractGenerated(id, now)
        ContractStatus.SENT -> ContractSent(id, expiresAt, now)
        ContractStatus.VIEWED -> ContractViewed(id, now)
        ContractStatus.COMPLETED -> ContractCompleted(id, leaseId, now)
        ContractStatus.DECLINED -> ContractDeclined(id, change.note, now)
        ContractStatus.EXPIRED -> ContractExpired(id, now)
        ContractStatus.CANCELLED -> when (val reason = cancelReason) {
            is CancelReason.GenerationFailed -> ContractGenerationFailed(id, reason.detail, now)
            is CancelReason.Requested -> ContractCancelled(id, reason.text, now)
            null -> ContractCancelled(id, change.note ?: "Cancelado.", now)
        }
    }

    private fun recordRejected(change: StatusChange, reason: String, now: Instant) {
        pendingHistory += StatusHistoryEntry(
            id = Uuid.random(),
            contractId = id,
            fromStatus = status,
            toStatus = change.target,
            outcome = HistoryOutcome.IGNORED_TRANSITION,
            source = change.source,
            sourceEventId = change.sourceEventId,
            note = reason,
            occurredAt = now,
        )
    }

    private fun recordHistory(
        current: ContractStatus,
        outcome: HistoryOutcome,
        source: ChangeSource,
        sourceEventId: String?,
        note: String,
        now: Instant,
    ) {
        pendingHistory += StatusHistoryEntry(
            id = Uuid.random(),
            contractId = id,
            fromStatus = current,
            toStatus = current,
            outcome = outcome,
            source = source,
            sourceEventId = sourceEventId,
            note = note,
            occurredAt = now,
        )
    }

    private fun touch(now: Instant) {
        updatedAt = now
    }

    companion object {
        private const val DEFAULT_GENERATION_FAILURE_NOTE = "O provedor reportou falha na criação do documento."

        /** Cria o contrato em `DRAFT` (nova versão da locação) e emite `ContractRequested`. */
        fun request(leaseId: LeaseId, versionNumber: Int, signers: List<Signer>, now: Instant): Contract {
            requireSigningOrder(signers)
            val contract = Contract(
                id = ContractId.new(),
                leaseId = leaseId,
                versionNumber = versionNumber,
                status = ContractStatus.DRAFT,
                providerDocumentId = null,
                providerLastModifiedAt = null,
                sentAt = null,
                expiresAt = null,
                reminderSentAt = null,
                lastReconciledAt = null,
                cancelReason = null,
                signedDocumentRef = null,
                signers = signers,
                createdAt = now,
                updatedAt = now,
                rowVersion = null,
            )
            contract.pendingEvents += ContractRequested(contract.id, leaseId, versionNumber, now)
            contract.pendingHistory += StatusHistoryEntry(
                id = Uuid.random(),
                contractId = contract.id,
                fromStatus = null,
                toStatus = ContractStatus.DRAFT,
                outcome = HistoryOutcome.APPLIED,
                source = ChangeSource.API,
                sourceEventId = null,
                note = "Contrato v$versionNumber solicitado.",
                occurredAt = now,
            )
            return contract
        }

        /** Reconstitui o agregado a partir da persistência, sem eventos nem auditoria pendentes. */
        fun restore(
            id: ContractId,
            leaseId: LeaseId,
            versionNumber: Int,
            status: ContractStatus,
            providerDocumentId: ProviderDocumentId?,
            providerLastModifiedAt: Instant?,
            sentAt: Instant?,
            expiresAt: Instant?,
            reminderSentAt: Instant?,
            lastReconciledAt: Instant?,
            cancelReason: CancelReason?,
            signedDocumentRef: String?,
            signers: List<Signer>,
            createdAt: Instant,
            updatedAt: Instant,
            rowVersion: Long?,
        ): Contract = Contract(
            id, leaseId, versionNumber, status, providerDocumentId, providerLastModifiedAt, sentAt, expiresAt,
            reminderSentAt, lastReconciledAt, cancelReason, signedDocumentRef, signers, createdAt, updatedAt,
            rowVersion,
        )

        /** R3: primeiro o locatário, depois o representante da imobiliária. */
        private fun requireSigningOrder(signers: List<Signer>) {
            val tenant = signers.firstOrNull { it.role == SignerRole.TENANT }
            val agency = signers.firstOrNull { it.role == SignerRole.AGENCY }
            if (signers.size != 2 || tenant == null || agency == null || tenant.order.value >= agency.order.value) {
                throw DomainException.BusinessRuleViolation(
                    "signers",
                    "O contrato exige o locatário e, depois dele, o representante da imobiliária.",
                )
            }
        }
    }
}

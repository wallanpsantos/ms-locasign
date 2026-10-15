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
 * - Gerenciar a sequência estrita de assinaturas (R3), prazos de expiração (R4), lembretes de pendência (R4) e idempotência/reconciliação (R5, R6, R8).
 * - Acumular eventos de domínio ([ContractEvent]) e entradas de histórico ([StatusHistoryEntry]) para descarregamento transacional atômico pelos casos de uso.
 */
class Contract private constructor(
    val id: ContractId,
    val leaseId: LeaseId,
    val versionNumber: Int,
    status: ContractStatus,
    providerDocumentId: ProviderDocumentId? = null,
    providerLastModifiedAt: Instant? = null,
    sentAt: Instant? = null,
    expiresAt: Instant? = null,
    reminderSentAt: Instant? = null,
    lastReconciledAt: Instant? = null,
    cancelReason: CancelReason? = null,
    signedDocumentRef: String? = null,
    signers: List<Signer>,
    val createdAt: Instant,
    updatedAt: Instant,
    val rowVersion: Long? = null,
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

    private val pendingEvents = mutableListOf<ContractEvent>()
    private val pendingHistory = mutableListOf<StatusHistoryEntry>()

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
        recordInfo("Documento criado no provedor.", source, sourceEventId, now)
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
                if (change.source == ChangeSource.RECONCILIATION && !status.isFinal && change.target.progress < status.progress) {
                    TransitionResult.NO_CHANGE
                } else {
                    recordRejected(change, decision.reason, now)
                    TransitionResult.IGNORED
                }
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
        recordInfo("Signatário $role assinou.", source, sourceEventId, now)
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
            NO_SIGNATURE_DEADLINE,
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
        NO_SIGNATURE_DEADLINE,
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
            NO_SIGNATURE_DEADLINE,
        )
    }

    /** Lembrete do 3º dia (R4): no máximo um por contrato. */
    fun markReminderSent(now: Instant): Boolean {
        if (!status.isAwaitingSignatures || reminderSentAt != null) return false
        reminderSentAt = now
        touch(now)
        pendingEvents += ContractReminderSent(id, now)
        recordInfo("Lembrete de assinatura registrado.", ChangeSource.SCHEDULER, null, now)
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
        recordInfo("PDF assinado arquivado ($storage).", source, sourceEventId, now)
        return true
    }

    /** Registra que a ação pós-assinatura ativou a locação (R8: só com o contrato concluído). */
    fun recordLeaseActivated(now: Instant): Boolean {
        if (status != ContractStatus.COMPLETED) return false
        touch(now)
        pendingEvents += LeaseActivated(id, leaseId, now)
        recordInfo("Locação ativada.", ChangeSource.API, null, now)
        return true
    }

    /** Registra uma anomalia (por exemplo, documento removido no provedor sem pedido nosso). */
    fun recordAnomaly(note: String, source: ChangeSource, sourceEventId: String?, now: Instant) {
        touch(now)
        recordInfo("ANOMALIA: $note", source, sourceEventId, now)
    }

    /** Marca que a reconciliação consultou o provedor, para não repetir a consulta a cada ciclo. */
    fun markReconciled(now: Instant) {
        lastReconciledAt = now
    }

    private fun applyTransition(change: StatusChange, now: Instant, signatureDeadline: Duration) {
        val from = status
        status = change.target
        change.providerModifiedAt?.let { modified ->
            val current = providerLastModifiedAt
            if (current == null || modified.isAfter(current)) {
                providerLastModifiedAt = modified
            }
        }
        val effectiveReason = if (change.target == ContractStatus.CANCELLED) effectiveCancelReason(change) else null
        when (change.target) {
            ContractStatus.SENT, ContractStatus.VIEWED, ContractStatus.PARTIALLY_SIGNED -> {
                // Cobre também saltos para frente (webhook de envio perdido): o prazo sempre existe.
                val sent = sentAt ?: now
                sentAt = sent
                expiresAt = expiresAt ?: sent.plus(signatureDeadline)
            }

            ContractStatus.COMPLETED -> signers = signers.map { it.copy(completedAt = it.completedAt ?: now) }
            ContractStatus.CANCELLED -> cancelReason = effectiveReason

            ContractStatus.DRAFT, ContractStatus.GENERATED, ContractStatus.DECLINED, ContractStatus.EXPIRED -> Unit
        }
        touch(now)
        eventFor(change, effectiveReason, now)?.let { pendingEvents += it }
        appendHistory(
            from = from,
            to = change.target,
            outcome = HistoryOutcome.APPLIED,
            source = change.source,
            sourceEventId = change.sourceEventId,
            note = change.note,
            now = now,
        )
    }

    private fun eventFor(change: StatusChange, cancelReason: CancelReason?, now: Instant): ContractEvent? =
        when (change.target) {
            ContractStatus.DRAFT, ContractStatus.PARTIALLY_SIGNED -> null
            ContractStatus.GENERATED -> ContractGenerated(id, now)
            ContractStatus.SENT -> ContractSent(id, expiresAt, now)
            ContractStatus.VIEWED -> ContractViewed(id, now)
            ContractStatus.COMPLETED -> ContractCompleted(id, leaseId, now)
            ContractStatus.DECLINED -> ContractDeclined(id, change.note, now)
            ContractStatus.EXPIRED -> ContractExpired(id, now)
            ContractStatus.CANCELLED -> when (val reason = checkNotNull(cancelReason)) {
                is CancelReason.GenerationFailed -> ContractGenerationFailed(id, reason.detail, now)
                is CancelReason.Requested -> ContractCancelled(id, reason.text, now)
            }
        }

    /** Motivo gravado e publicado no cancelamento: o explícito da mudança ou, na falta dele, a nota como pedido. */
    private fun effectiveCancelReason(change: StatusChange): CancelReason =
        change.cancelReason ?: CancelReason.Requested(change.note ?: DEFAULT_CANCEL_NOTE)

    private fun recordRejected(change: StatusChange, reason: String, now: Instant) = appendHistory(
        from = status,
        to = change.target,
        outcome = HistoryOutcome.IGNORED_TRANSITION,
        source = change.source,
        sourceEventId = change.sourceEventId,
        note = reason,
        now = now,
    )

    /** Fato que não muda o status (assinatura, arquivamento, anomalia...): origem e destino são o status atual. */
    private fun recordInfo(note: String, source: ChangeSource, sourceEventId: String?, now: Instant) = appendHistory(
        from = status,
        to = status,
        outcome = HistoryOutcome.INFO,
        source = source,
        sourceEventId = sourceEventId,
        note = note,
        now = now,
    )

    /** Único ponto que monta [StatusHistoryEntry]: acumula a linha de auditoria entregue por [pullHistory]. */
    private fun appendHistory(
        from: ContractStatus?,
        to: ContractStatus,
        outcome: HistoryOutcome,
        source: ChangeSource,
        sourceEventId: String?,
        note: String?,
        now: Instant,
    ) {
        pendingHistory += StatusHistoryEntry(
            id = Uuid.random(),
            contractId = id,
            fromStatus = from,
            toStatus = to,
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
        private const val DEFAULT_CANCEL_NOTE = "Cancelado."

        /**
         * Prazo passado nas transições para estados finais, que o ignoram: o prazo de assinatura (R4) só é calculado ao
         * entrar em `SENT`, `VIEWED` ou `PARTIALLY_SIGNED`.
         */
        private val NO_SIGNATURE_DEADLINE: Duration = Duration.ZERO

        /** Cria o contrato em `DRAFT` (nova versão da locação) e emite `ContractRequested`. */
        fun request(leaseId: LeaseId, versionNumber: Int, signers: List<Signer>, now: Instant): Contract {
            requireSigningOrder(signers)
            val contract = Contract(
                id = ContractId.new(),
                leaseId = leaseId,
                versionNumber = versionNumber,
                status = ContractStatus.DRAFT,
                signers = signers,
                createdAt = now,
                updatedAt = now,
            )
            contract.pendingEvents += ContractRequested(contract.id, leaseId, versionNumber, now)
            contract.appendHistory(
                from = null,
                to = ContractStatus.DRAFT,
                outcome = HistoryOutcome.APPLIED,
                source = ChangeSource.API,
                sourceEventId = null,
                note = "Contrato v$versionNumber solicitado.",
                now = now,
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

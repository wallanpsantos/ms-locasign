package br.com.locasign.contract.domain.events

import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainEvent
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Eventos do contrato. A hierarquia é selada para que o mapeamento para o envelope Kafka use
 * `when` exaustivo: um evento novo não compila até ser mapeado.
 */
sealed interface ContractEvent : DomainEvent {
    val contractId: ContractId

    override val aggregateType: String get() = AGGREGATE_TYPE
    override val aggregateId: String get() = contractId.toString()

    companion object {
        const val AGGREGATE_TYPE = "Contract"
    }
}

/** Contrato criado em `DRAFT` a pedido do corretor. */
data class ContractRequested(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    val versionNumber: Int,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** O provedor aceitou a criação e devolveu o identificador do documento. */
data class ContractDocumentCreated(
    override val contractId: ContractId,
    val providerDocumentId: ProviderDocumentId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** O documento ficou pronto no provedor (`GENERATED`). */
data class ContractGenerated(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** O provedor reportou falha na criação; o contrato vai para `CANCELLED` com motivo `GENERATION_FAILED`. */
data class ContractGenerationFailed(
    override val contractId: ContractId,
    val detail: String?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Envio confirmado; carrega o prazo de assinatura (R4). */
data class ContractSent(
    override val contractId: ContractId,
    val expiresAt: Instant?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Um signatário abriu o documento. */
data class ContractViewed(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Um signatário assinou. */
data class ContractSignerCompleted(
    override val contractId: ContractId,
    val role: SignerRole,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Todos assinaram. Dispara as ações pós-assinatura (R8). */
data class ContractCompleted(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Um signatário recusou. O provedor não documenta o motivo; [reason] traz o que for conhecido. */
data class ContractDeclined(
    override val contractId: ContractId,
    val reason: String?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** O prazo de assinatura venceu (R4). */
data class ContractExpired(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Cancelado pelo corretor. */
data class ContractCancelled(
    override val contractId: ContractId,
    val reason: String,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** Lembrete de assinatura (3º dia, R4). Opcional no MVP; a notificação é simulada. */
data class ContractReminderSent(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** O PDF assinado foi arquivado (ou apenas referenciado, quando o download não está disponível). */
data class SignedDocumentArchived(
    override val contractId: ContractId,
    val storage: String,
    val location: String,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/** A locação foi ativada pela ação pós-assinatura. */
data class LeaseActivated(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.shared.domain.valueobjects.Email
import java.time.Instant
import kotlin.uuid.Uuid

/** Signatário do contrato. Imutável: a conclusão da assinatura gera uma nova instância. */
data class Signer(
    val role: SignerRole,
    val name: String,
    val email: Email,
    val order: SigningOrder,
    val completedAt: Instant? = null,
) {
    val hasCompleted: Boolean get() = completedAt != null
}

/** Motivo do cancelamento. A falha de geração é distinguida do cancelamento pedido pelo corretor. */
sealed interface CancelReason {
    val storageValue: String

    data class GenerationFailed(val detail: String?) : CancelReason {
        override val storageValue: String
            get() = if (detail.isNullOrBlank()) GENERATION_FAILED else "$GENERATION_FAILED: $detail"
    }

    data class Requested(val text: String) : CancelReason {
        override val storageValue: String get() = "$REQUESTED: $text"
    }

    companion object {
        private const val GENERATION_FAILED = "GENERATION_FAILED"
        private const val REQUESTED = "REQUESTED"

        fun fromStorage(value: String): CancelReason = when {
            value == GENERATION_FAILED -> GenerationFailed(null)
            value.startsWith("$GENERATION_FAILED: ") -> GenerationFailed(value.removePrefix("$GENERATION_FAILED: "))
            value.startsWith("$REQUESTED: ") -> Requested(value.removePrefix("$REQUESTED: "))
            else -> Requested(value)
        }
    }
}

/** Uma mudança de status solicitada ao agregado, com a origem e o instante informado pelo provedor. */
data class StatusChange(
    val target: ContractStatus,
    val source: ChangeSource,
    val sourceEventId: String? = null,
    val providerModifiedAt: Instant? = null,
    val note: String? = null,
    val cancelReason: CancelReason? = null,
)

enum class TransitionResult { APPLIED, NO_CHANGE, IGNORED }

/** Linha da trilha de auditoria do contrato (R5, R7, R9). */
data class StatusHistoryEntry(
    val id: Uuid,
    val contractId: ContractId,
    val fromStatus: ContractStatus?,
    val toStatus: ContractStatus,
    val outcome: HistoryOutcome,
    val source: ChangeSource,
    val sourceEventId: String?,
    val note: String?,
    val occurredAt: Instant,
)

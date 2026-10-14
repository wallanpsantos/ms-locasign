package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.shared.domain.valueobjects.Email
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Representa um signatário vinculado a uma versão do contrato de locação com seus dados cadastrais e status de conclusão.
 *
 * **Responsabilidade:**
 * - Manter as informações imutáveis do signatário (papel, nome, e-mail tipado e ordem estrita de assinatura conforme R3).
 * - Registrar o instante de conclusão da assinatura eletrônica quando notificado pelo provedor.
 */
data class Signer(
    val role: SignerRole,
    val name: String,
    val email: Email,
    val order: SigningOrder,
    val completedAt: Instant? = null,
) {
    val hasCompleted: Boolean get() = completedAt != null
}

/**
 * Razão formal de cancelamento ou encerramento anômalo do contrato de locação.
 *
 * **Responsabilidade:**
 * - Discriminar a causa do cancelamento entre falha técnica na geração ([GenerationFailed]) e cancelamento voluntário solicitado pelo operador ([Requested]).
 */
sealed interface CancelReason {
    data class GenerationFailed(val detail: String?) : CancelReason

    data class Requested(val text: String) : CancelReason
}

/**
 * Instrução de alteração de estado solicitada à raiz de agregação [Contract].
 *
 * **Responsabilidade:**
 * - Encapsular o estado de destino almejado, a fonte originária da mudança, identificadores correlatos, timestamps e justificativas.
 * - Fornecer os dados necessários para avaliação da transição pela política de domínio e composição da trilha de auditoria.
 */
data class StatusChange(
    val target: ContractStatus,
    val source: ChangeSource,
    val sourceEventId: String? = null,
    val providerModifiedAt: Instant? = null,
    val note: String? = null,
    val cancelReason: CancelReason? = null,
)

/**
 * Resultado da aplicação de uma transição de estado no agregado [Contract].
 *
 * **Responsabilidade:**
 * - Indicar se a transição foi efetivamente aplicada ([APPLIED]), se resultou em nenhuma alteração por já estar no estado ([NO_CHANGE]), ou se foi ignorada/rejeitada ([IGNORED]) pela política de domínio.
 */
enum class TransitionResult { APPLIED, NO_CHANGE, IGNORED }

/**
 * Registro individual da trilha histórica de auditoria de alterações de status e fatos relevantes do contrato.
 *
 * **Responsabilidade:**
 * - Armazenar o histórico imutável das transições ocorridas, permitindo rastrear quem, quando, por qual canal e por qual razão o contrato mudou de estado (R5, R7, R9).
 * - Documentar fatos operacionais informativos ([HistoryOutcome.INFO]), como assinaturas parciais e arquivamento de documentos assinados.
 */
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

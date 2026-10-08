package br.com.locasign.contract.domain.models

/**
 * Representa os estados possíveis do ciclo de vida de um contrato de locação e sua ordem de progresso.
 *
 * **Responsabilidade:**
 * - Modelar os estados do contrato e definir o nível de avanço ordinal (`progress`) utilizado pelo [ContractTransitionPolicy] (ADR-010).
 * - Identificar estados terminais (`isFinal`) e indicar se o contrato está aguardando assinaturas (`isAwaitingSignatures`).
 * - Garantir que nenhuma transição inválida ou regressão de status ocorra no domínio.
 */
enum class ContractStatus(val progress: Int, val isFinal: Boolean) {
    DRAFT(0, false),
    GENERATED(1, false),
    SENT(2, false),
    VIEWED(3, false),
    PARTIALLY_SIGNED(4, false),
    COMPLETED(5, true),
    DECLINED(NO_PROGRESS, true),
    EXPIRED(NO_PROGRESS, true),
    CANCELLED(NO_PROGRESS, true),
    ;

    /** O documento já foi enviado e ainda aguarda assinaturas. */
    val isAwaitingSignatures: Boolean
        get() = this == SENT || this == VIEWED || this == PARTIALLY_SIGNED

    companion object {
        val FINAL: Set<ContractStatus> = entries.filter { it.isFinal }.toSet()
    }
}

private const val NO_PROGRESS = -1

/**
 * Papel atribuído a um signatário no contrato de locação.
 *
 * **Responsabilidade:**
 * - Diferenciar o locatário ([TENANT]) do representante da imobiliária ([AGENCY]) para imposição da regra de ordenação de assinaturas (R3).
 */
enum class SignerRole { TENANT, AGENCY }

/**
 * Canal ou mecanismo de origem que provocou uma tentativa ou efetivação de alteração no contrato.
 *
 * **Responsabilidade:**
 * - Rastrear a procedência da mutação (requisição HTTP [API], webhook externo [WEBHOOK], reconciliação periódica [RECONCILIATION] ou agendador de tarefas [SCHEDULER]).
 * - Prover contexto operacional para a trilha de auditoria e resolução de conflitos.
 */
enum class ChangeSource { API, WEBHOOK, RECONCILIATION, SCHEDULER }

/**
 * Resultado da avaliação de uma transição de status para registro na trilha histórica.
 *
 * **Responsabilidade:**
 * - Discriminar se a transição foi aplicada ([APPLIED]), ignorada por idempotência ou desordem ([IGNORED_TRANSITION]), ou se trata de um evento informativo sem alteração de status ([INFO]).
 * - Assegurar transparência total na auditoria do histórico do contrato.
 */
enum class HistoryOutcome {
    APPLIED,
    IGNORED_TRANSITION,

    /** Fato que não muda o status (assinatura de um signatário, arquivamento, anomalia...). */
    INFO,
}

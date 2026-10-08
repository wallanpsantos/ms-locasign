package br.com.locasign.contract.domain.services

import br.com.locasign.contract.domain.models.ContractStatus

/**
 * Resultado da avaliação de uma solicitação de transição de status pela política de ciclo de vida do contrato.
 *
 * **Responsabilidade:**
 * - Classificar a decisão entre aplicação efetiva ([Apply]), operação inócua idempotente ([NoOp]) ou rejeição justificada ([Reject]).
 * - Viabilizar tratamento exaustivo via `when` na máquina de estados do contrato.
 */
sealed interface TransitionDecision {
    /** A transição é válida e deve ser aplicada. */
    data object Apply : TransitionDecision

    /** Mesmo status: no-op silencioso. */
    data object NoOp : TransitionDecision

    /** A transição é ignorada e registrada na auditoria como `IGNORED_TRANSITION` (R5). */
    data class Reject(val reason: String) : TransitionDecision
}

/**
 * Política de domínio que governa as transições de status do contrato de locação (Regra R5 e ADR-010).
 *
 * **Responsabilidade:**
 * - Garantir que contratos em estados finais ([ContractStatus.isFinal]) sejam estritamente imutáveis.
 * - Impedir regressão de status (o progresso ordinal não pode diminuir).
 * - Permitir avanços monotônicos ou saltos para a frente, acomodando eventuais perdas ou atrasos na entrega de webhooks de provedores externos.
 */
object ContractTransitionPolicy {

    fun decide(current: ContractStatus, target: ContractStatus): TransitionDecision = when {
        current == target -> TransitionDecision.NoOp
        current.isFinal -> TransitionDecision.Reject("Contrato em estado final ($current) é imutável.")
        target.isFinal || target.progress > current.progress -> TransitionDecision.Apply
        else -> TransitionDecision.Reject("A transição $current -> $target regrediria o status.")
    }
}

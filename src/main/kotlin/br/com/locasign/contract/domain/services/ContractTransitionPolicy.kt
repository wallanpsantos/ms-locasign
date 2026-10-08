package br.com.locasign.contract.domain.services

import br.com.locasign.contract.domain.models.ContractStatus

/** Decisão da política para uma tentativa de transição. */
sealed interface TransitionDecision {
    /** A transição é válida e deve ser aplicada. */
    data object Apply : TransitionDecision

    /** Mesmo status: no-op silencioso. */
    data object NoOp : TransitionDecision

    /** A transição é ignorada e registrada na auditoria como `IGNORED_TRANSITION` (R5). */
    data class Reject(val reason: String) : TransitionDecision
}

/**
 * R5 + ADR-010: o status nunca regride e estados finais são imutáveis, mas saltos para frente são
 * permitidos, porque a PandaDoc não reenvia webhooks perdidos.
 */
object ContractTransitionPolicy {

    fun decide(current: ContractStatus, target: ContractStatus): TransitionDecision = when {
        current == target -> TransitionDecision.NoOp
        current.isFinal -> TransitionDecision.Reject("Contrato em estado final ($current) é imutável.")
        target.isFinal || target.progress > current.progress -> TransitionDecision.Apply
        else -> TransitionDecision.Reject("A transição $current -> $target regrediria o status.")
    }
}

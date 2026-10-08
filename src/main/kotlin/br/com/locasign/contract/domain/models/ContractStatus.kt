package br.com.locasign.contract.domain.models

/**
 * Status do contrato. [progress] define a ordem de progresso usada pela política de transições
 * (ADR-010): uma transição é aceita se o status atual não é final e o destino avança ou é final.
 * Os estados finais fora do caminho feliz não têm ordem de progresso.
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

enum class SignerRole { TENANT, AGENCY }

/** Origem de uma mudança, registrada na trilha de auditoria. */
enum class ChangeSource { API, WEBHOOK, RECONCILIATION, SCHEDULER }

enum class HistoryOutcome {
    APPLIED,
    IGNORED_TRANSITION,

    /** Fato que não muda o status (assinatura de um signatário, arquivamento, anomalia...). */
    INFO,
}

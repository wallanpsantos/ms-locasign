package br.com.locasign.shared.app

/**
 * Mensagem ilegível ou fora do contrato (envelope inválido). Não adianta retentar: o consumidor
 * a envia direto para a DLT, sem backoff.
 */
class UnreadableMessageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Reprocessamento manual da fila de erros (DLT) de um tópico. */
interface DeadLetterReplayPort {
    /** Republica no tópico original as mensagens da DLT. Devolve quantas foram reenviadas. */
    fun replay(sourceTopic: String): Int
}

package br.com.locasign.shared.app.ports

/**
 * Idempotência dos consumidores (R6): cada par (grupo, eventId) é processado uma única vez.
 * O registro deve acontecer na mesma transação do efeito.
 */
interface ProcessedMessagesPort {
    fun isProcessed(consumerGroup: String, eventId: String): Boolean

    /** Registra o processamento. Devolve `false` se o par já estava registrado. */
    fun markProcessed(consumerGroup: String, eventId: String): Boolean
}

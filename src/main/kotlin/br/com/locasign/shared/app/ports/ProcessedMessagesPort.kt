package br.com.locasign.shared.app.ports

/**
 * Porta de saída da camada de aplicação para garantia de idempotência de consumidores de mensageria.
 *
 * **Responsabilidade:**
 * - Verificar ([isProcessed]) e registrar ([markProcessed]) o par único de consumidor e evento (`consumer_group`, `event_id`).
 * - Proteger contra processamento duplicado de mensagens entregues sob a semântica at-least-once do Kafka, persistindo o registro na mesma transação do efeito colateral.
 */
interface ProcessedMessagesPort {
    fun isProcessed(consumerGroup: String, eventId: String): Boolean

    /** Registra o processamento. Devolve `false` se o par já estava registrado. */
    fun markProcessed(consumerGroup: String, eventId: String): Boolean
}

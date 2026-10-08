package br.com.locasign.shared.app.ports

import java.time.Instant

/**
 * Mensagem a publicar via outbox. [id] é o `eventId` do envelope e a chave de idempotência dos
 * consumidores (texto, porque itens de webhook usam `deliveryId:índice`).
 */
data class OutboxMessage(
    val id: String,
    val topic: String,
    val key: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val occurredAt: Instant,
    val payloadJson: String,
)

/**
 * Princípio 3: todo evento sai pelo outbox. Deve ser chamada dentro da mesma transação que
 * altera o estado; um relay publica no Kafka depois (garantia "pelo menos uma vez").
 */
interface OutboxPort {
    fun append(message: OutboxMessage)
}

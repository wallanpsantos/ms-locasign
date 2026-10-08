package br.com.locasign.shared.app.ports

import java.time.Instant

/**
 * Modelo de dados que representa um evento de negócio a ser persistido na tabela de transactional outbox.
 *
 * **Responsabilidade:**
 * - Encapsular os dados necessários para publicação assíncrona no Kafka ([topic], [key], [payloadJson], [eventType], [aggregateId]).
 * - Manter identificador estável ([id]) compatível com UUIDs e identificadores compostos de webhook (`deliveryId:index`) para garantia de idempotência.
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
 * Porta de saída da camada de aplicação para enfileiramento transacional de eventos de domínio no padrão Transactional Outbox.
 *
 * **Responsabilidade:**
 * - Garantir que eventos gerados por mutações de negócio sejam persistidos atomicamente na mesma transação de banco de dados da entidade alterada.
 * - Assegurar o desacoplamento entre a execução do caso de uso e a disponibilidade imediata do broker de mensageria (Kafka).
 */
interface OutboxPort {
    fun append(message: OutboxMessage)
}

package br.com.locasign.shared.infra.persistence

import br.com.locasign.shared.app.ports.ContextKeys
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.OutboxPort
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.WebhookInboxPort
import org.slf4j.MDC
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper

/**
 * Adaptador de persistência para gravação transacional de eventos no padrão Transactional Outbox.
 *
 * **Responsabilidade:**
 * - Implementar [OutboxPort], persistindo eventos na tabela `outbox_events` na mesma transação de negócio da mutação.
 * - Envelopar o payload do evento com metadados canônicos de rastreabilidade ([correlationId], [causationId], versão de schema) em formato JSONB.
 */
@Repository
class JdbcOutboxAdapter(
    private val jdbc: JdbcClient,
    private val mapper: JsonMapper,
) : OutboxPort {

    override fun append(message: OutboxMessage) {
        val correlationId = MDC.get(ContextKeys.CORRELATION_ID) ?: message.id
        val causationId: String? = MDC.get(ContextKeys.CAUSATION_ID)

        val envelope = linkedMapOf(
            "eventId" to message.id,
            "eventType" to message.eventType,
            "schemaVersion" to SCHEMA_VERSION,
            "occurredAt" to message.occurredAt.toString(),
            "aggregateType" to message.aggregateType,
            "aggregateId" to message.aggregateId,
            "correlationId" to correlationId,
            "causationId" to causationId,
            "payload" to mapper.readTree(message.payloadJson),
        )
        val headers = linkedMapOf(
            "eventType" to message.eventType,
            "eventId" to message.id,
            "schemaVersion" to SCHEMA_VERSION.toString(),
            "correlationId" to correlationId,
        )
        jdbc.sql(
            """
			INSERT INTO outbox_events
			    (id, aggregate_type, aggregate_id, event_type, topic, message_key, payload, headers)
			VALUES
			    (:id, :aggregateType, :aggregateId, :eventType, :topic, :messageKey,
			     CAST(:payload AS jsonb), CAST(:headers AS jsonb))
			ON CONFLICT (id) DO NOTHING
			""".trimIndent(),
        )
            .param("id", message.id)
            .param("aggregateType", message.aggregateType)
            .param("aggregateId", message.aggregateId)
            .param("eventType", message.eventType)
            .param("topic", message.topic)
            .param("messageKey", message.key)
            .param("payload", mapper.writeValueAsString(envelope))
            .param("headers", mapper.writeValueAsString(headers))
            .update()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Adaptador de persistência para controle transacional de idempotência de consumidores de mensageria.
 *
 * **Responsabilidade:**
 * - Implementar [ProcessedMessagesPort], consultando e inserindo o par ([consumerGroup], [eventId]) na tabela `processed_messages`.
 * - Prevenir efeitos colaterais duplicados decorrentes de reentregas de mensagens pelo Kafka (regra R6).
 */
@Repository
class JdbcProcessedMessagesAdapter(private val jdbc: JdbcClient) : ProcessedMessagesPort {

    override fun isProcessed(consumerGroup: String, eventId: String): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM processed_messages WHERE consumer_group = :group AND event_id = :eventId)")
            .param("group", consumerGroup)
            .param("eventId", eventId)
            .query(Boolean::class.java)
            .single()

    override fun markProcessed(consumerGroup: String, eventId: String): Boolean =
        jdbc.sql(
            """
			INSERT INTO processed_messages (consumer_group, event_id)
			VALUES (:group, :eventId)
			ON CONFLICT (consumer_group, event_id) DO NOTHING
			""".trimIndent(),
        )
            .param("group", consumerGroup)
            .param("eventId", eventId)
            .update() == 1
}

/**
 * Adaptador de persistência para armazenamento idempotente de webhooks no padrão Transactional Inbox.
 *
 * **Responsabilidade:**
 * - Implementar [WebhookInboxPort], persistindo o corpo bruto (`rawBody`) e o identificador de entrega ([deliveryId]) na tabela `webhook_inbox`.
 * - Assegurar deduplicação de requisições de webhook (regra R7) e integridade dos bytes brutos recebidos para validação criptográfica posterior.
 */
@Repository
class JdbcWebhookInboxAdapter(private val jdbc: JdbcClient) : WebhookInboxPort {

    override fun store(deliveryId: String, rawBody: String): Boolean =
        jdbc.sql(
            """
			INSERT INTO webhook_inbox (delivery_id, raw_body)
			VALUES (:deliveryId, :rawBody)
			ON CONFLICT (delivery_id) DO NOTHING
			""".trimIndent(),
        )
            .param("deliveryId", deliveryId)
            .param("rawBody", rawBody)
            .update() == 1
}

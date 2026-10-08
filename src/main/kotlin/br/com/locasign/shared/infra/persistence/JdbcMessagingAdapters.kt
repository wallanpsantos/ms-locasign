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
 * Escreve no outbox, na transação do chamador. Monta o envelope da seção 7.3 do guia:
 * `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `aggregateType/Id`, `correlationId`,
 * `causationId` e `payload`.
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

/** R6: o par (grupo, eventId) é processado uma única vez. */
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

/** R7: o corpo bruto do webhook é gravado como texto, preservando os bytes recebidos. */
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

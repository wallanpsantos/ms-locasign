package br.com.locasign.shared.infra.persistence

import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JdbcMessagingAdaptersIT(
    @Autowired private val processedMessagesAdapter: JdbcProcessedMessagesAdapter,
    @Autowired private val outboxAdapter: JdbcOutboxAdapter,
    @Autowired private val webhookInboxAdapter: JdbcWebhookInboxAdapter,
    @Autowired private val jdbc: JdbcClient,
) : IntegrationTestSupport() {

    @Test
    fun `controle de idempotência registra mensagem e rejeita duplicatas com sucesso`() {
        val eventId = UUID.randomUUID().toString()
        val consumerGroup = "test-group-idempotency"

        assertFalse(processedMessagesAdapter.isProcessed(consumerGroup, eventId))

        val firstInsert = processedMessagesAdapter.markProcessed(consumerGroup, eventId)
        assertTrue(firstInsert, "Primeira gravação de mensagem deve retornar true")

        assertTrue(processedMessagesAdapter.isProcessed(consumerGroup, eventId))

        val secondInsert = processedMessagesAdapter.markProcessed(consumerGroup, eventId)
        assertFalse(
            secondInsert,
            "Segunda tentativa com mesmo par (group, eventId) deve ser ignorada e retornar false (R6)",
        )
    }

    @Test
    fun `outbox persiste evento com envelope completo e payload jsonb`() {
        val eventId = UUID.randomUUID().toString()
        val aggregateId = UUID.randomUUID().toString()
        val message = OutboxMessage(
            id = eventId,
            aggregateType = "Contract",
            aggregateId = aggregateId,
            eventType = "ContractRequested",
            topic = Topics.CONTRACT_EVENTS,
            key = aggregateId,
            payloadJson = """{"contractId":"$aggregateId","version":1}""",
            occurredAt = Instant.now(),
        )

        outboxAdapter.append(message)

        val row = jdbc.sql(
            """
			SELECT aggregate_id,
			       payload ->> 'eventId'                 AS event_id,
			       payload ->> 'eventType'               AS event_type,
			       (payload ->> 'schemaVersion')::int    AS schema_version,
			       payload -> 'payload' ->> 'contractId' AS contract_id
			FROM outbox_events
			WHERE id = :id
			""".trimIndent(),
        )
            .param("id", eventId)
            .query()
            .singleRow()

        assertEquals(aggregateId, row["aggregate_id"])
        assertEquals(eventId, row["event_id"])
        assertEquals("ContractRequested", row["event_type"])
        assertEquals(1, row["schema_version"])
        // O payload do evento fica aninhado no envelope como JSON, não como string escapada.
        assertEquals(aggregateId, row["contract_id"])
    }

    @Test
    fun `inbox de webhook armazena requisição e deduplica por deliveryId`() {
        val deliveryId = "deliv-" + UUID.randomUUID()
        val rawBody = """{"event": "document_state_changed"}"""

        val firstStore = webhookInboxAdapter.store(deliveryId, rawBody)
        assertTrue(firstStore, "Primeiro armazenamento do webhook deve retornar true")

        val secondStore = webhookInboxAdapter.store(deliveryId, rawBody)
        assertFalse(
            secondStore,
            "Tentativa subsequente com mesmo deliveryId deve ser deduplicada e retornar false (R7)",
        )
    }
}

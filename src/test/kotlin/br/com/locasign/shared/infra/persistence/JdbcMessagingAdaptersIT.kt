package br.com.locasign.shared.infra.persistence

import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.support.TestcontainersSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersSupport::class)
class JdbcMessagingAdaptersIT {

    @Autowired
    private lateinit var processedMessagesAdapter: JdbcProcessedMessagesAdapter

    @Autowired
    private lateinit var outboxAdapter: JdbcOutboxAdapter

    @Autowired
    private lateinit var webhookInboxAdapter: JdbcWebhookInboxAdapter

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Test
    fun `controle de idempotencia registra mensagem e rejeita duplicatas com sucesso`() {
        val eventId = UUID.randomUUID().toString()
        val consumerGroup = "test-group-idempotency"

        assertFalse(processedMessagesAdapter.isProcessed(consumerGroup, eventId))

        val firstInsert = processedMessagesAdapter.markProcessed(consumerGroup, eventId)
        assertTrue(firstInsert, "Primeira gravação de mensagem deve retornar true")

        assertTrue(processedMessagesAdapter.isProcessed(consumerGroup, eventId))

        val secondInsert = processedMessagesAdapter.markProcessed(consumerGroup, eventId)
        assertFalse(
            secondInsert,
            "Segunda tentativa com mesmo par (group, eventId) deve ser ignorada e retornar false (R6)"
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
            topic = "locasign.contract.events.v1",
            key = aggregateId,
            payloadJson = """{"contractId":"$aggregateId","version":1}""",
            occurredAt = Instant.now(),
        )

        outboxAdapter.append(message)

        val count = jdbc.sql("SELECT count(*) FROM outbox_events WHERE id = :id AND aggregate_id = :aggId")
            .param("id", eventId)
            .param("aggId", aggregateId)
            .query(Int::class.java)
            .single()

        assertEquals(1, count)
    }

    @Test
    fun `inbox de webhook armazena requisicao e deduplica por deliveryId`() {
        val deliveryId = "deliv-" + UUID.randomUUID()
        val rawBody = """{"event": "document_state_changed"}"""

        val firstStore = webhookInboxAdapter.store(deliveryId, rawBody)
        assertTrue(firstStore, "Primeiro armazenamento do webhook deve retornar true")

        val secondStore = webhookInboxAdapter.store(deliveryId, rawBody)
        assertFalse(
            secondStore,
            "Tentativa subsequente com mesmo deliveryId deve ser deduplicada e retornar false (R7)"
        )
    }
}

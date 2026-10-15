package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.infra.persistence.JdbcOutboxAdapter
import br.com.locasign.support.IntegrationTestSupport
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.ConsumerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** O relay publica a linha do outbox com chave, envelope e headers gravados, e marca a linha como publicada. */
class OutboxRelayIT(
    @Autowired private val outbox: JdbcOutboxAdapter,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
) : IntegrationTestSupport() {

    @Test
    fun `relay publica no Kafka a mensagem do outbox com chave, envelope e headers e marca a linha`() {
        val id = UUID.randomUUID().toString()
        val key = "relay-it-$id"
        // eventType sem roteamento: os consumidores do tópico ignoram a mensagem.
        outbox.append(
            OutboxMessage(
                id = id,
                topic = Topics.CONTRACT_EVENTS,
                key = key,
                aggregateType = "Contract",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "RelayProbe",
                occurredAt = Instant.parse("2026-10-09T10:00:00Z"),
                payloadJson = """{"probe":true}""",
            ),
        )

        await().atMost(Duration.ofSeconds(30)).until { isPublished(id) }

        val record = consumeByKey(Topics.CONTRACT_EVENTS, key)
        val storedPayload = jdbc.sql("SELECT payload::text FROM outbox_events WHERE id = :id")
            .param("id", id)
            .query(String::class.java)
            .single()
        assertEquals(storedPayload, record.value())
        val headers = record.headers().associate { it.key() to it.value().toString(Charsets.UTF_8) }
        assertEquals(
            mapOf("eventType" to "RelayProbe", "eventId" to id, "schemaVersion" to "1", "correlationId" to id),
            headers,
        )
    }

    private fun isPublished(id: String): Boolean =
        jdbc.sql("SELECT published_at IS NOT NULL FROM outbox_events WHERE id = :id")
            .param("id", id)
            .query(Boolean::class.java)
            .single()

    private fun consumeByKey(topic: String, key: String): ConsumerRecord<String, String> =
        consumerFactory.createConsumer("outbox-relay-it-${UUID.randomUUID()}", "").use { consumer ->
            consumer.subscribe(listOf(topic))
            var found: ConsumerRecord<String, String>? = null
            await().atMost(Duration.ofSeconds(30)).until {
                found = consumer.poll(Duration.ofMillis(500)).firstOrNull { it.key() == key }
                found != null
            }
            checkNotNull(found)
        }
}

package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.TransactionRunner
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O relay publica a linha do outbox com chave, envelope e headers gravados, e marca a linha como publicada. */
class OutboxRelayIT(
    @Autowired private val outbox: JdbcOutboxAdapter,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
    @Autowired private val transactions: TransactionRunner,
) : IntegrationTestSupport() {

    /**
     * `eventType` sem roteamento e `aggregateId` UUID: os três consumidores do tópico aceitam e ignoram a mensagem.
     * Uma mensagem ilegível iria para a DLT e faria o replay do [DeadLetterReplayerIT] entrar em laço (`plan.md`, B8).
     */
    private fun appendProbe(key: String, payloadJson: String = """{"probe":true}"""): String {
        val id = UUID.randomUUID().toString()
        outbox.append(
            OutboxMessage(
                id = id,
                topic = Topics.CONTRACT_EVENTS,
                key = key,
                aggregateType = "Contract",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "RelayProbe",
                occurredAt = Instant.parse("2026-10-09T10:00:00Z"),
                payloadJson = payloadJson,
            ),
        )
        return id
    }

    @Test
    fun `relay publica no Kafka a mensagem do outbox com chave, envelope e headers e marca a linha`() {
        val key = "relay-it-${UUID.randomUUID()}"
        val id = appendProbe(key)

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

    @Test
    fun `linha já publicada não é publicada de novo e a seguinte da mesma chave sai depois dela`() {
        val key = "relay-it-${UUID.randomUUID()}"
        val first = appendProbe(key)
        await().atMost(Duration.ofSeconds(30)).until { isPublished(first) }
        val second = appendProbe(key)
        await().atMost(Duration.ofSeconds(30)).until { isPublished(second) }

        assertEquals(listOf(first, second), consumeEventIds(key, until = second))
    }

    @Test
    fun `linhas da mesma chave gravadas juntas saem no mesmo lote na ordem de gravação`() {
        val key = "relay-it-${UUID.randomUUID()}"
        // Uma transação só: as duas linhas ficam visíveis ao mesmo tempo e caem no mesmo lote do relay.
        val (first, second) = transactions.run { appendProbe(key) to appendProbe(key) }

        await().atMost(Duration.ofSeconds(30)).until { isPublished(first) && isPublished(second) }

        assertEquals(listOf(first, second), consumeEventIds(key, until = second))
    }

    @Test
    fun `falha ao publicar segura as linhas seguintes da mesma chave no lote, mas não as de outra chave`() {
        val key = "relay-it-${UUID.randomUUID()}"
        // Acima do max.request.size padrão do produtor (1 MB): o envio falha e a linha recebe markFailed.
        val tooLarge = """{"blob":"${"x".repeat(1_100_000)}"}"""
        val (failing, sameKey, otherKey) = transactions.run {
            Triple(appendProbe(key, tooLarge), appendProbe(key), appendProbe("relay-it-${UUID.randomUUID()}"))
        }
        try {
            await().atMost(Duration.ofSeconds(30)).until { isPublished(sameKey) && isPublished(otherKey) }

            val failed = jdbc.sql("SELECT attempts, last_error, published_at FROM outbox_events WHERE id = :id")
                .param("id", failing)
                .query()
                .singleRow()
            assertTrue((failed["attempts"] as Int) >= 1)
            assertNotNull(failed["last_error"])
            assertNull(failed["published_at"])
            // now() do PostgreSQL é o início da transação: a outra chave saiu no lote da falha, e a mesma chave
            // só saiu num lote seguinte, depois de ter sido pulada.
            assertTrue(publishedAt(sameKey).isAfter(publishedAt(otherKey)))
        } finally {
            jdbc.sql("DELETE FROM outbox_events WHERE id = :id").param("id", failing).update()
        }
    }

    private fun publishedAt(id: String): Instant =
        jdbc.sql("SELECT published_at FROM outbox_events WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.getTimestamp("published_at").toInstant() }
            .single()

    private fun consumeEventIds(key: String, until: String): List<String> {
        val eventIds = mutableListOf<String>()
        consumerFactory.createConsumer("outbox-relay-it-${UUID.randomUUID()}", "").use { consumer ->
            consumer.subscribe(listOf(Topics.CONTRACT_EVENTS))
            await().atMost(Duration.ofSeconds(30)).until {
                consumer.poll(Duration.ofMillis(500))
                    .filter { it.key() == key }
                    .forEach { eventIds += it.headers().lastHeader("eventId").value().toString(Charsets.UTF_8) }
                until in eventIds
            }
        }
        return eventIds
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

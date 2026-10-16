package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.infra.config.LocaSignProperties
import br.com.locasign.shared.infra.persistence.JdbcOutboxAdapter
import br.com.locasign.support.IntegrationTestSupport
import br.com.locasign.support.runAndRollback
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.NetworkException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

/**
 * Com o broker fora do ar, o relay não segura a transação do lote (`FOR UPDATE` e conexão do Hikari) por uma espera de
 * envio por linha: a primeira falha de transporte registra a falha só daquela linha e encerra o lote. Falha de um único
 * registro continua tratada pela chave (`blockedKeys`), sem interromper as outras chaves.
 *
 * Cada teste roda numa transação com rollback e um `KafkaTemplate` roteirizado: o relay do contexto não enxerga as
 * linhas e nenhuma mensagem chega ao Kafka.
 */
class OutboxRelayBrokerOutageIT(
    @Autowired private val outbox: JdbcOutboxAdapter,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val mapper: JsonMapper,
    @Autowired private val transactions: TransactionRunner,
    @Autowired private val properties: LocaSignProperties,
) : IntegrationTestSupport() {

    private val kafka = ScriptedKafkaTemplate()

    private fun relay(sendTimeout: Duration = properties.outbox.sendTimeout) = OutboxRelay(
        jdbc = jdbc,
        kafka = kafka,
        mapper = mapper,
        transactions = transactions,
        properties = properties.copy(outbox = properties.outbox.copy(sendTimeout = sendTimeout)),
        registry = SimpleMeterRegistry(),
    )

    @Test
    fun `rejeição de transporte no primeiro envio interrompe o lote e só essa linha recebe a marcação de falha`() {
        kafka.outcome = { rejectedSynchronously(KafkaTimeoutException("sem metadados após 60000 ms")) }

        assertBatchStopsAfterFirstSend()
    }

    @Test
    fun `falha retentável confirmada pelo broker no primeiro envio interrompe o lote`() {
        kafka.outcome = { failedAsynchronously(NetworkException("conexão perdida com o broker")) }

        assertBatchStopsAfterFirstSend()
    }

    @Test
    fun `confirmação que não chega dentro do sendTimeout no primeiro envio interrompe o lote`() {
        kafka.outcome = { neverAcknowledged() }

        assertBatchStopsAfterFirstSend(relay(sendTimeout = Duration.ofMillis(50)))
    }

    @Test
    fun `linhas deixadas pendentes pela interrupção saem no ciclo seguinte e a que falhou espera o backoff`() {
        kafka.outcome = { rejectedSynchronously(KafkaTimeoutException("sem metadados após 60000 ms")) }
        val relay = relay()

        val observed = observeInRollback {
            val ids = List(3) { appendProbe("relay-outage-it-${UUID.randomUUID()}") }
            relay.relay()
            kafka.outcome = ::acknowledged
            relay.relay()
            ids
        }

        assertEquals(
            observed.ids,
            observed.sentEventIds,
            "a segunda linha só é tentada no ciclo seguinte, com o broker de volta",
        )
        val (failed, second, third) = observed.states
        assertEquals(RowState(failed.id, attempts = 1, hasError = true, inBackoff = true), failed)
        assertEquals(RowState(second.id, published = true), second)
        assertEquals(RowState(third.id, published = true), third)
    }

    @Test
    fun `falha de um único registro não interrompe o lote, outras chaves saem e a mesma chave espera`() {
        val relay = relay()

        val observed = observeInRollback {
            val key = "relay-outage-it-${UUID.randomUUID()}"
            val ids = listOf(appendProbe(key), appendProbe(key), appendProbe("relay-outage-it-${UUID.randomUUID()}"))
            kafka.outcome = { record ->
                if (eventIdOf(record) == ids.first()) {
                    rejectedSynchronously(RecordTooLargeException("registro acima do limite"))
                } else {
                    acknowledged(record)
                }
            }
            relay.relay()
            ids
        }

        assertEquals(
            listOf(observed.ids[0], observed.ids[2]),
            observed.sentEventIds,
            "a segunda linha da chave com falha é pulada",
        )
        val (failed, sameKey, otherKey) = observed.states
        assertEquals(RowState(failed.id, attempts = 1, hasError = true, inBackoff = true), failed)
        assertEquals(RowState(sameKey.id), sameKey)
        assertEquals(RowState(otherKey.id, published = true), otherKey)
    }

    /** Um lote cheio de linhas com chaves distintas e um envio que falha por transporte: só a primeira linha é tentada. */
    private fun assertBatchStopsAfterFirstSend(relay: OutboxRelay = relay()) {
        val observed = observeInRollback {
            val ids = List(properties.outbox.batchSize) { appendProbe("relay-outage-it-${UUID.randomUUID()}") }
            relay.relay()
            ids
        }

        assertTrue(observed.ids.size > 1, "o lote precisa ter mais de uma linha")
        assertEquals(observed.ids.take(1), observed.sentEventIds, "só a primeira linha chega a ser enviada")
        val (failed, untouched) = observed.states.first() to observed.states.drop(1)
        assertEquals(RowState(failed.id, attempts = 1, hasError = true, inBackoff = true), failed)
        untouched.forEach { row ->
            assertEquals(RowState(row.id), row)
        }
    }

    private fun observeInRollback(block: () -> List<String>): Observed = transactions.runAndRollback {
        holdOtherPendingRows()
        val ids = block()
        Observed(ids, kafka.sentEventIds.toList(), stateOf(ids))
    }

    /** Tira da frente, só nesta transação, as linhas pendentes de outros testes: o lote do relay fica só com as do teste. */
    private fun holdOtherPendingRows() {
        jdbc.sql("UPDATE outbox_events SET available_at = now() + interval '1 hour' WHERE published_at IS NULL")
            .update()
    }

    private fun appendProbe(key: String): String {
        val id = UUID.randomUUID().toString()
        outbox.append(
            OutboxMessage(
                id = id,
                topic = Topics.CONTRACT_EVENTS,
                key = key,
                aggregateType = "Contract",
                aggregateId = UUID.randomUUID().toString(),
                eventType = "RelayProbe",
                occurredAt = Instant.parse("2026-10-15T10:00:00Z"),
                payloadJson = """{"probe":true}""",
            ),
        )
        return id
    }

    private fun stateOf(ids: List<String>): List<RowState> = jdbc.sql(
        """
		SELECT id, attempts, last_error IS NOT NULL AS has_error, published_at IS NOT NULL AS published,
		       available_at > now() AS in_backoff
		FROM outbox_events
		WHERE id IN (:ids)
		""".trimIndent(),
    )
        .param("ids", ids)
        .query { rs, _ ->
            RowState(
                id = rs.getString("id"),
                attempts = rs.getInt("attempts"),
                hasError = rs.getBoolean("has_error"),
                published = rs.getBoolean("published"),
                inBackoff = rs.getBoolean("in_backoff"),
            )
        }
        .list()
        .sortedBy { ids.indexOf(it.id) }

    /** O que o teste viu dentro da transação: as linhas gravadas, as enviadas ao broker (em ordem) e o estado de cada linha. */
    private data class Observed(val ids: List<String>, val sentEventIds: List<String>, val states: List<RowState>)

    /** Estado de uma linha do outbox; `inBackoff` compara `available_at` com o `now()` da transação. */
    private data class RowState(
        val id: String,
        val attempts: Int = 0,
        val hasError: Boolean = false,
        val published: Boolean = false,
        val inBackoff: Boolean = false,
    )

    /** `KafkaTemplate` que não fala com broker: registra o `eventId` de cada envio e responde como o roteiro manda. */
    private class ScriptedKafkaTemplate :
        KafkaTemplate<String, String>(DefaultKafkaProducerFactory<String, String>(emptyMap<String, Any>())) {

        val sentEventIds = mutableListOf<String>()
        var outcome: (ProducerRecord<String, String>) -> CompletableFuture<SendResult<String, String>> = ::acknowledged

        override fun send(record: ProducerRecord<String, String>): CompletableFuture<SendResult<String, String>> {
            sentEventIds += eventIdOf(record)
            return outcome(record)
        }
    }
}

private fun eventIdOf(record: ProducerRecord<String, String>): String =
    record.headers().lastHeader("eventId").value().decodeToString()

private fun acknowledged(record: ProducerRecord<String, String>): CompletableFuture<SendResult<String, String>> =
    CompletableFuture.completedFuture(
        SendResult(record, RecordMetadata(TopicPartition(record.topic(), 0), 0L, 0, 0L, 0, 0)),
    )

/** O produtor recusa o envio na própria chamada, como o `KafkaTemplate` faz com falha imediata (`Send failed`). */
private fun rejectedSynchronously(cause: Throwable): Nothing = throw KafkaException("Send failed", cause)

private fun failedAsynchronously(failure: Throwable): CompletableFuture<SendResult<String, String>> =
    CompletableFuture.failedFuture(failure)

private fun neverAcknowledged(): CompletableFuture<SendResult<String, String>> = CompletableFuture()

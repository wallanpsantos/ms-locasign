package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.infra.config.LocaSignProperties
import br.com.locasign.shared.infra.persistence.getInstant
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Componente agendado de infraestrutura responsável pela leitura e publicação de eventos do Transactional Outbox.
 *
 * **Responsabilidade:**
 * - Consultar periodicamente eventos não publicados no PostgreSQL usando concorrência segura (`FOR UPDATE SKIP LOCKED`).
 * - Publicar mensagens ordenadamente no Apache Kafka garantindo entrega at-least-once e registrar timestamp de publicação e telemetria.
 */
@Component
class OutboxRelay(
    private val jdbc: JdbcClient,
    private val kafka: KafkaTemplate<String, String>,
    private val mapper: JsonMapper,
    private val transactions: TransactionRunner,
    private val properties: LocaSignProperties,
    registry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Gauge.builder("locasign.outbox.oldest_unpublished_age_seconds") { oldestUnpublishedAgeSeconds() }
            .description("Idade do evento mais antigo ainda não publicado no outbox")
            .register(registry)
    }

    @Scheduled(fixedDelayString = "\${locasign.outbox.relay-interval:PT1.5S}")
    fun relay() {
        try {
            // Continua enquanto os lotes vierem cheios, para escoar um acúmulo sem esperar o próximo ciclo.
            do {
                val rowsRead = relayBatch()
            } while (rowsRead >= properties.outbox.batchSize)
        } catch (e: Exception) {
            log.error("Falha no relay do outbox; nova tentativa no próximo ciclo", e)
        }
    }

    /** Publica um lote em uma transação e devolve quantas linhas foram lidas. */
    fun relayBatch(): Int = transactions.run {
        val rows = jdbc.sql(
            """
			SELECT id, topic, message_key, event_type, payload::text AS payload, headers::text AS headers
			FROM outbox_events
			WHERE published_at IS NULL AND available_at <= now()
			ORDER BY seq
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""".trimIndent(),
        )
            .param("limit", properties.outbox.batchSize)
            .query { rs, _ ->
                OutboxRow(
                    id = rs.getString("id"),
                    topic = rs.getString("topic"),
                    key = rs.getString("message_key"),
                    eventType = rs.getString("event_type"),
                    payload = rs.getString("payload"),
                    headers = rs.getString("headers"),
                )
            }
            .list()

        // Se uma mensagem falha, as seguintes da mesma chave esperam, para não inverter a ordem por chave.
        val blocked = mutableSetOf<Pair<String, String>>()
        for (row in rows) {
            val partition = row.topic to row.key
            if (partition in blocked) continue
            try {
                publish(row)
                markPublished(row.id)
            } catch (e: Exception) {
                blocked += partition
                markFailed(row.id, e)
                log.warn("Falha ao publicar o evento {} no tópico {}: {}", row.id, row.topic, e.message)
            }
        }
        rows.size
    }

    private fun publish(row: OutboxRow) {
        val record = ProducerRecord<String, String>(row.topic, row.key, row.payload)
        mapper.readTree(row.headers).properties().forEach { (name, value) ->
            record.headers().add(name, value.asString().toByteArray(Charsets.UTF_8))
        }
        kafka.send(record).get(properties.outbox.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun markPublished(id: String) {
        jdbc.sql("UPDATE outbox_events SET published_at = now(), last_error = NULL WHERE id = :id")
            .param("id", id)
            .update()
    }

    private fun markFailed(id: String, error: Exception) {
        jdbc.sql(
            """
			UPDATE outbox_events
			SET attempts = attempts + 1,
			    last_error = :error,
			    available_at = now() + (LEAST(:maxBackoff, power(2, attempts + 1)) * interval '1 second')
			WHERE id = :id
			""".trimIndent(),
        )
            .param("id", id)
            .param("error", error.message?.take(MAX_ERROR_LENGTH) ?: error.javaClass.simpleName)
            .param("maxBackoff", MAX_BACKOFF_SECONDS)
            .update()
    }

    private fun oldestUnpublishedAgeSeconds(): Double = try {
        val oldest: Instant? =
            jdbc.sql("SELECT min(created_at) AS oldest FROM outbox_events WHERE published_at IS NULL")
                .query { rs, _ -> rs.getInstant("oldest") }
                .single()
        oldest?.let { Duration.between(it, Instant.now()).toMillis() / MILLIS_PER_SECOND } ?: 0.0
    } catch (_: Exception) {
        0.0
    }

    /**
     * Modelo interno de representação de uma linha da tabela `outbox_events` em memória.
     *
     * **Responsabilidade:**
     * - Mapear os campos relacionais essenciais para montagem do registro Kafka e cálculo de cabeçalhos.
     */
    private class OutboxRow(
        val id: String,
        val topic: String,
        val key: String,
        val eventType: String,
        val payload: String,
        val headers: String,
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 500
        const val MAX_BACKOFF_SECONDS = 300
        const val MILLIS_PER_SECOND = 1000.0
    }
}

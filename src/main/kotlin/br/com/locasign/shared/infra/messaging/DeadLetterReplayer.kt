package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.DeadLetterReplayPort
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Reprocessamento manual da fila de erros: lê a DLT do tópico, republica cada mensagem no tópico
 * original e confirma o offset do grupo de replay. Como os consumidores são idempotentes (R6),
 * reenviar uma mensagem já tratada é seguro.
 */
@Component
class DeadLetterReplayer(
	private val consumerFactory: ConsumerFactory<String, String>,
	private val kafka: KafkaTemplate<String, String>,
	private val properties: LocaSignProperties,
) : DeadLetterReplayPort {
	private val log = LoggerFactory.getLogger(javaClass)
	private val lock = ReentrantLock()

	override fun replay(sourceTopic: String): Int {
		if (sourceTopic !in Topics.ALL) {
			throw DomainException.NotFound("Tópico", sourceTopic)
		}
		return lock.withLock { replayLocked(sourceTopic) }
	}

	private fun replayLocked(sourceTopic: String): Int {
		var replayed = 0
		consumerFactory.createConsumer(REPLAY_GROUP, "").use { consumer ->
			consumer.subscribe(listOf(sourceTopic + Topics.DEAD_LETTER_SUFFIX))
			var emptyPolls = 0
			while (emptyPolls < MAX_EMPTY_POLLS) {
				val records = consumer.poll(POLL_TIMEOUT)
				if (records.isEmpty) {
					emptyPolls++
					continue
				}
				emptyPolls = 0
				for (record in records) {
					val target = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)
						?.value()?.toString(Charsets.UTF_8) ?: sourceTopic
					val outgoing = ProducerRecord(target, record.key(), record.value())
					record.headers().forEach { header ->
						if (!header.key().startsWith(DLT_HEADER_PREFIX)) outgoing.headers().add(header)
					}
					kafka.send(outgoing).get(properties.outbox.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
					replayed++
				}
				consumer.commitSync()
			}
		}
		log.info("Reprocessamento da DLT de {} concluído: {} mensagens reenviadas", sourceTopic, replayed)
		return replayed
	}

	private companion object {
		const val REPLAY_GROUP = "locasign-dlt-replay"
		const val DLT_HEADER_PREFIX = "kafka_dlt-"
		const val MAX_EMPTY_POLLS = 2
		val POLL_TIMEOUT: Duration = Duration.ofSeconds(2)
	}
}

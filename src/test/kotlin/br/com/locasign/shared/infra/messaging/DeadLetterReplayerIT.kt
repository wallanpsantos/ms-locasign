package br.com.locasign.shared.infra.messaging

import br.com.locasign.shared.app.Topics
import br.com.locasign.support.IntegrationTestSupport
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O replay devolve a mensagem da DLT ao tópico original sem os headers `kafka_dlt-*`.
 *
 * O valor é um envelope que os três consumidores de `contract.events` aceitam sem erro (`eventType` sem roteamento e
 * `aggregateId` UUID): um valor ilegível voltaria à DLT na hora e seria relido pelo mesmo replay (`plan.md`, B8).
 */
class DeadLetterReplayerIT(
    @Autowired private val replayer: DeadLetterReplayer,
    @Autowired private val kafka: KafkaTemplate<String, String>,
    @Autowired private val consumerFactory: ConsumerFactory<String, String>,
) : IntegrationTestSupport() {

    @Test
    fun `replay devolve a mensagem da DLT ao tópico original sem os headers kafka_dlt`() {
        val key = "replay-it-${UUID.randomUUID()}"
        val value = """{"eventId":"${UUID.randomUUID()}","eventType":"ReplayProbe","aggregateId":"${UUID.randomUUID()}"}"""
        val deadLetter = ProducerRecord(Topics.CONTRACT_EVENTS + Topics.DEAD_LETTER_SUFFIX, key, value)
        deadLetter.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, Topics.CONTRACT_EVENTS.toByteArray())
        deadLetter.headers().add("kafka_dlt-exception-message", "falha simulada".toByteArray())
        deadLetter.headers().add("x-locasign-probe", "mantido".toByteArray())
        kafka.send(deadLetter).get(10, TimeUnit.SECONDS)

        val replayed = consumerFactory.createConsumer("dlt-replay-it-${UUID.randomUUID()}", "").use { consumer ->
            consumer.subscribe(listOf(Topics.CONTRACT_EVENTS))
            var found: ConsumerRecord<String, String>? = null
            // O replay é repetido até a mensagem aparecer: a 1ª chamada pode terminar antes de o consumidor
            // de replay receber a partição; depois de reenviar, as chamadas seguintes não acham mais nada na DLT.
            await().atMost(Duration.ofSeconds(90)).until {
                replayer.replay(Topics.CONTRACT_EVENTS)
                found = consumer.poll(Duration.ofSeconds(1)).firstOrNull { it.key() == key }
                found != null
            }
            checkNotNull(found)
        }

        assertEquals(value, replayed.value())
        assertEquals("mantido", replayed.headers().lastHeader("x-locasign-probe")?.value()?.toString(Charsets.UTF_8))
        assertTrue(replayed.headers().none { it.key().startsWith("kafka_dlt-") })
    }
}

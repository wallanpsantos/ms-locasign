package br.com.locasign.shared.infra.config

import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.MetricsPort
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries

/**
 * Configuração de infraestrutura do Spring Kafka para declaração explícita de tópicos e tratamento de erros.
 *
 * **Responsabilidade:**
 * - Registrar os beans de tópicos de produção e suas respectivas DLTs ([NewTopic]) para criação declarativa no broker.
 * - Configurar a estratégia de tratamento de erros com retentativa exponencial (3 tentativas) e desvio de mensagens venenosas ou ilegíveis para DLT com contagem de métricas de telemetria.
 */
@Configuration(proxyBeanMethods = false)
class KafkaConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun pandaDocWebhooksTopic(): NewTopic = topic(Topics.PANDADOC_WEBHOOKS, partitions = PARTITIONS)

    @Bean
    fun contractEventsTopic(): NewTopic = topic(Topics.CONTRACT_EVENTS, partitions = PARTITIONS)

    @Bean
    fun pandaDocWebhooksDeadLetterTopic(): NewTopic =
        topic(Topics.PANDADOC_WEBHOOKS + Topics.DEAD_LETTER_SUFFIX, partitions = DLT_PARTITIONS)

    @Bean
    fun contractEventsDeadLetterTopic(): NewTopic =
        topic(Topics.CONTRACT_EVENTS + Topics.DEAD_LETTER_SUFFIX, partitions = DLT_PARTITIONS)

    /** O Spring Boot aplica este `CommonErrorHandler` ao container factory dos `@KafkaListener`. */
    @Bean
    fun kafkaErrorHandler(kafka: KafkaTemplate<*, *>, metrics: MetricsPort): DefaultErrorHandler {
        val deadLetters = DeadLetterPublishingRecoverer(kafka) { record, _ ->
            // A DLT tem 1 partição: o destino é sempre a partição 0 do tópico original com o sufixo `.dlt`.
            TopicPartition(record.topic() + Topics.DEAD_LETTER_SUFFIX, 0)
        }
        val recoverer = ConsumerRecordRecoverer { record, error ->
            metrics.count(Metrics.DEAD_LETTER, "topic", record.topic())
            log.error(
                "Mensagem enviada para a DLT: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key(), error,
            )
            deadLetters.accept(record, error)
        }
        val backOff = ExponentialBackOffWithMaxRetries(MAX_RETRIES).apply {
            initialInterval = INITIAL_BACKOFF_MS
            multiplier = BACKOFF_MULTIPLIER
            maxInterval = MAX_BACKOFF_MS
        }
        return DefaultErrorHandler(recoverer, backOff).apply {
            // Payload ilegível não melhora com retentativa: vai direto para a DLT.
            addNotRetryableExceptions(UnreadableMessageException::class.java)
        }
    }

    private fun topic(name: String, partitions: Int): NewTopic =
        TopicBuilder.name(name).partitions(partitions).replicas(1).build()

    private companion object {
        const val PARTITIONS = 3
        const val DLT_PARTITIONS = 1
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1_000L
        const val BACKOFF_MULTIPLIER = 2.0
        const val MAX_BACKOFF_MS = 10_000L
    }
}

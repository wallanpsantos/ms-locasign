package br.com.locasign.shared.infra.observability

import org.apache.kafka.clients.admin.Admin
import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Indicador customizado de integridade e prontidão operacional do Apache Kafka integrado ao Spring Boot Actuator.
 *
 * **Responsabilidade:**
 * - Sondar a conectividade ativa com os nós do cluster Kafka utilizando o `AdminClient` com timeout rígido.
 * - Expor o status de saúde (`UP` ou `DOWN`) e metadados de diagnóstico (ID do cluster e quantidade de nós) no endpoint `/actuator/health`.
 */
@Component("kafka")
class KafkaHealthIndicator(private val kafkaAdmin: KafkaAdmin) : AbstractHealthIndicator("Kafka health check failed") {

    override fun doHealthCheck(builder: Health.Builder) {
        Admin.create(kafkaAdmin.configurationProperties).use { admin ->
            val cluster = admin.describeCluster()
            val clusterId = cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val nodes = cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            builder.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes.size)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 3L
    }
}

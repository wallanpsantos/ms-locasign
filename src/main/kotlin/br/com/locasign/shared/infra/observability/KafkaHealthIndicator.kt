package br.com.locasign.shared.infra.observability

import org.apache.kafka.clients.admin.Admin
import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Indicador de saúde do Kafka (o Actuator não traz um): consulta o cluster com tempo limite curto. */
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

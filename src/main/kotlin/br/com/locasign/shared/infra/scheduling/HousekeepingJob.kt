package br.com.locasign.shared.infra.scheduling

import br.com.locasign.shared.infra.config.LocaSignProperties
import br.com.locasign.shared.infra.persistence.toDb
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/** Limpeza diária (opcional): remove outbox publicado e inbox antigos, além de registros de idempotência. */
@Component
@ConditionalOnProperty(prefix = "locasign.jobs.housekeeping", name = ["enabled"], matchIfMissing = true)
class HousekeepingJob(
	private val jdbc: JdbcClient,
	private val clock: Clock,
	private val properties: LocaSignProperties,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(
		fixedDelayString = "\${locasign.jobs.housekeeping.interval:PT24H}",
		initialDelayString = "\${locasign.jobs.housekeeping.interval:PT24H}",
	)
	fun cleanup() {
		val cutoff = clock.instant().minus(properties.outbox.retention).toDb()
		try {
			val outbox = jdbc.sql("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :cutoff")
				.param("cutoff", cutoff).update()
			val inbox = jdbc.sql("DELETE FROM webhook_inbox WHERE received_at < :cutoff")
				.param("cutoff", cutoff).update()
			val processed = jdbc.sql("DELETE FROM processed_messages WHERE processed_at < :cutoff")
				.param("cutoff", cutoff).update()
			log.info("Limpeza concluída: outbox={}, inbox={}, processed_messages={}", outbox, inbox, processed)
		} catch (e: Exception) {
			log.error("Falha na limpeza periódica", e)
		}
	}
}

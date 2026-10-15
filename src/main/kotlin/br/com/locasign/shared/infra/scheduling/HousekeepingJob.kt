package br.com.locasign.shared.infra.scheduling

import br.com.locasign.shared.infra.config.LocaSignProperties
import br.com.locasign.shared.infra.persistence.toDb
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Tarefa agendada de infraestrutura para limpeza periódica de registros operacionais históricos.
 *
 * **Responsabilidade:**
 * - Expurgar registros defasados das tabelas `outbox_events` (já publicados), `webhook_inbox` e `processed_messages` com base no tempo de retenção configurado.
 * - Evitar crescimento indefinido do banco de dados relacional e manter a alta performance de índices no PostgreSQL.
 * - Isolar a falha de cada tabela: o `DELETE` que falha é registrado em log e as demais tabelas seguem sendo limpas.
 * - Fazer a primeira limpeza poucos minutos depois da subida, para que uma instância reiniciada com frequência (antes do intervalo de 24 h) também expurgue.
 */
@Component
@ConditionalOnProperty(prefix = "locasign.jobs.housekeeping", name = ["enabled"], matchIfMissing = true)
class HousekeepingJob(
    private val jdbc: JdbcClient,
    private val clock: Clock,
    private val properties: LocaSignProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${locasign.jobs.housekeeping.interval:PT24H}", initialDelayString = "PT5M")
    fun cleanup() {
        val cutoff = clock.instant().minus(properties.outbox.retention).toDb()
        purge(
            "outbox_events",
            "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :cutoff",
            cutoff,
        )
        purge("webhook_inbox", "DELETE FROM webhook_inbox WHERE received_at < :cutoff", cutoff)
        purge("processed_messages", "DELETE FROM processed_messages WHERE processed_at < :cutoff", cutoff)
    }

    /** Executa o `DELETE` de uma tabela; a falha fica no log (sem dados das linhas) e não interrompe a limpeza das outras. */
    private fun purge(table: String, sql: String, cutoff: OffsetDateTime) {
        try {
            val removed = jdbc.sql(sql).param("cutoff", cutoff).update()
            log.info("Limpeza de {} concluída: {} linha(s) removida(s)", table, removed)
        } catch (e: Exception) {
            log.error("Falha na limpeza periódica de {}; as demais tabelas seguem", table, e)
        }
    }
}

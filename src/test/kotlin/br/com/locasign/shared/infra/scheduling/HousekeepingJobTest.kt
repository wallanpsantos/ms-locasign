package br.com.locasign.shared.infra.scheduling

import br.com.locasign.shared.infra.config.LocaSignProperties
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A limpeza começa logo depois da subida e a falha em uma tabela não impede a limpeza das outras. */
class HousekeepingJobTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `primeira limpeza roda poucos minutos depois da subida e não só depois do intervalo de 24 h`() {
        val scheduled =
            assertNotNull(HousekeepingJob::class.java.getMethod("cleanup").getAnnotation(Scheduled::class.java))

        val initialDelay = scheduled.initialDelayString

        assertFalse(
            "locasign.jobs.housekeeping.interval" in initialDelay,
            "o atraso inicial não pode herdar o intervalo: $initialDelay"
        )
        assertTrue(
            Duration.parse(initialDelay) <= Duration.ofMinutes(10),
            "atraso inicial grande demais: $initialDelay"
        )
    }

    @Test
    fun `falha no DELETE de uma tabela não impede a limpeza das outras duas`() {
        val sql = SqlRecorder(failingTable = "outbox_events")

        HousekeepingJob(sql.jdbc, clock, LocaSignProperties()).cleanup()

        assertEquals(
            listOf("outbox_events", "webhook_inbox", "processed_messages"),
            sql.attemptedTables,
            "cada tabela é limpa mesmo quando uma anterior falha",
        )
    }

    /**
     * `JdbcClient` de teste, só com o que o job usa (`sql(...).param(...).update()`): anota a tabela de cada `DELETE`
     * e lança [DataAccessResourceFailureException] no `DELETE` da tabela [failingTable].
     */
    private class SqlRecorder(private val failingTable: String) {
        val attemptedTables = mutableListOf<String>()

        val jdbc: JdbcClient = proxyOf(JdbcClient::class.java) { _, method, args ->
            check(method.name == "sql") { "o job só deveria chamar sql(...), mas chamou ${method.name}" }
            statementFor(args.first().toString())
        }

        private fun statementFor(sql: String): JdbcClient.StatementSpec {
            val table = sql.substringAfter("DELETE FROM ").substringBefore(' ')
            return proxyOf(JdbcClient.StatementSpec::class.java) { statement, method, _ ->
                when (method.name) {
                    "param" -> statement
                    "update" -> {
                        attemptedTables += table
                        if (table == failingTable) throw DataAccessResourceFailureException("falha simulada em $table")
                        0
                    }

                    else -> error("o job só deveria chamar param(...) e update(), mas chamou ${method.name}")
                }
            }
        }

        private fun <T> proxyOf(type: Class<T>, handler: (Any, Method, Array<Any?>) -> Any?): T =
            type.cast(
                Proxy.newProxyInstance(type.classLoader, arrayOf<Class<*>>(type)) { proxy, method, args ->
                    handler(proxy, method, args ?: emptyArray())
                },
            )
    }
}

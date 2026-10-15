package br.com.locasign.shared.infra.persistence

import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.support.IntegrationTestSupport
import br.com.locasign.support.runAndRollback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.Test
import kotlin.test.assertTrue

/** A migration V2 cria os índices que sustentam os três `DELETE` do `HousekeepingJob`, sem `Seq Scan` nas tabelas grandes. */
class HousekeepingIndexesIT(
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val transactions: TransactionRunner,
) : IntegrationTestSupport() {

    /** Índice, tabela e o `DELETE` do `HousekeepingJob` que ele atende (mesmo predicado do job). */
    private data class Case(val index: String, val definition: String, val delete: String)

    private val cases = listOf(
        Case(
            index = "ix_outbox_published",
            definition = "ON public.outbox_events USING btree (published_at) WHERE (published_at IS NOT NULL)",
            delete = "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < now() - interval '30 days'",
        ),
        Case(
            index = "ix_inbox_received",
            definition = "ON public.webhook_inbox USING btree (received_at)",
            delete = "DELETE FROM webhook_inbox WHERE received_at < now() - interval '30 days'",
        ),
        Case(
            index = "ix_processed_at",
            definition = "ON public.processed_messages USING btree (processed_at)",
            delete = "DELETE FROM processed_messages WHERE processed_at < now() - interval '30 days'",
        ),
    )

    @Test
    fun `migration cria os três índices de apoio à limpeza com a definição esperada`() {
        val definitions = jdbc.sql("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'")
            .query { rs, _ -> rs.getString("indexname") to rs.getString("indexdef") }
            .list()
            .toMap()

        cases.forEach { case ->
            val actual = definitions[case.index]
            assertTrue(
                actual != null && actual.endsWith(case.definition),
                "${case.index}: esperava `${case.definition}`, veio `$actual`"
            )
        }
    }

    @Test
    fun `cada DELETE do housekeeping usa o seu índice quando o planner não pode escolher Seq Scan`() {
        cases.forEach { case ->
            val plan = transactions.runAndRollback {
                jdbc.sql("SET LOCAL enable_seqscan = off").update()
                jdbc.sql("EXPLAIN ${case.delete}").query(String::class.java).list().joinToString("\n")
            }

            assertTrue(case.index in plan, "o plano de `${case.delete}` deveria usar ${case.index}, mas foi:\n$plan")
        }
    }
}

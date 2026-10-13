package br.com.locasign.support

import br.com.locasign.contract.app.fakes.FixedSignatureProvider
import br.com.locasign.contract.infra.scheduling.ExpirationJob
import br.com.locasign.contract.infra.scheduling.ReconciliationJob
import br.com.locasign.contract.infra.scheduling.ReminderJob
import br.com.locasign.shared.infra.config.LocaSignProperties
import br.com.locasign.shared.infra.scheduling.HousekeepingJob
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Garante que [IntegrationTestSupport] aplica o AGENTS.md §7 e docs/agents/testes.md §6 a todo `*IT` que herda dela. */
class IntegrationTestSupportIT(
    @Autowired private val context: ApplicationContext,
    @Autowired private val properties: LocaSignProperties,
) : IntegrationTestSupport() {

    @Test
    fun `contexto de integração usa o provedor fixo no lugar da PandaDoc`() {
        assertIs<FixedSignatureProvider>(context.getBean("signatureProvider"))
    }

    @Test
    fun `contexto de integração não registra os jobs agendados`() {
        listOf(
            ReconciliationJob::class,
            ExpirationJob::class,
            ReminderJob::class,
            HousekeepingJob::class
        ).forEach { job ->
            assertTrue(context.getBeanNamesForType(job.java).isEmpty(), "${job.simpleName} deveria estar desligado")
        }
    }

    @Test
    fun `contexto de integração acelera o relay do outbox e arquiva PDFs em diretório temporário`() {
        assertEquals(Duration.ofMillis(200), properties.outbox.relayInterval)

        val tmp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        val archive = Path.of(properties.archive.directory).toAbsolutePath().normalize()
        assertTrue(archive.startsWith(tmp), "$archive deveria ficar em $tmp")
    }

    @Test
    fun `contexto de integração define a chave de webhook de teste`() {
        assertTrue(properties.pandadoc.webhookSharedKey.isNotBlank(), "sem a chave, todo webhook recebe 401")
    }
}

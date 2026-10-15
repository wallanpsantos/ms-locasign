package br.com.locasign.shared.infra.config

import br.com.locasign.support.IntegrationTestSupport
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.kafka.core.ProducerFactory
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Garante que os ajustes de desempenho declarados em `application.yaml` chegam aos componentes que os consomem.
 *
 * **Responsabilidade:**
 * - Provar, com o contexto Spring completo, que o pool Hikari, o producer Kafka, o cache do health e o limite do corpo
 *   do webhook partem dos valores esperados quando nenhuma variável de ambiente os sobrescreve.
 * - Proteger o contrato "200 rápido" do webhook (R7): sem `connection-timeout` curto, uma rajada espera até 30 s por conexão.
 */
class PerformanceConfigurationIT(
    @Autowired private val dataSource: DataSource,
    @Autowired private val producerFactory: ProducerFactory<*, *>,
    @Autowired private val environment: Environment,
) : IntegrationTestSupport() {

    @Test
    fun `pool Hikari tem dez conexões no máximo (R7)`() {
        val pool = assertIs<HikariDataSource>(dataSource)

        assertEquals(10, pool.maximumPoolSize)
    }

    @Test
    fun `pool Hikari espera no máximo 5 segundos por uma conexão (R7)`() {
        val pool = assertIs<HikariDataSource>(dataSource)

        assertEquals(5_000L, pool.connectionTimeout)
    }

    @Test
    fun `producer Kafka envia sem esperar o linger padrão`() {
        val lingerMs = producerFactory.configurationProperties["linger.ms"]

        assertEquals("0", lingerMs?.toString())
    }

    @Test
    fun `health do actuator guarda a resposta por 10 segundos`() {
        val ttl = Binder.get(environment)
            .bind("management.endpoint.health.cache.time-to-live", Duration::class.java)

        assertEquals(Duration.ofSeconds(10), ttl.orElse(null))
    }

    @Test
    fun `corpo do webhook da PandaDoc tem limite padrão de 1 MiB (R7)`() {
        val limit = environment.getProperty("locasign.pandadoc.webhook-max-body-bytes", Int::class.java)

        assertNotNull(
            limit,
            "a propriedade locasign.pandadoc.webhook-max-body-bytes precisa existir no application.yaml"
        )
        assertEquals(1_048_576, limit)
    }

    @Test
    fun `consumer Kafka busca no máximo dez mensagens por poll (RL1)`() {
        val maxPoll = environment.getProperty("spring.kafka.consumer.max-poll-records", Int::class.java)

        assertEquals(10, maxPoll)
    }
}

package br.com.locasign.contract.infra.pandadoc

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.shared.app.fakes.RecordingMetricsPort
import br.com.locasign.shared.app.ports.Metrics
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Relógio e espera falsos: o tempo só anda quando o teste o move ou quando o limitador "dorme". */
class SlidingWindowRateLimiterTest {

    private var nanos = 0L
    private val sleeps = mutableListOf<Long>()
    private val metrics = RecordingMetricsPort()

    private fun limiter(
        permits: Int,
        maxWait: Duration = Duration.ofSeconds(30),
        fullWindowMaxWait: Duration = Duration.ofSeconds(70),
    ) = SlidingWindowRateLimiter(
        permitsPerMinute = permits,
        metrics = metrics,
        maxWait = maxWait,
        fullWindowMaxWait = fullWindowMaxWait,
        nanoTime = { nanos },
        sleeper = { millis ->
            sleeps += millis
            nanos += TimeUnit.MILLISECONDS.toNanos(millis)
        },
    )

    private fun advanceSeconds(seconds: Long) {
        nanos += TimeUnit.SECONDS.toNanos(seconds)
    }

    @Test
    fun `permite até o limite dentro da janela de 1 minuto sem esperar`() {
        val limiter = limiter(permits = 2)

        limiter.acquire("create")
        advanceSeconds(1)
        limiter.acquire("create")

        assertTrue(sleeps.isEmpty())
        assertTrue(metrics.counted.isEmpty())
    }

    @Test
    fun `com a janela cheia espera a permissão mais antiga sair da janela e então reserva`() {
        val limiter = limiter(permits = 1)
        limiter.acquire("create")
        advanceSeconds(40)

        limiter.acquire("create")

        assertEquals(listOf(TimeUnit.SECONDS.toMillis(20) + 1), sleeps)
        assertEquals(listOf(Metrics.PROVIDER_LOCAL_RATE_WAITED to listOf("operation", "create")), metrics.counted)
    }

    @Test
    fun `espera acima do máximo lança RateLimited sem dormir e conta a métrica de bloqueio local`() {
        val limiter = limiter(permits = 1, maxWait = Duration.ofSeconds(10))
        limiter.acquire("send")
        advanceSeconds(5)

        val ex = assertFailsWith<ProviderException.RateLimited> { limiter.acquire("send", RateLimitWait.SHORT) }

        assertEquals("Limite local de 1 requisições/min atingido para 'send'", ex.message)
        assertTrue(sleeps.isEmpty())
        assertEquals(listOf(Metrics.PROVIDER_LOCAL_RATE_LIMITED to listOf("operation", "send")), metrics.counted)
    }

    @Test
    fun `FULL_WINDOW aceita esperar a janela inteira enquanto SHORT falha rápido`() {
        val limiter = limiter(permits = 1, maxWait = Duration.ofSeconds(10), fullWindowMaxWait = Duration.ofSeconds(70))
        limiter.acquire("send")
        advanceSeconds(10)

        // Com SHORT espera seria de 50 s > 10 s -> estoura
        assertFailsWith<ProviderException.RateLimited> { limiter.acquire("send", RateLimitWait.SHORT) }

        // Com FULL_WINDOW espera de 50 s <= 70 s -> dorme e adquire
        limiter.acquire("send", RateLimitWait.FULL_WINDOW)
        assertEquals(listOf(TimeUnit.SECONDS.toMillis(50) + 1), sleeps)
    }

    @Test
    fun `operações diferentes têm janelas independentes`() {
        val limiter = limiter(permits = 1)

        limiter.acquire("create")
        limiter.acquire("send")

        assertTrue(sleeps.isEmpty())
    }
}

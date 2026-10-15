package br.com.locasign.contract.infra.pandadoc

import br.com.locasign.contract.app.ports.out.integration.ProviderException
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

    private fun limiter(permits: Int, maxWait: Duration = Duration.ofSeconds(30)) = SlidingWindowRateLimiter(
        permitsPerMinute = permits,
        maxWait = maxWait,
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
    }

    @Test
    fun `com a janela cheia espera a permissão mais antiga sair da janela e então reserva`() {
        val limiter = limiter(permits = 1)
        limiter.acquire("create")
        advanceSeconds(40)

        limiter.acquire("create")

        assertEquals(listOf(TimeUnit.SECONDS.toMillis(20) + 1), sleeps)
    }

    @Test
    fun `espera acima do máximo lança RateLimited sem dormir`() {
        val limiter = limiter(permits = 1, maxWait = Duration.ofSeconds(10))
        limiter.acquire("send")
        advanceSeconds(5)

        val ex = assertFailsWith<ProviderException.RateLimited> { limiter.acquire("send") }

        assertEquals("Limite local de 1 requisições/min atingido para 'send'", ex.message)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `operações diferentes têm janelas independentes`() {
        val limiter = limiter(permits = 1)

        limiter.acquire("create")
        limiter.acquire("send")

        assertTrue(sleeps.isEmpty())
    }
}

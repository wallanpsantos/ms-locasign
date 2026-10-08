package br.com.locasign.contract.infra.pandadoc

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Limitador de taxa em memória baseado em algoritmo de janela deslizante de 1 minuto para requisições externas à PandaDoc.
 *
 * **Responsabilidade:**
 * - Proteger a aplicação contra bloqueios por excesso de requisições (HTTP 429) no sandbox ou produção da PandaDoc.
 * - Bloquear a execução de threads concorrentes até a liberação de permissão dentro da janela de tempo ou lançar [ProviderException.RateLimited] ao exceder o tempo limite de espera ([maxWait]).
 */
class SlidingWindowRateLimiter(
    private val permitsPerMinute: Int,
    private val maxWait: Duration = Duration.ofSeconds(30),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** Bloqueia (thread virtual ou do consumidor) até haver permissão para [operation]. */
    fun acquire(operation: String) {
        val window = windows.computeIfAbsent(operation) { ArrayDeque() }
        val deadline = nanoTime() + maxWait.toNanos()
        while (true) {
            val waitNanos = synchronized(window) {
                val now = nanoTime()
                while (window.isNotEmpty() && now - window.first() >= WINDOW_NANOS) window.removeFirst()
                if (window.size < permitsPerMinute) {
                    window.addLast(now)
                    return
                }
                window.first() + WINDOW_NANOS - now
            }
            if (nanoTime() + waitNanos > deadline) {
                throw ProviderException.RateLimited("Limite local de $permitsPerMinute requisições/min atingido para '$operation'")
            }
            sleeper(TimeUnit.NANOSECONDS.toMillis(waitNanos) + 1)
        }
    }

    private companion object {
        val WINDOW_NANOS: Long = TimeUnit.MINUTES.toNanos(1)
    }
}

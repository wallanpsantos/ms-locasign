package br.com.locasign.contract.infra.pandadoc

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.MetricsPort
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Quanto quem chama aceita esperar por uma permissão do [SlidingWindowRateLimiter] antes de receber [ProviderException.RateLimited].
 *
 * **Responsabilidade:**
 * - Separar o chamador que não pode ficar parado ([SHORT]: jobs e endpoint de operador) do consumidor Kafka, que prefere esperar a cota a mandar o evento para a DLT por uma limitação local ([FULL_WINDOW]).
 */
enum class RateLimitWait { SHORT, FULL_WINDOW }

/**
 * Limitador de taxa em memória baseado em algoritmo de janela deslizante de 1 minuto para requisições externas à PandaDoc.
 *
 * **Responsabilidade:**
 * - Proteger a aplicação contra bloqueios por excesso de requisições (HTTP 429) no sandbox ou produção da PandaDoc.
 * - Bloquear a execução de threads concorrentes até a liberação de permissão dentro da janela de tempo ou lançar [ProviderException.RateLimited] ao exceder o tempo limite de espera ([maxWait]).
 */
class SlidingWindowRateLimiter(
    private val permitsPerMinute: Int,
    private val metrics: MetricsPort,
    private val maxWait: Duration = Duration.ofSeconds(30),
    private val fullWindowMaxWait: Duration = Duration.ofSeconds(70),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** Bloqueia (thread virtual ou do consumidor) até haver permissão para [operation]. */
    fun acquire(operation: String, wait: RateLimitWait = RateLimitWait.SHORT) {
        val window = windows.computeIfAbsent(operation) { ArrayDeque() }
        val timeout = when (wait) {
            RateLimitWait.SHORT -> maxWait
            RateLimitWait.FULL_WINDOW -> fullWindowMaxWait
        }
        val deadline = nanoTime() + timeout.toNanos()
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
                metrics.count(Metrics.PROVIDER_LOCAL_RATE_LIMITED, "operation", operation)
                throw ProviderException.RateLimited("Limite local de $permitsPerMinute requisições/min atingido para '$operation'")
            }
            metrics.count(Metrics.PROVIDER_LOCAL_RATE_WAITED, "operation", operation)
            sleeper(TimeUnit.NANOSECONDS.toMillis(waitNanos) + 1)
        }
    }

    private companion object {
        val WINDOW_NANOS: Long = TimeUnit.MINUTES.toNanos(1)
    }
}

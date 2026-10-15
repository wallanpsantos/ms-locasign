package br.com.locasign.shared.infra.observability

import br.com.locasign.shared.app.ports.ContextKeys
import br.com.locasign.shared.app.ports.CorrelationContext
import br.com.locasign.shared.app.ports.MetricsPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.stereotype.Component

/**
 * Adaptador de infraestrutura para coleta de métricas operacionais via Micrometer, expostas em `/actuator/metrics` (não há Prometheus).
 *
 * **Responsabilidade:**
 * - Implementar a porta [MetricsPort], incrementando contadores dimensionais no [MeterRegistry] do Spring Boot.
 * - Desacoplar os casos de uso de bibliotecas concretas de métricas.
 */
@Component
class MicrometerMetricsAdapter(private val registry: MeterRegistry) : MetricsPort {
    override fun count(name: String, vararg tags: String) {
        registry.counter(name, *tags).increment()
    }
}

/**
 * Adaptador de infraestrutura para injeção do contexto de correlação no MDC do SLF4J.
 *
 * **Responsabilidade:**
 * - Implementar a porta [CorrelationContext], gravando `correlationId`, `causationId` e metadados no MDC da thread corrente.
 * - Restaurar o mapa anterior do MDC ao término do bloco: o MDC é por thread, e a restauração evita vazamento para a próxima tarefa na mesma thread ou para o contexto externo em chamadas aninhadas.
 */
@Component
class MdcCorrelationContext : CorrelationContext {

    override fun <T> with(
        correlationId: String?,
        causationId: String?,
        entries: Map<String, String>,
        block: () -> T,
    ): T {
        val previous: Map<String, String>? = MDC.getCopyOfContextMap()
        try {
            correlationId?.let { MDC.put(ContextKeys.CORRELATION_ID, it) }
            causationId?.let { MDC.put(ContextKeys.CAUSATION_ID, it) }
            entries.forEach { (key, value) -> MDC.put(key, value) }
            return block()
        } finally {
            if (previous == null) MDC.clear() else MDC.setContextMap(previous)
        }
    }
}

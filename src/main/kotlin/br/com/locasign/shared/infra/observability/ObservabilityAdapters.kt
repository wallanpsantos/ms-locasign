package br.com.locasign.shared.infra.observability

import br.com.locasign.shared.app.ports.ContextKeys
import br.com.locasign.shared.app.ports.CorrelationContext
import br.com.locasign.shared.app.ports.MetricsPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.stereotype.Component

@Component
class MicrometerMetricsAdapter(private val registry: MeterRegistry) : MetricsPort {
	override fun count(name: String, vararg tags: String) {
		registry.counter(name, *tags).increment()
	}
}

/** Propaga correlação e causalidade pelo MDC do SLF4J, restaurando o contexto anterior ao terminar. */
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

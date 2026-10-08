package br.com.locasign.shared.app.ports

/** Chaves de contexto de log (MDC) usadas em logs estruturados e nos envelopes de eventos. */
object ContextKeys {
	const val CORRELATION_ID = "correlationId"
	const val CAUSATION_ID = "causationId"
	const val CONTRACT_ID = "contractId"
	const val EVENT_ID = "eventId"
}

/**
 * Propaga `correlationId` (origem da requisição ou do webhook) e `causationId` (mensagem que
 * causou a atual) para logs e envelopes de eventos, sem poluir as assinaturas dos use cases.
 */
interface CorrelationContext {
	fun <T> with(
		correlationId: String?,
		causationId: String? = null,
		entries: Map<String, String> = emptyMap(),
		block: () -> T,
	): T
}

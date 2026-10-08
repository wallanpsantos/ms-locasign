package br.com.locasign.shared.domain

import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Fato relevante ocorrido em um agregado. O [eventId] é a chave de idempotência dos consumidores.
 */
interface DomainEvent {
    val eventId: Uuid
    val occurredAt: Instant
    val aggregateType: String
    val aggregateId: String

    /** Nome estável do evento, usado no envelope e no roteamento dos consumidores. */
    val eventType: String
        get() = this::class.simpleName ?: error("Evento de domínio anônimo não é suportado")
}

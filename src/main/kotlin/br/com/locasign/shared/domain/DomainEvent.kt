package br.com.locasign.shared.domain

import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Contrato base para todos os eventos de domínio emitidos pelos agregados do sistema.
 *
 * **Responsabilidade:**
 * - Definir o contrato uniforme de eventos de domínio com identificador único ([eventId]), data/hora de ocorrência ([occurredAt]), tipo de agregado e nome canônico do evento.
 * - Servir como chave de deduplicação e idempotência para consumidores assíncronos e mensageria via transactional outbox.
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

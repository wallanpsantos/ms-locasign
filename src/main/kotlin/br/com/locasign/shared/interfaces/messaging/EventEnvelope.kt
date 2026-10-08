package br.com.locasign.shared.interfaces.messaging

import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.ports.ContextKeys
import br.com.locasign.shared.app.ports.CorrelationContext
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Envelope das mensagens (guia, seção 7.3). O `payload` fica como árvore: cada consumidor lê o que precisa. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EventEnvelope(
    val eventId: String,
    val eventType: String,
    val schemaVersion: Int = 1,
    val occurredAt: String? = null,
    val aggregateType: String? = null,
    val aggregateId: String,
    val correlationId: String? = null,
    val causationId: String? = null,
    val payload: JsonNode? = null,
)

/** Lê o envelope e executa o tratamento da mensagem com o contexto de correlação preenchido. */
@Component
class EnvelopeReader(
    private val mapper: JsonMapper,
    private val correlation: CorrelationContext,
) {
    /** Mensagem ilegível lança [UnreadableMessageException], que vai direto para a DLT. */
    fun read(json: String): EventEnvelope = try {
        mapper.readValue(json, EventEnvelope::class.java)
    } catch (e: JacksonException) {
        throw UnreadableMessageException("Envelope inválido: ${e.originalMessage}", e)
    }

    /** O `payload` como texto JSON, para consumidores que repassam o conteúdo (por exemplo, itens de webhook). */
    fun payloadJson(envelope: EventEnvelope): String {
        val payload = envelope.payload ?: throw UnreadableMessageException("Envelope ${envelope.eventId} sem payload")
        return mapper.writeValueAsString(payload)
    }

    /** Executa [block] com `correlationId`, `causationId` e `eventId` no contexto de log, mais [extraEntries]. */
    fun <T> handle(envelope: EventEnvelope, extraEntries: Map<String, String> = emptyMap(), block: () -> T): T =
        correlation.with(
            correlationId = envelope.correlationId ?: envelope.eventId,
            causationId = envelope.eventId,
            entries = mapOf(ContextKeys.EVENT_ID to envelope.eventId) + extraEntries,
            block = block,
        )
}

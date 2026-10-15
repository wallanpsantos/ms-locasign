package br.com.locasign.shared.interfaces.messaging

import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import org.slf4j.MDC
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class EventEnvelopeTest {

    private val mapper = JsonMapper.builder().findAndAddModules().build()
    private val correlation = MdcCorrelationContext()
    private val reader = EnvelopeReader(mapper, correlation)

    private fun envelope(aggregateId: String) =
        EventEnvelope(eventId = "evt-1", eventType = "ContractSent", aggregateId = aggregateId)

    @Test
    fun `aggregateId inválido é mensagem ilegível com o id do evento na mensagem (DLT)`() {
        val ex = assertFailsWith<UnreadableMessageException> { envelope("x").aggregateUuid() }

        assertEquals("Evento evt-1 com aggregateId inválido: x", ex.message)
    }

    @Test
    fun `aggregateId UUID válido é devolvido como o mesmo Uuid`() {
        val id = Uuid.random()

        assertEquals(id, envelope(id.toString()).aggregateUuid())
    }

    @Test
    fun `EnvelopeReader faz parse de JSON valido com payload polimorfico`() {
        val json =
            """{"eventId":"evt-100","eventType":"ContractRequested","aggregateId":"agg-1","payload":{"key":"value"}}"""
        val parsed = reader.read(json)

        assertEquals("evt-100", parsed.eventId)
        assertEquals("ContractRequested", parsed.eventType)
        assertEquals("agg-1", parsed.aggregateId)
        assertNotNull(parsed.payload)
        assertEquals("""{"key":"value"}""", reader.payloadJson(parsed))
    }

    @Test
    fun `EnvelopeReader trata JSON corrompido como UnreadableMessageException para DLT imediata`() {
        val corruptJson = "{nao-e-json}"
        val ex = assertFailsWith<UnreadableMessageException> { reader.read(corruptJson) }

        assertEquals(true, ex.message?.contains("Envelope inválido"))
    }

    @Test
    fun `payloadJson lanca UnreadableMessageException quando payload e nulo`() {
        val envelopeWithoutPayload = EventEnvelope(eventId = "evt-200", eventType = "Probe", aggregateId = "agg-2")
        val ex = assertFailsWith<UnreadableMessageException> { reader.payloadJson(envelopeWithoutPayload) }

        assertEquals("Envelope evt-200 sem payload", ex.message)
    }

    @Test
    fun `handle vincula correlationId e eventId ao contexto de log e limpa apos execucao`() {
        val env = EventEnvelope(
            eventId = "evt-300",
            eventType = "Probe",
            aggregateId = "agg-3",
            correlationId = "corr-fixed",
        )

        reader.handle(env, mapOf("customKey" to "customValue")) {
            assertEquals("corr-fixed", MDC.get("correlationId"))
            assertEquals("evt-300", MDC.get("causationId"))
            assertEquals("evt-300", MDC.get("eventId"))
            assertEquals("customValue", MDC.get("customKey"))
        }

        // Verifica que o MDC foi limpo após o bloco
        assertEquals(null, MDC.get("correlationId"))
        assertEquals(null, MDC.get("causationId"))
    }
}

package br.com.locasign.shared.interfaces.messaging

import br.com.locasign.shared.app.UnreadableMessageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class EventEnvelopeTest {

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
}

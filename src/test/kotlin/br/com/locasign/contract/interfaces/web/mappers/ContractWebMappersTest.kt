package br.com.locasign.contract.interfaces.web.mappers

import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.app.queries.SignerView
import br.com.locasign.contract.interfaces.web.dto.response.HistoryEntryResponse
import br.com.locasign.contract.interfaces.web.dto.response.SignerResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cada campo recebe um valor distinto, para que uma troca de argumentos do mesmo tipo seja detectada. */
class ContractWebMappersTest {

    private val at = Instant.parse("2026-10-09T10:00:00Z")

    @Test
    fun `signatário é convertido campo a campo para a resposta`() {
        val view = SignerView(
            role = "TENANT",
            name = "Juliana Mendes",
            email = "juliana@example.com",
            signingOrder = 1,
            completedAt = at,
        )

        val expected = SignerResponse(
            role = "TENANT",
            name = "Juliana Mendes",
            email = "juliana@example.com",
            signingOrder = 1,
            completedAt = at,
        )
        assertEquals(expected, view.toResponse())
    }

    @Test
    fun `entrada do histórico é convertida campo a campo para a resposta`() {
        val view = HistoryEntryView(
            id = "historico-1",
            fromStatus = "SENT",
            toStatus = "VIEWED",
            outcome = "APPLIED",
            source = "WEBHOOK",
            sourceEventId = "evt-1",
            note = "Documento visualizado.",
            occurredAt = at,
        )

        val expected = HistoryEntryResponse(
            id = "historico-1",
            fromStatus = "SENT",
            toStatus = "VIEWED",
            outcome = "APPLIED",
            source = "WEBHOOK",
            sourceEventId = "evt-1",
            note = "Documento visualizado.",
            occurredAt = at,
        )
        assertEquals(expected, view.toResponse())
    }
}

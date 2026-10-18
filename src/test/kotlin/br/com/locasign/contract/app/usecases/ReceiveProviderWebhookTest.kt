package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FixedWebhookGateway
import br.com.locasign.contract.app.ports.`in`.ReceiveProviderWebhookCommand
import br.com.locasign.contract.app.ports.`in`.WebhookReceipt
import br.com.locasign.contract.app.ports.out.integration.WebhookItem
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryOutboxPort
import br.com.locasign.shared.app.fakes.InMemoryWebhookInbox
import br.com.locasign.shared.app.fakes.RecordingMetricsPort
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.OutboxMessage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiveProviderWebhookTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val gateway = FixedWebhookGateway()
    private val inbox = InMemoryWebhookInbox()
    private val outbox = InMemoryOutboxPort()
    private val metrics = RecordingMetricsPort()

    private val receiveProviderWebhook = ReceiveProviderWebhook(
        gateway = gateway,
        inbox = inbox,
        outbox = outbox,
        clock = FixedBusinessClock(now),
        metrics = metrics,
        transactions = ImmediateTransactionRunner,
    )

    private val body = """[{"event":"document_state_changed"},{"event":"?"}]"""

    private fun receive(
        rawBody: String = body,
        deliveryId: String? = "entrega-1",
        signature: String? = FixedWebhookGateway.VALID_SIGNATURE,
    ): WebhookReceipt =
        receiveProviderWebhook.execute(ReceiveProviderWebhookCommand(rawBody.toByteArray(), signature, deliveryId))

    private fun counted(vararg names: String) = names.map { it to emptyList<String>() }

    @Test
    fun `entrega sem identificador usa o SHA-256 do corpo em hexadecimal minúsculo como deliveryId (R7)`() {
        val sha256OfEmptyArray = "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945"

        assertEquals(WebhookReceipt.Accepted(sha256OfEmptyArray, 0), receive(rawBody = "[]", deliveryId = null))
        assertEquals(WebhookReceipt.Duplicate(sha256OfEmptyArray), receive(rawBody = "[]", deliveryId = "   "))

        assertEquals(mapOf(sha256OfEmptyArray to "[]"), inbox.stored)
        assertEquals(counted(Metrics.WEBHOOK_RECEIVED, Metrics.WEBHOOK_DUPLICATE), metrics.counted)
    }

    @Test
    fun `cada item da entrega vira uma mensagem do outbox com id deliveryId e índice (R7)`() {
        val named = WebhookItem("""{"event":"document_state_changed"}""", "document_state_changed", "doc-1")
        val anonymous = WebhookItem("""{"event":"?"}""", null, null)
        gateway.items = listOf(named, anonymous)

        val receipt = receive(deliveryId = "  entrega-1  ")

        assertEquals(WebhookReceipt.Accepted("entrega-1", 2), receipt)
        assertEquals(mapOf("entrega-1" to body), inbox.stored)
        val expected = listOf(
            OutboxMessage(
                id = "entrega-1:0",
                topic = Topics.PANDADOC_WEBHOOKS,
                key = "doc-1",
                aggregateType = "ProviderWebhook",
                aggregateId = "doc-1",
                eventType = "document_state_changed",
                occurredAt = now,
                payloadJson = named.json,
            ),
            OutboxMessage(
                id = "entrega-1:1",
                topic = Topics.PANDADOC_WEBHOOKS,
                key = "entrega-1",
                aggregateType = "ProviderWebhook",
                aggregateId = "entrega-1",
                eventType = "unknown",
                occurredAt = now,
                payloadJson = anonymous.json,
            ),
        )
        assertEquals(expected, outbox.messages)
        assertEquals(counted(Metrics.WEBHOOK_RECEIVED), metrics.counted)
    }

    @Test
    fun `entrega repetida devolve Duplicate sem gravar mensagens novas`() {
        gateway.items = listOf(WebhookItem("""{"event":"document_state_changed"}""", "document_state_changed", "doc-1"))

        receive()
        val second = receive()

        assertEquals(WebhookReceipt.Duplicate("entrega-1"), second)
        assertEquals(1, outbox.messages.size)
        assertEquals(counted(Metrics.WEBHOOK_RECEIVED, Metrics.WEBHOOK_DUPLICATE), metrics.counted)
    }

    @Test
    fun `assinatura ausente ou inválida devolve InvalidSignature sem gravar nada`() {
        gateway.items = listOf(WebhookItem("""{"event":"document_state_changed"}""", "document_state_changed", "doc-1"))

        assertEquals(WebhookReceipt.InvalidSignature, receive(signature = "assinatura-errada"))
        assertEquals(WebhookReceipt.InvalidSignature, receive(signature = null))

        assertTrue(inbox.stored.isEmpty())
        assertTrue(outbox.messages.isEmpty())
        assertEquals(counted(Metrics.WEBHOOK_INVALID, Metrics.WEBHOOK_INVALID), metrics.counted)
    }

    @Test
    fun `corpo malformado é gravado no inbox como chegou e não gera mensagens (R7)`() {
        gateway.malformed = true

        val receipt = receive(rawBody = "isto não é JSON")

        assertEquals(WebhookReceipt.Accepted("entrega-1", 0), receipt)
        assertEquals(mapOf("entrega-1" to "isto não é JSON"), inbox.stored)
        assertTrue(outbox.messages.isEmpty())
    }
}

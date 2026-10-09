package br.com.locasign.shared.app.fakes

import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.CorrelationContext
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.OutboxPort
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.app.ports.WebhookInboxPort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** ADR-011: só executa o bloco. Não faz rollback; a atomicidade se testa nos *IT. */
object ImmediateTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}

class FixedBusinessClock(
    var instant: Instant,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) : BusinessClock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = LocalDate.ofInstant(instant, zone)
}

class InMemoryProcessedMessages : ProcessedMessagesPort {
    private val processed = mutableSetOf<Pair<String, String>>()
    override fun isProcessed(consumerGroup: String, eventId: String): Boolean =
        (consumerGroup to eventId) in processed

    override fun markProcessed(consumerGroup: String, eventId: String): Boolean =
        processed.add(consumerGroup to eventId)
}

class InMemoryOutboxPort : OutboxPort {
    private val messages = mutableListOf<OutboxMessage>()

    override fun append(message: OutboxMessage) {
        messages.add(message)
    }

    fun messages(): List<OutboxMessage> = messages.toList()
    fun clear() = messages.clear()
}

class InMemoryWebhookInbox : WebhookInboxPort {
    private val inbox = mutableMapOf<String, String>()

    override fun store(deliveryId: String, rawBody: String): Boolean {
        if (inbox.containsKey(deliveryId)) return false
        inbox[deliveryId] = rawBody
        return true
    }

    fun get(deliveryId: String): String? = inbox[deliveryId]
}

object NoOpMetricsPort : MetricsPort {
    override fun count(name: String, vararg tags: String) = Unit
}

object SimpleCorrelationContext : CorrelationContext {
    override fun <T> with(
        correlationId: String?,
        causationId: String?,
        entries: Map<String, String>,
        block: () -> T,
    ): T = block()
}

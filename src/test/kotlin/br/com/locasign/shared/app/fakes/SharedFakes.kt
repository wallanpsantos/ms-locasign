package br.com.locasign.shared.app.fakes

import br.com.locasign.shared.app.ports.BusinessClock
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

object NoOpMetricsPort : MetricsPort {
    override fun count(name: String, vararg tags: String) = Unit
}

/** Registra cada contador incrementado, com nome e tags, na ordem das chamadas. */
class RecordingMetricsPort : MetricsPort {
    val counted = mutableListOf<Pair<String, List<String>>>()

    override fun count(name: String, vararg tags: String) {
        counted += name to tags.toList()
    }
}

/** Executa o bloco direto, como o [ImmediateTransactionRunner], e informa se há transação aberta. */
class RecordingTransactionRunner : TransactionRunner {
    var depth = 0
        private set

    val inTransaction: Boolean get() = depth > 0

    override fun <T> run(block: () -> T): T {
        depth++
        try {
            return block()
        } finally {
            depth--
        }
    }
}

/**
 * Guarda as mensagens anexadas ao outbox, na ordem de inserção.
 *
 * Não deduplica por `id`, ao contrário do adapter real (`ON CONFLICT (id) DO NOTHING`): uma gravação em duplicidade
 * fica visível no teste em vez de ser absorvida.
 */
class InMemoryOutboxPort : OutboxPort {
    val messages = mutableListOf<OutboxMessage>()

    override fun append(message: OutboxMessage) {
        messages += message
    }
}

/** Inbox de webhooks deduplicado por `deliveryId`, como a restrição `uq_webhook_inbox_delivery` (R7). */
class InMemoryWebhookInbox : WebhookInboxPort {
    val stored = linkedMapOf<String, String>()

    override fun store(deliveryId: String, rawBody: String): Boolean = stored.putIfAbsent(deliveryId, rawBody) == null
}

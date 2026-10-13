package br.com.locasign.shared.app.fakes

import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
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

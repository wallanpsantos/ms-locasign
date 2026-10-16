package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.domain.events.ContractExpired
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpireOverdueContractsTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()

    private val expireOverdueContracts = ExpireOverdueContracts(
        contracts = contracts,
        persister = ContractPersister(contracts, publisher),
        clock = FixedBusinessClock(now),
        settings = ContractSettings(Duration.ofDays(7), Duration.ofDays(3), Duration.ofHours(1), jobBatchSize = 50),
        transactions = ImmediateTransactionRunner,
    )

    private fun overdueContract(status: ContractStatus = ContractStatus.SENT, documentId: String = "doc-1"): Contract =
        restoreContract(
            status = status,
            now = now.minus(Duration.ofDays(8)),
            providerDocumentId = ProviderDocumentId.of(documentId),
            sentAt = now.minus(Duration.ofDays(8)),
            expiresAt = now.minus(Duration.ofDays(1)),
        ).also(contracts::save)

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["SENT", "VIEWED", "PARTIALLY_SIGNED"])
    fun `contrato vencido expira e grava ContractExpired no outbox para a anulação assíncrona (R4)`(status: ContractStatus) {
        val contract = overdueContract(status)

        val expired = expireOverdueContracts.execute()

        assertEquals(1, expired)
        assertEquals(ContractStatus.EXPIRED, contract.status)
        assertEquals(listOf(contract.id), publisher.published.filterIsInstance<ContractExpired>().map { it.contractId })
    }

    @Test
    fun `lote maior que o limite de 8 chamadas por minuto do provedor expira todos os contratos`() {
        val batch = (1..12).map { overdueContract(documentId = "doc-$it") }

        val expired = expireOverdueContracts.execute()

        assertEquals(12, expired)
        assertTrue(batch.all { it.status == ContractStatus.EXPIRED })
        assertEquals(12, publisher.published.filterIsInstance<ContractExpired>().size)
    }

    @Test
    fun `contrato dentro do prazo não expira nem gera evento`() {
        val contract = restoreContract(
            status = ContractStatus.SENT,
            now = now,
            providerDocumentId = ProviderDocumentId.of("doc-1"),
            sentAt = now,
            expiresAt = now.plus(Duration.ofDays(1)),
        ).also(contracts::save)

        val expired = expireOverdueContracts.execute()

        assertEquals(0, expired)
        assertEquals(ContractStatus.SENT, contract.status)
        assertTrue(publisher.published.isEmpty())
    }
}

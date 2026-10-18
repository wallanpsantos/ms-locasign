package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.ports.`in`.CancelContractCommand
import br.com.locasign.contract.domain.events.ContractCancelled
import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CancelContractTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()
    private val document = ProviderDocumentId.of("doc-1")

    private val cancelContract = CancelContract(
        contracts = contracts,
        persister = ContractPersister(contracts, publisher),
        clock = FixedBusinessClock(now),
        transactions = ImmediateTransactionRunner,
    )

    /**
     * Todo contrato tem documento no provedor, mas o caso de uso não recebe `SignatureProviderPort`: a anulação é
     * assíncrona e parte do evento no outbox (ADR-016).
     */
    private fun contractIn(status: ContractStatus): Contract =
        restoreContract(status = status, now = now, providerDocumentId = document).also(contracts::save)

    private fun cancel(contractId: ContractId, reason: String) =
        cancelContract.execute(CancelContractCommand(contractId, reason))

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["DRAFT", "GENERATED", "SENT", "VIEWED", "PARTIALLY_SIGNED"])
    fun `cancelamento grava ContractCancelled no outbox para a anulação assíncrona do documento`(status: ContractStatus) {
        val contract = contractIn(status)

        cancel(contract.id, "  Desistência do locatário.  ")

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(CancelReason.Requested("Desistência do locatário."), contract.cancelReason)
        val event = publisher.published.single()
        assertEquals(ContractCancelled(contract.id, "Desistência do locatário.", now, event.eventId), event)
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["COMPLETED", "DECLINED", "EXPIRED", "CANCELLED"])
    fun `contrato em estado final não é cancelado nem gera evento (R5)`(status: ContractStatus) {
        val contract = contractIn(status)

        val ex = assertFailsWith<DomainException.ContractFinal> { cancel(contract.id, "Desistência do locatário.") }

        assertEquals(contract.id.toString(), ex.contractId)
        assertEquals(status.name, ex.status)
        assertEquals(status, contract.status)
        assertTrue(publisher.published.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `motivo vazio é rejeitado no campo reason sem alterar o contrato`(reason: String) {
        val contract = contractIn(ContractStatus.SENT)

        val ex = assertFailsWith<DomainException.BusinessRuleViolation> { cancel(contract.id, reason) }

        assertEquals("reason", ex.field)
        assertEquals("O motivo do cancelamento é obrigatório (até 500 caracteres).", ex.message)
        assertEquals(ContractStatus.SENT, contract.status)
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `motivo com mais de 500 caracteres é rejeitado e com 500 é aceito`() {
        val contract = contractIn(ContractStatus.SENT)

        val ex = assertFailsWith<DomainException.BusinessRuleViolation> { cancel(contract.id, "x".repeat(501)) }
        assertEquals("reason", ex.field)
        assertEquals("O motivo do cancelamento é obrigatório (até 500 caracteres).", ex.message)
        assertEquals(ContractStatus.SENT, contract.status)

        cancel(contract.id, "x".repeat(500))
        assertEquals(ContractStatus.CANCELLED, contract.status)
    }

    @Test
    fun `contrato inexistente lança NotFound`() {
        val missing = ContractId.new()

        val ex = assertFailsWith<DomainException.NotFound> { cancel(missing, "Desistência do locatário.") }

        assertEquals("Contrato", ex.resource)
        assertEquals(missing.toString(), ex.id)
    }
}

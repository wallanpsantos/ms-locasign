package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.RecordingTransactionRunner
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Email
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CancelContractTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val provider = RecordingSignatureProvider()
    private val transactions = RecordingTransactionRunner()
    private val document = ProviderDocumentId.of("doc-1")

    private val cancelContract = CancelContract(
        contracts = contracts,
        provider = provider,
        persister = ContractPersister(contracts, RecordingContractEventPublisher()),
        clock = FixedBusinessClock(now),
        transactions = transactions,
    )

    private val signers = listOf(
        Signer(SignerRole.TENANT, "Juliana Mendes", Email.of("juliana@example.com"), SigningOrder.of(1)),
        Signer(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), SigningOrder.of(2)),
    )

    /** Todo contrato tem documento no provedor: a decisão de anular vem só do status. */
    private fun contractIn(status: ContractStatus): Contract =
        restoreContract(status, LeaseId.new(), signers, now, providerDocumentId = document).also(contracts::save)

    private fun cancel(contractId: ContractId, reason: String) =
        cancelContract.execute(CancelContractCommand(contractId, reason))

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["SENT", "VIEWED", "PARTIALLY_SIGNED"])
    fun `cancelamento de contrato aguardando assinaturas anula o documento no provedor`(status: ContractStatus) {
        val contract = contractIn(status)

        cancel(contract.id, "  Desistência do locatário.  ")

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(CancelReason.Requested("Desistência do locatário."), contract.cancelReason)
        assertEquals(listOf(document), provider.cancelled)
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["DRAFT", "GENERATED"])
    fun `cancelamento de contrato ainda não enviado não anula o documento no provedor`(status: ContractStatus) {
        val contract = contractIn(status)

        cancel(contract.id, "Desistência do locatário.")

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertTrue(provider.cancelled.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["COMPLETED", "DECLINED", "EXPIRED", "CANCELLED"])
    fun `contrato em estado final não é cancelado nem anulado no provedor (R5)`(status: ContractStatus) {
        val contract = contractIn(status)

        val ex = assertFailsWith<DomainException.ContractFinal> { cancel(contract.id, "Desistência do locatário.") }

        assertEquals(contract.id.toString(), ex.contractId)
        assertEquals(status.name, ex.status)
        assertEquals(status, contract.status)
        assertTrue(provider.cancelled.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `motivo vazio é rejeitado no campo reason sem alterar o contrato`(reason: String) {
        val contract = contractIn(ContractStatus.SENT)

        val ex = assertFailsWith<DomainException.BusinessRuleViolation> { cancel(contract.id, reason) }

        assertEquals("reason", ex.field)
        assertEquals("O motivo do cancelamento é obrigatório (até 500 caracteres).", ex.message)
        assertEquals(ContractStatus.SENT, contract.status)
        assertTrue(provider.cancelled.isEmpty())
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
    fun `anulação no provedor acontece depois da transação que grava o cancelamento`() {
        val contract = contractIn(ContractStatus.SENT)
        provider.beforeEachCall = {
            assertFalse(transactions.inTransaction, "anulação chamada dentro da transação")
            assertEquals(ContractStatus.CANCELLED, contract.status)
        }

        cancel(contract.id, "Desistência do locatário.")

        assertEquals(listOf(document), provider.cancelled)
    }

    @Test
    fun `falha ao anular no provedor não desfaz o cancelamento nem propaga (melhor esforço)`() {
        val contract = contractIn(ContractStatus.SENT)
        provider.cancelFailure = ProviderException.Unavailable("tempo esgotado")

        cancel(contract.id, "Desistência do locatário.")

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(listOf(document), provider.cancelled)
    }

    @Test
    fun `contrato inexistente lança NotFound`() {
        val missing = ContractId.new()

        val ex = assertFailsWith<DomainException.NotFound> { cancel(missing, "Desistência do locatário.") }

        assertEquals("Contrato", ex.resource)
        assertEquals(missing.toString(), ex.id)
    }
}

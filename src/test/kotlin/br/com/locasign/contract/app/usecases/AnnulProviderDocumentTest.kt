package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.app.fakes.RecordingTransactionRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnulProviderDocumentTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val provider = RecordingSignatureProvider()
    private val processed = InMemoryProcessedMessages()
    private val transactions = RecordingTransactionRunner()
    private val document = ProviderDocumentId.of("doc-1")

    private val annulProviderDocument = AnnulProviderDocument(contracts, provider, processed, transactions)

    private val eventProcessed: Boolean
        get() = processed.isProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, EVENT_ID)

    /** Por padrão o documento chegou a ser enviado: só então ele pode ser assinado e precisa ser anulado. */
    private fun contractIn(
        status: ContractStatus,
        providerDocumentId: ProviderDocumentId? = document,
        sentAt: Instant? = now,
    ): Contract = restoreContract(
        status = status,
        now = now,
        providerDocumentId = providerDocumentId,
        sentAt = sentAt,
    ).also(contracts::save)

    private fun annul(contractId: ContractId) =
        annulProviderDocument.execute(ContractEventCommand(EVENT_ID, contractId))

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["CANCELLED", "EXPIRED"])
    fun `contrato cancelado ou expirado depois do envio tem o documento anulado no provedor`(status: ContractStatus) {
        val contract = contractIn(status)

        annul(contract.id)

        assertEquals(listOf(document), provider.cancelled)
        assertTrue(eventProcessed)
    }

    @Test
    fun `contrato cancelado antes do envio não tem documento a anular no provedor`() {
        val contract = contractIn(ContractStatus.CANCELLED, sentAt = null)

        annul(contract.id)

        assertTrue(provider.cancelled.isEmpty())
        assertTrue(eventProcessed)
    }

    @Test
    fun `contrato sem documento no provedor não chama o provedor`() {
        val contract = contractIn(ContractStatus.CANCELLED, providerDocumentId = null)

        annul(contract.id)

        assertTrue(provider.cancelled.isEmpty())
        assertTrue(eventProcessed)
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["CANCELLED", "EXPIRED"])
    fun `contrato em outro status não é anulado no provedor`(status: ContractStatus) {
        val contract = contractIn(status)

        annul(contract.id)

        assertTrue(provider.cancelled.isEmpty())
        assertTrue(eventProcessed)
    }

    @Test
    fun `contrato inexistente descarta o evento sem chamar o provedor`() {
        annul(ContractId.new())

        assertTrue(provider.cancelled.isEmpty())
        assertTrue(eventProcessed)
    }

    @Test
    fun `reentrega do mesmo evento não anula o documento de novo (R6)`() {
        val contract = contractIn(ContractStatus.CANCELLED)

        annul(contract.id)
        annul(contract.id)

        assertEquals(listOf(document), provider.cancelled)
    }

    @Test
    fun `evento já processado não chama o provedor`() {
        val contract = contractIn(ContractStatus.EXPIRED)
        processed.markProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, EVENT_ID)

        annul(contract.id)

        assertTrue(provider.cancelled.isEmpty())
    }

    @Test
    fun `anulação acontece fora da transação e o evento só é marcado depois dela`() {
        val contract = contractIn(ContractStatus.CANCELLED)
        provider.beforeEachCall = {
            assertFalse(transactions.inTransaction, "anulação chamada dentro da transação")
            assertFalse(eventProcessed, "evento marcado antes da chamada externa")
        }

        annul(contract.id)

        assertEquals(listOf(document), provider.cancelled)
        assertTrue(eventProcessed)
    }

    @Test
    fun `documento ausente ou já anulado no provedor conta como resolvido sem erro (ADR-012)`() {
        val contract = contractIn(ContractStatus.CANCELLED)
        provider.cancelFailure = ProviderException.NotReady("PandaDoc status falhou com HTTP 409")

        annul(contract.id)

        assertEquals(listOf(document), provider.cancelled)
        assertTrue(eventProcessed)
    }

    @ParameterizedTest
    @EnumSource(DefinitiveFailure::class)
    fun `rejeição definitiva do provedor encerra o fluxo sem retentativa`(failure: DefinitiveFailure) {
        val contract = contractIn(ContractStatus.CANCELLED)
        provider.cancelFailure = failure.error

        annul(contract.id)

        assertEquals(listOf(document), provider.cancelled)
        assertTrue(eventProcessed)
    }

    @ParameterizedTest
    @EnumSource(TransientFailure::class)
    fun `falha transitória sobe para o Kafka retentar e não marca o evento (R6)`(failure: TransientFailure) {
        val contract = contractIn(ContractStatus.CANCELLED)
        provider.cancelFailure = failure.error

        assertFailsWith<ProviderException> { annul(contract.id) }
        assertFalse(eventProcessed)

        // A retentativa do Kafka reentrega o mesmo evento, agora com o provedor disponível.
        provider.cancelFailure = null
        annul(contract.id)

        assertEquals(listOf(document, document), provider.cancelled)
        assertTrue(eventProcessed)
    }

    enum class DefinitiveFailure(val error: ProviderException) {
        FORBIDDEN(ProviderException.Forbidden("PandaDoc status falhou com HTTP 403")),
        REJECTED(ProviderException.Rejected(400, "PandaDoc status falhou com HTTP 400")),
    }

    enum class TransientFailure(val error: ProviderException) {
        RATE_LIMITED(ProviderException.RateLimited("PandaDoc status falhou com HTTP 429")),
        UNAVAILABLE(ProviderException.Unavailable("PandaDoc status indisponível")),
    }

    private companion object {
        const val EVENT_ID = "evt-1"
    }
}

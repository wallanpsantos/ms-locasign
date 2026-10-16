package br.com.locasign.contract.interfaces.messaging

import br.com.locasign.contract.app.fakes.FakeLeaseActivation
import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.InMemoryPostSignatureActions
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.usecases.AnnulProviderDocument
import br.com.locasign.contract.app.usecases.ContractPersister
import br.com.locasign.contract.app.usecases.ContractSettings
import br.com.locasign.contract.app.usecases.CreateProviderDocument
import br.com.locasign.contract.app.usecases.RunPostSignatureActions
import br.com.locasign.contract.app.usecases.SendContract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.DEFAULT_TEST_NOW
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.models.sampleLeaseSnapshot
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.messaging.EnvelopeReader
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractConsumersTest {

    private val now = DEFAULT_TEST_NOW
    private val clock = FixedBusinessClock(now)
    private val contracts = InMemoryContractRepository()
    private val persister = ContractPersister(contracts, RecordingContractEventPublisher())
    private val processed = InMemoryProcessedMessages()
    private val provider = RecordingSignatureProvider()
    private val leases = FakeLeaseLookup()
    private val reader = EnvelopeReader(JsonMapper.builder().findAndAddModules().build(), MdcCorrelationContext())

    private val orchestrator = OrchestratorConsumer(
        reader = reader,
        createProviderDocument = CreateProviderDocument(
            contracts, leases, provider, persister, processed, clock, ImmediateTransactionRunner,
        ),
        sendContract = SendContract(
            contracts,
            provider,
            persister,
            processed,
            clock,
            ContractSettings(Duration.ofDays(7), Duration.ofDays(3), Duration.ofHours(1), 50),
            ImmediateTransactionRunner,
        ),
    )

    private val postSignature = PostSignatureConsumer(
        reader = reader,
        runPostSignatureActions = RunPostSignatureActions(
            contracts, leases, FakeLeaseActivation(), InMemoryPostSignatureActions(), persister, processed, clock,
            ImmediateTransactionRunner,
        ),
    )

    private val annulment = DocumentAnnulmentConsumer(
        reader = reader,
        annulProviderDocument = AnnulProviderDocument(contracts, provider, processed, ImmediateTransactionRunner),
    )

    private fun message(eventType: String, aggregateId: String) =
        """{"eventId":"evt-1","eventType":"$eventType","aggregateId":"$aggregateId"}"""

    @ParameterizedTest
    @ValueSource(strings = ["ContractRequested", "ContractGenerated"])
    fun `orquestrador trata aggregateId inválido como mensagem ilegível (DLT)`(eventType: String) {
        val ex = assertFailsWith<UnreadableMessageException> {
            orchestrator.onMessage(message(eventType, "nao-e-uuid"))
        }

        assertEquals("Evento evt-1 com aggregateId inválido: nao-e-uuid", ex.message)
    }

    @Test
    fun `orquestrador ignora evento fora do seu roteamento sem validar o aggregateId`() {
        orchestrator.onMessage(message("ContractCompleted", "nao-e-uuid"))

        assertTrue(provider.created.isEmpty())
    }

    @Test
    fun `orquestrador repassa o id do contrato do aggregateId ao caso de uso`() {
        val snapshot = sampleLeaseSnapshot().also(leases::register)
        val contract = restoreContract(ContractStatus.DRAFT, leaseId = snapshot.leaseId).also(contracts::save)

        orchestrator.onMessage(message("ContractRequested", contract.id.toString()))

        assertEquals(contract.id, provider.created.single().contractId)
        assertTrue(processed.isProcessed(ConsumerGroups.ORCHESTRATOR, "evt-1"))
    }

    @Test
    fun `pós-assinatura trata aggregateId inválido de ContractCompleted como mensagem ilegível (DLT)`() {
        val ex = assertFailsWith<UnreadableMessageException> {
            postSignature.onMessage(message("ContractCompleted", "nao-e-uuid"))
        }

        assertEquals("Evento evt-1 com aggregateId inválido: nao-e-uuid", ex.message)
    }

    @Test
    fun `pós-assinatura ignora outros eventos sem validar o aggregateId`() {
        postSignature.onMessage(message("ContractRequested", "nao-e-uuid"))

        assertFalse(processed.isProcessed(ConsumerGroups.POST_SIGNATURE, "evt-1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ContractCancelled", "ContractExpired"])
    fun `anulação trata aggregateId inválido como mensagem ilegível (DLT)`(eventType: String) {
        val ex = assertFailsWith<UnreadableMessageException> {
            annulment.onMessage(message(eventType, "nao-e-uuid"))
        }

        assertEquals("Evento evt-1 com aggregateId inválido: nao-e-uuid", ex.message)
    }

    @Test
    fun `anulação ignora outros eventos sem validar o aggregateId`() {
        annulment.onMessage(message("ContractCompleted", "nao-e-uuid"))

        assertFalse(processed.isProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, "evt-1"))
        assertTrue(provider.cancelled.isEmpty())
    }

    @ParameterizedTest
    @CsvSource("ContractCancelled,CANCELLED", "ContractExpired,EXPIRED")
    fun `anulação repassa o id do contrato do aggregateId ao caso de uso`(eventType: String, status: ContractStatus) {
        val document = ProviderDocumentId.of("doc-1")
        val contract = restoreContract(status, providerDocumentId = document, sentAt = now, now = now)
            .also(contracts::save)

        annulment.onMessage(message(eventType, contract.id.toString()))

        assertEquals(listOf(document), provider.cancelled)
        assertTrue(processed.isProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, "evt-1"))
    }
}

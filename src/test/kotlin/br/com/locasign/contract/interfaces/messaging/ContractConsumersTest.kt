package br.com.locasign.contract.interfaces.messaging

import br.com.locasign.contract.app.fakes.FakeLeaseActivation
import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.InMemoryPostSignatureActions
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.app.usecases.ContractPersister
import br.com.locasign.contract.app.usecases.ContractSettings
import br.com.locasign.contract.app.usecases.CreateProviderDocument
import br.com.locasign.contract.app.usecases.RunPostSignatureActions
import br.com.locasign.contract.app.usecases.SendContract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.messaging.EnvelopeReader
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractConsumersTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
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
        val leaseId = LeaseId.new()
        leases.register(
            LeaseSnapshot(
                leaseId = leaseId,
                tenantName = "Juliana Mendes",
                tenantCpf = Cpf.of("529.982.247-25"),
                tenantEmail = Email.of("juliana@example.com"),
                agencySignerName = "Imobiliária Alfa",
                agencySignerEmail = Email.of("alfa@example.com"),
                propertyAddress = "Rua Oscar Freire, 100",
                rentAmount = Money.positive("5000.00"),
                startDate = LocalDate.of(2026, 11, 1),
                termMonths = 30,
            ),
        )
        val signers = listOf(
            Signer(SignerRole.TENANT, "Juliana Mendes", Email.of("juliana@example.com"), SigningOrder.of(1)),
            Signer(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), SigningOrder.of(2)),
        )
        val contract = restoreContract(ContractStatus.DRAFT, leaseId, signers, now).also(contracts::save)

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
}

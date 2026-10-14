package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.ports.out.integration.LeaseTemplateData
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderRecipient
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.domain.events.ContractDocumentCreated
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
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.app.fakes.RecordingTransactionRunner
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateProviderDocumentTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()
    private val leases = FakeLeaseLookup()
    private val provider = RecordingSignatureProvider()
    private val processed = InMemoryProcessedMessages()
    private val transactions = RecordingTransactionRunner()

    private val createProviderDocument = CreateProviderDocument(
        contracts = contracts,
        leases = leases,
        provider = provider,
        persister = ContractPersister(contracts, publisher),
        processed = processed,
        clock = FixedBusinessClock(now),
        transactions = transactions,
    )

    private val leaseId = LeaseId.new()
    private val tenant = Signer(SignerRole.TENANT, "Juliana M.", Email.of("juliana@example.com"), SigningOrder.of(1))
    private val agency =
        Signer(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), SigningOrder.of(2))

    /** Nome do locatário difere do signatário: o modelo usa a locação, e os destinatários, o contrato. */
    private val lease = LeaseSnapshot(
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
    )

    /** Signatários fora de ordem na lista: o pedido precisa reordená-los pela ordem de assinatura (R3). */
    private fun contractIn(status: ContractStatus, providerDocumentId: ProviderDocumentId? = null): Contract =
        restoreContract(
            status = status,
            leaseId = leaseId,
            signers = listOf(agency, tenant),
            now = now,
            versionNumber = 2,
            providerDocumentId = providerDocumentId,
        ).also(contracts::save)

    private fun requested(contractId: ContractId) = ContractEventCommand("evt-requested", contractId)

    private val eventMarked: Boolean
        get() = processed.isProcessed(ConsumerGroups.ORCHESTRATOR, "evt-requested")

    @Test
    fun `pedido ao provedor leva nome do documento, signatários em ordem de assinatura e modelo da locação (R3)`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)

        createProviderDocument.execute(requested(contract.id))

        val expected = ProviderDocumentRequest(
            contractId = contract.id,
            leaseId = leaseId,
            documentName = "LocaSign contrato ${contract.id} v2",
            recipients = listOf(
                ProviderRecipient(SignerRole.TENANT, "Juliana M.", Email.of("juliana@example.com"), 1),
                ProviderRecipient(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), 2),
            ),
            template = LeaseTemplateData(
                tenantName = "Juliana Mendes",
                tenantCpf = Cpf.of("529.982.247-25"),
                propertyAddress = "Rua Oscar Freire, 100",
                rentAmount = Money.positive("5000.00"),
                startDate = LocalDate.of(2026, 11, 1),
                termMonths = 30,
            ),
        )
        assertEquals(listOf(expected), provider.created)
        assertEquals(provider.documentToCreate, contract.providerDocumentId)
        assertIs<ContractDocumentCreated>(publisher.published.single())
        assertTrue(eventMarked)
    }

    @Test
    fun `criação no provedor acontece fora de transação e antes de marcar o evento (R6)`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)
        provider.beforeEachCall = {
            assertFalse(transactions.inTransaction, "criação chamada dentro da transação")
            assertFalse(eventMarked, "evento marcado antes da chamada ao provedor")
        }

        createProviderDocument.execute(requested(contract.id))

        assertEquals(1, provider.created.size)
        assertTrue(eventMarked)
    }

    @Test
    fun `evento já processado não chama o provedor (R6)`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)
        processed.markProcessed(ConsumerGroups.ORCHESTRATOR, "evt-requested")

        createProviderDocument.execute(requested(contract.id))

        assertTrue(provider.created.isEmpty())
    }

    @Test
    fun `contrato fora de DRAFT marca o evento e não chama o provedor`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.GENERATED)

        createProviderDocument.execute(requested(contract.id))

        assertTrue(provider.created.isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `contrato que já tem documento no provedor marca o evento e não chama o provedor`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT, ProviderDocumentId.of("doc-existente"))

        createProviderDocument.execute(requested(contract.id))

        assertTrue(provider.created.isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `contrato inexistente marca o evento e não chama o provedor`() {
        createProviderDocument.execute(requested(ContractId.new()))

        assertTrue(provider.created.isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `locação inexistente falha sem marcar o evento para que o consumidor retente`() {
        val contract = contractIn(ContractStatus.DRAFT)

        val ex = assertFailsWith<IllegalStateException> { createProviderDocument.execute(requested(contract.id)) }

        assertEquals("Locação $leaseId do contrato não existe", ex.message)
        assertTrue(provider.created.isEmpty())
        assertFalse(eventMarked)
    }

    @Test
    fun `provedor que nega a criação cancela o contrato com falha de geração`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)
        provider.createFailure = ProviderException.Forbidden("cota esgotada")

        createProviderDocument.execute(requested(contract.id))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(
            CancelReason.GenerationFailed("Provedor negou a criação do documento: cota esgotada"),
            contract.cancelReason,
        )
        assertTrue(eventMarked)
    }

    @Test
    fun `provedor que rejeita o pedido cancela o contrato com falha de geração`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)
        provider.createFailure = ProviderException.Rejected(422, "modelo inválido")

        createProviderDocument.execute(requested(contract.id))

        assertEquals(ContractStatus.CANCELLED, contract.status)
        assertEquals(
            CancelReason.GenerationFailed("Provedor rejeitou a criação do documento: modelo inválido"),
            contract.cancelReason,
        )
        assertTrue(eventMarked)
    }

    @Test
    fun `falha transitória do provedor propaga sem marcar o evento (R6)`() {
        leases.register(lease)
        val contract = contractIn(ContractStatus.DRAFT)
        provider.createFailure = ProviderException.Unavailable("tempo esgotado")

        assertFailsWith<ProviderException.Unavailable> { createProviderDocument.execute(requested(contract.id)) }

        assertEquals(ContractStatus.DRAFT, contract.status)
        assertFalse(eventMarked)
    }
}

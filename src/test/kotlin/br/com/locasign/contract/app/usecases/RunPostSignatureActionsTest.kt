package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FakeLeaseActivation
import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.InMemoryPostSignatureActions
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunPostSignatureActionsTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val clock = FixedBusinessClock(now)
    private val contracts = InMemoryContractRepository()
    private val leases = FakeLeaseLookup()
    private val leaseActivation = FakeLeaseActivation()
    private val actions = InMemoryPostSignatureActions()
    private val publisher = RecordingContractEventPublisher()
    private val persister = ContractPersister(contracts, publisher)
    private val processed = InMemoryProcessedMessages()

    private val runPostSignatureActions = RunPostSignatureActions(
        contracts = contracts,
        leases = leases,
        leaseActivation = leaseActivation,
        actions = actions,
        persister = persister,
        processed = processed,
        clock = clock,
        transactions = ImmediateTransactionRunner,
    )

    private val leaseId = LeaseId.new()
    private val leaseSnapshot = LeaseSnapshot(
        leaseId = leaseId,
        tenantName = "Juliana Mendes",
        tenantCpf = Cpf.of("529.982.247-25"),
        tenantEmail = Email.of("juliana@example.com"),
        agencySignerName = "Imobiliária Alfa",
        agencySignerEmail = Email.of("alfa@imobiliaria.com"),
        propertyAddress = "Rua Oscar Freire, 100",
        rentAmount = Money.positive("5000.00"),
        startDate = LocalDate.of(2026, 11, 1),
        termMonths = 30,
    )

    private fun createContract(status: ContractStatus): Contract {
        val contract = Contract.restore(
            id = ContractId.new(),
            leaseId = leaseId,
            versionNumber = 1,
            status = status,
            providerDocumentId = null,
            providerLastModifiedAt = null,
            sentAt = now,
            expiresAt = now.plus(Duration.ofDays(7)),
            reminderSentAt = null,
            lastReconciledAt = null,
            cancelReason = null,
            signedDocumentRef = null,
            signers = listOf(
                Signer(SignerRole.TENANT, "Juliana", Email.of("juliana@example.com"), SigningOrder.of(1)),
                Signer(SignerRole.AGENCY, "Alfa", Email.of("alfa@imobiliaria.com"), SigningOrder.of(2)),
            ),
            createdAt = now,
            updatedAt = now,
            rowVersion = 1L,
        )
        contracts.save(contract)
        return contract
    }

    @Test
    fun `não executa ações pós-assinatura se o contrato não estiver COMPLETED (R8)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.SENT)

        runPostSignatureActions.execute(ContractEventCommand("evt-1", contract.id))

        assertFalse(leaseActivation.activated.contains(leaseId))
    }

    @Test
    fun `executa ativação da locação e registra ações quando o contrato está COMPLETED (R8)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.COMPLETED)

        runPostSignatureActions.execute(ContractEventCommand("evt-1", contract.id))

        assertTrue(leaseActivation.activated.contains(leaseId))
    }

    @Test
    fun `o mesmo evento processado duas vezes é ignorado por idempotência (R6)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.COMPLETED)

        runPostSignatureActions.execute(ContractEventCommand("evt-dup", contract.id))
        assertTrue(leaseActivation.activated.contains(leaseId))

        // Limpa o registro de ativações simulado para verificar que na segunda chamada nada roda
        leaseActivation.activated.clear()
        runPostSignatureActions.execute(ContractEventCommand("evt-dup", contract.id))

        assertFalse(leaseActivation.activated.contains(leaseId))
    }
}

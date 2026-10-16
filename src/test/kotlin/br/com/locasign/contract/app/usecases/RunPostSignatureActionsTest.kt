package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FakeLeaseActivation
import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.InMemoryPostSignatureActions
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.models.sampleLeaseSnapshot
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import java.time.Duration
import java.time.Instant
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
    private val leaseSnapshot = sampleLeaseSnapshot(leaseId = leaseId)

    private fun createContract(status: ContractStatus): Contract = restoreContract(
        status = status,
        leaseId = leaseId,
        now = now,
        sentAt = now,
        expiresAt = now.plus(Duration.ofDays(7)),
    ).also(contracts::save)

    @Test
    fun `não executa ações pós-assinatura se o contrato não estiver COMPLETED (R8)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.SENT)

        runPostSignatureActions.execute(ContractEventCommand("evt-1", contract.id))

        assertFalse(leaseId in leaseActivation.activated)
    }

    @Test
    fun `executa ativação da locação e registra ações quando o contrato está COMPLETED (R8)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.COMPLETED)

        runPostSignatureActions.execute(ContractEventCommand("evt-1", contract.id))

        assertTrue(leaseId in leaseActivation.activated)
    }

    @Test
    fun `o mesmo evento processado duas vezes é ignorado por idempotência (R6)`() {
        leases.register(leaseSnapshot)
        val contract = createContract(ContractStatus.COMPLETED)

        runPostSignatureActions.execute(ContractEventCommand("evt-dup", contract.id))
        assertTrue(leaseId in leaseActivation.activated)

        // Limpa o registro de ativações simulado para verificar que na segunda chamada nada roda
        leaseActivation.activated.clear()
        runPostSignatureActions.execute(ContractEventCommand("evt-dup", contract.id))

        assertFalse(leaseId in leaseActivation.activated)
    }
}

package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.DEFAULT_TEST_NOW
import br.com.locasign.contract.domain.models.StatusChange
import br.com.locasign.contract.domain.models.defaultSigners
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.sampleLease
import br.com.locasign.lease.infra.persistence.adapters.LeaseQueryAdapter
import br.com.locasign.lease.infra.persistence.adapters.LeaseRepositoryAdapter
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ContractRepositoryAdapterIT(
    @Autowired private val contractRepositoryAdapter: ContractRepositoryAdapter,
    @Autowired private val leaseRepositoryAdapter: LeaseRepositoryAdapter,
    @Autowired private val leaseQueryAdapter: LeaseQueryAdapter,
) : IntegrationTestSupport() {

    private val now = DEFAULT_TEST_NOW

    private fun createAndSaveLease(): Lease =
        sampleLease(now = now).also(leaseRepositoryAdapter::save)

    private fun createContract(lease: Lease, versionNumber: Int = 1): Contract =
        Contract.request(
            leaseId = lease.id,
            versionNumber = versionNumber,
            signers = defaultSigners(),
            now = now,
        )

    @Test
    fun `persiste contrato e recupera com sucesso por id`() {
        val lease = createAndSaveLease()
        val contract = createContract(lease)

        contractRepositoryAdapter.save(contract)

        val retrieved = assertNotNull(contractRepositoryAdapter.findById(contract.id))
        assertEquals(contract.id, retrieved.id)
        assertEquals(ContractStatus.DRAFT, retrieved.status)
        assertEquals(1, retrieved.versionNumber)
    }

    @Test
    fun `segundo contrato ativo para a mesma locação lança ActiveContractExists (R1)`() {
        val lease = createAndSaveLease()
        val contract1 = createContract(lease, versionNumber = 1)
        val contract2 = createContract(lease, versionNumber = 2)

        contractRepositoryAdapter.save(contract1)

        val exception = assertFailsWith<DomainException.ActiveContractExists> {
            contractRepositoryAdapter.save(contract2)
        }
        assertEquals(lease.id.toString(), exception.leaseId)
    }

    @Test
    fun `após finalizar contrato anterior permite criação de nova versão`() {
        val lease = createAndSaveLease()
        val contract1 = createContract(lease, versionNumber = 1)
        contractRepositoryAdapter.save(contract1)

        // Recarrega o contrato com rowVersion atribuído pelo banco para efetuar o update
        val toCancel = assertNotNull(contractRepositoryAdapter.findById(contract1.id))
        toCancel.cancel("Cancelado para novo teste", now)
        contractRepositoryAdapter.save(toCancel)

        val nextVersion = contractRepositoryAdapter.nextVersionNumber(lease.id)
        assertEquals(2, nextVersion)

        val contract2 = createContract(lease, versionNumber = nextVersion)
        contractRepositoryAdapter.save(contract2)

        val retrieved2 = assertNotNull(contractRepositoryAdapter.findById(contract2.id))
        assertEquals(ContractStatus.DRAFT, retrieved2.status)
        assertEquals(2, retrieved2.versionNumber)
    }

    @Test
    fun `detalhe da locação traz a versão mais recente do contrato como contrato corrente (R9)`() {
        val lease = createAndSaveLease()
        val contract1 = createContract(lease, versionNumber = 1)
        contractRepositoryAdapter.save(contract1)
        val toCancel = assertNotNull(contractRepositoryAdapter.findById(contract1.id))
        toCancel.cancel("Cancelado para nova versão", now)
        contractRepositoryAdapter.save(toCancel)
        val contract2 = createContract(lease, versionNumber = 2)
        contractRepositoryAdapter.save(contract2)
        val toSend = assertNotNull(contractRepositoryAdapter.findById(contract2.id))
        toSend.apply(StatusChange(ContractStatus.SENT, ChangeSource.WEBHOOK, "evt-sent"), now, Duration.ofDays(7))
        contractRepositoryAdapter.save(toSend)

        val current = assertNotNull(leaseQueryAdapter.findDetail(lease.id)?.currentContract)

        assertEquals(contract2.id.toString(), current.id)
        assertEquals(2, current.versionNumber)
        assertEquals("SENT", current.status)
        assertEquals(now.plus(Duration.ofDays(7)), current.expiresAt)
    }
}

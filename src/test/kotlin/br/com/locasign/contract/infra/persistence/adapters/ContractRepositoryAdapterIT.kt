package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.lease.infra.persistence.adapters.LeaseRepositoryAdapter
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import br.com.locasign.support.TestcontainersSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersSupport::class)
class ContractRepositoryAdapterIT(
    @Autowired private val contractRepositoryAdapter: ContractRepositoryAdapter,
    @Autowired private val leaseRepositoryAdapter: LeaseRepositoryAdapter,
) {

    private val today = LocalDate.of(2026, 11, 1)
    private val now = Instant.parse("2026-10-09T10:00:00Z")

    private fun createAndSaveLease(): Lease {
        val lease = Lease.register(
            tenant = Tenant("Maria Silva", Cpf.of("529.982.247-25"), Email.of("maria@example.com")),
            agencySigner = AgencySigner("Imobiliária Aliança", Email.of("alianca@example.com")),
            propertyAddress = "Rua das Flores, 123",
            rentAmount = Money.positive("3200.00"),
            startDate = today,
            term = LeaseTerm.of(30),
            today = today,
            now = now,
        )
        leaseRepositoryAdapter.save(lease)
        return lease
    }

    private fun createContract(lease: Lease, versionNumber: Int = 1): Contract {
        val signers = listOf(
            Signer(SignerRole.TENANT, "Maria Silva", Email.of("maria@example.com"), SigningOrder.of(1)),
            Signer(SignerRole.AGENCY, "Imobiliária Aliança", Email.of("alianca@example.com"), SigningOrder.of(2)),
        )
        return Contract.request(
            leaseId = lease.id,
            versionNumber = versionNumber,
            signers = signers,
            now = now,
        )
    }

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
    fun `regra R1 proibe segundo contrato ativo para a mesma locacao lancando ActiveContractExists`() {
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
    fun `apos finalizar contrato anterior permite criacao de nova versao`() {
        val lease = createAndSaveLease()
        val contract1 = createContract(lease, versionNumber = 1)
        contractRepositoryAdapter.save(contract1)

        // Recarrega o contrato com rowVersion atribuído pelo banco para efetuar o update
        val toCancel = assertNotNull(contractRepositoryAdapter.findById(contract1.id))
        toCancel.cancel("Cancelado para novo teste", Instant.now())
        contractRepositoryAdapter.save(toCancel)

        val nextVersion = contractRepositoryAdapter.nextVersionNumber(lease.id)
        assertEquals(2, nextVersion)

        val contract2 = createContract(lease, versionNumber = nextVersion)
        contractRepositoryAdapter.save(contract2)

        val retrieved2 = assertNotNull(contractRepositoryAdapter.findById(contract2.id))
        assertEquals(ContractStatus.DRAFT, retrieved2.status)
        assertEquals(2, retrieved2.versionNumber)
    }
}

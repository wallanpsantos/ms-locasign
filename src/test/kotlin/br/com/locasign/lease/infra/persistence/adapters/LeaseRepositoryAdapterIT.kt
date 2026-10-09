package br.com.locasign.lease.infra.persistence.adapters

import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import br.com.locasign.support.TestcontainersSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersSupport::class)
class LeaseRepositoryAdapterIT {

    @Autowired
    private lateinit var leaseRepositoryAdapter: LeaseRepositoryAdapter

    @Autowired
    private lateinit var leaseQueryAdapter: LeaseQueryAdapter

    private val today = LocalDate.of(2026, 12, 1)
    private val now = Instant.parse("2026-10-09T10:00:00Z")

    @Test
    fun `persiste locacao, recupera por id e atualiza status para ativo`() {
        val tenant = Tenant(
            name = "Carlos Locatário",
            cpf = Cpf.of("529.982.247-25"),
            email = Email.of("carlos@example.com"),
        )
        val agencySigner = AgencySigner(
            name = "Imobiliária Prime",
            email = Email.of("assinaturas@prime.com.br"),
        )
        val lease = Lease.register(
            tenant = tenant,
            agencySigner = agencySigner,
            propertyAddress = "Avenida Paulista, 1000, Apto 42",
            rentAmount = Money.positive("4500.00"),
            startDate = today,
            term = LeaseTerm.of(30),
            today = today,
            now = now,
        )

        leaseRepositoryAdapter.save(lease)

        val retrieved = leaseRepositoryAdapter.findById(lease.id)
        assertNotNull(retrieved)
        assertEquals(lease.id, retrieved?.id)
        assertEquals("Carlos Locatário", retrieved?.tenant?.name)
        assertEquals(LeaseStatus.REGISTERED, retrieved?.status)

        // Ativação da locação (R8)
        val activated = retrieved!!.activate(now)
        assertTrue(activated, "Locação deve ser ativada pela primeira vez com sucesso")
        leaseRepositoryAdapter.save(retrieved)

        val detail = leaseQueryAdapter.findDetail(lease.id)
        assertNotNull(detail)
        assertEquals(LeaseStatus.ACTIVE.name, detail?.status)
        assertEquals("Avenida Paulista, 1000, Apto 42", detail?.propertyAddress)
    }
}

package br.com.locasign.lease.infra.persistence.adapters

import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.lease.domain.models.sampleLease
import br.com.locasign.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeaseRepositoryAdapterIT(
    @Autowired private val leaseRepositoryAdapter: LeaseRepositoryAdapter,
    @Autowired private val leaseQueryAdapter: LeaseQueryAdapter,
) : IntegrationTestSupport() {

    private val now = Instant.parse("2026-10-09T10:00:00Z")

    @Test
    fun `persiste locação, recupera por id e atualiza status para ativo`() {
        val lease = sampleLease(
            tenantName = "Carlos Locatário",
            propertyAddress = "Avenida Paulista, 1000, Apto 42",
            now = now,
        )

        leaseRepositoryAdapter.save(lease)

        val retrieved = assertNotNull(leaseRepositoryAdapter.findById(lease.id))
        assertEquals(lease.id, retrieved.id)
        assertEquals("Carlos Locatário", retrieved.tenant.name)
        assertEquals(LeaseStatus.REGISTERED, retrieved.status)

        // Ativação da locação (R8)
        val activated = retrieved.activate(now)
        assertTrue(activated, "Locação deve ser ativada pela primeira vez com sucesso")
        leaseRepositoryAdapter.save(retrieved)

        val detail = assertNotNull(leaseQueryAdapter.findDetail(lease.id))
        assertEquals(LeaseStatus.ACTIVE.name, detail.status)
        assertEquals("Avenida Paulista, 1000, Apto 42", detail.propertyAddress)
    }
}

package br.com.locasign.lease.app.usecases

import br.com.locasign.lease.app.fakes.InMemoryLeaseRepository
import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.domain.DomainException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RegisterLeaseTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val clock = FixedBusinessClock(now)
    private val repository = InMemoryLeaseRepository()
    private val agencyDefaults = AgencySignerDefaults(
        name = "Imobiliária Padrão",
        email = "padrao@imobiliaria.com",
    )

    private val registerLease = RegisterLease(
        leases = repository,
        clock = clock,
        transactions = ImmediateTransactionRunner,
        agencyDefaults = agencyDefaults,
    )

    @Test
    fun `cadastra locação com signatário da imobiliária padrão quando omitido (R2)`() {
        val command = RegisterLeaseCommand(
            tenantName = "Pedro Alvares",
            tenantCpf = "529.982.247-25",
            tenantEmail = "pedro@example.com",
            agencySignerName = null,
            agencySignerEmail = null,
            propertyAddress = "Av Paulista, 1000",
            rentAmount = "3500.00",
            startDate = clock.today(),
            termMonths = 30,
        )

        val leaseId = registerLease.execute(command)

        val saved = repository.findById(leaseId)
        assertNotNull(saved)
        assertEquals(LeaseStatus.REGISTERED, saved.status)
        assertEquals("Imobiliária Padrão", saved.agencySigner.name)
        assertEquals("padrao@imobiliaria.com", saved.agencySigner.email.value)
    }

    @Test
    fun `rejeita cadastro com preenchimento parcial de signatário da imobiliária (R2)`() {
        val command = RegisterLeaseCommand(
            tenantName = "Pedro Alvares",
            tenantCpf = "529.982.247-25",
            tenantEmail = "pedro@example.com",
            agencySignerName = "Apenas Nome",
            agencySignerEmail = null,
            propertyAddress = "Av Paulista, 1000",
            rentAmount = "3500.00",
            startDate = clock.today(),
            termMonths = 30,
        )

        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            registerLease.execute(command)
        }
        assertEquals("agencySigner", ex.field)
    }
}

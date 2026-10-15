package br.com.locasign.lease.app.usecases

import br.com.locasign.lease.app.fakes.InMemoryLeaseRepository
import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ActivateLeaseTest {

    private val registeredAt = Instant.parse("2026-10-09T10:00:00Z")
    private val activatedAt = Instant.parse("2026-10-12T15:30:00Z")
    private val leases = InMemoryLeaseRepository()
    private val clock = FixedBusinessClock(activatedAt)
    private val activateLease = ActivateLease(leases, clock, ImmediateTransactionRunner)

    private fun registeredLease(): Lease = Lease.register(
        tenant = Tenant("Maria Silva", Cpf.of("529.982.247-25"), Email.of("maria@example.com")),
        agencySigner = AgencySigner("Imobiliária Aliança", Email.of("alianca@example.com")),
        propertyAddress = "Rua das Flores, 123",
        rentAmount = Money.positive("3200.00"),
        startDate = LocalDate.of(2026, 11, 1),
        term = LeaseTerm.of(30),
        today = LocalDate.of(2026, 10, 9),
        now = registeredAt,
    ).also(leases::save)

    @Test
    fun `locação registrada passa a ACTIVE com a data do relógio e é gravada`() {
        val lease = registeredLease()

        activateLease.execute(lease.id)

        val stored = leases.findById(lease.id)
        assertEquals(LeaseStatus.ACTIVE, stored?.status)
        assertEquals(activatedAt, stored?.activatedAt)
        assertEquals(2, leases.saves)
    }

    @Test
    fun `locação já ativa não é gravada de novo nem muda a data de ativação`() {
        val lease = registeredLease()
        activateLease.execute(lease.id)
        clock.instant = activatedAt.plusSeconds(3600)

        activateLease.execute(lease.id)

        assertEquals(2, leases.saves)
        assertEquals(activatedAt, leases.findById(lease.id)?.activatedAt)
    }

    @Test
    fun `locação inexistente lança NotFound`() {
        val missing = LeaseId.new()

        val ex = assertFailsWith<DomainException.NotFound> { activateLease.execute(missing) }

        assertEquals("Locação", ex.resource)
        assertEquals("Locação não encontrado(a): $missing", ex.message)
    }
}

package br.com.locasign.lease.domain.models

import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaseTest {

    private val today = LocalDate.of(2026, 10, 9)
    private val now = Instant.parse("2026-10-09T10:00:00Z")

    private val tenant = Tenant(
        name = "Maria Oliveira",
        cpf = Cpf.of("529.982.247-25"),
        email = Email.of("maria@example.com"),
    )

    private val agencySigner = AgencySigner(
        name = "Carlos Corretor",
        email = Email.of("carlos@imobiliaria.com"),
    )

    @Test
    fun `cadastro de locação bem-sucedido cria agregado com status REGISTERED (R2)`() {
        val lease = Lease.register(
            tenant = tenant,
            agencySigner = agencySigner,
            propertyAddress = "Rua das Flores, 123 - Apto 402",
            rentAmount = Money.positive("2800.00"),
            startDate = today,
            term = LeaseTerm.of(30),
            today = today,
            now = now,
        )

        assertEquals(LeaseStatus.REGISTERED, lease.status)
        assertEquals("Rua das Flores, 123 - Apto 402", lease.propertyAddress)
        assertEquals("2800.00", lease.rentAmount.toPlainString())
        assertEquals(30, lease.term.months)
    }

    @Test
    fun `cadastro rejeita data de início anterior à data atual do cadastro (R2)`() {
        val pastDate = today.minusDays(1)

        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Lease.register(
                tenant = tenant,
                agencySigner = agencySigner,
                propertyAddress = "Rua das Flores, 123",
                rentAmount = Money.positive("2800.00"),
                startDate = pastDate,
                term = LeaseTerm.of(30),
                today = today,
                now = now,
            )
        }
        assertEquals("startDate", ex.field)
        assertTrue(ex.message.orEmpty().contains("A data de início deve ser igual ou posterior à data do cadastro."))
    }

    @Test
    fun `cadastro rejeita endereço em branco ou que exceda 300 caracteres (R2)`() {
        assertFailsWith<DomainException.BusinessRuleViolation> {
            Lease.register(
                tenant = tenant,
                agencySigner = agencySigner,
                propertyAddress = "   ",
                rentAmount = Money.positive("2800.00"),
                startDate = today,
                term = LeaseTerm.of(30),
                today = today,
                now = now,
            )
        }

        val longAddress = "A".repeat(301)
        assertFailsWith<DomainException.BusinessRuleViolation> {
            Lease.register(
                tenant = tenant,
                agencySigner = agencySigner,
                propertyAddress = longAddress,
                rentAmount = Money.positive("2800.00"),
                startDate = today,
                term = LeaseTerm.of(30),
                today = today,
                now = now,
            )
        }
    }

    @Test
    fun `ativação da locação transiciona para ACTIVE e segunda chamada é idempotente (R8)`() {
        val lease = Lease.register(
            tenant = tenant,
            agencySigner = agencySigner,
            propertyAddress = "Rua das Flores, 123",
            rentAmount = Money.positive("2800.00"),
            startDate = today,
            term = LeaseTerm.of(30),
            today = today,
            now = now,
        )

        assertTrue(lease.activate(now))
        assertEquals(LeaseStatus.ACTIVE, lease.status)
        assertEquals(now, lease.activatedAt)

        // Segunda chamada
        assertFalse(lease.activate(now.plusSeconds(10)))
        assertEquals(LeaseStatus.ACTIVE, lease.status)
    }
}

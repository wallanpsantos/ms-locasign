package br.com.locasign.lease.domain.models

import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Cria uma instância de [Lease] registrada para cenários de teste com dados válidos (CPF mod 11 conforme R2/R10).
 */
fun sampleLease(
    id: LeaseId = LeaseId.new(),
    tenantName: String = "Maria Oliveira",
    tenantCpf: Cpf = Cpf.of("529.982.247-25"),
    tenantEmail: Email = Email.of("maria@example.com"),
    agencySignerName: String = "Carlos Corretor",
    agencySignerEmail: Email = Email.of("carlos@imobiliaria.com"),
    propertyAddress: String = "Rua das Flores, 123",
    rentAmount: Money = Money.positive("2800.00"),
    startDate: LocalDate = LocalDate.of(2026, 10, 9),
    termMonths: Int = 30,
    today: LocalDate = startDate,
    now: Instant = Instant.parse("2026-10-09T10:00:00Z"),
): Lease = Lease.register(
    tenant = Tenant(tenantName, tenantCpf, tenantEmail),
    agencySigner = AgencySigner(agencySignerName, agencySignerEmail),
    propertyAddress = propertyAddress,
    rentAmount = rentAmount,
    startDate = startDate,
    term = LeaseTerm.of(termMonths),
    today = today,
    now = now,
)

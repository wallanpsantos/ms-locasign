package br.com.locasign.lease.app.usecases

import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.LocalDate

data class RegisterLeaseCommand(
    val tenantName: String,
    val tenantCpf: String,
    val tenantEmail: String,
    val agencySignerName: String?,
    val agencySignerEmail: String?,
    val propertyAddress: String,
    val rentAmount: String,
    val startDate: LocalDate,
    val termMonths: Int?,
)

/** Signatário padrão da imobiliária, vindo da configuração (`AGENCY_SIGNER_NAME` / `AGENCY_SIGNER_EMAIL`). */
data class AgencySignerDefaults(val name: String?, val email: String?)

/** Cadastra a locação aplicando as validações de domínio (R2). */
class RegisterLease(
    private val leases: LeaseRepositoryPort,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
    private val agencyDefaults: AgencySignerDefaults,
) {

    fun execute(command: RegisterLeaseCommand): LeaseId {
        val lease = Lease.register(
            tenant = Tenant(
                name = command.tenantName.trim(),
                cpf = Cpf.of(command.tenantCpf, "tenant.cpf"),
                email = Email.of(command.tenantEmail, "tenant.email"),
            ),
            agencySigner = resolveAgencySigner(command),
            propertyAddress = command.propertyAddress,
            rentAmount = Money.positive(command.rentAmount),
            startDate = command.startDate,
            term = LeaseTerm.of(command.termMonths),
            today = clock.today(),
            now = clock.now(),
        )
        transactions.run { leases.save(lease) }
        return lease.id
    }

    private fun resolveAgencySigner(command: RegisterLeaseCommand): AgencySigner {
        val requestedName = command.agencySignerName?.trim().takeUnless { it.isNullOrEmpty() }
        val requestedEmail = command.agencySignerEmail?.trim().takeUnless { it.isNullOrEmpty() }
        if ((requestedName == null) != (requestedEmail == null)) {
            throw DomainException.BusinessRuleViolation(
                "agencySigner",
                "Informe nome e e-mail do signatário da imobiliária, ou nenhum dos dois para usar o padrão.",
            )
        }
        val name = requestedName ?: agencyDefaults.name?.trim().takeUnless { it.isNullOrEmpty() }
        val email = requestedEmail ?: agencyDefaults.email?.trim().takeUnless { it.isNullOrEmpty() }
        if (name == null || email == null) {
            throw DomainException.BusinessRuleViolation(
                "agencySigner",
                "Signatário da imobiliária não informado e nenhum padrão configurado " +
                        "(AGENCY_SIGNER_NAME / AGENCY_SIGNER_EMAIL).",
            )
        }
        return AgencySigner(name = name, email = Email.of(email, "agencySigner.email"))
    }
}

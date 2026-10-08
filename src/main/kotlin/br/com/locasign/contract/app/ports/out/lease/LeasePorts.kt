package br.com.locasign.contract.app.ports.out.lease

import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.LocalDate

/** Visão da locação que o módulo de contratos precisa. O módulo de locação continua dono dos dados. */
data class LeaseSnapshot(
	val leaseId: LeaseId,
	val tenantName: String,
	val tenantCpf: Cpf,
	val tenantEmail: Email,
	val agencySignerName: String,
	val agencySignerEmail: Email,
	val propertyAddress: String,
	val rentAmount: Money,
	val startDate: LocalDate,
	val termMonths: Int,
)

interface LeaseLookupPort {
	fun find(leaseId: LeaseId): LeaseSnapshot?
}

/** Ativa a locação como ação pós-assinatura. Devolve `true` se a ativação ocorreu agora. */
interface LeaseActivationPort {
	fun activate(leaseId: LeaseId): Boolean
}

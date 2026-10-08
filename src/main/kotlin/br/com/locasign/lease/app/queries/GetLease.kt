package br.com.locasign.lease.app.queries

import br.com.locasign.lease.app.ports.out.repository.LeaseQueryPort
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException

/** Query: lê direto do banco pelo query port, sem carregar o agregado. */
class GetLease(private val queries: LeaseQueryPort) {

	fun execute(leaseId: LeaseId): LeaseDetailView =
		queries.findDetail(leaseId) ?: throw DomainException.NotFound("Locação", leaseId.toString())
}

package br.com.locasign.lease.app.fakes

import br.com.locasign.lease.app.ports.out.repository.LeaseQueryPort
import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId

class InMemoryLeaseRepository : LeaseRepositoryPort {
    private val leases = mutableMapOf<LeaseId, Lease>()

    override fun save(lease: Lease) {
        leases[lease.id] = lease
    }

    override fun findById(id: LeaseId): Lease? = leases[id]
}

class InMemoryLeaseQueryPort(private val repository: InMemoryLeaseRepository) : LeaseQueryPort {
    override fun findDetail(id: LeaseId): LeaseDetailView? {
        val lease = repository.findById(id) ?: return null
        return LeaseDetailView(
            id = lease.id.toString(),
            status = lease.status.name,
            tenantName = lease.tenant.name,
            tenantCpfMasked = lease.tenant.cpf.masked(),
            tenantEmail = lease.tenant.email.value,
            agencySignerName = lease.agencySigner.name,
            agencySignerEmail = lease.agencySigner.email.value,
            propertyAddress = lease.propertyAddress,
            rentAmount = lease.rentAmount.toPlainString(),
            startDate = lease.startDate,
            termMonths = lease.term.months,
            createdAt = lease.createdAt,
            activatedAt = lease.activatedAt,
            currentContract = null,
        )
    }
}

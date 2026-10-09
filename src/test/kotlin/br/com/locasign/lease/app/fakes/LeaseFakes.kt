package br.com.locasign.lease.app.fakes

import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId

class InMemoryLeaseRepository : LeaseRepositoryPort {
    private val leases = mutableMapOf<LeaseId, Lease>()

    override fun save(lease: Lease) {
        leases[lease.id] = lease
    }

    override fun findById(id: LeaseId): Lease? = leases[id]
}

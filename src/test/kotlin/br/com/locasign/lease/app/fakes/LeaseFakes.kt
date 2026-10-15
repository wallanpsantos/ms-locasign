package br.com.locasign.lease.app.fakes

import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId

class InMemoryLeaseRepository : LeaseRepositoryPort {
    private val leases = mutableMapOf<LeaseId, Lease>()

    /** Quantas vezes [save] foi chamado, para conferir gravações condicionais. */
    var saves = 0
        private set

    override fun save(lease: Lease) {
        saves++
        leases[lease.id] = lease
    }

    override fun findById(id: LeaseId): Lease? = leases[id]
}

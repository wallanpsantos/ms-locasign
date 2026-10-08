package br.com.locasign.lease.app.ports.out.repository

import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId

/** Porta de saída de escrita: carrega e persiste o agregado. */
interface LeaseRepositoryPort {
    fun save(lease: Lease)

    fun findById(id: LeaseId): Lease?
}

/** Porta de saída de leitura: devolve modelos de leitura sem passar pelo agregado. */
interface LeaseQueryPort {
    fun findDetail(id: LeaseId): LeaseDetailView?
}

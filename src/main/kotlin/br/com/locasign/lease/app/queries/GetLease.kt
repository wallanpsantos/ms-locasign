package br.com.locasign.lease.app.queries

import br.com.locasign.lease.app.ports.out.repository.LeaseQueryPort
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException

/**
 * Caso de uso de consulta (Query) para obtenção dos detalhes consolidados de uma locação.
 *
 * **Responsabilidade:**
 * - Orquestrar a leitura da projeção detalhada da locação através de [LeaseQueryPort].
 * - Lançar [DomainException.NotFound] caso o identificador da locação não exista no sistema.
 */
class GetLease(private val queries: LeaseQueryPort) {

    fun execute(leaseId: LeaseId): LeaseDetailView =
        queries.findDetail(leaseId) ?: throw DomainException.NotFound("Locação", leaseId.toString())
}

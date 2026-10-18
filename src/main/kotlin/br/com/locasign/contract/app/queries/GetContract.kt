package br.com.locasign.contract.app.queries

import br.com.locasign.contract.app.ports.`in`.GetContractPort
import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.domain.DomainException

/**
 * Caso de uso de consulta que recupera os detalhes operacionais e de negócio de um contrato.
 *
 * **Responsabilidade:**
 * - Buscar o contrato via [ContractQueryPort] e lançar [DomainException.NotFound] se não for localizado.
 */
class GetContract(private val queries: ContractQueryPort) : GetContractPort {
    override fun execute(id: ContractId): ContractDetailView =
        queries.findDetail(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

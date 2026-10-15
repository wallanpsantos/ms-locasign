package br.com.locasign.contract.app.queries

import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.domain.DomainException

/**
 * Caso de uso de consulta que recupera a linha do tempo histórica de auditoria de um contrato.
 *
 * **Responsabilidade:**
 * - Consultar a trilha de auditoria completa (incluindo transições ignoradas e fatos informativos conforme R5 e R7).
 * - Garantir que o contrato exista antes de fornecer o histórico.
 */
class GetContractHistory(private val queries: ContractQueryPort) {
    fun execute(id: ContractId): List<HistoryEntryView> =
        queries.findHistory(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

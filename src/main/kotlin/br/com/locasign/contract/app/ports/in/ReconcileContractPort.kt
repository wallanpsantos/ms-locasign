package br.com.locasign.contract.app.ports.`in`

import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.lease.domain.valueobjects.LeaseId

/**
 * Metadados mínimos do contrato após tentativa de reconciliação (ADR-018).
 *
 * **Responsabilidade:**
 * - Prover identificador da locação ([leaseId]) e status final ([status]) para resposta de comando REST sem consultas adicionais.
 */
data class ReconciledContractSummary(
    val leaseId: LeaseId,
    val status: ContractStatus,
)

/**
 * Porta de entrada da reconciliação pontual de um contrato pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller só [executeFor], a consulta forçada de um contrato no provedor.
 * - Deixar a varredura agendada (`executeStale`) na classe concreta, fora desta porta (ADR-019).
 */
interface ReconcileContractPort {
    fun executeFor(contractId: ContractId): ReconciledContractSummary
}

package br.com.locasign.contract.app.ports.`in`

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.lease.domain.valueobjects.LeaseId

/**
 * Parâmetros de comando para solicitação e criação de uma nova versão de contrato para uma locação.
 *
 * **Responsabilidade:**
 * - Conter o identificador da locação ([leaseId]) para a qual o contrato será gerado.
 */
data class RequestContractCommand(val leaseId: LeaseId)

/**
 * Porta de entrada da solicitação de contrato pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller a criação de um rascunho para a locação informada, sem revelar a classe concreta que aplica R1 e R3.
 */
fun interface RequestContractPort {
    fun execute(command: RequestContractCommand): ContractId
}

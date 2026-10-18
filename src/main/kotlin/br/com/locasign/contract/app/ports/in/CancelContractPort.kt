package br.com.locasign.contract.app.ports.`in`

import br.com.locasign.contract.domain.valueobjects.ContractId

/**
 * Parâmetros de comando para cancelamento voluntário de um contrato de locação em andamento.
 *
 * **Responsabilidade:**
 * - Conter o identificador do contrato ([contractId]) e a justificativa formal do cancelamento ([reason]).
 */
data class CancelContractCommand(val contractId: ContractId, val reason: String)

/**
 * Porta de entrada do cancelamento de contrato pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller o cancelamento formal com justificativa, sem revelar a classe concreta que aplica R9.
 */
fun interface CancelContractPort {
    fun execute(command: CancelContractCommand)
}

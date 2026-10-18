package br.com.locasign.contract.app.ports.`in`

import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.domain.valueobjects.ContractId

/**
 * Porta de entrada da consulta do contrato pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller a leitura do detalhe operacional do contrato, sem revelar a classe concreta da query.
 */
interface GetContractPort {
    fun execute(id: ContractId): ContractDetailView
}

package br.com.locasign.contract.app.ports.`in`

import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.domain.valueobjects.ContractId

/**
 * Porta de entrada da consulta do histórico do contrato pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller a linha do tempo do contrato, inclusive transições ignoradas, sem revelar a classe concreta da query.
 */
interface GetContractHistoryPort {
    fun execute(id: ContractId): List<HistoryEntryView>
}

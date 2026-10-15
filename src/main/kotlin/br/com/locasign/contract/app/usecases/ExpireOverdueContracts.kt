package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.TransitionResult
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Caso de uso agendado responsável por identificar e expirar contratos com prazo limite de assinatura vencido (Regra R4).
 *
 * **Responsabilidade:**
 * - Buscar contratos em aberto que excederam o prazo regulamentar e aplicar a transição para `EXPIRED` no agregado [br.com.locasign.contract.domain.models.Contract].
 * - Processar cada contrato em sua própria transação delimitada para isolar falhas.
 * - Não chamar a PandaDoc: o evento `ContractExpired` vai para o outbox na mesma transação e [AnnulProviderDocument] anula o documento depois, com retentativa (ADR-016). Assim o limite de chamadas do provedor não afeta o lote.
 */
class ExpireOverdueContracts(
    private val contracts: ContractRepositoryPort,
    private val persister: ContractPersister,
    private val clock: BusinessClock,
    private val settings: ContractSettings,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Devolve quantos contratos foram expirados. */
    fun execute(): Int {
        val ids = contracts.findOverdueIds(clock.now(), settings.jobBatchSize)
        return ids.count { expire(it) }
    }

    private fun expire(id: ContractId): Boolean = try {
        transactions.run {
            val contract = contracts.findById(id) ?: return@run false
            val expired = contract.expireIfOverdue(clock.now()) == TransitionResult.APPLIED
            if (expired) persister.save(contract)
            expired
        }
    } catch (e: RuntimeException) {
        log.error("Falha ao expirar o contrato {}", id, e)
        false
    }
}

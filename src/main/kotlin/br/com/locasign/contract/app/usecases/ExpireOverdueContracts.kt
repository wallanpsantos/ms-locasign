package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.TransitionResult
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Caso de uso agendado responsável por identificar e expirar contratos com prazo limite de assinatura vencido (Regra R4).
 *
 * **Responsabilidade:**
 * - Buscar contratos em aberto que excederam o prazo regulamentar e aplicar a transição para `EXPIRED` no agregado [Contract].
 * - Processar cada contrato em sua própria transação delimitada para isolar falhas.
 * - Anular o documento no provedor externo em melhor esforço após a confirmação da transação.
 */
class ExpireOverdueContracts(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
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
        val expired = transactions.run {
            val contract = contracts.findById(id)
            if (contract?.expireIfOverdue(clock.now()) == TransitionResult.APPLIED) {
                persister.save(contract)
                Expired(contract.providerDocumentId)
            } else {
                null
            }
        }
        expired?.documentToVoid?.let { provider.cancelDocumentQuietly(it, log, "Contrato expirado") }
        expired != null
    } catch (e: RuntimeException) {
        log.error("Falha ao expirar o contrato {}", id, e)
        false
    }

    /** Contrato expirado agora; o documento só é anulado no provedor depois que a transação confirma. */
    private class Expired(val documentToVoid: ProviderDocumentId?)
}

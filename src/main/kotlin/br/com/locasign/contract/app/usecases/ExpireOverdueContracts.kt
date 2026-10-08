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
 * Job de expiração (R4): contratos aguardando assinatura cujo prazo venceu vão para `EXPIRED`.
 * Cada contrato roda na própria transação, então uma falha isolada não bloqueia os demais.
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

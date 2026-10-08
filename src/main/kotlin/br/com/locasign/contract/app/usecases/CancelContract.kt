package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.slf4j.LoggerFactory

data class CancelContractCommand(val contractId: ContractId, val reason: String)

/**
 * Cancelamento pelo corretor (R9). A transição é gravada primeiro; depois, fora da transação, o
 * documento é anulado no provedor em regime de melhor esforço, para que ninguém mais o assine.
 */
class CancelContract(
	private val contracts: ContractRepositoryPort,
	private val provider: SignatureProviderPort,
	private val persister: ContractPersister,
	private val clock: BusinessClock,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun execute(command: CancelContractCommand) {
		val reason = command.reason.trim()
		if (reason.isEmpty() || reason.length > MAX_REASON_LENGTH) {
			throw DomainException.BusinessRuleViolation(
				"reason",
				"O motivo do cancelamento é obrigatório (até $MAX_REASON_LENGTH caracteres).",
			)
		}
		val documentToVoid = transactions.run {
			val contract = contracts.findById(command.contractId)
				?: throw DomainException.NotFound("Contrato", command.contractId.toString())
			val wasSent = contract.status.progress >= ContractStatus.SENT.progress && !contract.status.isFinal
			contract.cancel(reason, clock.now())
			persister.save(contract)
			contract.providerDocumentId.takeIf { wasSent }
		}
		documentToVoid?.let { provider.cancelDocumentQuietly(it, log, "Contrato cancelado") }
	}

	private companion object {
		const val MAX_REASON_LENGTH = 500
	}
}

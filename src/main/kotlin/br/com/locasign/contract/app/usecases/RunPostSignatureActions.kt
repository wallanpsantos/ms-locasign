package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Reação a `ContractCompleted` (R8): ativa a locação e registra a vistoria de entrada e a primeira
 * cobrança. Vistoria e financeiro são simulados no MVP: o sistema só registra que a ação aconteceria.
 *
 * Idempotência em duas camadas (R6): `processed_messages` por grupo e UNIQUE (contrato, ação).
 */
class RunPostSignatureActions(
	private val contracts: ContractRepositoryPort,
	private val leases: LeaseLookupPort,
	private val leaseActivation: LeaseActivationPort,
	private val actions: PostSignatureActionsPort,
	private val persister: ContractPersister,
	private val processed: ProcessedMessagesPort,
	private val clock: BusinessClock,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun execute(command: ContractEventCommand) {
		transactions.run {
			if (!processed.markProcessed(ConsumerGroups.POST_SIGNATURE, command.eventId)) return@run
			val contract = contracts.findById(command.contractId) ?: return@run
			if (contract.status != ContractStatus.COMPLETED) {
				// R8: ações pós-assinatura só acontecem com o contrato concluído.
				log.warn("Contrato {} não está concluído ({}); ações pós-assinatura ignoradas", contract.id, contract.status)
				return@run
			}
			val lease = checkNotNull(leases.find(contract.leaseId)) { "Locação ${contract.leaseId} do contrato não existe" }
			val now = clock.now()

			if (actions.registerIfAbsent(contract.id, ACTIVATE_LEASE, STATUS_DONE, null)) {
				leaseActivation.activate(contract.leaseId)
				contract.recordLeaseActivated(now)
			}
			if (actions.registerIfAbsent(
					contract.id,
					SCHEDULE_ENTRY_INSPECTION,
					STATUS_SIMULATED,
					"""{"scheduledFor":"${lease.startDate}"}""",
				)
			) {
				log.info("[simulado] Vistoria de entrada agendada para o contrato {} em {}", contract.id, lease.startDate)
			}
			if (actions.registerIfAbsent(
					contract.id,
					REGISTER_FIRST_CHARGE,
					STATUS_SIMULATED,
					"""{"amount":"${lease.rentAmount.toPlainString()}","dueDate":"${lease.startDate}"}""",
				)
			) {
				log.info(
					"[simulado] Primeira cobrança de R$ {} registrada para o contrato {}",
					lease.rentAmount.toPlainString(),
					contract.id,
				)
			}
			if (contract.hasPendingChanges) persister.save(contract)
		}
	}

	private companion object {
		const val ACTIVATE_LEASE = "ACTIVATE_LEASE"
		const val SCHEDULE_ENTRY_INSPECTION = "SCHEDULE_ENTRY_INSPECTION"
		const val REGISTER_FIRST_CHARGE = "REGISTER_FIRST_CHARGE"
		const val STATUS_DONE = "DONE"
		const val STATUS_SIMULATED = "SIMULATED"
	}
}

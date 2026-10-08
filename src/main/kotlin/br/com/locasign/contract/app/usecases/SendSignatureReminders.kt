package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Job de lembrete (R4, opcional no MVP): no 3º dia, registra um lembrete simulado para cada
 * contrato que ainda aguarda assinatura. O evento `ContractReminderSent` alimenta as notificações.
 */
class SendSignatureReminders(
	private val contracts: ContractRepositoryPort,
	private val persister: ContractPersister,
	private val clock: BusinessClock,
	private val settings: ContractSettings,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	/** Devolve quantos lembretes foram registrados. */
	fun execute(): Int {
		val now = clock.now()
		val ids = contracts.findReminderDueIds(now.minus(settings.reminderAfter), settings.jobBatchSize)
		return ids.count { remind(it) }
	}

	private fun remind(id: ContractId): Boolean = try {
		transactions.run {
			val contract = contracts.findById(id) ?: return@run false
			val reminded = contract.markReminderSent(clock.now())
			if (reminded) persister.save(contract)
			reminded
		}
	} catch (e: RuntimeException) {
		log.error("Falha ao registrar o lembrete do contrato {}", id, e)
		false
	}
}

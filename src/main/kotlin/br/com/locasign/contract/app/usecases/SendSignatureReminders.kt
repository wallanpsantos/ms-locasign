package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Caso de uso agendado responsável por identificar contratos pendentes e disparar lembretes de assinatura (Regra R4 e R7).
 *
 * **Responsabilidade:**
 * - Identificar contratos aguardando assinaturas que atingiram a janela de lembrete (3º dia).
 * - Registrar o lembrete no agregado [Contract] e emitir o evento `ContractReminderSent` via outbox para notificação simulada.
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

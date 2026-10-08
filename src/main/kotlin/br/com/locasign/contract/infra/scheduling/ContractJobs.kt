package br.com.locasign.contract.infra.scheduling

import br.com.locasign.contract.app.usecases.ExpireOverdueContracts
import br.com.locasign.contract.app.usecases.ReconcileContracts
import br.com.locasign.contract.app.usecases.SendSignatureReminders
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Reconciliação (guia, seção 12): consulta o provedor para contratos parados. Cobre webhooks
 * perdidos (a PandaDoc não reenvia) e é o plano B se os webhooks não estiverem disponíveis na conta.
 * Localmente há uma única instância; ao escalar, proteger os jobs com um lock distribuído (ShedLock).
 */
@Component
@ConditionalOnProperty(prefix = "locasign.jobs.reconciliation", name = ["enabled"], matchIfMissing = true)
class ReconciliationJob(private val reconcile: ReconcileContracts) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(
		fixedDelayString = "\${locasign.jobs.reconciliation.interval:PT5M}",
		initialDelayString = "\${locasign.jobs.reconciliation.interval:PT5M}",
	)
	fun run() {
		try {
			val consulted = reconcile.executeStale()
			if (consulted > 0) log.info("Reconciliação consultou {} contrato(s) no provedor", consulted)
		} catch (e: Exception) {
			log.error("Falha na reconciliação", e)
		}
	}
}

/** Expiração (R4): contratos aguardando assinatura com prazo vencido vão para `EXPIRED`. */
@Component
@ConditionalOnProperty(prefix = "locasign.jobs.expiration", name = ["enabled"], matchIfMissing = true)
class ExpirationJob(private val expire: ExpireOverdueContracts) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(
		fixedDelayString = "\${locasign.jobs.expiration.interval:PT15M}",
		initialDelayString = "\${locasign.jobs.expiration.interval:PT15M}",
	)
	fun run() {
		try {
			val expired = expire.execute()
			if (expired > 0) log.info("{} contrato(s) expirado(s)", expired)
		} catch (e: Exception) {
			log.error("Falha na expiração de contratos", e)
		}
	}
}

/** Lembrete do 3º dia (R4, opcional no MVP): registra um lembrete simulado por contrato. */
@Component
@ConditionalOnProperty(prefix = "locasign.jobs.reminder", name = ["enabled"], matchIfMissing = true)
class ReminderJob(private val reminders: SendSignatureReminders) {
	private val log = LoggerFactory.getLogger(javaClass)

	@Scheduled(
		fixedDelayString = "\${locasign.jobs.reminder.interval:PT24H}",
		initialDelayString = "\${locasign.jobs.reminder.interval:PT24H}",
	)
	fun run() {
		try {
			val sent = reminders.execute()
			if (sent > 0) log.info("{} lembrete(s) de assinatura registrado(s)", sent)
		} catch (e: Exception) {
			log.error("Falha ao registrar lembretes", e)
		}
	}
}

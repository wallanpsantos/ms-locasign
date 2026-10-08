package br.com.locasign.notification.app.usecases

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.domain.NotificationPolicy
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Parâmetros de comando para execução do registro de notificações geradas por eventos de contrato.
 *
 * **Responsabilidade:**
 * - Transportar os identificadores do evento de domínio original, tipo de evento e contrato afetado.
 * - Fornecer os dados necessários para avaliação da audiência e idempotência do processamento.
 */
data class RecordNotificationsCommand(val eventId: String, val eventType: String, val contractId: String)

/**
 * Caso de uso responsável por registrar notificações simuladas decorrentes dos eventos de domínio do contrato.
 *
 * **Responsabilidade:**
 * - Avaliar o público-alvo com base no tipo de evento utilizando [NotificationPolicy].
 * - Assegurar consumo idempotente por meio de [ProcessedMessagesPort] e demarcação transacional via [TransactionRunner].
 * - Obter os contatos mascarados via [NotificationRecipientsPort] e registrar a auditoria via [NotificationLogPort] sem expor dados pessoais sensíveis (R1-R10 / LGPD).
 */
class RecordNotifications(
    private val recipients: NotificationRecipientsPort,
    private val log: NotificationLogPort,
    private val processed: ProcessedMessagesPort,
    private val transactions: TransactionRunner,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(command: RecordNotificationsCommand) {
        val audience = NotificationPolicy.audienceFor(command.eventType) ?: return
        transactions.run {
            if (!processed.markProcessed(ConsumerGroups.NOTIFICATIONS, command.eventId)) return@run
            recipients.recipientsOf(command.contractId, audience).forEach { recipient ->
                log.record(command.contractId, command.eventId, command.eventType, recipient.emailMasked)
                logger.info(
                    "[simulado] Notificação {} para {} ({}) sobre o contrato {}",
                    command.eventType,
                    recipient.emailMasked,
                    recipient.role,
                    command.contractId,
                )
            }
        }
    }
}

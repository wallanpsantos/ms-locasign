package br.com.locasign.notification.app.usecases

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.domain.NotificationPolicy
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

data class RecordNotificationsCommand(val eventId: String, val eventType: String, val contractId: String)

/**
 * Notificações simuladas: não há e-mail, WhatsApp nem meio de pagamento reais no MVP. O sistema
 * apenas registra que a notificação seria enviada, com o destinatário mascarado.
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

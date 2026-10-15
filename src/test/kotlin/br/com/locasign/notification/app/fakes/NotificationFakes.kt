package br.com.locasign.notification.app.fakes

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipient
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.domain.Audience
import kotlin.uuid.Uuid

/** Signatários por contrato, filtrados pelo público como o adapter JDBC (`AGENCY_ONLY` devolve só a imobiliária). */
class InMemoryNotificationRecipients : NotificationRecipientsPort {
    private val byContract = mutableMapOf<String, MutableList<NotificationRecipient>>()
    val requested = mutableListOf<Pair<String, Audience>>()

    fun register(contractId: Uuid, recipient: NotificationRecipient) {
        byContract.getOrPut(contractId.toString()) { mutableListOf() } += recipient
    }

    override fun recipientsOf(contractId: Uuid, audience: Audience): List<NotificationRecipient> {
        requested += contractId.toString() to audience
        val all = byContract[contractId.toString()].orEmpty()
        return when (audience) {
            Audience.ALL_PARTIES -> all
            Audience.AGENCY_ONLY -> all.filter { it.role == "AGENCY" }
        }
    }
}

/** Linha gravada em `notifications_log`, com o id do contrato em texto. */
data class LoggedNotification(
    val contractId: String,
    val eventId: String,
    val eventType: String,
    val recipientMasked: String,
)

/** Guarda as notificações registradas, na ordem de gravação. */
class InMemoryNotificationLog : NotificationLogPort {
    val records = mutableListOf<LoggedNotification>()

    override fun record(contractId: Uuid, eventId: String, eventType: String, recipientMasked: String) {
        records += LoggedNotification(contractId.toString(), eventId, eventType, recipientMasked)
    }
}

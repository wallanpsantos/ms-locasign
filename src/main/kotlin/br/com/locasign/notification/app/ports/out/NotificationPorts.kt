package br.com.locasign.notification.app.ports.out

import br.com.locasign.notification.domain.Audience

/** Destinatário já mascarado: dados pessoais completos nunca saem do módulo de contratos. */
data class NotificationRecipient(val role: String, val emailMasked: String)

/** Resolve os destinatários consultando o contrato pelo id (os eventos não carregam e-mails). */
interface NotificationRecipientsPort {
    fun recipientsOf(contractId: String, audience: Audience): List<NotificationRecipient>
}

interface NotificationLogPort {
    fun record(contractId: String, eventId: String, eventType: String, recipientMasked: String)
}

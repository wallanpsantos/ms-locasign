package br.com.locasign.notification.app.usecases

import br.com.locasign.notification.app.fakes.InMemoryNotificationLog
import br.com.locasign.notification.app.fakes.InMemoryNotificationRecipients
import br.com.locasign.notification.app.fakes.LoggedNotification
import br.com.locasign.notification.app.ports.out.NotificationRecipient
import br.com.locasign.notification.domain.Audience
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RecordNotificationsTest {

    private val recipients = InMemoryNotificationRecipients()
    private val notificationLog = InMemoryNotificationLog()
    private val processed = InMemoryProcessedMessages()

    private val recordNotifications = RecordNotifications(
        recipients,
        notificationLog,
        processed,
        ImmediateTransactionRunner,
    )

    private val contractId = Uuid.parse("0f3e6b0a-6f0c-4a3e-9d6f-2b1c8a7e5d41")
    private val tenant = NotificationRecipient("TENANT", "j***@example.com")
    private val agency = NotificationRecipient("AGENCY", "a***@example.com")

    init {
        recipients.register(contractId, tenant)
        recipients.register(contractId, agency)
    }

    private fun command(eventType: String, eventId: String = "evt-1") =
        RecordNotificationsCommand(eventId, eventType, contractId)

    private fun logged(recipient: NotificationRecipient, eventType: String) =
        LoggedNotification(contractId.toString(), "evt-1", eventType, recipient.emailMasked)

    @Test
    fun `evento com público registra uma linha por destinatário daquele público`() {
        recordNotifications.execute(command("ContractSent"))

        assertEquals(listOf(contractId.toString() to Audience.AGENCY_ONLY), recipients.requested)
        assertEquals(listOf(logged(agency, "ContractSent")), notificationLog.records)
        assertTrue(processed.isProcessed(ConsumerGroups.NOTIFICATIONS, "evt-1"))
    }

    @Test
    fun `conclusão do contrato notifica todas as partes devolvidas pela porta`() {
        recordNotifications.execute(command("ContractCompleted"))

        assertEquals(
            listOf(logged(tenant, "ContractCompleted"), logged(agency, "ContractCompleted")),
            notificationLog.records,
        )
    }

    @Test
    fun `evento sem público não grava nada nem marca o evento como processado`() {
        recordNotifications.execute(command("ContractRequested"))

        assertTrue(recipients.requested.isEmpty())
        assertTrue(notificationLog.records.isEmpty())
        assertFalse(processed.isProcessed(ConsumerGroups.NOTIFICATIONS, "evt-1"))
    }

    @Test
    fun `o mesmo evento processado duas vezes gera um único registro (R6)`() {
        recordNotifications.execute(command("ContractSent"))
        recordNotifications.execute(command("ContractSent"))

        assertEquals(1, notificationLog.records.size)
    }
}

package br.com.locasign.notification.interfaces.messaging

import br.com.locasign.notification.app.fakes.InMemoryNotificationLog
import br.com.locasign.notification.app.fakes.InMemoryNotificationRecipients
import br.com.locasign.notification.app.ports.out.NotificationRecipient
import br.com.locasign.notification.app.usecases.RecordNotifications
import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.messaging.EnvelopeReader
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class NotificationConsumerTest {

    private val recipients = InMemoryNotificationRecipients()
    private val notificationLog = InMemoryNotificationLog()

    private val consumer = NotificationConsumer(
        reader = EnvelopeReader(JsonMapper.builder().findAndAddModules().build(), MdcCorrelationContext()),
        recordNotifications = RecordNotifications(
            recipients,
            notificationLog,
            InMemoryProcessedMessages(),
            ImmediateTransactionRunner,
        ),
    )

    private fun message(eventType: String, aggregateId: String) =
        """{"eventId":"evt-1","eventType":"$eventType","aggregateId":"$aggregateId"}"""

    @Test
    fun `aggregateId inválido é mensagem ilegível mesmo em evento sem público (DLT)`() {
        val ex = assertFailsWith<UnreadableMessageException> {
            consumer.onMessage(message("ContractRequested", "nao-e-uuid"))
        }

        assertEquals("Evento evt-1 com aggregateId inválido: nao-e-uuid", ex.message)
    }

    @Test
    fun `o aggregateId vira o id do contrato das notificações registradas`() {
        val contractId = Uuid.random()
        recipients.register(contractId, NotificationRecipient("AGENCY", "a***@example.com"))

        consumer.onMessage(message("ContractSent", contractId.toString()))

        assertEquals(contractId.toString(), notificationLog.records.single().contractId)
    }
}

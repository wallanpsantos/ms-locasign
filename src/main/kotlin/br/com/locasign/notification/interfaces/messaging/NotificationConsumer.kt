package br.com.locasign.notification.interfaces.messaging

import br.com.locasign.notification.app.usecases.RecordNotifications
import br.com.locasign.notification.app.usecases.RecordNotificationsCommand
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.interfaces.messaging.EnvelopeReader
import br.com.locasign.shared.interfaces.messaging.aggregateUuid
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Consumidor Kafka do grupo de notificações (`notifications`), responsável por escutar eventos do ciclo de vida dos contratos.
 *
 * **Responsabilidade:**
 * - Consumir mensagens do tópico [Topics.CONTRACT_EVENTS], desserializar o envelope e propagar o contexto de correlação.
 * - Invocar o caso de uso [RecordNotifications] para simular o envio e registrar o log da notificação de forma assíncrona.
 */
@Component
class NotificationConsumer(
    private val reader: EnvelopeReader,
    private val recordNotifications: RecordNotifications,
) {

    @KafkaListener(topics = [Topics.CONTRACT_EVENTS], groupId = ConsumerGroups.NOTIFICATIONS)
    fun onMessage(message: String) {
        val envelope = reader.read(message)
        val contractId = envelope.aggregateUuid()
        reader.handle(envelope) {
            recordNotifications.execute(
                RecordNotificationsCommand(envelope.eventId, envelope.eventType, contractId.toString()),
            )
        }
    }
}

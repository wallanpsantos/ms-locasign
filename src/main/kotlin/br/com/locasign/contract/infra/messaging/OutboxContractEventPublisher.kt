package br.com.locasign.contract.infra.messaging

import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.domain.events.ContractEvent
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.OutboxPort
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Adaptador de mensageria que implementa [ContractEventPublisherPort] persistindo mensagens na tabela transacional `outbox`.
 *
 * **Responsabilidade:**
 * - Serializar cada evento de contrato em JSON e gravá-lo na tabela `outbox` na mesma transação atômica do agregado.
 * - Definir a chave de partição Kafka com base no identificador do contrato (`contractId`), garantindo ordem causal estrita por partição (Princípio 3).
 */
@Component
class OutboxContractEventPublisher(
    private val outbox: OutboxPort,
    private val mapper: JsonMapper,
) : ContractEventPublisherPort {

    override fun publish(events: List<ContractEvent>) {
        events.forEach { event ->
            outbox.append(
                OutboxMessage(
                    id = event.eventId.toString(),
                    topic = Topics.CONTRACT_EVENTS,
                    // Chave = contractId: todos os eventos do contrato ficam na mesma partição, em ordem.
                    key = event.aggregateId,
                    aggregateType = event.aggregateType,
                    aggregateId = event.aggregateId,
                    eventType = event.eventType,
                    occurredAt = event.occurredAt,
                    payloadJson = mapper.writeValueAsString(event.toPayload()),
                ),
            )
        }
    }
}

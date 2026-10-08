package br.com.locasign.contract.app.ports.out.messaging

import br.com.locasign.contract.domain.events.ContractEvent

/**
 * Publica eventos do contrato. A implementação escreve no outbox, dentro da transação em
 * andamento; nunca no Kafka diretamente (princípio 3).
 */
interface ContractEventPublisherPort {
    fun publish(events: List<ContractEvent>)
}

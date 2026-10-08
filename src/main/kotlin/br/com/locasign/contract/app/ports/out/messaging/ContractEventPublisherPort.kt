package br.com.locasign.contract.app.ports.out.messaging

import br.com.locasign.contract.domain.events.ContractEvent

/**
 * Porta de saída para publicação de eventos de domínio gerados pelo módulo de contratos.
 *
 * **Responsabilidade:**
 * - Persistir eventos de domínio na tabela transacional de outbox dentro da mesma transação do agregado (Princípio 3 da arquitetura).
 * - Garantir que use cases nunca emitam eventos diretamente para o Apache Kafka, prevenindo perda de mensagens ou inconsistência dual-write.
 */
interface ContractEventPublisherPort {
    fun publish(events: List<ContractEvent>)
}

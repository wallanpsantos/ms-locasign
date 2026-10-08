package br.com.locasign.contract.interfaces.messaging

import br.com.locasign.contract.app.usecases.ContractEventCommand
import br.com.locasign.contract.app.usecases.CreateProviderDocument
import br.com.locasign.contract.app.usecases.ProcessProviderWebhookItem
import br.com.locasign.contract.app.usecases.RunPostSignatureActions
import br.com.locasign.contract.app.usecases.SendContract
import br.com.locasign.contract.app.usecases.WebhookItemCommand
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.UnreadableMessageException
import br.com.locasign.shared.app.ports.ContextKeys
import br.com.locasign.shared.interfaces.messaging.EnvelopeReader
import br.com.locasign.shared.interfaces.messaging.EventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/** O `aggregateId` dos eventos de contrato é o id do contrato; um valor inválido é mensagem ilegível (DLT). */
private fun EventEnvelope.contractId(): ContractId =
    ContractId.parseOrNull(aggregateId)
        ?: throw UnreadableMessageException("Evento $eventId com aggregateId inválido: $aggregateId")

/** Trata um evento de contrato com `contractId` no contexto de log, junto da correlação do envelope. */
private fun EnvelopeReader.handleContractEvent(envelope: EventEnvelope, action: (ContractEventCommand) -> Unit) {
    val contractId = envelope.contractId()
    handle(envelope, mapOf(ContextKeys.CONTRACT_ID to contractId.toString())) {
        action(ContractEventCommand(envelope.eventId, contractId))
    }
}

/**
 * Consumidor Kafka do grupo orchestrator encarregado da criação e despacho de documentos no parceiro de assinatura.
 *
 * Escuta o tópico de eventos de contrato (`contract-events`) e reage aos eventos [ContractRequested] (invocando a criação
 * do rascunho via [CreateProviderDocument]) e [ContractGenerated] (invocando o envio para os signatários via [SendContract]).
 * Eventos que não pertencem ao fluxo de orquestração são ignorados sem descarte de erros, garantindo que falhas
 * inesperadas acionem retentativas automáticas e posterior redirecionamento para a DLT (Dead Letter Topic).
 *
 * **Responsabilidade:**
 * - Adaptador de entrada (driving adapter) de mensageria para o grupo de consumidores [ConsumerGroups.ORCHESTRATOR].
 * - Coordenar as etapas assíncronas do ciclo de vida de contratos com a PandaDoc através do consumo de eventos de domínio.
 * - Garantir a correlação de traces distribuídos e o tratamento resiliente de mensagens Kafka.
 */
@Component
class OrchestratorConsumer(
    private val reader: EnvelopeReader,
    private val createProviderDocument: CreateProviderDocument,
    private val sendContract: SendContract,
) {

    @KafkaListener(topics = [Topics.CONTRACT_EVENTS], groupId = ConsumerGroups.ORCHESTRATOR)
    fun onMessage(message: String) {
        val envelope = reader.read(message)
        when (envelope.eventType) {
            "ContractRequested" -> reader.handleContractEvent(envelope, createProviderDocument::execute)
            "ContractGenerated" -> reader.handleContractEvent(envelope, sendContract::execute)
            else -> Unit
        }
    }
}

/**
 * Consumidor Kafka do grupo post-signature responsável por acionar as rotinas subsequentes à conclusão das assinaturas.
 *
 * Escuta o tópico de eventos de contrato (`contract-events`) e filtra mensagens do tipo [ContractCompleted],
 * disparando a execução do caso de uso [RunPostSignatureActions] para orquestrar o download do PDF assinado,
 * o arquivamento no repositório de arquivos e a transição da proposta de locação para o status de ativação (R8).
 *
 * **Responsabilidade:**
 * - Adaptador de entrada (driving adapter) de mensageria para o grupo de consumidores [ConsumerGroups.POST_SIGNATURE].
 * - Desencadear as ações pós-assinatura assim que a última assinatura for formalizada e o contrato for concluído.
 * - Assegurar a continuidade do fluxo de negócio sem intervenção manual (Regra R8).
 */
@Component
class PostSignatureConsumer(
    private val reader: EnvelopeReader,
    private val runPostSignatureActions: RunPostSignatureActions,
) {

    @KafkaListener(topics = [Topics.CONTRACT_EVENTS], groupId = ConsumerGroups.POST_SIGNATURE)
    fun onMessage(message: String) {
        val envelope = reader.read(message)
        if (envelope.eventType == "ContractCompleted") {
            reader.handleContractEvent(envelope, runPostSignatureActions::execute)
        }
    }
}

/**
 * Consumidor Kafka do grupo provider-events responsável por consumir itens desempacotados do inbox de webhooks da PandaDoc.
 *
 * Escuta o tópico dedicado a webhooks da PandaDoc (`pandadoc-webhooks`) e delega o processamento de cada item individual
 * ao caso de uso [ProcessProviderWebhookItem]. Converte o payload JSON original no comando correspondente,
 * permitindo que atualizações de status ou cancelamentos externos no parceiro sejam refletidos no agregado [Contract].
 *
 * **Responsabilidade:**
 * - Adaptador de entrada (driving adapter) de mensageria para o grupo de consumidores [ConsumerGroups.PROVIDER_EVENTS].
 * - Processar assincronamente as notificações de mudança de estado originadas no provedor de assinatura eletrônica.
 * - Manter o desacoplamento temporal entre o recebimento HTTP dos webhooks e a efetivação das mutações de negócio.
 */
@Component
class ProviderEventsConsumer(
    private val reader: EnvelopeReader,
    private val processWebhookItem: ProcessProviderWebhookItem,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [Topics.PANDADOC_WEBHOOKS], groupId = ConsumerGroups.PROVIDER_EVENTS)
    fun onMessage(message: String) {
        val envelope = reader.read(message)
        reader.handle(envelope) {
            log.debug("Item de webhook {} ({})", envelope.eventId, envelope.eventType)
            processWebhookItem.execute(WebhookItemCommand(envelope.eventId, reader.payloadJson(envelope)))
        }
    }
}

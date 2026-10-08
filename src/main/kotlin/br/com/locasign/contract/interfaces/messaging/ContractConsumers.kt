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
 * Grupo `orchestrator` (guia, seção 7.4): `ContractRequested` cria o documento no provedor e
 * `ContractGenerated` o envia. Eventos que não interessam ao grupo são lidos e ignorados.
 * Nenhuma exceção é engolida: o `DefaultErrorHandler` retenta com backoff e, depois, envia para a DLT.
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

/** Grupo `post-signature`: `ContractCompleted` dispara as ações pós-assinatura (R8). */
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

/** Grupo `provider-events`: cada item de webhook vira uma atualização do contrato (ou o arquivamento do PDF). */
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

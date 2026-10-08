package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Comando de processamento individual de item de webhook recebido da fila de eventos.
 *
 * **Responsabilidade:**
 * - Conter a chave composta de idempotência ([eventId]) no formato `deliveryId:index` e o JSON do item individual ([itemJson]).
 */
data class WebhookItemCommand(val eventId: String, val itemJson: String)

/**
 * Caso de uso assíncrono responsável por traduzir e processar itens individuais de webhooks recebidos do Apache Kafka.
 *
 * **Responsabilidade:**
 * - Traduzir o JSON do item em sinais neutros ([ProviderSignal]) através do gateway agnóstico.
 * - Despachar atualizações de status para o caso de uso [ApplyProviderUpdate] ou ordens de arquivamento para [ArchiveSignedDocument].
 * - Assegurar idempotência estrita via [ProcessedMessagesPort] e descarte seguro de eventos desconhecidos sem falhar a fila.
 */
class ProcessProviderWebhookItem(
    private val gateway: ProviderWebhookGateway,
    private val applyUpdate: ApplyProviderUpdate,
    private val archive: ArchiveSignedDocument,
    private val processed: ProcessedMessagesPort,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: WebhookItemCommand) {
        if (processed.isProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)) return

        val signal = gateway.translate(command.itemJson)
        when (signal) {
            null -> transactions.run {
                log.debug("Item de webhook {} ignorado (evento não utilizado)", command.eventId)
                processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)
            }

            is ProviderSignal.PdfReady -> archive.execute(
                ArchiveSignedDocumentCommand(
                    command.eventId,
                    signal.documentId
                )
            )

            is ProviderSignal.StatusChanged,
            is ProviderSignal.RecipientCompleted,
            is ProviderSignal.CreationFailed,
            is ProviderSignal.DocumentDeleted,
                -> transactions.run {
                if (processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)) {
                    applyUpdate.execute(
                        ApplyProviderUpdateCommand(
                            signals = listOf(signal),
                            source = ChangeSource.WEBHOOK,
                            sourceEventId = command.eventId,
                        ),
                    )
                }
            }
        }
    }
}

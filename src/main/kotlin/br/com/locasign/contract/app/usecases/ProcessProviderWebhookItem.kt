package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/** Item de webhook lido do tópico. [eventId] tem a forma `deliveryId:índice` e é a chave de idempotência. */
data class WebhookItemCommand(val eventId: String, val itemJson: String)

/**
 * Consumidor do tópico de webhooks: traduz o item e o aplica ao contrato (ou arquiva o PDF).
 * Eventos desconhecidos são registrados como processados e ignorados; o corpo bruto já está no inbox.
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
			is ProviderSignal.PdfReady -> archive.execute(ArchiveSignedDocumentCommand(command.eventId, signal.documentId))
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

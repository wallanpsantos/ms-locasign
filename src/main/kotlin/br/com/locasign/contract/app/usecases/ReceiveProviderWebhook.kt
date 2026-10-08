package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.MalformedWebhookPayload
import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.app.ports.OutboxMessage
import br.com.locasign.shared.app.ports.OutboxPort
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.app.ports.WebhookInboxPort
import org.slf4j.LoggerFactory
import java.security.MessageDigest

class ReceiveProviderWebhookCommand(
	val rawBody: ByteArray,
	val signature: String?,
	val deliveryId: String?,
)

sealed interface WebhookReceipt {
	/** Entrega nova gravada no inbox, com [items] itens enfileirados no outbox. */
	data class Accepted(val deliveryId: String, val items: Int) : WebhookReceipt

	/** Entrega já recebida (reenvio manual pelo painel): nenhum efeito novo. */
	data class Duplicate(val deliveryId: String) : WebhookReceipt

	/** Assinatura ausente ou incorreta: o corpo não é gravado. */
	data object InvalidSignature : WebhookReceipt
}

/**
 * Receptor de webhooks (guia, seção 6.2). Valida a assinatura sobre os bytes brutos, grava a entrega
 * no inbox (R7) e enfileira cada item no outbox na mesma transação. O processamento acontece depois, via Kafka.
 */
class ReceiveProviderWebhook(
	private val gateway: ProviderWebhookGateway,
	private val inbox: WebhookInboxPort,
	private val outbox: OutboxPort,
	private val clock: BusinessClock,
	private val metrics: MetricsPort,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun execute(command: ReceiveProviderWebhookCommand): WebhookReceipt {
		if (!gateway.isAuthentic(command.rawBody, command.signature)) {
			metrics.count(Metrics.WEBHOOK_INVALID)
			return WebhookReceipt.InvalidSignature
		}
		val deliveryId = command.deliveryId?.trim()?.takeIf { it.isNotEmpty() } ?: sha256Hex(command.rawBody)
		val rawBody = command.rawBody.toString(Charsets.UTF_8)
		val items = try {
			gateway.splitItems(command.rawBody)
		} catch (e: MalformedWebhookPayload) {
			// R7: o corpo ainda é registrado como chegou, mesmo sem itens aproveitáveis.
			log.warn("Webhook {} com corpo inesperado: {}", deliveryId, e.message)
			emptyList()
		}
		val now = clock.now()

		val isNew = transactions.run {
			val inserted = inbox.store(deliveryId, rawBody)
			if (inserted) {
				items.forEachIndexed { index, item ->
					outbox.append(
						OutboxMessage(
							id = "$deliveryId:$index",
							topic = Topics.PANDADOC_WEBHOOKS,
							// Chave = id do documento: garante ordem por documento dentro da partição.
							key = item.documentId ?: deliveryId,
							aggregateType = AGGREGATE_TYPE,
							aggregateId = item.documentId ?: deliveryId,
							eventType = item.eventName ?: UNKNOWN_EVENT,
							occurredAt = now,
							payloadJson = item.json,
						),
					)
				}
			}
			inserted
		}
		return if (isNew) {
			metrics.count(Metrics.WEBHOOK_RECEIVED)
			WebhookReceipt.Accepted(deliveryId, items.size)
		} else {
			metrics.count(Metrics.WEBHOOK_DUPLICATE)
			WebhookReceipt.Duplicate(deliveryId)
		}
	}

	private fun sha256Hex(bytes: ByteArray): String =
		MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

	private companion object {
		const val AGGREGATE_TYPE = "ProviderWebhook"
		const val UNKNOWN_EVENT = "unknown"
	}
}

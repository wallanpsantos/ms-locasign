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

/**
 * Parâmetros de entrada contendo os bytes brutos e cabeçalhos de uma entrega de webhook do provedor.
 *
 * **Responsabilidade:**
 * - Transportar o payload original não parseado ([rawBody]), a assinatura criptográfica ([signature]) e o identificador de entrega ([deliveryId]).
 */
class ReceiveProviderWebhookCommand(
    val rawBody: ByteArray,
    val signature: String?,
    val deliveryId: String?,
)

/**
 * Resultado da recepção do webhook pelo gateway de entrada da aplicação.
 *
 * **Responsabilidade:**
 * - Diferenciar recepção válida com itens enfileirados ([Accepted]), entrega duplicada descartada ([Duplicate]) e falha de integridade criptográfica ([InvalidSignature]).
 */
sealed interface WebhookReceipt {
    /** Entrega nova gravada no inbox, com [items] itens enfileirados no outbox. */
    data class Accepted(val deliveryId: String, val items: Int) : WebhookReceipt

    /** Entrega já recebida (reenvio manual pelo painel): nenhum efeito novo. */
    data class Duplicate(val deliveryId: String) : WebhookReceipt

    /** Assinatura ausente ou incorreta: o corpo não é gravado. */
    data object InvalidSignature : WebhookReceipt
}

/**
 * Caso de uso síncrono responsável pela recepção, validação HMAC e armazenamento transacional no inbox de webhooks do provedor.
 *
 * **Responsabilidade:**
 * - Validar a assinatura criptográfica em tempo constante sobre os bytes brutos (`raw bytes`) via [ProviderWebhookGateway] antes de qualquer parsing (ADR-012).
 * - Persistir a entrega bruta na tabela de inbox para auditoria forense (Regra R7).
 * - Enfileirar itens decompostos na tabela de outbox na mesma transação atômica e responder HTTP 200 de imediato, garantindo isolamento de processamento via Kafka.
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

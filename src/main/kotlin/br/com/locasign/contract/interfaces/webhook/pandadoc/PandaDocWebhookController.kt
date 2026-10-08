package br.com.locasign.contract.interfaces.webhook.pandadoc

import br.com.locasign.contract.app.usecases.ReceiveProviderWebhook
import br.com.locasign.contract.app.usecases.ReceiveProviderWebhookCommand
import br.com.locasign.contract.app.usecases.WebhookReceipt
import br.com.locasign.shared.app.ports.CorrelationContext
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador REST para recepção de webhooks enviados pelo parceiro externo PandaDoc (guia, seção 6.2).
 *
 * Lê o corpo da requisição estritamente como bytes brutos (`ByteArray`) para viabilizar a conferência da assinatura
 * HMAC sem distorções de serialização JSON. Propaga o identificador de entrega para o contexto de correlação e
 * delega o registro imediato na tabela de inbox ao caso de uso [ReceiveProviderWebhook]. Responde HTTP 200 de forma
 * rápida e idempotente para lotes aceitos ou duplicados, HTTP 401 para assinaturas inválidas e nunca retorna
 * HTTP 410 (para evitar que o provedor desative o endpoint).
 *
 * **Responsabilidade:**
 * - Atuar como adaptador de entrada (driving adapter) HTTP para recepção assíncrona de eventos externos da PandaDoc.
 * - Desacoplar a recepção HTTP do processamento efetivo dos eventos, assegurando conformidade com o padrão de Inbox transacional.
 * - Garantir as regras de segurança e integração com a PandaDoc (verificação HMAC sobre raw bytes e retenção de webhooks).
 */
@RestController
@Hidden
class PandaDocWebhookController(
    private val receiveWebhook: ReceiveProviderWebhook,
    private val correlation: CorrelationContext,
) {

    @PostMapping("/webhooks/pandadoc")
    fun receive(
        @RequestBody(required = false) body: ByteArray?,
        @RequestParam(name = "signature", required = false) signature: String?,
        @RequestHeader(name = DELIVERY_ID_HEADER, required = false) deliveryId: String?,
    ): ResponseEntity<Void> {
        val receipt = correlation.with(deliveryId) {
            receiveWebhook.execute(ReceiveProviderWebhookCommand(body ?: ByteArray(0), signature, deliveryId))
        }
        return when (receipt) {
            is WebhookReceipt.Accepted, is WebhookReceipt.Duplicate -> ResponseEntity.ok().build()
            WebhookReceipt.InvalidSignature -> ResponseEntity.status(UNAUTHORIZED).build()
        }
    }

    private companion object {
        const val DELIVERY_ID_HEADER = "X-PandaDoc-Webhook-Event-Id"
        const val UNAUTHORIZED = 401
    }
}

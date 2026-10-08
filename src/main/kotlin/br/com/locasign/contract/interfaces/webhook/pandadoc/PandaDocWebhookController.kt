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
 * Receptor de webhooks da PandaDoc (guia, seção 6.2).
 *
 * O corpo é lido como **bytes brutos**: reserializar o JSON quebraria a assinatura HMAC. Responde
 * 200 rapidamente (o processamento real acontece depois, via Kafka) e **nunca responde 410**, pois a
 * PandaDoc desativa o webhook ao receber 410. Eventos desconhecidos também recebem 200.
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

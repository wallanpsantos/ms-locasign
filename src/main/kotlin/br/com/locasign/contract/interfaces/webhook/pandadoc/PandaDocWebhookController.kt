package br.com.locasign.contract.interfaces.webhook.pandadoc

import br.com.locasign.contract.app.usecases.ReceiveProviderWebhook
import br.com.locasign.contract.app.usecases.ReceiveProviderWebhookCommand
import br.com.locasign.contract.app.usecases.WebhookReceipt
import br.com.locasign.shared.app.ports.CorrelationContext
import br.com.locasign.shared.interfaces.web.PayloadTooLargeException
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador REST para recepção de webhooks enviados pelo parceiro externo PandaDoc (guia, seção 6.2).
 *
 * Lê o corpo da requisição como bytes brutos (`ByteArray`) e os repassa intactos; quem valida a assinatura HMAC é o
 * [ReceiveProviderWebhook] (`docs/agents/arquitetura.md` §4). Propaga o identificador de entrega para o contexto de correlação e
 * delega o registro imediato na tabela de inbox ao caso de uso [ReceiveProviderWebhook]. Responde HTTP 200 de forma
 * rápida e idempotente para lotes aceitos ou duplicados, HTTP 401 para assinaturas inválidas, HTTP 413 para corpos acima
 * de `locasign.pandadoc.webhook-max-body-bytes` (`PANDADOC_WEBHOOK_MAX_BODY_BYTES`, padrão 1 MiB) e nunca retorna
 * HTTP 410 (para evitar que o provedor desative o endpoint).
 *
 * **Responsabilidade:**
 * - Atuar como adaptador de entrada (driving adapter) HTTP para recepção assíncrona de eventos externos da PandaDoc.
 * - Desacoplar a recepção HTTP do processamento efetivo dos eventos, assegurando conformidade com o padrão de Inbox transacional.
 * - Responder 200 só depois de autenticar o HMAC e gravar a entrega bruta no inbox (R7).
 * - Limitar a memória usada por entrega: rejeitar pelo `Content-Length` quando presente e, sem ele (chunked), ler no máximo
 *   o limite mais um byte, sem alterar os bytes dos corpos aceitos.
 */
@RestController
@Hidden
class PandaDocWebhookController(
    private val receiveWebhook: ReceiveProviderWebhook,
    private val correlation: CorrelationContext,
    @param:Value("\${locasign.pandadoc.webhook-max-body-bytes:1048576}") private val maxBodyBytes: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        check(maxBodyBytes in 1 until Int.MAX_VALUE) {
            "locasign.pandadoc.webhook-max-body-bytes deve ficar entre 1 e ${Int.MAX_VALUE - 1}, mas é $maxBodyBytes."
        }
    }

    @PostMapping("/webhooks/pandadoc")
    fun receive(
        request: HttpServletRequest,
        @RequestParam(name = "signature", required = false) signature: String?,
        @RequestHeader(name = DELIVERY_ID_HEADER, required = false) deliveryId: String?,
    ): ResponseEntity<Void> = correlation.with(deliveryId) {
        val body = readBodyWithinLimit(request)
        when (receiveWebhook.execute(ReceiveProviderWebhookCommand(body, signature, deliveryId))) {
            is WebhookReceipt.Accepted, is WebhookReceipt.Duplicate -> ResponseEntity.ok().build()
            WebhookReceipt.InvalidSignature -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }

    /** Devolve os bytes exatos do corpo (base do HMAC, R7) ou lança [PayloadTooLargeException] acima do limite. */
    private fun readBodyWithinLimit(request: HttpServletRequest): ByteArray {
        val declaredLength = request.contentLengthLong
        if (declaredLength > maxBodyBytes) {
            throw rejectOversized(declaredLength)
        }
        val body = request.inputStream.readNBytes(maxBodyBytes + 1)
        if (body.size > maxBodyBytes) {
            throw rejectOversized(declaredLength)
        }
        return body
    }

    private fun rejectOversized(declaredLength: Long): PayloadTooLargeException {
        log.warn(
            "Webhook da PandaDoc rejeitado: corpo acima de {} bytes (Content-Length: {}).",
            maxBodyBytes,
            declaredLength
        )
        return PayloadTooLargeException(maxBodyBytes)
    }

    private companion object {
        const val DELIVERY_ID_HEADER = "X-PandaDoc-Webhook-Event-Id"
    }
}

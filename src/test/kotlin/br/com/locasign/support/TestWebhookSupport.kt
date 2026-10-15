package br.com.locasign.support

import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utilitário para cálculo de assinatura HMAC-SHA256 em testes de integração e ponta a ponta com webhooks.
 */
object TestWebhookSupport {

    fun calculateHmac(secret: String, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        return HexFormat.of().formatHex(mac.doFinal(data))
    }
}

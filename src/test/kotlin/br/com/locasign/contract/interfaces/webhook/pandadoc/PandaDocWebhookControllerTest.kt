package br.com.locasign.contract.interfaces.webhook.pandadoc

import br.com.locasign.contract.app.usecases.ReceiveProviderWebhook
import br.com.locasign.contract.app.usecases.WebhookReceipt
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.web.ApiExceptionHandler
import br.com.locasign.support.any
import org.assertj.core.api.Assertions.assertThat
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.Test

@WebMvcTest(PandaDocWebhookController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class)
class PandaDocWebhookControllerTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var receiveWebhook: ReceiveProviderWebhook

    private fun postWebhook(signature: String, deliveryId: String? = null): MvcTestResult {
        val request = post("/webhooks/pandadoc")
            .param("signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""[{"event":"document_state_changed"}]""")
        deliveryId?.let { request.header("X-PandaDoc-Webhook-Event-Id", it) }
        return mvc.perform(request)
    }

    @Test
    fun `webhook com assinatura válida responde 200 OK (R7)`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.Accepted("del-1", 1))

        val result = postWebhook(signature = "valid-hmac-signature", deliveryId = "del-1")

        assertThat(result)
            .hasStatus(HttpStatus.OK)
    }

    @Test
    fun `webhook duplicado responde 200 OK de forma idempotente (R7)`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.Duplicate("del-dup"))

        val result = postWebhook(signature = "valid-hmac-signature", deliveryId = "del-dup")

        assertThat(result)
            .hasStatus(HttpStatus.OK)
    }

    @Test
    fun `webhook com assinatura inválida ou ausente responde 401 Unauthorized e nunca 410 (R7)`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.InvalidSignature)

        val result = postWebhook(signature = "wrong-signature")

        assertThat(result)
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }
}

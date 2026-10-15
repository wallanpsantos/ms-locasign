package br.com.locasign.contract.interfaces.webhook.pandadoc

import br.com.locasign.contract.app.usecases.ReceiveProviderWebhook
import br.com.locasign.contract.app.usecases.ReceiveProviderWebhookCommand
import br.com.locasign.contract.app.usecases.WebhookReceipt
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.web.ApiExceptionHandler
import br.com.locasign.support.any
import jakarta.servlet.ServletInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.DelegatingServletInputStream
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@WebMvcTest(PandaDocWebhookController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class)
@TestPropertySource(properties = ["locasign.pandadoc.webhook-max-body-bytes=$MAX_BODY_BYTES"])
class PandaDocWebhookControllerTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var receiveWebhook: ReceiveProviderWebhook

    /** Sem este padrão, um corpo rejeitado por engano apareceria como 500 (mock sem retorno) em vez de 200. */
    @BeforeEach
    fun aceitaPorPadrao() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.Accepted("del-padrao", 1))
    }

    private fun postWebhook(signature: String, deliveryId: String? = null): MvcTestResult {
        val request = post("/webhooks/pandadoc")
            .param("signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""[{"event":"document_state_changed"}]""")
        deliveryId?.let { request.header("X-PandaDoc-Webhook-Event-Id", it) }
        return mvc.perform(request)
    }

    private fun postBody(body: ByteArray): MvcTestResult = mvc.perform(
        post("/webhooks/pandadoc")
            .param("signature", "valid-hmac-signature")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    /**
     * Requisição que declara [declaredLength] (ou -1, como no `Transfer-Encoding: chunked`) e entrega [source],
     * para provar quanto do corpo o controller consome.
     */
    private fun postStream(declaredLength: Long, source: InputStream): MvcTestResult = mvc.perform(
        post("/webhooks/pandadoc").with { original ->
            object : MockHttpServletRequest(original.servletContext, "POST", "/webhooks/pandadoc") {
                override fun getContentLengthLong(): Long = declaredLength
                override fun getContentLength(): Int = declaredLength.toInt()
                override fun getInputStream(): ServletInputStream = DelegatingServletInputStream(source)
            }.apply {
                setParameter("signature", "valid-hmac-signature")
                contentType = MediaType.APPLICATION_JSON_VALUE
            }
        },
    )

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

    @Test
    fun `rejeição por assinatura responde 401 sem corpo (R7)`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.InvalidSignature)

        val result = postWebhook(signature = "wrong-signature")

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
        assertThat(result).body().isEmpty()
    }

    @Test
    fun `corpo com exatamente o limite segue o fluxo normal e responde 200 (R7)`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.Accepted("del-limite", 1))

        val result = postBody(ByteArray(MAX_BODY_BYTES) { 'a'.code.toByte() })

        assertThat(result).hasStatus(HttpStatus.OK)
        verify(receiveWebhook).execute(any())
    }

    @Test
    fun `corpo um byte acima do limite responde 413 e não chama o caso de uso (R7)`() {
        val result = postBody(ByteArray(MAX_BODY_BYTES + 1) { 'a'.code.toByte() })

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE)
        verify(receiveWebhook, never()).execute(any())
    }

    @Test
    fun `corpo acima do limite responde 413 no formato Problem Details (RFC 9457)`() {
        val result = postBody(ByteArray(MAX_BODY_BYTES + 1) { 'a'.code.toByte() })

        val json = assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE).bodyJson()
        json.extractingPath("$.type").isEqualTo("/problems/payload-too-large")
        json.extractingPath("$.status").isEqualTo(413)
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `Content-Length declarado acima do limite responde 413 sem ler o corpo (R7)`() {
        val unreadable = object : InputStream() {
            override fun read(): Int = throw IOException("o corpo não deveria ser lido")
        }

        val result = postStream(declaredLength = MAX_BODY_BYTES + 1L, source = unreadable)

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE)
        verify(receiveWebhook, never()).execute(any())
    }

    @Test
    fun `corpo sem Content-Length acima do limite responde 413 lendo no máximo o limite mais um byte (R7)`() {
        val chunked = ByteArrayInputStream(ByteArray(CHUNKED_BODY_BYTES) { 'a'.code.toByte() })

        val result = postStream(declaredLength = -1, source = chunked)

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE)
        verify(receiveWebhook, never()).execute(any())
        val consumed = CHUNKED_BODY_BYTES - chunked.available()
        assertTrue(
            consumed <= MAX_BODY_BYTES + 1,
            "a leitura deveria parar em ${MAX_BODY_BYTES + 1} bytes, mas leu $consumed"
        )
    }

    @Test
    fun `corpo sem Content-Length dentro do limite chega inteiro ao caso de uso`() {
        given(receiveWebhook.execute(any())).willReturn(WebhookReceipt.Accepted("del-chunked", 1))
        val body = """[{"event":"document_state_changed"}]""".toByteArray(Charsets.UTF_8)

        val result = postStream(declaredLength = -1, source = ByteArrayInputStream(body))

        assertThat(result).hasStatus(HttpStatus.OK)
    }

    @Test
    fun `corpo válido chega ao caso de uso byte a byte idêntico para o HMAC (R7)`() {
        var received: ReceiveProviderWebhookCommand? = null
        given(receiveWebhook.execute(any())).willAnswer { invocation ->
            received = invocation.getArgument(0)
            WebhookReceipt.Accepted("del-bytes", 1)
        }
        // Inclui bytes que um parse ou uma conversão de charset alterariam: NUL, 0xFF, CRLF, espaços e UTF-8 válido.
        val body = byteArrayOf(0x7B, 0x00, 0xFF.toByte(), 0x0D, 0x0A, 0x20, 0x20) +
                "ação 😀".toByteArray(Charsets.UTF_8) +
                byteArrayOf(0x0A, 0x7D, 0x0A)

        val result = postBody(body)

        assertThat(result).hasStatus(HttpStatus.OK)
        val command = assertNotNull(received)
        assertContentEquals(body, command.rawBody)
    }
}

private const val MAX_BODY_BYTES = 64
private const val CHUNKED_BODY_BYTES = 10_000

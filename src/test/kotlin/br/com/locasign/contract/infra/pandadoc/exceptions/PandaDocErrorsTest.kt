package br.com.locasign.contract.infra.pandadoc.exceptions

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class PandaDocErrorsTest {

    private fun httpError(status: Int, body: String = "erro"): RestClientResponseException =
        RestClientResponseException(
            "HTTP $status",
            HttpStatusCode.valueOf(status),
            "",
            null,
            body.toByteArray(),
            Charsets.UTF_8,
        )

    @Test
    fun `404 e 409 viram NotReady`() {
        listOf(404, 409).forEach { status ->
            val error = httpError(status)
            val translated = error.toProviderException("send")
            assertIs<ProviderException.NotReady>(translated)
            assertEquals("PandaDoc send falhou com HTTP $status: erro", translated.message)
            assertSame(error, translated.cause)
        }
    }

    @Test
    fun `429 vira RateLimited`() {
        assertIs<ProviderException.RateLimited>(httpError(429).toProviderException("details"))
    }

    @Test
    fun `403 vira Forbidden`() {
        assertIs<ProviderException.Forbidden>(httpError(403).toProviderException("details"))
    }

    @Test
    fun `401 vira Unavailable com mensagem de credencial`() {
        val translated = httpError(401).toProviderException("download")
        assertIs<ProviderException.Unavailable>(translated)
        assertEquals("PandaDoc download recusou as credenciais (HTTP 401)", translated.message)
    }

    @Test
    fun `demais 4xx, inclusive código fora do padrão, viram Rejected com o status`() {
        listOf(400, 422, 499).forEach { status ->
            val translated = httpError(status).toProviderException("create")
            assertIs<ProviderException.Rejected>(translated)
            assertEquals(status, translated.httpStatus)
        }
    }

    @Test
    fun `5xx vira Unavailable`() {
        val translated = httpError(503).toProviderException("create")
        assertIs<ProviderException.Unavailable>(translated)
        assertEquals("PandaDoc create falhou com HTTP 503: erro", translated.message)
    }

    @Test
    fun `corpo de erro tem e-mails mascarados e tamanho limitado`() {
        val body = "destinatario fulano@example.com invalido " + "x".repeat(500)
        val message = httpError(400, body).toProviderException("create").message.orEmpty()
        assertContains(message, "***@***")
        assertFalse(message.contains("fulano@example.com"))
        assertEquals("PandaDoc create falhou com HTTP 400: ".length + 200, message.length)
    }

    @Test
    fun `falha de conexão vira Unavailable`() {
        val translated = ResourceAccessException("timeout ao falar com a@b.com").toProviderException("send")
        assertIs<ProviderException.Unavailable>(translated)
        assertEquals("PandaDoc send indisponível: timeout ao falar com ***@***", translated.message)
    }

    @Test
    fun `outra falha do cliente vira Unavailable genérica`() {
        val translated = RestClientException("conversor ausente").toProviderException("details")
        assertIs<ProviderException.Unavailable>(translated)
        assertEquals("PandaDoc details falhou: conversor ausente", translated.message)
    }
}

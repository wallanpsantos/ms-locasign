package br.com.locasign.shared.interfaces.web

import br.com.locasign.shared.app.DeadLetterReplayPort
import br.com.locasign.shared.app.Topics
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.Test
import kotlin.test.assertEquals

@WebMvcTest(AdminController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class, OperatorAccessGuard::class)
@TestPropertySource(properties = ["locasign.admin.token=segredo-admin-teste"])
class AdminControllerTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var deadLetters: DeadLetterReplayPort

    @Test
    fun `requisicao sem token de operador e rejeitada com HTTP 403 Forbidden`() {
        val response = mvc.perform(post("/api/v1/admin/dead-letters/${Topics.CONTRACT_EVENTS}/replay")).response

        assertEquals(403, response.status)
    }

    @Test
    fun `requisicao com token invalido e rejeitada com HTTP 403 Forbidden`() {
        val response = mvc.perform(
            post("/api/v1/admin/dead-letters/${Topics.CONTRACT_EVENTS}/replay")
                .header("X-Admin-Token", "token-incorreto"),
        ).response

        assertEquals(403, response.status)
    }

    @Test
    fun `requisicao com token valido executa replay e devolve HTTP 200 com total reprocessado`() {
        val topic = Topics.CONTRACT_EVENTS
        given(deadLetters.replay(topic)).willReturn(3)

        val result = mvc.perform(
            post("/api/v1/admin/dead-letters/$topic/replay")
                .header("X-Admin-Token", "segredo-admin-teste")
                .accept(MediaType.APPLICATION_JSON),
        )

        assertEquals(200, result.response.status)
        assertEquals("""{"topic":"$topic","replayed":3}""", result.response.contentAsString)
    }

    @Test
    fun `topico inexistente lanca NotFound e responde HTTP 404 Problem Details`() {
        val invalidTopic = "topico-inexistente"
        given(deadLetters.replay(invalidTopic)).willThrow(DomainException.NotFound("Tópico", invalidTopic))

        val result = mvc.perform(
            post("/api/v1/admin/dead-letters/$invalidTopic/replay")
                .header("X-Admin-Token", "segredo-admin-teste")
                .accept(MediaType.APPLICATION_JSON),
        )

        assertEquals(404, result.response.status)
    }
}

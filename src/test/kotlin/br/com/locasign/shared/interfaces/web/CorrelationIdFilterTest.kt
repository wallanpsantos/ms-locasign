package br.com.locasign.shared.interfaces.web

import br.com.locasign.lease.app.queries.GetLease
import br.com.locasign.lease.app.usecases.RegisterLease
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.interfaces.web.LeaseController
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** O filtro devolve o `X-Correlation-Id` recebido ou gera um UUID quando ele falta ou passa de 100 caracteres. */
@WebMvcTest(LeaseController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class)
class CorrelationIdFilterTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var registerLease: RegisterLease

    @MockitoBean
    private lateinit var getLease: GetLease

    private val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    private fun correlationIdReturnedFor(sent: String?): String {
        val leaseId = LeaseId.new()
        given(getLease.execute(leaseId)).willThrow(DomainException.NotFound("Locação", leaseId.toString()))
        val request = get("/api/v1/leases/$leaseId")
        sent?.let { request.header("X-Correlation-Id", it) }
        return assertNotNull(mvc.perform(request).response.getHeader("X-Correlation-Id"))
    }

    @Test
    fun `X-Correlation-Id recebido volta igual na resposta, inclusive com 100 caracteres`() {
        assertEquals("corr-123", correlationIdReturnedFor("corr-123"))
        val limit = "c".repeat(100)
        assertEquals(limit, correlationIdReturnedFor("  $limit  "))
    }

    @Test
    fun `X-Correlation-Id ausente, em branco ou maior que 100 caracteres é trocado por um UUID gerado`() {
        listOf(null, "   ", "c".repeat(101)).forEach { sent ->
            val returned = correlationIdReturnedFor(sent)
            assertTrue(uuidPattern.matches(returned), "'$returned' deveria ser um UUID")
            assertNotEquals(sent, returned)
        }
    }
}

package br.com.locasign.lease.interfaces.web

import br.com.locasign.lease.app.queries.GetLease
import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.app.usecases.RegisterLease
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

@WebMvcTest(LeaseController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class)
class LeaseControllerTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var registerLease: RegisterLease

    @MockitoBean
    private lateinit var getLease: GetLease

    private val validRequestJson = """
        {
            "tenant": {
                "name": "Maria Silva",
                "cpf": "529.982.247-25",
                "email": "maria@example.com"
            },
            "property": {
                "address": "Av Paulista, 1000"
            },
            "rentAmount": "3500.00",
            "startDate": "2026-11-01",
            "termMonths": 30
        }
    """.trimIndent()

    private fun postLease(json: String): MvcTestResult = mvc.perform(
        post("/api/v1/leases")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json),
    )

    @Test
    fun `cadastro de locação válido responde 201 Created com cabeçalho Location (R2)`() {
        val createdId = LeaseId.new()
        given(registerLease.execute(any())).willReturn(createdId)

        val result = postLease(validRequestJson)

        assertThat(result)
            .hasStatus(HttpStatus.CREATED)
            .hasHeader("Location", "http://localhost/api/v1/leases/$createdId")
            .bodyJson()
            .extractingPath("$.status").isEqualTo("REGISTERED")
    }

    @Test
    fun `requisição com campos obrigatórios ausentes dispara Bean Validation e responde 400 com problems validation (R2)`() {
        val invalidJson = """
            {
                "tenant": {
                    "name": "",
                    "cpf": "",
                    "email": ""
                },
                "property": {
                    "address": ""
                },
                "rentAmount": "",
                "startDate": "2026-11-01"
            }
        """.trimIndent()

        val result = postLease(invalidJson)

        assertThat(result)
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.type").isEqualTo("/problems/validation")
    }

    @Test
    fun `violação de regra de negócio no domínio responde 422 com problems business-rule`() {
        given(registerLease.execute(any())).willThrow(
            DomainException.BusinessRuleViolation(
                "startDate",
                "A data de início deve ser igual ou posterior à data do cadastro."
            )
        )

        val result = postLease(validRequestJson)

        assertThat(result)
            .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
            .bodyJson()
            .extractingPath("$.type").isEqualTo("/problems/business-rule")
    }

    @Test
    fun `consulta de locação por id inexistente responde 404 com problems not-found`() {
        val nonExistentId = LeaseId.new()
        given(getLease.execute(nonExistentId)).willThrow(
            DomainException.NotFound("Locação", nonExistentId.toString())
        )

        val result = mvc.perform(get("/api/v1/leases/$nonExistentId"))

        assertThat(result)
            .hasStatus(HttpStatus.NOT_FOUND)
            .bodyJson()
            .extractingPath("$.type").isEqualTo("/problems/not-found")
    }

    @Test
    fun `consulta de locação por id existente responde 200 com os dados detalhados`() {
        val leaseId = LeaseId.new()
        val detail = LeaseDetailView(
            id = leaseId.toString(),
            status = "REGISTERED",
            tenantName = "Maria Silva",
            tenantCpfMasked = "***.982.247-**",
            tenantEmail = "maria@example.com",
            agencySignerName = "Imobiliária Central",
            agencySignerEmail = "contato@imobiliaria.com",
            propertyAddress = "Av Paulista, 1000",
            rentAmount = "3500.00",
            startDate = LocalDate.of(2026, 11, 1),
            termMonths = 30,
            createdAt = Instant.parse("2026-10-09T10:00:00Z"),
            activatedAt = null,
            currentContract = null,
        )
        given(getLease.execute(leaseId)).willReturn(detail)

        val result = mvc.perform(get("/api/v1/leases/$leaseId"))

        assertThat(result)
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.tenant.name").isEqualTo("Maria Silva")
    }
}

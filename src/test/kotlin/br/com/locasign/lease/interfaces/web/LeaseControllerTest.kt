package br.com.locasign.lease.interfaces.web

import br.com.locasign.lease.app.ports.`in`.GetLeasePort
import br.com.locasign.lease.app.ports.`in`.RegisterLeasePort
import br.com.locasign.lease.app.queries.CurrentContractView
import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.web.ApiExceptionHandler
import br.com.locasign.support.any
import org.assertj.core.api.Assertions.assertThat
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
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
    private lateinit var registerLease: RegisterLeasePort

    @MockitoBean
    private lateinit var getLease: GetLeasePort

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
        given(getLease.execute(leaseId)).willReturn(leaseDetail(leaseId))

        val result = mvc.perform(get("/api/v1/leases/$leaseId"))

        assertThat(result)
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.tenant.name").isEqualTo("Maria Silva")
    }

    private fun leaseDetail(leaseId: LeaseId, currentContract: CurrentContractView? = null) = LeaseDetailView(
        id = leaseId.toString(),
        status = "REGISTERED",
        tenantName = "Maria Silva",
        tenantCpfMasked = "***.982.247-**",
        tenantEmail = "maria@example.com",
        agencySignerName = "Imobiliária Central",
        agencySignerEmail = "contato@example.com",
        propertyAddress = "Av Paulista, 1000",
        rentAmount = "3500.00",
        startDate = LocalDate.of(2026, 11, 1),
        termMonths = 30,
        createdAt = Instant.parse("2026-10-09T10:00:00Z"),
        activatedAt = null,
        currentContract = currentContract,
    )

    @Test
    fun `consulta de locação com contrato corrente devolve id, versão, status e expiração do contrato`() {
        val leaseId = LeaseId.new()
        val current = CurrentContractView("contrato-v2", 2, "SENT", Instant.parse("2026-10-16T10:00:00Z"))
        given(getLease.execute(leaseId)).willReturn(leaseDetail(leaseId, current))

        val json = assertThat(mvc.perform(get("/api/v1/leases/$leaseId"))).hasStatus(HttpStatus.OK).bodyJson()

        json.extractingPath("$.currentContract.id").isEqualTo("contrato-v2")
        json.extractingPath("$.currentContract.versionNumber").isEqualTo(2)
        json.extractingPath("$.currentContract.status").isEqualTo("SENT")
        json.extractingPath("$.currentContract.expiresAt").isEqualTo("2026-10-16T10:00:00Z")
    }

    @Test
    fun `JSON malformado responde 400 problems validation com o detalhe de corpo ilegível`() {
        val json = assertThat(postLease("{")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()

        json.extractingPath("$.type").isEqualTo("/problems/validation")
        json.extractingPath("$.title").isEqualTo("Requisição inválida")
        json.extractingPath("$.status").isEqualTo(400)
        json.extractingPath("$.detail")
            .isEqualTo("Corpo da requisição ilegível: verifique o JSON, os tipos dos campos e os campos obrigatórios.")
    }

    @Test
    fun `campos inválidos devolvem a lista errors com campo e mensagem de cada erro`() {
        val invalidJson = """
            {
                "tenant": { "name": "Maria Silva", "cpf": "", "email": "maria@example.com" },
                "property": { "address": "" },
                "rentAmount": "",
                "startDate": "2026-11-01"
            }
        """.trimIndent()

        val json = assertThat(postLease(invalidJson)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()

        json.extractingPath("$.title").isEqualTo("Requisição inválida")
        json.extractingPath("$.detail").isEqualTo("Um ou mais campos são inválidos.")
        json.extractingPath("$.errors[*].field").asArray()
            .containsExactlyInAnyOrder("tenant.cpf", "property.address", "rentAmount")
        json.extractingPath("$.errors[0].message").asString().isNotBlank()
    }

    @Test
    fun `regra de negócio responde 422 com título, detalhe e o campo do JSON`() {
        val message = "A data de início deve ser igual ou posterior à data do cadastro."
        given(registerLease.execute(any())).willThrow(DomainException.BusinessRuleViolation("startDate", message))

        val json = assertThat(postLease(validRequestJson)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson()

        json.extractingPath("$.title").isEqualTo("Regra de negócio violada")
        json.extractingPath("$.detail").isEqualTo(message)
        json.extractingPath("$.field").isEqualTo("startDate")
        json.extractingPath("$.status").isEqualTo(422)
    }

    @Test
    fun `recurso inexistente responde com título e detalhe de não encontrado`() {
        val leaseId = LeaseId.new()
        given(getLease.execute(leaseId)).willThrow(DomainException.NotFound("Locação", leaseId.toString()))

        val json = assertThat(mvc.perform(get("/api/v1/leases/$leaseId"))).hasStatus(HttpStatus.NOT_FOUND).bodyJson()

        json.extractingPath("$.title").isEqualTo("Recurso não encontrado")
        json.extractingPath("$.detail").isEqualTo("Locação não encontrado(a): $leaseId")
    }

    @Test
    fun `atualização concorrente responde 409 problems concurrent-update`() {
        val leaseId = LeaseId.new()
        given(getLease.execute(leaseId)).willThrow(OptimisticLockingFailureException("versão antiga"))

        val json = assertThat(mvc.perform(get("/api/v1/leases/$leaseId"))).hasStatus(HttpStatus.CONFLICT).bodyJson()

        json.extractingPath("$.type").isEqualTo("/problems/concurrent-update")
        json.extractingPath("$.title").isEqualTo("Atualização concorrente")
        json.extractingPath("$.detail").isEqualTo("O recurso foi alterado por outra operação. Tente novamente.")
    }

    @Test
    fun `erro inesperado responde 500 problems internal sem expor a causa`() {
        val leaseId = LeaseId.new()
        given(getLease.execute(leaseId)).willThrow(IllegalStateException("detalhe interno"))

        val json = assertThat(mvc.perform(get("/api/v1/leases/$leaseId")))
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyJson()

        json.extractingPath("$.type").isEqualTo("/problems/internal")
        json.extractingPath("$.title").isEqualTo("Erro interno")
        json.extractingPath("$.detail").isEqualTo("Erro inesperado ao processar a requisição.")
    }

    private fun leaseJson(
        rentAmount: String = "3500.00",
        cpf: String = "529.982.247-25",
        email: String = "maria@example.com",
        agencySignerEmail: String? = null,
    ): String {
        val agencySigner = agencySignerEmail
            ?.let { """"agencySigner": { "name": "Imobiliária Central", "email": "$it" },""" }
            .orEmpty()
        return """
            {
                "tenant": { "name": "Maria Silva", "cpf": "$cpf", "email": "$email" },
                $agencySigner
                "property": { "address": "Av Paulista, 1000" },
                "rentAmount": "$rentAmount",
                "startDate": "2026-11-01"
            }
        """.trimIndent()
    }

    @Test
    fun `rentAmount com mais de 13 caracteres responde 400 e não chama o caso de uso (L1)`() {
        val json = assertThat(postLease(leaseJson(rentAmount = "1".repeat(14))))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()

        json.extractingPath("$.type").isEqualTo("/problems/validation")
        json.extractingPath("$.errors[*].field").asArray().containsExactly("rentAmount")
        verifyNoInteractions(registerLease)
    }

    @Test
    fun `texto gigante em rentAmount responde 400 sem chegar ao caso de uso (L1)`() {
        assertThat(postLease(leaseJson(rentAmount = "1".repeat(1_000_000))))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.errors[*].field").asArray().containsExactly("rentAmount")
        verifyNoInteractions(registerLease)
    }

    @Test
    fun `cpf com mais de 14 caracteres responde 400 e não chama o caso de uso (L1)`() {
        val json = assertThat(postLease(leaseJson(cpf = "5".repeat(15))))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()

        json.extractingPath("$.errors[*].field").asArray().containsExactly("tenant.cpf")
        verifyNoInteractions(registerLease)
    }

    @Test
    fun `e-mail do locatário ou da imobiliária com mais de 254 caracteres responde 400 e não chama o caso de uso (L1)`() {
        val tooLongEmail = "a".repeat(243) + "@example.com"

        val json = assertThat(postLease(leaseJson(email = tooLongEmail, agencySignerEmail = tooLongEmail)))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()

        json.extractingPath("$.errors[*].field").asArray()
            .containsExactlyInAnyOrder("tenant.email", "agencySigner.email")
        verifyNoInteractions(registerLease)
    }

    @Test
    fun `campos exatamente nos limites de tamanho (13, 14 e 254) seguem para o caso de uso (L1)`() {
        given(registerLease.execute(any())).willReturn(LeaseId.new())
        val emailAtLimit = "a".repeat(242) + "@example.com"

        val result = postLease(
            leaseJson(
                rentAmount = "9999999999.99",
                cpf = "529.982.247-25",
                email = emailAtLimit,
                agencySignerEmail = emailAtLimit,
            ),
        )

        assertThat(result).hasStatus(HttpStatus.CREATED)
    }
}

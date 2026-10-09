package br.com.locasign.contract.interfaces.web

import br.com.locasign.contract.app.queries.GetContract
import br.com.locasign.contract.app.queries.GetContractHistory
import br.com.locasign.contract.app.usecases.CancelContract
import br.com.locasign.contract.app.usecases.ReconcileContracts
import br.com.locasign.contract.app.usecases.RequestContract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import br.com.locasign.shared.interfaces.web.ApiExceptionHandler
import br.com.locasign.shared.interfaces.web.OperatorAccessGuard
import br.com.locasign.support.any
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import kotlin.test.Test

@WebMvcTest(ContractController::class)
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class, OperatorAccessGuard::class)
class ContractControllerTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var requestContract: RequestContract

    @MockitoBean
    private lateinit var getContract: GetContract

    @MockitoBean
    private lateinit var getContractHistory: GetContractHistory

    @MockitoBean
    private lateinit var cancelContract: CancelContract

    @MockitoBean
    private lateinit var reconcileContracts: ReconcileContracts

    @Test
    fun `solicitação de contrato responde 202 Accepted com cabeçalho Location (R1)`() {
        val leaseId = LeaseId.new()
        val contractId = ContractId.new()
        given(requestContract.execute(any())).willReturn(contractId)

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/leases/${leaseId}/contracts")
        )

        org.assertj.core.api.Assertions.assertThat(result)
            .hasStatus(HttpStatus.ACCEPTED)
            .hasHeader("Location", "http://localhost/api/v1/contracts/${contractId}")
            .bodyJson()
            .extractingPath("$.status").isEqualTo("DRAFT")
    }

    @Test
    fun `solicitação rejeitada por contrato ativo responde 409 com problems active-contract-exists (R1)`() {
        val leaseId = LeaseId.new()
        given(requestContract.execute(any())).willThrow(
            DomainException.ActiveContractExists(
                leaseId.toString(),
                "Já existe um contrato em andamento para esta locação. Cancele-o antes de gerar uma nova versão."
            )
        )

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/leases/${leaseId}/contracts")
        )

        org.assertj.core.api.Assertions.assertThat(result)
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson()
            .extractingPath("$.type").isEqualTo("/problems/active-contract-exists")
    }

    @Test
    fun `cancelamento de contrato responde 202 Accepted com status CANCELLED`() {
        val contractId = ContractId.new()

        val cancelJson = """
            {
                "reason": "Desistência por parte do locatário"
            }
        """.trimIndent()

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/${contractId}/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelJson)
        )

        org.assertj.core.api.Assertions.assertThat(result)
            .hasStatus(HttpStatus.ACCEPTED)
            .bodyJson()
            .extractingPath("$.status").isEqualTo("CANCELLED")
    }

    @Test
    fun `reconciliação sem token de operador responde 403 Forbidden`() {
        val contractId = ContractId.new()

        val result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/contracts/${contractId}/reconcile")
        )

        org.assertj.core.api.Assertions.assertThat(result)
            .hasStatus(HttpStatus.FORBIDDEN)
    }
}

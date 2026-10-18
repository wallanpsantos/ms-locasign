package br.com.locasign.shared.interfaces.web

import br.com.locasign.contract.app.ports.`in`.CancelContractPort
import br.com.locasign.contract.app.ports.`in`.GetContractHistoryPort
import br.com.locasign.contract.app.ports.`in`.GetContractPort
import br.com.locasign.contract.app.ports.`in`.ReconcileContractPort
import br.com.locasign.contract.app.ports.`in`.ReconciledContractSummary
import br.com.locasign.contract.app.ports.`in`.RequestContractPort
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.interfaces.web.ContractController
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.infra.observability.MdcCorrelationContext
import org.assertj.core.api.Assertions.assertThat
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.Test

/** Com `ADMIN_TOKEN` configurado, só o header `X-Admin-Token` com o mesmo valor libera a reconciliação (ADR-013). */
@WebMvcTest(controllers = [ContractController::class], properties = ["locasign.admin.token=token-de-teste"])
@Import(MdcCorrelationContext::class, ApiExceptionHandler::class, OperatorAccessGuard::class)
class OperatorAccessGuardTest(@Autowired private val mvc: MockMvcTester) {

    @MockitoBean
    private lateinit var requestContract: RequestContractPort

    @MockitoBean
    private lateinit var getContract: GetContractPort

    @MockitoBean
    private lateinit var getContractHistory: GetContractHistoryPort

    @MockitoBean
    private lateinit var cancelContract: CancelContractPort

    @MockitoBean
    private lateinit var reconcileContracts: ReconcileContractPort

    private fun reconcile(contractId: ContractId, token: String) =
        mvc.perform(post("/api/v1/contracts/$contractId/reconcile").header("X-Admin-Token", token))

    @Test
    fun `reconciliação com o token de operador correto é aceita (ADR-013)`() {
        val contractId = ContractId.new()
        val leaseId = LeaseId.new()
        given(reconcileContracts.executeFor(contractId)).willReturn(
            ReconciledContractSummary(leaseId, ContractStatus.SENT)
        )

        assertThat(reconcile(contractId, "token-de-teste")).hasStatus(HttpStatus.ACCEPTED)
    }

    @Test
    fun `reconciliação com token de operador diferente responde 403 (ADR-013)`() {
        assertThat(reconcile(ContractId.new(), "outro-token")).hasStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `reconciliação sem o header X-Admin-Token responde 403 mesmo com o token configurado (ADR-013)`() {
        val result = mvc.perform(post("/api/v1/contracts/${ContractId.new()}/reconcile"))

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
    }
}

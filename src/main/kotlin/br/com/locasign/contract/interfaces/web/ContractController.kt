package br.com.locasign.contract.interfaces.web

import br.com.locasign.contract.app.queries.GetContract
import br.com.locasign.contract.app.queries.GetContractHistory
import br.com.locasign.contract.app.usecases.CancelContract
import br.com.locasign.contract.app.usecases.CancelContractCommand
import br.com.locasign.contract.app.usecases.ReconcileContracts
import br.com.locasign.contract.app.usecases.RequestContract
import br.com.locasign.contract.app.usecases.RequestContractCommand
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.interfaces.web.dto.request.CancelContractRequest
import br.com.locasign.contract.interfaces.web.dto.response.ContractAcceptedResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractHistoryResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractResponse
import br.com.locasign.contract.interfaces.web.mappers.toResponse
import br.com.locasign.contract.interfaces.web.openapi.ContractApi
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.interfaces.web.OperatorAccessGuard
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

/**
 * Operações assíncronas respondem 202 com o `Location` do recurso. Nenhuma chamada ao provedor
 * acontece na requisição: a geração e o envio rodam nos consumidores Kafka (princípio 2).
 */
@RestController
@RequestMapping("/api/v1")
class ContractController(
	private val requestContract: RequestContract,
	private val getContract: GetContract,
	private val getContractHistory: GetContractHistory,
	private val cancelContract: CancelContract,
	private val reconcileContracts: ReconcileContracts,
	private val operatorAccess: OperatorAccessGuard,
) : ContractApi {

	@PostMapping("/leases/{leaseId}/contracts")
	override fun request(@PathVariable leaseId: String): ResponseEntity<ContractAcceptedResponse> {
		val lease = LeaseId.parse(leaseId)
		val id = requestContract.execute(RequestContractCommand(lease))
		return accepted(id, lease.toString(), ContractStatus.DRAFT.name)
	}

	@GetMapping("/contracts/{contractId}")
	override fun get(@PathVariable contractId: String): ContractResponse =
		getContract.execute(ContractId.parse(contractId)).toResponse()

	@GetMapping("/contracts/{contractId}/history")
	override fun history(@PathVariable contractId: String): ContractHistoryResponse {
		val id = ContractId.parse(contractId)
		return getContractHistory.execute(id).toResponse(id.toString())
	}

	@PostMapping("/contracts/{contractId}/cancel")
	override fun cancel(
		@PathVariable contractId: String,
		@Valid @RequestBody request: CancelContractRequest,
	): ResponseEntity<ContractAcceptedResponse> {
		val id = ContractId.parse(contractId)
		cancelContract.execute(CancelContractCommand(id, request.reason))
		return accepted(id, leaseId = null, status = ContractStatus.CANCELLED.name)
	}

	@PostMapping("/contracts/{contractId}/reconcile")
	override fun reconcile(@PathVariable contractId: String): ResponseEntity<ContractAcceptedResponse> {
		// Consome cota da PandaDoc (limite de requisições por minuto): restrito a operadores.
		operatorAccess.requireOperator()
		val id = ContractId.parse(contractId)
		reconcileContracts.executeFor(id)
		val current = getContract.execute(id)
		return accepted(id, current.leaseId, current.status)
	}

	private fun accepted(id: ContractId, leaseId: String?, status: String): ResponseEntity<ContractAcceptedResponse> =
		ResponseEntity.accepted()
			.location(contractLocation(id))
			.body(ContractAcceptedResponse(id.toString(), leaseId, status))

	private fun contractLocation(id: ContractId): URI =
		ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/contracts/{id}").buildAndExpand(id.toString()).toUri()
}

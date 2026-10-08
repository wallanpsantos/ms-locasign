package br.com.locasign.lease.interfaces.web.openapi

import br.com.locasign.lease.interfaces.web.dto.request.CreateLeaseRequest
import br.com.locasign.lease.interfaces.web.dto.response.LeaseCreatedResponse
import br.com.locasign.lease.interfaces.web.dto.response.LeaseResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

/** Documentação OpenAPI separada do controller, que fica só com o roteamento. */
@Tag(name = "Locações", description = "Cadastro e consulta de locações")
interface LeaseApi {

    @Operation(summary = "Cadastra uma locação")
    @ApiResponse(responseCode = "201", description = "Locação cadastrada")
    @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou malformado")
    @ApiResponse(responseCode = "422", description = "Regra de negócio violada (CPF inválido, data passada...)")
    fun register(request: CreateLeaseRequest): ResponseEntity<LeaseCreatedResponse>

    @Operation(summary = "Consulta a locação e o contrato atual")
    @ApiResponse(responseCode = "200", description = "Locação encontrada")
    @ApiResponse(responseCode = "404", description = "Locação inexistente")
    fun get(leaseId: String): LeaseResponse
}

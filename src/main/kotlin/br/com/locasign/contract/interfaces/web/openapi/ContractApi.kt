package br.com.locasign.contract.interfaces.web.openapi

import br.com.locasign.contract.interfaces.web.dto.request.CancelContractRequest
import br.com.locasign.contract.interfaces.web.dto.response.ContractAcceptedResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractHistoryResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

/** Documentação OpenAPI separada do controller, que fica só com o roteamento. */
@Tag(name = "Contratos", description = "Geração, acompanhamento e cancelamento de contratos")
interface ContractApi {

    @Operation(
        summary = "Solicita o contrato da locação (nova versão)",
        description = "Responde 202: a geração e o envio acontecem de forma assíncrona. Consulte o contrato para acompanhar.",
    )
    @ApiResponse(responseCode = "202", description = "Contrato aceito em DRAFT")
    @ApiResponse(responseCode = "404", description = "Locação inexistente")
    @ApiResponse(responseCode = "409", description = "Já existe contrato em andamento ou concluído (R1)")
    fun request(leaseId: String): ResponseEntity<ContractAcceptedResponse>

    @Operation(summary = "Status atual, signatários e prazos do contrato")
    @ApiResponse(responseCode = "200", description = "Contrato encontrado")
    @ApiResponse(responseCode = "404", description = "Contrato inexistente")
    fun get(contractId: String): ContractResponse

    @Operation(summary = "Linha do tempo completa, incluindo transições ignoradas")
    @ApiResponse(responseCode = "200", description = "Histórico do contrato")
    @ApiResponse(responseCode = "404", description = "Contrato inexistente")
    fun history(contractId: String): ContractHistoryResponse

    @Operation(summary = "Cancela o contrato (motivo obrigatório)")
    @ApiResponse(responseCode = "202", description = "Cancelamento registrado")
    @ApiResponse(responseCode = "404", description = "Contrato inexistente")
    @ApiResponse(responseCode = "409", description = "Contrato em estado final")
    fun cancel(contractId: String, request: CancelContractRequest): ResponseEntity<ContractAcceptedResponse>

    @Operation(
        summary = "Força a reconciliação com o provedor (desenvolvimento e operação)",
        description = "Consulta o status do documento na PandaDoc e aplica a diferença, como se o webhook tivesse chegado.",
    )
    @ApiResponse(responseCode = "202", description = "Reconciliação executada")
    @ApiResponse(responseCode = "404", description = "Contrato inexistente")
    fun reconcile(contractId: String): ResponseEntity<ContractAcceptedResponse>
}

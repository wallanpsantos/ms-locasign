package br.com.locasign.contract.infra.pandadoc

import br.com.locasign.contract.infra.pandadoc.dto.request.CreateDocumentRequest
import br.com.locasign.contract.infra.pandadoc.dto.request.SendDocumentRequest
import br.com.locasign.contract.infra.pandadoc.dto.request.StatusChangeRequest
import br.com.locasign.contract.infra.pandadoc.dto.response.DocumentDetailsResponse
import br.com.locasign.contract.infra.pandadoc.dto.response.DocumentResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PatchExchange
import org.springframework.web.service.annotation.PostExchange

/**
 * Cliente declarativo HTTP para integração com a API v1 da PandaDoc via Spring HTTP Interfaces (ADR-004).
 *
 * **Responsabilidade:**
 * - Definir os contratos das chamadas REST remotas (criação de documentos a partir de template, consulta de status, envio de links, alteração manual de status e download protegido de PDF).
 * - Delegar a execução para o [org.springframework.web.client.RestClient] pré-configurado com autenticação via `API-Key`.
 */
@HttpExchange(accept = ["application/json"])
interface PandaDocClient {

    @PostExchange("/documents", contentType = "application/json")
    fun createDocument(@RequestBody body: CreateDocumentRequest): DocumentResponse

    @GetExchange("/documents/{id}/details")
    fun details(@PathVariable id: String): DocumentDetailsResponse

    @PostExchange("/documents/{id}/send", contentType = "application/json")
    fun send(@PathVariable id: String, @RequestBody body: SendDocumentRequest): DocumentResponse

    @PatchExchange("/documents/{id}/status", contentType = "application/json")
    fun changeStatus(@PathVariable id: String, @RequestBody body: StatusChangeRequest): ResponseEntity<Void>

    /** 200 com o PDF, ou 202 enquanto o arquivo ainda é gerado. Exige chave de produção. */
    @GetExchange("/documents/{id}/download-protected", accept = ["application/pdf", "application/octet-stream"])
    fun downloadProtected(@PathVariable id: String): ResponseEntity<ByteArray>
}

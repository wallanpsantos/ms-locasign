package br.com.locasign.contract.infra.pandadoc.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/** `POST /public/v1/documents`: criação a partir de template. */
data class CreateDocumentRequest(
    val name: String,
    @param:JsonProperty("template_uuid") val templateUuid: String,
    val recipients: List<RecipientRequest>,
    val tokens: List<TokenRequest>,
    /** Pares chave-valor devolvidos nos webhooks; usados para correlacionar o documento ao contrato. */
    val metadata: Map<String, String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecipientRequest(
    val email: String,
    @param:JsonProperty("first_name") val firstName: String,
    @param:JsonProperty("last_name") val lastName: String?,
    /** Precisa bater exatamente com o nome da role no modelo. */
    val role: String,
    @param:JsonProperty("signing_order") val signingOrder: Int,
)

data class TokenRequest(val name: String, val value: String)

/** `POST /public/v1/documents/{id}/send`. */
data class SendDocumentRequest(val subject: String, val message: String, val silent: Boolean = false)

/** `PATCH /public/v1/documents/{id}/status`: `11` = `document.voided`. */
data class StatusChangeRequest(
    val status: Int,
    val note: String,
    @param:JsonProperty("notify_recipients") val notifyRecipients: Boolean = false,
)

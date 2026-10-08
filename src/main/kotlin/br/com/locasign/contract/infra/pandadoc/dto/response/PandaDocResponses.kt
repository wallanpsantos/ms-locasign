package br.com.locasign.contract.infra.pandadoc.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** Resposta de criação e de envio: `id`, `status` (por exemplo `document.uploaded`) e `date_modified`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentResponse(
	val id: String,
	val status: String? = null,
	@param:JsonProperty("date_modified") val dateModified: String? = null,
)

/** `GET /public/v1/documents/{id}/details`: estado do documento e de cada destinatário. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentDetailsResponse(
	val id: String,
	val status: String? = null,
	@param:JsonProperty("date_modified") val dateModified: String? = null,
	val recipients: List<RecipientResponse>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipientResponse(
	val email: String? = null,
	val role: String? = null,
	@param:JsonProperty("has_completed") val hasCompleted: Boolean? = null,
)

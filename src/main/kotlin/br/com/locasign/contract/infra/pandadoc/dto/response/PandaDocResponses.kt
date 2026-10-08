package br.com.locasign.contract.infra.pandadoc.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO de resposta retornado pela PandaDoc após operações de criação e envio de documentos.
 *
 * **Responsabilidade:**
 * - Desserializar o identificador gerado (`id`), o status imediato do documento e a data de modificação (`date_modified`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentResponse(
    val id: String,
    val status: String? = null,
    @param:JsonProperty("date_modified") val dateModified: String? = null,
)

/**
 * DTO de resposta retornado pela consulta detalhada `GET /public/v1/documents/{id}/details` da PandaDoc.
 *
 * **Responsabilidade:**
 * - Desserializar os dados completos do documento, seu status consolidado e o progresso individual de cada signatário.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentDetailsResponse(
    val id: String,
    val status: String? = null,
    @param:JsonProperty("date_modified") val dateModified: String? = null,
    val recipients: List<RecipientResponse>? = null,
)

/**
 * DTO que reflete o estado e os dados de um signatário retornado pela consulta de detalhes da PandaDoc.
 *
 * **Responsabilidade:**
 * - Indicar se o signatário já realizou a assinatura eletrônica do documento (`has_completed`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipientResponse(
    val email: String? = null,
    val role: String? = null,
    @param:JsonProperty("has_completed") val hasCompleted: Boolean? = null,
)

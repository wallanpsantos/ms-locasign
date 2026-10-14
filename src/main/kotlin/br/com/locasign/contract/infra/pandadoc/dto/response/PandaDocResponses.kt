package br.com.locasign.contract.infra.pandadoc.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO de resposta retornado pela PandaDoc após operações de criação e envio de documentos.
 *
 * **Responsabilidade:**
 * - Desserializar o identificador gerado (`id`), o único campo lido; os demais campos da resposta são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentResponse(val id: String)

/**
 * DTO de resposta retornado pela consulta detalhada `GET /public/v1/documents/{id}/details` da PandaDoc.
 *
 * **Responsabilidade:**
 * - Desserializar o `id`, o status, a data de modificação e, por destinatário, o papel e se já assinou.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DocumentDetailsResponse(
    val id: String,
    val status: String? = null,
    @param:JsonProperty("date_modified") val dateModified: String? = null,
    val recipients: List<RecipientResponse>? = null,
)

/**
 * DTO que reflete o papel e o estado de um signatário retornado pela consulta de detalhes da PandaDoc.
 *
 * **Responsabilidade:**
 * - Indicar se o signatário já realizou a assinatura eletrônica do documento (`has_completed`).
 * - Não ler o e-mail do destinatário, que ficaria completo no `toString()` gerado (R10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RecipientResponse(
    val role: String? = null,
    @param:JsonProperty("has_completed") val hasCompleted: Boolean? = null,
)

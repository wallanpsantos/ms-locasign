package br.com.locasign.contract.infra.pandadoc.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO de requisição enviado ao endpoint `POST /public/v1/documents` da PandaDoc para criação de documento a partir de modelo.
 *
 * **Responsabilidade:**
 * - Serializar os metadados do documento, identificador do modelo (`template_uuid`), lista de destinatários, tokens e tags de correlação.
 */
data class CreateDocumentRequest(
    val name: String,
    @param:JsonProperty("template_uuid") val templateUuid: String,
    val recipients: List<RecipientRequest>,
    val tokens: List<TokenRequest>,
    /** Pares chave-valor devolvidos nos webhooks; usados para correlacionar o documento ao contrato. */
    val metadata: Map<String, String>,
)

/**
 * DTO que define os dados de um signatário individual no payload de criação do documento da PandaDoc.
 *
 * **Responsabilidade:**
 * - Transportar e-mail, nomes, ordem ordinal de assinatura e o papel correspondente configurado no modelo (`role`).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecipientRequest(
    val email: String,
    @param:JsonProperty("first_name") val firstName: String,
    @param:JsonProperty("last_name") val lastName: String?,
    /** Precisa bater exatamente com o nome da role no modelo. */
    val role: String,
    @param:JsonProperty("signing_order") val signingOrder: Int,
)

/**
 * DTO de mapeamento de variável dinâmica (token) para substituição de texto no modelo da PandaDoc.
 *
 * **Responsabilidade:**
 * - Associar o nome da tag do modelo (ex.: `Locatario.Nome`) ao valor preenchido em tempo de execução.
 */
data class TokenRequest(val name: String, val value: String)

/**
 * DTO de requisição para o endpoint `POST /public/v1/documents/{id}/send` da PandaDoc.
 *
 * **Responsabilidade:**
 * - Conter assunto, mensagem de notificação por e-mail e flag de envio silencioso (`silent`).
 */
data class SendDocumentRequest(val subject: String, val message: String, val silent: Boolean = false)

/**
 * DTO de requisição para o endpoint `PATCH /public/v1/documents/{id}/status` da PandaDoc.
 *
 * **Responsabilidade:**
 * - Instruir a mudança de status manual/forçada no provedor (ex.: código `11` para anulação do documento `document.voided`).
 */
data class StatusChangeRequest(
    val status: Int,
    val note: String,
    @param:JsonProperty("notify_recipients") val notifyRecipients: Boolean = false,
)

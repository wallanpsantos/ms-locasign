package br.com.locasign.contract.infra.pandadoc.adapters

import br.com.locasign.contract.app.ports.out.integration.MalformedWebhookPayload
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.app.ports.out.integration.WebhookItem
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.infra.pandadoc.mappers.toInstantOrNull
import br.com.locasign.contract.infra.pandadoc.mappers.toProviderStatus
import br.com.locasign.contract.infra.pandadoc.mappers.toSignerRole
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Autenticidade, divisão do array e tradução dos webhooks da PandaDoc (guia, seções 5.6 e 6).
 * A assinatura é o HMAC-SHA256 em hexadecimal do corpo bruto, com a shared key, no parâmetro `signature`.
 */
class PandaDocWebhookGateway(
    private val mapper: JsonMapper,
    private val properties: LocaSignProperties.PandaDocProperties,
) : ProviderWebhookGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun isAuthentic(rawBody: ByteArray, signature: String?): Boolean {
        val sharedKey = properties.webhookSharedKey
        if (sharedKey.isBlank()) {
            log.error("PANDADOC_WEBHOOK_SHARED_KEY não configurada: todo webhook será recusado")
            return false
        }
        if (signature.isNullOrBlank()) return false
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(sharedKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        }
        val expected = HexFormat.of().formatHex(mac.doFinal(rawBody))
        // Comparação em tempo constante, para não vazar quantos caracteres coincidem.
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            signature.trim().lowercase().toByteArray(Charsets.US_ASCII),
        )
    }

    override fun splitItems(rawBody: ByteArray): List<WebhookItem> {
        val root = try {
            mapper.readTree(rawBody)
        } catch (e: JacksonException) {
            throw MalformedWebhookPayload("Corpo do webhook não é JSON válido", e)
        }
        val nodes = when {
            root.isArray -> root.toList()
            root.isObject -> listOf(root)
            else -> throw MalformedWebhookPayload("Corpo do webhook não é um array nem um objeto JSON")
        }
        return nodes.filter { it.isObject }.map { node ->
            WebhookItem(
                json = mapper.writeValueAsString(node),
                eventName = node.path("event").textOrNull(),
                documentId = node.path("data").path("id").textOrNull(),
            )
        }
    }

    override fun translate(itemJson: String): ProviderSignal? {
        val root = try {
            mapper.readTree(itemJson)
        } catch (e: JacksonException) {
            throw MalformedWebhookPayload("Item de webhook ilegível", e)
        }
        val data = root.path("data")
        val documentId = data.path("id").textOrNull()?.takeIf { it.isNotBlank() }?.let(ProviderDocumentId::of)
            ?: return null
        // A metadata enviada na criação permite ligar o evento ao contrato antes de gravarmos o id do documento.
        val hint = ContractId.parseOrNull(data.path("metadata").path("contract_id").textOrNull())
        val modifiedAt = data.path("date_modified").textOrNull().toInstantOrNull()

        return when (root.path("event").textOrNull()) {
            "document_state_changed" -> ProviderSignal.StatusChanged(
                documentId, hint, data.path("status").textOrNull().toProviderStatus(), modifiedAt,
            )

            "recipient_completed" -> ProviderSignal.RecipientCompleted(
                documentId, hint, completedRoles(data.path("recipients")), modifiedAt,
            )

            "document_creation_failed" -> ProviderSignal.CreationFailed(
                documentId,
                hint,
                failureDetail(data.path("error"))
            )

            "document_completed_pdf_ready" -> ProviderSignal.PdfReady(documentId, hint)
            "document_deleted" -> ProviderSignal.DocumentDeleted(documentId, hint)
            // `document_updated` e outros descrevem a mesma mudança com ids diferentes (guia, seção 6.3).
            else -> null
        }
    }

    private fun completedRoles(recipients: JsonNode) = recipients
        .filter { it.path("has_completed").asBoolean(false) }
        .mapNotNull { it.path("role").textOrNull().toSignerRole(properties) }
        .toSet()

    private fun failureDetail(error: JsonNode): String? {
        if (error.isMissingNode || error.isNull) return null
        val detail = error.path("detail")
        val text = when {
            detail.isString -> detail.stringValue()
            detail.isMissingNode -> error.toString()
            else -> detail.toString()
        }
        return text.take(MAX_DETAIL_LENGTH)
    }

    private fun JsonNode.textOrNull(): String? = if (isString) stringValue() else null

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val MAX_DETAIL_LENGTH = 300
    }
}

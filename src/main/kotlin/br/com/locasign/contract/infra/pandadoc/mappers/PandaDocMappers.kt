package br.com.locasign.contract.infra.pandadoc.mappers

import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderRecipient
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.infra.pandadoc.dto.request.CreateDocumentRequest
import br.com.locasign.contract.infra.pandadoc.dto.request.RecipientRequest
import br.com.locasign.contract.infra.pandadoc.dto.request.TokenRequest
import br.com.locasign.contract.infra.pandadoc.dto.response.DocumentDetailsResponse
import br.com.locasign.shared.infra.config.LocaSignProperties
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

private val PT_BR: Locale = Locale.of("pt", "BR")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Converte o status textual proprietário da PandaDoc para o enum de status agnóstico [ProviderDocumentStatus].
 *
 * **Responsabilidade:**
 * - Traduzir os identificadores de status da API externa (`document.draft`, `document.sent`, etc.) em tipos neutros do sistema.
 */
fun String?.toProviderStatus(): ProviderDocumentStatus = when (this) {
    "document.uploaded" -> ProviderDocumentStatus.UPLOADED
    "document.draft" -> ProviderDocumentStatus.DRAFT
    "document.sent" -> ProviderDocumentStatus.SENT
    "document.viewed" -> ProviderDocumentStatus.VIEWED
    "document.completed" -> ProviderDocumentStatus.COMPLETED
    "document.declined" -> ProviderDocumentStatus.DECLINED
    "document.voided" -> ProviderDocumentStatus.VOIDED
    "document.error" -> ProviderDocumentStatus.ERROR
    else -> ProviderDocumentStatus.OTHER
}

/**
 * Converte o papel de signatário de domínio [SignerRole] no nome formal da role configurada no template da PandaDoc.
 *
 * **Responsabilidade:**
 * - Assegurar aderência estrita aos identificadores de papéis esperados pelo template externo (seção 5.1 do guia).
 */
fun SignerRole.toProviderRole(properties: LocaSignProperties.PandaDocProperties): String = when (this) {
    SignerRole.TENANT -> properties.tenantRole
    SignerRole.AGENCY -> properties.agencyRole
}

fun String?.toSignerRole(properties: LocaSignProperties.PandaDocProperties): SignerRole? = when (this) {
    properties.tenantRole -> SignerRole.TENANT
    properties.agencyRole -> SignerRole.AGENCY
    else -> null
}

fun String?.toInstantOrNull(): Instant? = try {
    this?.let(Instant::parse)
} catch (_: DateTimeParseException) {
    null
}

/**
 * Mapeia os dados canônicos da solicitação de criação de documento para a requisição de API [CreateDocumentRequest].
 *
 * **Responsabilidade:**
 * - Converter e formatar os campos e tokens dinâmicos no padrão monetário brasileiro (BRL) e datas pt-BR (`dd/MM/yyyy`).
 * - Inserir identificadores de correlação (`contract_id` e `lease_id`) nos metadados do documento no provedor.
 */
fun ProviderDocumentRequest.toPandaDoc(properties: LocaSignProperties.PandaDocProperties): CreateDocumentRequest =
    CreateDocumentRequest(
        name = documentName,
        templateUuid = properties.templateId,
        recipients = recipients.sortedBy { it.signingOrder }.map { it.toPandaDoc(properties) },
        tokens = listOf(
            TokenRequest("Locatario.Nome", template.tenantName),
            TokenRequest("Locatario.CPF", template.tenantCpf.formatted()),
            TokenRequest("Imovel.Endereco", template.propertyAddress),
            TokenRequest("Aluguel.Valor", template.rentAmount.amount.formatBrl()),
            TokenRequest("Locacao.Inicio", template.startDate.format(DATE_FORMAT)),
            TokenRequest("Locacao.Prazo", template.termMonths.formatTerm()),
        ),
        metadata = mapOf(
            "contract_id" to contractId.toString(),
            "lease_id" to leaseId.toString(),
        ),
    )

private fun ProviderRecipient.toPandaDoc(properties: LocaSignProperties.PandaDocProperties): RecipientRequest {
    val trimmed = name.trim()
    val firstName = trimmed.substringBefore(' ')
    val lastName = trimmed.substringAfter(' ', "").trim().ifEmpty { null }
    return RecipientRequest(
        email = email.value,
        firstName = firstName,
        lastName = lastName,
        role = role.toProviderRole(properties),
        signingOrder = signingOrder,
    )
}

/** `2500.00` vira `R$ 2.500,00`. */
internal fun java.math.BigDecimal.formatBrl(): String =
    "R$ " + DecimalFormat("#,##0.00", DecimalFormatSymbols(PT_BR)).format(this)

internal fun Int.formatTerm(): String = if (this == 1) "1 mês" else "$this meses"

/**
 * Converte os detalhes da resposta da API da PandaDoc ([DocumentDetailsResponse]) na visão de estado do provedor ([ProviderDocumentState]).
 *
 * **Responsabilidade:**
 * - Extrair os signatários que já concluíram sua assinatura eletrônica (`hasCompleted == true`) e correlacioná-los aos seus papéis de domínio.
 * - Fornecer os dados necessários para o fluxo de reconciliação de contratos (Regra R6).
 */
fun DocumentDetailsResponse.toState(properties: LocaSignProperties.PandaDocProperties): ProviderDocumentState =
    ProviderDocumentState(
        documentId = ProviderDocumentId.of(id),
        status = status.toProviderStatus(),
        completedRoles = recipients.orEmpty()
            .filter { it.hasCompleted == true }
            .mapNotNull { it.role.toSignerRole(properties) }
            .toSet(),
        modifiedAt = dateModified.toInstantOrNull(),
    )

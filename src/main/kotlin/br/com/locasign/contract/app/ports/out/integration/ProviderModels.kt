package br.com.locasign.contract.app.ports.out.integration

import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate

/** Status do documento no provedor, em termos neutros (a PandaDoc é só uma implementação). */
enum class ProviderDocumentStatus { UPLOADED, DRAFT, SENT, VIEWED, COMPLETED, DECLINED, VOIDED, ERROR, OTHER }

data class ProviderRecipient(
    val role: SignerRole,
    val name: String,
    val email: Email,
    val signingOrder: Int,
)

/** Dados da locação que preenchem o modelo do contrato. A formatação é feita pelo adapter. */
data class LeaseTemplateData(
    val tenantName: String,
    val tenantCpf: Cpf,
    val propertyAddress: String,
    val rentAmount: Money,
    val startDate: LocalDate,
    val termMonths: Int,
)

data class ProviderDocumentRequest(
    val contractId: ContractId,
    val leaseId: LeaseId,
    val documentName: String,
    val recipients: List<ProviderRecipient>,
    val template: LeaseTemplateData,
)

data class ProviderSendRequest(val subject: String, val message: String)

/** Estado do documento devolvido por uma consulta (reconciliação). */
data class ProviderDocumentState(
    val documentId: ProviderDocumentId,
    val status: ProviderDocumentStatus,
    val completedRoles: Set<SignerRole>,
    val modifiedAt: Instant?,
)

/** Resultado do download do PDF assinado. */
sealed interface SignedDocument {
    class Available(val bytes: ByteArray) : SignedDocument

    /** Sandbox ou download desligado: só a referência do documento é guardada. */
    data class Unavailable(val reason: String) : SignedDocument
}

/**
 * Sinal neutro derivado de um evento de webhook ou de uma consulta de reconciliação. O adapter
 * traduz nomes e status da PandaDoc para estes tipos; o domínio decide o que muda.
 */
sealed interface ProviderSignal {
    val documentId: ProviderDocumentId

    /** Identificador do contrato enviado como metadata na criação; permite correlacionar antes de gravarmos o id. */
    val contractHint: ContractId?

    data class StatusChanged(
        override val documentId: ProviderDocumentId,
        override val contractHint: ContractId?,
        val status: ProviderDocumentStatus,
        val modifiedAt: Instant?,
    ) : ProviderSignal

    data class RecipientCompleted(
        override val documentId: ProviderDocumentId,
        override val contractHint: ContractId?,
        val completedRoles: Set<SignerRole>,
        val modifiedAt: Instant?,
    ) : ProviderSignal

    data class CreationFailed(
        override val documentId: ProviderDocumentId,
        override val contractHint: ContractId?,
        val detail: String?,
    ) : ProviderSignal

    data class PdfReady(
        override val documentId: ProviderDocumentId,
        override val contractHint: ContractId?,
    ) : ProviderSignal

    data class DocumentDeleted(
        override val documentId: ProviderDocumentId,
        override val contractHint: ContractId?,
    ) : ProviderSignal
}

/** Um item do array entregue por um webhook. */
data class WebhookItem(val json: String, val eventName: String?, val documentId: String?)

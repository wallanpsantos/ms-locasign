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

/**
 * Estados do ciclo de vida do documento no provedor externo de assinatura eletrônica, expressos em termos neutros.
 *
 * **Responsabilidade:**
 * - Desacoplar o vocabulário proprietário da API externa (ex.: nomes de status do PandaDoc) da aplicação.
 * - Fornecer enum neutro para mapeamento e interpretação de progresso remoto.
 */
enum class ProviderDocumentStatus { UPLOADED, DRAFT, SENT, VIEWED, COMPLETED, DECLINED, VOIDED, ERROR, OTHER }

/**
 * Informações do signatário enviadas ao provedor para composição do documento e ordem de assinatura.
 *
 * **Responsabilidade:**
 * - Transportar os dados necessários de cada signatário para inclusão no fluxo de assinatura externa.
 */
data class ProviderRecipient(
    val role: SignerRole,
    val name: String,
    val email: Email,
    val signingOrder: Int,
)

/**
 * Dados consolidados da locação utilizados para preenchimento de campos dinâmicos no modelo do contrato.
 *
 * **Responsabilidade:**
 * - Agrupar os valores contratuais (endereço, locatário, valores, datas e vigência) exigidos para mesclagem no template.
 */
data class LeaseTemplateData(
    val tenantName: String,
    val tenantCpf: Cpf,
    val propertyAddress: String,
    val rentAmount: Money,
    val startDate: LocalDate,
    val termMonths: Int,
)

/**
 * Parâmetros completos de solicitação de criação de documento no provedor externo.
 *
 * **Responsabilidade:**
 * - Reunir identificadores do contrato e locação, nome do documento, signatários e dados de preenchimento do modelo.
 */
data class ProviderDocumentRequest(
    val contractId: ContractId,
    val leaseId: LeaseId,
    val documentName: String,
    val recipients: List<ProviderRecipient>,
    val template: LeaseTemplateData,
)

/**
 * Parâmetros de solicitação de envio e notificação do documento aos signatários via provedor.
 *
 * **Responsabilidade:**
 * - Encapsular o assunto e o corpo da mensagem de e-mail enviada pelo provedor aos signatários.
 */
data class ProviderSendRequest(val subject: String, val message: String)

/**
 * Fotografia do estado atual do documento obtida por consulta ativa na API do provedor externo.
 *
 * **Responsabilidade:**
 * - Retornar o status remoto, signatários que já assinaram e timestamp de modificação para processos de reconciliação periódica (R6).
 */
data class ProviderDocumentState(
    val documentId: ProviderDocumentId,
    val status: ProviderDocumentStatus,
    val completedRoles: Set<SignerRole>,
    val modifiedAt: Instant?,
)

/**
 * Resultado da operação de download do arquivo PDF assinado junto ao provedor externo.
 *
 * **Responsabilidade:**
 * - Representar polimorficamente o sucesso com os bytes disponíveis ([Available]) ou a indisponibilidade justificada ([Unavailable]) por limitações de ambiente sandbox ou configuração.
 */
sealed interface SignedDocument {
    class Available(val bytes: ByteArray) : SignedDocument

    /** Sandbox ou download desligado: só a referência do documento é guardada. */
    data class Unavailable(val reason: String) : SignedDocument
}

/**
 * Sinal agnóstico de evento emitido pelo provedor de assinatura eletrônica (oriundo de webhook ou reconciliação).
 *
 * **Responsabilidade:**
 * - Isolar o modelo de domínio dos eventos específicos da API proprietária (PandaDoc).
 * - Carregar a identificação remota do documento ([documentId]) e a dica de correlação do contrato local ([contractHint]).
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

/**
 * Representa um item individual decomposto de um lote (array) de eventos de webhook entregue pelo provedor.
 *
 * **Responsabilidade:**
 * - Conter o JSON bruto do item individual, o nome do evento e o identificador do documento para processamento desacoplado.
 */
data class WebhookItem(val json: String, val eventName: String?, val documentId: String?)

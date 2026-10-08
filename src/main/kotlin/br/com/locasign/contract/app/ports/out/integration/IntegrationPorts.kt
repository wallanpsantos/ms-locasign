package br.com.locasign.contract.app.ports.out.integration

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId

/**
 * Porta de saída para comunicação agnóstica com o provedor de assinatura eletrônica (PandaDoc).
 *
 * **Responsabilidade:**
 * - Abstrair as operações remotas de criação de documento, consulta de estado, envio para signatários, cancelamento e download de PDF assinado.
 * - Isolar o núcleo da aplicação e o domínio dos detalhes da API externa (endpoints, DTOs e termos de status do PandaDoc), padronizando exceções em [ProviderException].
 */
interface SignatureProviderPort {
    /** Cria o documento a partir do modelo. A criação é assíncrona no provedor. */
    fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId

    /** Consulta o status do documento e quem já assinou (usado na reconciliação). */
    fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState

    /** Envia o documento pronto aos signatários. */
    fun send(documentId: ProviderDocumentId, request: ProviderSendRequest)

    /** Anula o documento no provedor, para que ninguém mais possa assiná-lo. */
    fun cancelDocument(documentId: ProviderDocumentId)

    /** Baixa o PDF assinado, ou informa que está indisponível (sandbox ou flag desligada). */
    fun downloadSigned(documentId: ProviderDocumentId): SignedDocument
}

/**
 * Gateway de entrada para tratamento preliminar e validação criptográfica de payloads de webhooks do provedor.
 *
 * **Responsabilidade:**
 * - Validar a assinatura HMAC sobre os bytes brutos (`raw bytes`) do corpo da requisição em tempo constante antes de qualquer parsing (ADR-012).
 * - Decompor arrays de payloads múltiplos em itens individuais ([WebhookItem]) e traduzi-los em sinais neutros ([ProviderSignal]).
 */
interface ProviderWebhookGateway {
    /** Valida a assinatura sobre o corpo bruto, em tempo constante. */
    fun isAuthentic(rawBody: ByteArray, signature: String?): Boolean

    /** Divide o corpo (um array que pode ter vários eventos) em itens. */
    fun splitItems(rawBody: ByteArray): List<WebhookItem>

    /** Traduz um item em sinal neutro; `null` para eventos que o LocaSign não usa. */
    fun translate(itemJson: String): ProviderSignal?
}

/**
 * Porta de saída para persistência física do arquivo PDF do contrato assinado.
 *
 * **Responsabilidade:**
 * - Armazenar os bytes do documento assinado em sistema de arquivos ou storage permanente.
 * - Retornar a referência ou caminho canônico para arquivamento e rastreabilidade documental (Regra R8).
 */
interface SignedDocumentStoragePort {
    fun store(contractId: ContractId, bytes: ByteArray): String
}

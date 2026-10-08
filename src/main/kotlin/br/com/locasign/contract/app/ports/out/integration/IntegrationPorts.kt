package br.com.locasign.contract.app.ports.out.integration

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId

/**
 * Porta do provedor de assinatura. Não menciona "PandaDoc": o adapter é o único lugar que conhece
 * DTOs, nomes de status e endpoints. Todas as operações podem lançar [ProviderException].
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

/** Porta de entrada de webhooks do provedor: autenticidade, divisão do array e tradução para sinais neutros. */
interface ProviderWebhookGateway {
    /** Valida a assinatura sobre o corpo bruto, em tempo constante. */
    fun isAuthentic(rawBody: ByteArray, signature: String?): Boolean

    /** Divide o corpo (um array que pode ter vários eventos) em itens. */
    fun splitItems(rawBody: ByteArray): List<WebhookItem>

    /** Traduz um item em sinal neutro; `null` para eventos que o LocaSign não usa. */
    fun translate(itemJson: String): ProviderSignal?
}

/** Guarda o PDF assinado e devolve a localização (caminho, URL...). */
interface SignedDocumentStoragePort {
    fun store(contractId: ContractId, bytes: ByteArray): String
}

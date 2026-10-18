package br.com.locasign.contract.app.ports.`in`

/**
 * Parâmetros de entrada contendo os bytes brutos e cabeçalhos de uma entrega de webhook do provedor.
 *
 * **Responsabilidade:**
 * - Transportar o payload original não parseado ([rawBody]), a assinatura criptográfica ([signature]) e o identificador de entrega ([deliveryId]).
 */
class ReceiveProviderWebhookCommand(
    val rawBody: ByteArray,
    val signature: String?,
    val deliveryId: String?,
)

/**
 * Resultado da recepção do webhook pelo gateway de entrada da aplicação.
 *
 * **Responsabilidade:**
 * - Diferenciar recepção válida com itens enfileirados ([Accepted]), entrega duplicada descartada ([Duplicate]) e falha de integridade criptográfica ([InvalidSignature]).
 */
sealed interface WebhookReceipt {
    /** Entrega nova gravada no inbox, com [items] itens enfileirados no outbox. */
    data class Accepted(val deliveryId: String, val items: Int) : WebhookReceipt

    /** Entrega já recebida (reenvio manual pelo painel): nenhum efeito novo. */
    data class Duplicate(val deliveryId: String) : WebhookReceipt

    /** Assinatura ausente ou incorreta: o corpo não é gravado. */
    data object InvalidSignature : WebhookReceipt
}

/**
 * Porta de entrada da recepção do webhook da PandaDoc.
 *
 * **Responsabilidade:**
 * - Expor ao controller a validação HMAC e a gravação bruta no inbox, sem revelar a classe concreta que cumpre R7.
 */
interface ReceiveProviderWebhookPort {
    fun execute(command: ReceiveProviderWebhookCommand): WebhookReceipt
}

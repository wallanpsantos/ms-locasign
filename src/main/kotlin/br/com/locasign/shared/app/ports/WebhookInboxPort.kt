package br.com.locasign.shared.app.ports

/**
 * Porta de saída da camada de aplicação para persistência idempotente no padrão Transactional Inbox de webhooks externos.
 *
 * **Responsabilidade:**
 * - Armazenar o payload bruto (`rawBody`) recebido de provedores externos indexado pelo identificador de entrega ([deliveryId]).
 * - Deduplicar entregas repetidas de provedores (regra R7), retornando `false` caso a entrega já tenha sido registrada anteriormente.
 */
interface WebhookInboxPort {
    /** Grava a entrega. Devolve `false` se o [deliveryId] já existia (entrega duplicada). */
    fun store(deliveryId: String, rawBody: String): Boolean
}

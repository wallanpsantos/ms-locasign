package br.com.locasign.shared.app.ports

/** Princípio 4 / R7: todo webhook entra pelo inbox, com o corpo bruto exatamente como chegou. */
interface WebhookInboxPort {
	/** Grava a entrega. Devolve `false` se o [deliveryId] já existia (entrega duplicada). */
	fun store(deliveryId: String, rawBody: String): Boolean
}

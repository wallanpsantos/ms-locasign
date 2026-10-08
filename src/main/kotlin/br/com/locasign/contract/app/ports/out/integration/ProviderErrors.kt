package br.com.locasign.contract.app.ports.out.integration

/**
 * Erros do provedor de assinatura, já classificados para que os use cases decidam entre
 * retentar (o consumidor Kafka reprocessa e, depois do limite, envia para a DLT) e falhar o contrato.
 */
sealed class ProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

	/** O documento ainda não está pronto (404 ou 409 antes de `document.draft`). Retentável. */
	class NotReady(message: String, cause: Throwable? = null) : ProviderException(message, cause)

	/** Limite de requisições excedido, mesmo após as retentativas do adapter. Retentável. */
	class RateLimited(message: String, cause: Throwable? = null) : ProviderException(message, cause)

	/** 5xx, timeout ou falha de rede. Retentável. */
	class Unavailable(message: String, cause: Throwable? = null) : ProviderException(message, cause)

	/** 403: créditos esgotados ou permissão negada (por exemplo, e-mail fora do domínio no sandbox). Não retentar. */
	class Forbidden(message: String, cause: Throwable? = null) : ProviderException(message, cause)

	/** Outros 4xx: requisição inválida (por exemplo, role ausente no modelo). Não retentar. */
	class Rejected(val httpStatus: Int, message: String, cause: Throwable? = null) :
		ProviderException(message, cause)
}

/** Corpo de webhook que não é JSON no formato esperado. O corpo ainda é gravado no inbox (R7). */
class MalformedWebhookPayload(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

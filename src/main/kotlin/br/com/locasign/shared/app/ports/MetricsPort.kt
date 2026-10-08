package br.com.locasign.shared.app.ports

/** Contadores de negócio. Os nomes não podem carregar dados pessoais (seção 14 do guia). */
interface MetricsPort {
    fun count(name: String, vararg tags: String)
}

/** Nomes das métricas de negócio, em um só lugar para evitar divergência entre quem emite e quem consulta. */
object Metrics {
    const val WEBHOOK_RECEIVED = "locasign.webhook.received"
    const val WEBHOOK_INVALID = "locasign.webhook.invalid"
    const val WEBHOOK_DUPLICATE = "locasign.webhook.duplicate"
    const val TRANSITION_APPLIED = "locasign.contract.transition.applied"
    const val TRANSITION_IGNORED = "locasign.contract.transition.ignored"
    const val PROVIDER_RATE_LIMITED = "locasign.pandadoc.rate_limited"
    const val DEAD_LETTER = "locasign.kafka.dead_letter"
}

package br.com.locasign.shared.app.ports

/**
 * Porta de saída da camada de aplicação para emissão de métricas operacionais e contadores de negócio.
 *
 * **Responsabilidade:**
 * - Definir o contrato abstrato para coleta de métricas sem dependência direta de bibliotecas de observabilidade como Micrometer.
 * - Assegurar a contabilização de eventos de negócio preservando a privacidade de dados (sem permitir dados sensíveis/PII em tags ou nomes).
 */
interface MetricsPort {
    fun count(name: String, vararg tags: String)
}

/**
 * Catálogo centralizado de nomes de métricas operacionais e de negócio do sistema.
 *
 * **Responsabilidade:**
 * - Centralizar as constantes textuais de métricas (webhooks, transições de contrato, rate limiting e mensagens em DLT).
 * - Garantir uniformidade na telemetria entre componentes emissores e painéis de monitoramento e alertas.
 */
object Metrics {
    const val WEBHOOK_RECEIVED = "locasign.webhook.received"
    const val WEBHOOK_INVALID = "locasign.webhook.invalid"
    const val WEBHOOK_DUPLICATE = "locasign.webhook.duplicate"
    const val TRANSITION_APPLIED = "locasign.contract.transition.applied"
    const val TRANSITION_IGNORED = "locasign.contract.transition.ignored"
    const val PROVIDER_RATE_LIMITED = "locasign.pandadoc.rate_limited"
    const val DEAD_LETTER = "locasign.kafka.dead_letter"
}

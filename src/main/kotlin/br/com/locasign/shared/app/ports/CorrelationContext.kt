package br.com.locasign.shared.app.ports

/**
 * Dicionário canônico de constantes para chaves de correlação e contexto de rastreabilidade distribuída (MDC).
 *
 * **Responsabilidade:**
 * - Padronizar os nomes de chaves de identificação nos logs estruturados e envelopes de eventos (`correlationId`, `causationId`, `contractId`, `eventId`).
 * - Prevenir divergências de nomenclatura de metadados entre adaptadores de entrada HTTP, tópicos Kafka e tarefas agendadas.
 */
object ContextKeys {
    const val CORRELATION_ID = "correlationId"
    const val CAUSATION_ID = "causationId"
    const val CONTRACT_ID = "contractId"
    const val EVENT_ID = "eventId"
}

/**
 * Porta de saída da camada de aplicação para propagação de contexto de rastreabilidade distribuída.
 *
 * **Responsabilidade:**
 * - Propagar identificadores de rastreamento ([correlationId] e [causationId]) e metadados contextuais durante o ciclo de execução de blocos de código.
 * - Manter o contexto de MDC (Mapped Diagnostic Context) acessível para ferramentas de logging sem acoplar as assinaturas dos casos de uso a frameworks específicos.
 */
interface CorrelationContext {
    fun <T> with(
        correlationId: String?,
        causationId: String? = null,
        entries: Map<String, String> = emptyMap(),
        block: () -> T,
    ): T
}

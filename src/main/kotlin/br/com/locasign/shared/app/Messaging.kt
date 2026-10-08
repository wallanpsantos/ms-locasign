package br.com.locasign.shared.app

/**
 * Dicionário canônico de tópicos Kafka utilizados pela mensageria assíncrona do sistema.
 *
 * **Responsabilidade:**
 * - Centralizar os nomes e versões de tópicos (`PANDADOC_WEBHOOKS`, `CONTRACT_EVENTS`), respeitando a convenção de versionamento de schemas (ADR-009).
 * - Definir o sufixo padrão de Dead Letter Topics ([DEAD_LETTER_SUFFIX]) para isolamento de mensagens venenosas.
 */
object Topics {
    const val PANDADOC_WEBHOOKS = "locasign.pandadoc.webhooks.v1"
    const val CONTRACT_EVENTS = "locasign.contract.events.v1"
    const val DEAD_LETTER_SUFFIX = ".dlt"

    val ALL: List<String> = listOf(PANDADOC_WEBHOOKS, CONTRACT_EVENTS)
}

/**
 * Catálogo canônico de grupos de consumidores Kafka da aplicação.
 *
 * **Responsabilidade:**
 * - Identificar os grupos de consumo concorrente e independente para cada responsabilidade assíncrona do sistema (`ORCHESTRATOR`, `PROVIDER_EVENTS`, `POST_SIGNATURE`, `NOTIFICATIONS`).
 * - Servir como chave de partição para o controle de idempotência na tabela `processed_messages` junto ao ID do evento.
 */
object ConsumerGroups {
    const val ORCHESTRATOR = "locasign-orchestrator"
    const val PROVIDER_EVENTS = "locasign-provider-events"
    const val POST_SIGNATURE = "locasign-post-signature"
    const val NOTIFICATIONS = "locasign-notifications"
}

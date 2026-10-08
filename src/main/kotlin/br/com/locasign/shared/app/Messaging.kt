package br.com.locasign.shared.app

/** Tópicos Kafka do LocaSign (versionados no nome, ADR-009). O sufixo `.dlt` é a fila de erros. */
object Topics {
    const val PANDADOC_WEBHOOKS = "locasign.pandadoc.webhooks.v1"
    const val CONTRACT_EVENTS = "locasign.contract.events.v1"
    const val DEAD_LETTER_SUFFIX = ".dlt"

    val ALL: List<String> = listOf(PANDADOC_WEBHOOKS, CONTRACT_EVENTS)
}

/** Grupos de consumidores. Cada grupo reage de forma independente aos mesmos eventos. */
object ConsumerGroups {
    const val ORCHESTRATOR = "locasign-orchestrator"
    const val PROVIDER_EVENTS = "locasign-provider-events"
    const val POST_SIGNATURE = "locasign-post-signature"
    const val NOTIFICATIONS = "locasign-notifications"
}

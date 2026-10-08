package br.com.locasign.notification.domain

/** Quem recebe a notificação de um evento do contrato. */
enum class Audience {
    /** O corretor, representado pelo signatário da imobiliária no MVP. */
    AGENCY_ONLY,

    /** Todas as partes: locatário e imobiliária. */
    ALL_PARTIES,
}

/**
 * Catálogo de reações do grupo de notificações (guia, seção 7.4). Os nomes são o `eventType` do
 * envelope, que é a linguagem publicada pelo módulo de contratos.
 */
object NotificationPolicy {

    fun audienceFor(eventType: String): Audience? = when (eventType) {
        "ContractSent",
        "ContractSignerCompleted",
        "ContractDeclined",
        "ContractExpired",
        "ContractCancelled",
        "ContractGenerationFailed",
        "ContractReminderSent",
            -> Audience.AGENCY_ONLY

        "ContractCompleted" -> Audience.ALL_PARTIES
        else -> null
    }
}

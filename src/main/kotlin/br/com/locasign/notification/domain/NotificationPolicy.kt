package br.com.locasign.notification.domain

/**
 * Define o público-alvo destinatário das notificações disparadas pelos eventos do ciclo de vida do contrato.
 *
 * **Responsabilidade:**
 * - Classificar as partes interessadas elegíveis para recebimento de alertas (somente imobiliária ou todas as partes).
 * - Garantir conformidade com as políticas de privacidade e comunicação do negócio (ex.: notificar locatário apenas na conclusão do contrato).
 */
enum class Audience {
    /** O corretor, representado pelo signatário da imobiliária no MVP. */
    AGENCY_ONLY,

    /** Todas as partes: locatário e imobiliária. */
    ALL_PARTIES,
}

/**
 * Catálogo e política de roteamento de notificações baseada no tipo de evento de domínio emitido pelo contrato.
 *
 * **Responsabilidade:**
 * - Determinar o público-alvo ([Audience]) para cada tipo de evento do ciclo de vida do contrato conforme as regras da seção 7.4 do guia técnico.
 * - Centralizar a política de notificação no domínio puro, desacoplando o caso de uso de regras estáticas de audiência.
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

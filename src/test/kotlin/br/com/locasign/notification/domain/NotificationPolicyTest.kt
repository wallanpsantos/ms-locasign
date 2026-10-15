package br.com.locasign.notification.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationPolicyTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "ContractSent",
            "ContractSignerCompleted",
            "ContractDeclined",
            "ContractExpired",
            "ContractCancelled",
            "ContractGenerationFailed",
            "ContractReminderSent",
        ],
    )
    fun `eventos operacionais direcionam apenas para a imobiliaria`(eventType: String) {
        assertEquals(Audience.AGENCY_ONLY, NotificationPolicy.audienceFor(eventType))
    }

    @Test
    fun `evento de conclusao direciona para todas as partes (R8)`() {
        assertEquals(Audience.ALL_PARTIES, NotificationPolicy.audienceFor("ContractCompleted"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "ContractRequested",
            "ContractDocumentCreated",
            "ContractGenerated",
            "ContractViewed",
            "SignedDocumentArchived",
            "LeaseActivated",
            "UnknownEvent",
        ],
    )
    fun `eventos sem notificacao retornam nulo`(eventType: String) {
        assertNull(NotificationPolicy.audienceFor(eventType))
    }
}

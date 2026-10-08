package br.com.locasign.contract.infra.messaging

import br.com.locasign.contract.domain.events.ContractCancelled
import br.com.locasign.contract.domain.events.ContractCompleted
import br.com.locasign.contract.domain.events.ContractDeclined
import br.com.locasign.contract.domain.events.ContractDocumentCreated
import br.com.locasign.contract.domain.events.ContractEvent
import br.com.locasign.contract.domain.events.ContractExpired
import br.com.locasign.contract.domain.events.ContractGenerated
import br.com.locasign.contract.domain.events.ContractGenerationFailed
import br.com.locasign.contract.domain.events.ContractReminderSent
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.events.ContractSent
import br.com.locasign.contract.domain.events.ContractSignerCompleted
import br.com.locasign.contract.domain.events.ContractViewed
import br.com.locasign.contract.domain.events.LeaseActivated
import br.com.locasign.contract.domain.events.SignedDocumentArchived

/**
 * Payload do envelope (guia, seção 7.3): ids e dados essenciais, **sem CPF nem e-mail completos**.
 * O `when` é exaustivo sobre a hierarquia selada: um evento novo não compila até ser mapeado aqui.
 */
fun ContractEvent.toPayload(): Map<String, Any?> {
    val payload = linkedMapOf<String, Any?>("contractId" to contractId.toString())
    payload += when (this) {
        is ContractRequested -> mapOf("leaseId" to leaseId.toString(), "versionNumber" to versionNumber)
        is ContractDocumentCreated -> mapOf("providerDocumentId" to providerDocumentId.value)
        is ContractGenerated -> emptyMap()
        is ContractGenerationFailed -> mapOf("detail" to detail)
        is ContractSent -> mapOf("expiresAt" to expiresAt?.toString())
        is ContractViewed -> emptyMap()
        is ContractSignerCompleted -> mapOf("role" to role.name)
        is ContractCompleted -> mapOf("leaseId" to leaseId.toString())
        is ContractDeclined -> mapOf("reason" to reason)
        is ContractExpired -> emptyMap()
        is ContractCancelled -> mapOf("reason" to reason)
        is ContractReminderSent -> emptyMap()
        is SignedDocumentArchived -> mapOf("storage" to storage, "location" to location)
        is LeaseActivated -> mapOf("leaseId" to leaseId.toString())
    }
    return payload
}

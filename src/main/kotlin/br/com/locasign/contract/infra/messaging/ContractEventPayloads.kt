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
 * Serializa os atributos específicos de cada evento de domínio [ContractEvent] para o mapa de carga útil (`payload`) do envelope Kafka.
 *
 * **Responsabilidade:**
 * - Proteger a privacidade e cumprir a LGPD assegurando que dados cadastrais completos (CPF e e-mail) nunca sejam publicados nos tópicos Kafka.
 * - Garantir mapeamento exaustivo via `when` sobre a hierarquia selada [ContractEvent], impedindo novos eventos sem contrato de serialização.
 */
fun ContractEvent.toPayload(): Map<String, Any?> = buildMap {
    put("contractId", contractId.toString())
    when (this@toPayload) {
        is ContractRequested -> {
            put("leaseId", leaseId.toString())
            put("versionNumber", versionNumber)
        }
        is ContractDocumentCreated -> put("providerDocumentId", providerDocumentId.value)
        is ContractGenerated -> Unit
        is ContractGenerationFailed -> put("detail", detail)
        is ContractSent -> put("expiresAt", expiresAt?.toString())
        is ContractViewed -> Unit
        is ContractSignerCompleted -> put("role", role.name)
        is ContractCompleted -> put("leaseId", leaseId.toString())
        is ContractDeclined -> put("reason", reason)
        is ContractExpired -> Unit
        is ContractCancelled -> put("reason", reason)
        is ContractReminderSent -> Unit
        is SignedDocumentArchived -> {
            put("storage", storage)
            put("location", location)
        }
        is LeaseActivated -> put("leaseId", leaseId.toString())
    }
}

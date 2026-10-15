package br.com.locasign.contract.app.fakes

import br.com.locasign.contract.app.ports.out.integration.MalformedWebhookPayload
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.app.ports.out.integration.SignedDocumentStoragePort
import br.com.locasign.contract.app.ports.out.integration.WebhookItem
import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.domain.events.ContractEvent
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import java.time.Instant

class InMemoryContractRepository : ContractRepositoryPort {
    private val contracts = mutableMapOf<ContractId, Contract>()

    override fun save(contract: Contract) {
        val existingActive = contracts.values.firstOrNull {
            it.leaseId == contract.leaseId && it.id != contract.id && !it.status.isFinal
        }
        if (existingActive != null && !contract.status.isFinal) {
            throw DomainException.ActiveContractExists(contract.leaseId.toString(), "Já existe contrato ativo.")
        }
        contracts[contract.id] = contract
    }

    override fun findById(id: ContractId): Contract? = contracts[id]

    override fun findByProviderDocumentId(documentId: ProviderDocumentId): Contract? =
        contracts.values.firstOrNull { it.providerDocumentId == documentId }

    override fun nextVersionNumber(leaseId: LeaseId): Int =
        (contracts.values.filter { it.leaseId == leaseId }.maxOfOrNull { it.versionNumber } ?: 0) + 1

    override fun existsOpenContract(leaseId: LeaseId): Boolean =
        contracts.values.any { it.leaseId == leaseId && !it.status.isFinal }

    override fun existsCompletedContract(leaseId: LeaseId): Boolean =
        contracts.values.any { it.leaseId == leaseId && it.status == ContractStatus.COMPLETED }

    override fun findOverdueIds(now: Instant, limit: Int): List<ContractId> =
        contracts.values
            .filter { it.status.isAwaitingSignatures && it.expiresAt?.isBefore(now) == true }
            .take(limit)
            .map { it.id }

    override fun findReminderDueIds(sentBefore: Instant, limit: Int): List<ContractId> =
        contracts.values
            .filter { it.status.isAwaitingSignatures && it.sentAt?.isBefore(sentBefore) == true }
            .take(limit)
            .map { it.id }

    override fun findReconciliationCandidateIds(staleBefore: Instant, limit: Int): List<ContractId> =
        contracts.values
            .filter { !it.status.isFinal && it.providerDocumentId != null && it.updatedAt.isBefore(staleBefore) }
            .take(limit)
            .map { it.id }
}

class RecordingContractEventPublisher : ContractEventPublisherPort {
    val published = mutableListOf<ContractEvent>()

    override fun publish(events: List<ContractEvent>) {
        published.addAll(events)
    }
}

class InMemoryPostSignatureActions : PostSignatureActionsPort {
    private val recorded = mutableSetOf<Triple<ContractId, String, String>>()

    override fun registerIfAbsent(
        contractId: ContractId,
        actionType: String,
        status: String,
        detailsJson: String?,
    ): Boolean = recorded.add(Triple(contractId, actionType, status))
}

/**
 * Provedor de assinatura com respostas fixas, que substitui o adapter da PandaDoc nos testes com contexto Spring.
 *
 * **Responsabilidade:**
 * - Garantir que nenhum teste automatizado chame a PandaDoc, nem o sandbox (AGENTS.md §7 e docs/agents/testes.md §6).
 * - Devolver um id de documento por contrato, para não violar `uq_contracts_provider_document`.
 */
class FixedSignatureProvider : SignatureProviderPort {
    override fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId =
        ProviderDocumentId.of("doc-${request.contractId.value}")

    override fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState =
        ProviderDocumentState(documentId, ProviderDocumentStatus.DRAFT, emptySet(), modifiedAt = null)

    override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) = Unit

    override fun cancelDocument(documentId: ProviderDocumentId) = Unit

    override fun downloadSigned(documentId: ProviderDocumentId): SignedDocument =
        SignedDocument.Unavailable("Provedor fixo de teste: sem PDF.")
}

/**
 * Provedor de assinatura que registra as chamadas dos casos de uso e devolve respostas configuráveis por teste.
 *
 * **Responsabilidade:**
 * - Guardar pedidos de criação, envios, anulações e downloads, na ordem das chamadas.
 * - Rodar [beforeEachCall] em cada chamada, para conferir o estado no momento da chamada externa (transação aberta,
 *   evento marcado por outro consumidor).
 * - Simular falhas do provedor ([createFailure], [cancelFailure], [downloadFailure]) depois de registrar a chamada.
 */
class RecordingSignatureProvider : SignatureProviderPort {
    val created = mutableListOf<ProviderDocumentRequest>()
    val sent = mutableListOf<Pair<ProviderDocumentId, ProviderSendRequest>>()
    val cancelled = mutableListOf<ProviderDocumentId>()
    val downloaded = mutableListOf<ProviderDocumentId>()

    var beforeEachCall: () -> Unit = {}
    var documentToCreate: ProviderDocumentId = ProviderDocumentId.of("doc-criado")
    var createFailure: ProviderException? = null
    var cancelFailure: ProviderException? = null
    var signedDocument: SignedDocument = SignedDocument.Unavailable("Sandbox sem download.")
    var downloadFailure: ProviderException? = null

    override fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId {
        created += request
        beforeEachCall()
        createFailure?.let { throw it }
        return documentToCreate
    }

    override fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState {
        beforeEachCall()
        return ProviderDocumentState(documentId, ProviderDocumentStatus.DRAFT, emptySet(), modifiedAt = null)
    }

    override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) {
        sent += documentId to request
        beforeEachCall()
    }

    override fun cancelDocument(documentId: ProviderDocumentId) {
        cancelled += documentId
        beforeEachCall()
        cancelFailure?.let { throw it }
    }

    override fun downloadSigned(documentId: ProviderDocumentId): SignedDocument {
        downloaded += documentId
        beforeEachCall()
        downloadFailure?.let { throw it }
        return signedDocument
    }
}

/** Gateway de webhook com assinatura válida e itens fixos; [malformed] simula corpo que não é um array JSON. */
class FixedWebhookGateway(
    var items: List<WebhookItem> = emptyList(),
    var malformed: Boolean = false,
) : ProviderWebhookGateway {
    override fun isAuthentic(rawBody: ByteArray, signature: String?): Boolean = signature == VALID_SIGNATURE

    override fun splitItems(rawBody: ByteArray): List<WebhookItem> =
        if (malformed) throw MalformedWebhookPayload("Corpo do webhook não é um array JSON.") else items

    /** Nenhum item vira sinal: a porta usa `null` para eventos que o LocaSign não usa. */
    override fun translate(itemJson: String): ProviderSignal? = null

    companion object {
        const val VALID_SIGNATURE = "assinatura-valida"
    }
}

/** Armazena os PDFs em memória e devolve sempre a mesma [location]. */
class InMemorySignedDocumentStorage(val location: String = "armazenamento/contrato-assinado.pdf") :
    SignedDocumentStoragePort {
    val stored = mutableMapOf<ContractId, ByteArray>()

    override fun store(contractId: ContractId, bytes: ByteArray): String {
        stored[contractId] = bytes
        return location
    }
}

class FakeLeaseLookup : LeaseLookupPort {
    private val leases = mutableMapOf<LeaseId, LeaseSnapshot>()

    fun register(snapshot: LeaseSnapshot) {
        leases[snapshot.leaseId] = snapshot
    }

    override fun find(leaseId: LeaseId): LeaseSnapshot? = leases[leaseId]
}

class FakeLeaseActivation : LeaseActivationPort {
    val activated = mutableSetOf<LeaseId>()

    override fun activate(leaseId: LeaseId) {
        activated.add(leaseId)
    }
}

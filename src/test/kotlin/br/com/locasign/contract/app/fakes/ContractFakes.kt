package br.com.locasign.contract.app.fakes

import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.app.ports.out.integration.SignedDocumentStoragePort
import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.app.queries.HistoryEntryView
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
            .filter { it.status.isAwaitingSignatures && it.expiresAt != null && it.expiresAt!!.isBefore(now) }
            .take(limit)
            .map { it.id }

    override fun findReminderDueIds(sentBefore: Instant, limit: Int): List<ContractId> =
        contracts.values
            .filter { it.status.isAwaitingSignatures && it.sentAt != null && it.sentAt!!.isBefore(sentBefore) }
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

    fun clear() = published.clear()
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

class FakeSignatureProvider : SignatureProviderPort {
    var nextDocumentId: ProviderDocumentId = ProviderDocumentId.of("doc-123")
    val createdRequests = mutableListOf<ProviderDocumentRequest>()
    val sentRequests = mutableListOf<Pair<ProviderDocumentId, ProviderSendRequest>>()
    val cancelledDocuments = mutableListOf<ProviderDocumentId>()
    var stubbedState: ProviderDocumentState = ProviderDocumentState(
        documentId = ProviderDocumentId.of("doc-123"),
        status = ProviderDocumentStatus.DRAFT,
        completedRoles = emptySet(),
        modifiedAt = null,
    )
    var stubbedDownload: SignedDocument = SignedDocument.Available(ByteArray(10) { 1 })

    override fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId {
        createdRequests.add(request)
        return nextDocumentId
    }

    override fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState = stubbedState

    override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) {
        sentRequests.add(documentId to request)
    }

    override fun cancelDocument(documentId: ProviderDocumentId) {
        cancelledDocuments.add(documentId)
    }

    override fun downloadSigned(documentId: ProviderDocumentId): SignedDocument = stubbedDownload
}

class FakeSignedDocumentStorage : SignedDocumentStoragePort {
    val stored = mutableMapOf<ContractId, ByteArray>()

    override fun store(contractId: ContractId, bytes: ByteArray): String {
        stored[contractId] = bytes
        return "/storage/contracts/${contractId.value}.pdf"
    }
}

class FakeLeaseLookup(private val leases: MutableMap<LeaseId, LeaseSnapshot> = mutableMapOf()) : LeaseLookupPort {
    fun register(snapshot: LeaseSnapshot) {
        leases[snapshot.leaseId] = snapshot
    }

    override fun find(leaseId: LeaseId): LeaseSnapshot? = leases[leaseId]
}

class FakeLeaseActivation : LeaseActivationPort {
    val activated = mutableSetOf<LeaseId>()

    override fun activate(leaseId: LeaseId): Boolean = activated.add(leaseId)
}

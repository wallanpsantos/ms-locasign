package br.com.locasign.contract.domain.models

import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import java.time.Instant

/**
 * Reconstitui um [Contract] em qualquer status, sem eventos nem histórico pendentes, para montar cenários de teste.
 *
 * Os campos opcionais do ciclo de vida começam vazios, `createdAt` e `updatedAt` valem `now` e o agregado já
 * nasce com versão de persistência, como se tivesse sido lido do banco.
 */
fun restoreContract(
    status: ContractStatus,
    leaseId: LeaseId,
    signers: List<Signer>,
    now: Instant,
    id: ContractId = ContractId.new(),
    versionNumber: Int = 1,
    providerDocumentId: ProviderDocumentId? = null,
    providerLastModifiedAt: Instant? = null,
    sentAt: Instant? = null,
    expiresAt: Instant? = null,
    cancelReason: CancelReason? = null,
    signedDocumentRef: String? = null,
    createdAt: Instant = now,
): Contract = Contract.restore(
    id = id,
    leaseId = leaseId,
    versionNumber = versionNumber,
    status = status,
    providerDocumentId = providerDocumentId,
    providerLastModifiedAt = providerLastModifiedAt,
    sentAt = sentAt,
    expiresAt = expiresAt,
    reminderSentAt = null,
    lastReconciledAt = null,
    cancelReason = cancelReason,
    signedDocumentRef = signedDocumentRef,
    signers = signers,
    createdAt = createdAt,
    updatedAt = now,
    rowVersion = 1L,
)

package br.com.locasign.contract.app.queries

import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.domain.DomainException
import java.time.Instant

data class SignerView(
	val role: String,
	val name: String,
	val email: String,
	val signingOrder: Int,
	val completedAt: Instant?,
)

data class ContractDetailView(
	val id: String,
	val leaseId: String,
	val versionNumber: Int,
	val status: String,
	val providerDocumentId: String?,
	val sentAt: Instant?,
	val expiresAt: Instant?,
	val cancelReason: String?,
	val signedDocumentRef: String?,
	val signers: List<SignerView>,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class HistoryEntryView(
	val id: String,
	val fromStatus: String?,
	val toStatus: String,
	val outcome: String,
	val source: String,
	val sourceEventId: String?,
	val note: String?,
	val occurredAt: Instant,
)

/** Query: status atual, signatários e prazos. */
class GetContract(private val queries: ContractQueryPort) {
	fun execute(id: ContractId): ContractDetailView =
		queries.findDetail(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

/** Query: linha do tempo completa, incluindo transições ignoradas (R5). */
class GetContractHistory(private val queries: ContractQueryPort) {
	fun execute(id: ContractId): List<HistoryEntryView> =
		queries.findHistory(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

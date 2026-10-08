package br.com.locasign.contract.interfaces.web.dto.response

import java.time.Instant

/** Resposta de operações assíncronas (202): o contrato foi aceito e segue o ciclo de vida. */
data class ContractAcceptedResponse(val id: String, val leaseId: String?, val status: String)

data class ContractResponse(
	val id: String,
	val leaseId: String,
	val versionNumber: Int,
	val status: String,
	val providerDocumentId: String?,
	val sentAt: Instant?,
	val expiresAt: Instant?,
	val cancelReason: String?,
	val signedDocumentRef: String?,
	val signers: List<SignerResponse>,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class SignerResponse(
	val role: String,
	val name: String,
	val email: String,
	val signingOrder: Int,
	val completedAt: Instant?,
)

data class ContractHistoryResponse(val contractId: String, val entries: List<HistoryEntryResponse>)

data class HistoryEntryResponse(
	val id: String,
	val fromStatus: String?,
	val toStatus: String,
	val outcome: String,
	val source: String,
	val sourceEventId: String?,
	val note: String?,
	val occurredAt: Instant,
)

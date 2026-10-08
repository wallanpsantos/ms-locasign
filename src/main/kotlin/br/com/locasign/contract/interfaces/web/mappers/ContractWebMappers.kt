package br.com.locasign.contract.interfaces.web.mappers

import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.app.queries.SignerView
import br.com.locasign.contract.interfaces.web.dto.response.ContractHistoryResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractResponse
import br.com.locasign.contract.interfaces.web.dto.response.HistoryEntryResponse
import br.com.locasign.contract.interfaces.web.dto.response.SignerResponse

/**
 * Converte a visão de detalhe do contrato ([ContractDetailView]) no DTO de resposta da API [ContractResponse].
 *
 * **Responsabilidade:**
 * - Formatar a projeção de leitura do contrato e seus signatários para exibição nos endpoints HTTP.
 */
fun ContractDetailView.toResponse(): ContractResponse = ContractResponse(
    id = id,
    leaseId = leaseId,
    versionNumber = versionNumber,
    status = status,
    providerDocumentId = providerDocumentId,
    sentAt = sentAt,
    expiresAt = expiresAt,
    cancelReason = cancelReason,
    signedDocumentRef = signedDocumentRef,
    signers = signers.map { it.toResponse() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Converte a visão de signatário ([SignerView]) no DTO de resposta [SignerResponse].
 */
fun SignerView.toResponse(): SignerResponse = SignerResponse(role, name, email, signingOrder, completedAt)

/**
 * Converte a visão de entrada histórica ([HistoryEntryView]) no DTO [HistoryEntryResponse].
 */
fun HistoryEntryView.toResponse(): HistoryEntryResponse =
    HistoryEntryResponse(id, fromStatus, toStatus, outcome, source, sourceEventId, note, occurredAt)

/**
 * Converte a lista de entradas históricas no DTO consolidado [ContractHistoryResponse].
 */
fun List<HistoryEntryView>.toResponse(contractId: String): ContractHistoryResponse =
    ContractHistoryResponse(contractId, map { it.toResponse() })

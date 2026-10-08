package br.com.locasign.contract.interfaces.web.dto.response

import java.time.Instant

/**
 * DTO de resposta HTTP 202 (Accepted) para comandos assíncronos disparados sobre contratos de locação.
 *
 * **Responsabilidade:**
 * - Retornar o identificador do contrato gerado, a locação vinculada e o status imediato da operação aceita.
 */
data class ContractAcceptedResponse(val id: String, val leaseId: String?, val status: String)

/**
 * DTO de resposta detalhada contendo o estado consolidado de um contrato de locação para a API REST.
 *
 * **Responsabilidade:**
 * - Expor atributos cadastrais, status, datas-chave de ciclo de vida e lista de signatários da versão consultada.
 */
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

/**
 * DTO de resposta contendo os dados e status individual de assinatura de um signatário do contrato.
 *
 * **Responsabilidade:**
 * - Retornar papel, nome, e-mail mascarado, ordem ordinal e confirmação de conclusão de assinatura.
 */
data class SignerResponse(
    val role: String,
    val name: String,
    val email: String,
    val signingOrder: Int,
    val completedAt: Instant?,
)

/**
 * DTO de resposta contendo o histórico consolidado de auditoria e transições de um contrato.
 *
 * **Responsabilidade:**
 * - Agrupar a lista ordenada de eventos históricos ([HistoryEntryResponse]) associados ao contrato.
 */
data class ContractHistoryResponse(val contractId: String, val entries: List<HistoryEntryResponse>)

/**
 * DTO de resposta individual representando uma entrada na linha do tempo histórica do contrato.
 *
 * **Responsabilidade:**
 * - Descrever uma transição de status ou fato relevante com canal originador, resultado da avaliação e observações.
 */
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

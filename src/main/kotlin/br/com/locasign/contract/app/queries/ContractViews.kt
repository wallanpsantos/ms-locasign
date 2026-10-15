package br.com.locasign.contract.app.queries

import java.time.Instant

/**
 * Projeção de leitura dos dados de um signatário para exibição na API.
 *
 * **Responsabilidade:**
 * - Expor papel, nome, e-mail completo (sem máscara) e status de assinatura. Não registrar a view em log (`docs/agents/regras-de-codigo.md` §5).
 */
data class SignerView(
    val role: String,
    val name: String,
    val email: String,
    val signingOrder: Int,
    val completedAt: Instant?,
)

/**
 * Visão consolidada detalhada de um contrato de locação para retorno em consultas da API.
 *
 * **Responsabilidade:**
 * - Reunir metadados, identificadores externos, datas de ciclo de vida e a lista de signatários de uma versão específica do contrato.
 */
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

/**
 * Visão detalhada de uma entrada da linha do tempo histórica de transições do contrato.
 *
 * **Responsabilidade:**
 * - Expor registros de auditoria com status de origem e destino, canal gerador, resultado da transição e notas operacionais.
 */
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

package br.com.locasign.contract.app.queries

import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.domain.DomainException
import java.time.Instant

/**
 * Projeção de leitura dos dados de um signatário para exibição na API.
 *
 * **Responsabilidade:**
 * - Expor informações textuais de papel, nome, e-mail (mascarado ou formatado) e status de assinatura.
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

/**
 * Caso de uso de consulta que recupera os detalhes operacionais e de negócio de um contrato.
 *
 * **Responsabilidade:**
 * - Buscar o contrato via [ContractQueryPort] e lançar [DomainException.NotFound] se não for localizado.
 */
class GetContract(private val queries: ContractQueryPort) {
    fun execute(id: ContractId): ContractDetailView =
        queries.findDetail(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

/**
 * Caso de uso de consulta que recupera a linha do tempo histórica de auditoria de um contrato.
 *
 * **Responsabilidade:**
 * - Consultar a trilha de auditoria completa (incluindo transições ignoradas e fatos informativos conforme R5 e R7).
 * - Garantir que o contrato exista antes de fornecer o histórico.
 */
class GetContractHistory(private val queries: ContractQueryPort) {
    fun execute(id: ContractId): List<HistoryEntryView> =
        queries.findHistory(id) ?: throw DomainException.NotFound("Contrato", id.toString())
}

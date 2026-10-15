package br.com.locasign.contract.app.ports.out.repository

import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import java.time.Instant

/**
 * Porta de saída para persistência e recuperação do agregado [Contract].
 *
 * **Responsabilidade:**
 * - Persistir o agregado [Contract], seus signatários e as entradas de histórico na mesma transação atômica.
 * - Assegurar bloqueio otimista via controle de versão de linha (`row_version`) e unicidade de contrato ativo por locação (Regra R1).
 * - Prover consultas especializadas para identificação de contratos vencidos (R4), lembretes pendentes (R4) e candidatos à reconciliação (guia, §6.4).
 */
interface ContractRepositoryPort {
    /**
     * Persiste o contrato, os signatários e as linhas de auditoria pendentes (`pullHistory`).
     * Lança `ActiveContractExists` se violar R1 e `OptimisticLockingFailureException` em conflito de versão.
     */
    fun save(contract: Contract)

    fun findById(id: ContractId): Contract?

    fun findByProviderDocumentId(documentId: ProviderDocumentId): Contract?

    fun nextVersionNumber(leaseId: LeaseId): Int

    /** Existe contrato não final para a locação (R1). */
    fun existsOpenContract(leaseId: LeaseId): Boolean

    fun existsCompletedContract(leaseId: LeaseId): Boolean

    /** Contratos aguardando assinatura cujo prazo venceu. */
    fun findOverdueIds(now: Instant, limit: Int): List<ContractId>

    /** Contratos aguardando assinatura, enviados antes de [sentBefore], sem lembrete registrado. */
    fun findReminderDueIds(sentBefore: Instant, limit: Int): List<ContractId>

    /**
     * Contratos não finais, com documento no provedor, sem atualização nem consulta desde [staleBefore], do mais
     * antigo para o mais novo. Já traz o documento do provedor: quem reconcilia não precisa carregar o agregado.
     */
    fun findReconciliationCandidates(staleBefore: Instant, limit: Int): List<ReconciliationCandidate>
}

/**
 * Contrato que a reconciliação deve consultar no provedor, na forma mínima para a consulta.
 *
 * **Responsabilidade:**
 * - Levar o identificador do contrato e o do documento no provedor, para consultar o provedor sem carregar o agregado [Contract] (guia, §6.4).
 */
data class ReconciliationCandidate(val id: ContractId, val providerDocumentId: ProviderDocumentId)

/**
 * Porta de saída que registra apenas que a reconciliação consultou o provedor para um contrato.
 *
 * **Responsabilidade:**
 * - Gravar `last_reconciled_at` sem reescrever o agregado: sem signatários, sem histórico e sem nova versão otimista, para o caso em que a consulta não mudou nada.
 * - Adiar a próxima consulta do contrato (ordem por idade efetiva dos candidatos) sem alterar o status nem `updated_at` (R5).
 */
interface ReconciliationMarkerPort {
    /** Marca [contractId] como consultado em [at]. Não toca em `updated_at` nem na versão otimista. */
    fun markReconciled(contractId: ContractId, at: Instant)
}

/**
 * Porta de saída para consultas de leitura especializadas de contratos, desacopladas do carregamento do agregado.
 *
 * **Responsabilidade:**
 * - Recuperar projeções de detalhe ([ContractDetailView]) e histórico de auditoria ([HistoryEntryView]) para a camada de apresentação.
 * - Evitar a sobrecarga de reconstituição da raiz de agregação em cenários somente-leitura.
 */
interface ContractQueryPort {
    fun findDetail(id: ContractId): ContractDetailView?

    /** `null` se o contrato não existe; lista vazia nunca ocorre para um contrato existente. */
    fun findHistory(id: ContractId): List<HistoryEntryView>?
}

/**
 * Porta de saída para controle de execução idempotente de efeitos colaterais pós-assinatura (Regras R6 e R8).
 *
 * **Responsabilidade:**
 * - Registrar de forma atômica no banco de dados a execução de ações pós-assinatura (ativação da locação e, simuladas, vistoria de entrada e primeira cobrança); o arquivamento do PDF não passa por esta porta.
 * - Garantir semântica *at-most-once* por ação e por contrato, impedindo duplicidade em caso de reprocessamento.
 */
interface PostSignatureActionsPort {
    /** Registra a ação. Devolve `false` se ela já havia sido executada. */
    fun registerIfAbsent(contractId: ContractId, actionType: String, status: String, detailsJson: String?): Boolean
}

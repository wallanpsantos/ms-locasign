package br.com.locasign.contract.app.ports.out.repository

import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import java.time.Instant

/** Porta de escrita do agregado `Contract`. */
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

	/** Contratos não finais, com documento no provedor, sem atualização nem consulta desde [staleBefore]. */
	fun findReconciliationCandidateIds(staleBefore: Instant, limit: Int): List<ContractId>
}

/** Porta de leitura: modelos prontos para a API, sem carregar o agregado. */
interface ContractQueryPort {
	fun findDetail(id: ContractId): ContractDetailView?

	/** `null` se o contrato não existe; lista vazia nunca ocorre para um contrato existente. */
	fun findHistory(id: ContractId): List<HistoryEntryView>?
}

/** Efeitos pós-assinatura executados no máximo uma vez por contrato (R6, R8). */
interface PostSignatureActionsPort {
	/** Registra a ação. Devolve `false` se ela já havia sido executada. */
	fun registerIfAbsent(contractId: ContractId, actionType: String, status: String, detailsJson: String?): Boolean
}

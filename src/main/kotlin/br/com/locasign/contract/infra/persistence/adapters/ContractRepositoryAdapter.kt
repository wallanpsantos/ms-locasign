package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.StatusHistoryEntry
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.infra.persistence.mappers.toDomain
import br.com.locasign.contract.infra.persistence.mappers.toEntity
import br.com.locasign.contract.infra.persistence.repositories.ContractJdbcRepository
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.infra.persistence.toDb
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Adaptador de persistência que implementa a porta de escrita [ContractRepositoryPort].
 *
 * **Responsabilidade:**
 * - Salvar atomicamente o agregado [Contract], os signatários associados e descarregar as linhas de auditoria pendentes na tabela `contract_status_history`.
 * - Tratar violações de unicidade de contrato ativo (índice parcial da Regra R1) convertendo erros relacionais em [DomainException.ActiveContractExists].
 * - Executar consultas especializadas com SQL otimizado para expiração, lembretes e reconciliação em lote.
 */
@Repository
class ContractRepositoryAdapter(
    private val repository: ContractJdbcRepository,
    private val jdbc: JdbcClient,
) : ContractRepositoryPort {

    override fun save(contract: Contract) {
        val history = contract.pullHistory()
        try {
            repository.save(contract.toEntity())
        } catch (e: RuntimeException) {
            // R1 (índice único parcial) e a numeração de versões viram 409, não 500.
            if (isActiveContractViolation(e)) {
                throw DomainException.ActiveContractExists(
                    contract.leaseId.toString(),
                    "Já existe um contrato em andamento para esta locação.",
                )
            }
            throw e
        }
        history.forEach(::insertHistory)
    }

    override fun findById(id: ContractId): Contract? =
        repository.findByIdOrNull(id.value.toJavaUuid())?.toDomain()

    override fun findByProviderDocumentId(documentId: ProviderDocumentId): Contract? =
        repository.findByProviderDocumentId(documentId.value)?.toDomain()

    override fun nextVersionNumber(leaseId: LeaseId): Int =
        jdbc.sql("SELECT COALESCE(MAX(version_number), 0) + 1 FROM contracts WHERE lease_id = :leaseId")
            .param("leaseId", leaseId.value.toJavaUuid())
            .query(Int::class.java)
            .single()

    override fun existsOpenContract(leaseId: LeaseId): Boolean =
        jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM contracts WHERE lease_id = :leaseId AND status NOT IN (:finalStatuses))",
        )
            .param("leaseId", leaseId.value.toJavaUuid())
            .param("finalStatuses", FINAL_STATUSES)
            .query(Boolean::class.java)
            .single()

    override fun existsCompletedContract(leaseId: LeaseId): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM contracts WHERE lease_id = :leaseId AND status = 'COMPLETED')")
            .param("leaseId", leaseId.value.toJavaUuid())
            .query(Boolean::class.java)
            .single()

    override fun findOverdueIds(now: Instant, limit: Int): List<ContractId> =
        jdbc.sql(
            """
			SELECT id FROM contracts
			WHERE status IN (:awaiting) AND expires_at <= :now
			ORDER BY expires_at
			LIMIT :limit
			""".trimIndent(),
        )
            .param("awaiting", AWAITING_STATUSES)
            .param("now", now.toDb())
            .param("limit", limit)
            .queryIds()

    override fun findReminderDueIds(sentBefore: Instant, limit: Int): List<ContractId> =
        jdbc.sql(
            """
			SELECT id FROM contracts
			WHERE status IN (:awaiting) AND sent_at <= :sentBefore AND reminder_sent_at IS NULL
			ORDER BY sent_at
			LIMIT :limit
			""".trimIndent(),
        )
            .param("awaiting", AWAITING_STATUSES)
            .param("sentBefore", sentBefore.toDb())
            .param("limit", limit)
            .queryIds()

    override fun findReconciliationCandidateIds(staleBefore: Instant, limit: Int): List<ContractId> =
        jdbc.sql(
            """
			SELECT id FROM contracts
			WHERE status NOT IN (:finalStatuses)
			  AND provider_document_id IS NOT NULL
			  AND GREATEST(updated_at, COALESCE(last_reconciled_at, updated_at)) <= :staleBefore
			ORDER BY GREATEST(updated_at, COALESCE(last_reconciled_at, updated_at))
			LIMIT :limit
			""".trimIndent(),
        )
            .param("finalStatuses", FINAL_STATUSES)
            .param("staleBefore", staleBefore.toDb())
            .param("limit", limit)
            .queryIds()

    private fun insertHistory(entry: StatusHistoryEntry) {
        jdbc.sql(
            """
			INSERT INTO contract_status_history
			    (id, contract_id, from_status, to_status, outcome, source, source_event_id, note, occurred_at)
			VALUES
			    (:id, :contractId, :fromStatus, :toStatus, :outcome, :source, :sourceEventId, :note, :occurredAt)
			""".trimIndent(),
        )
            .param("id", entry.id.toJavaUuid())
            .param("contractId", entry.contractId.value.toJavaUuid())
            .param("fromStatus", entry.fromStatus?.name)
            .param("toStatus", entry.toStatus.name)
            .param("outcome", entry.outcome.name)
            .param("source", entry.source.name)
            .param("sourceEventId", entry.sourceEventId)
            .param("note", entry.note)
            .param("occurredAt", entry.occurredAt.toDb())
            .update()
    }

    private fun JdbcClient.StatementSpec.queryIds(): List<ContractId> =
        query { rs, _ -> ContractId(rs.getObject("id", java.util.UUID::class.java).toKotlinUuid()) }.list()

    /** O Spring Data JDBC embrulha a violação em `DbActionExecutionException`; a causa raiz traz o nome da constraint. */
    private fun isActiveContractViolation(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            VIOLATED_CONSTRAINTS.any { constraint -> cause.message.orEmpty().contains(constraint) }
        }

    private companion object {
        val FINAL_STATUSES: List<String> = ContractStatus.FINAL.map { it.name }
        val AWAITING_STATUSES: List<String> = ContractStatus.entries.filter { it.isAwaitingSignatures }.map { it.name }
        val VIOLATED_CONSTRAINTS = listOf("ux_contracts_one_active_per_lease", "uq_contracts_lease_version")
    }
}

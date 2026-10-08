package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.app.queries.ContractDetailView
import br.com.locasign.contract.app.queries.HistoryEntryView
import br.com.locasign.contract.app.queries.SignerView
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.infra.persistence.getInstant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import kotlin.uuid.toJavaUuid

/** Leitura direta do banco, sem carregar o agregado ("queries can bypass domain layer"). */
@Repository
class ContractQueryAdapter(private val jdbc: JdbcClient) : ContractQueryPort {

	override fun findDetail(id: ContractId): ContractDetailView? {
		val uuid = id.value.toJavaUuid()
		val signers = jdbc.sql(
			"""
			SELECT role, name, email, signing_order, completed_at
			FROM contract_signers WHERE contract_id = :id ORDER BY signing_order
			""".trimIndent(),
		)
			.param("id", uuid)
			.query { rs, _ ->
				SignerView(
					role = rs.getString("role"),
					name = rs.getString("name"),
					email = rs.getString("email"),
					signingOrder = rs.getInt("signing_order"),
					completedAt = rs.getInstant("completed_at"),
				)
			}
			.list()

		return jdbc.sql(
			"""
			SELECT id, lease_id, version_number, status, provider_document_id, sent_at, expires_at,
			       cancel_reason, signed_document_ref, created_at, updated_at
			FROM contracts WHERE id = :id
			""".trimIndent(),
		)
			.param("id", uuid)
			.query { rs, _ ->
				ContractDetailView(
					id = rs.getString("id"),
					leaseId = rs.getString("lease_id"),
					versionNumber = rs.getInt("version_number"),
					status = rs.getString("status"),
					providerDocumentId = rs.getString("provider_document_id"),
					sentAt = rs.getInstant("sent_at"),
					expiresAt = rs.getInstant("expires_at"),
					cancelReason = rs.getString("cancel_reason"),
					signedDocumentRef = rs.getString("signed_document_ref"),
					signers = signers,
					createdAt = checkNotNull(rs.getInstant("created_at")),
					updatedAt = checkNotNull(rs.getInstant("updated_at")),
				)
			}
			.optional()
			.orElse(null)
	}

	override fun findHistory(id: ContractId): List<HistoryEntryView>? {
		val uuid = id.value.toJavaUuid()
		val exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM contracts WHERE id = :id)")
			.param("id", uuid)
			.query(Boolean::class.java)
			.single()
		if (!exists) return null

		return jdbc.sql(
			"""
			SELECT id, from_status, to_status, outcome, source, source_event_id, note, occurred_at
			FROM contract_status_history WHERE contract_id = :id ORDER BY seq
			""".trimIndent(),
		)
			.param("id", uuid)
			.query { rs, _ ->
				HistoryEntryView(
					id = rs.getString("id"),
					fromStatus = rs.getString("from_status"),
					toStatus = rs.getString("to_status"),
					outcome = rs.getString("outcome"),
					source = rs.getString("source"),
					sourceEventId = rs.getString("source_event_id"),
					note = rs.getString("note"),
					occurredAt = checkNotNull(rs.getInstant("occurred_at")),
				)
			}
			.list()
	}
}

@Repository
class PostSignatureActionsAdapter(private val jdbc: JdbcClient) : PostSignatureActionsPort {

	override fun registerIfAbsent(
		contractId: ContractId,
		actionType: String,
		status: String,
		detailsJson: String?,
	): Boolean = jdbc.sql(
		"""
		INSERT INTO post_signature_actions (contract_id, action_type, status, details)
		VALUES (:contractId, :actionType, :status, CAST(:details AS jsonb))
		ON CONFLICT (contract_id, action_type) DO NOTHING
		""".trimIndent(),
	)
		.param("contractId", contractId.value.toJavaUuid())
		.param("actionType", actionType)
		.param("status", status)
		.param("details", detailsJson)
		.update() == 1
}

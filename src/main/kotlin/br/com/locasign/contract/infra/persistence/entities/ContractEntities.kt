package br.com.locasign.contract.infra.persistence.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

/**
 * Registro de persistência do agregado `Contract` (Spring Data JDBC, ADR-003). Os signatários são
 * filhos do agregado: a cada `save` o Spring Data JDBC reescreve esse conjunto na mesma transação.
 * O `rowVersion` nulo marca a inserção e depois implementa o lock otimista.
 */
@Table("contracts")
data class ContractEntity(
    @Id val id: UUID,
    val leaseId: UUID,
    val versionNumber: Int,
    val status: String,
    val providerDocumentId: String?,
    val providerLastModifiedAt: Instant?,
    val sentAt: Instant?,
    val expiresAt: Instant?,
    val reminderSentAt: Instant?,
    val lastReconciledAt: Instant?,
    val cancelReason: String?,
    val signedDocumentRef: String?,
    @Version val rowVersion: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @MappedCollection(idColumn = "contract_id")
    val signers: Set<ContractSignerEntity>,
)

@Table("contract_signers")
data class ContractSignerEntity(
    val role: String,
    val name: String,
    val email: String,
    val signingOrder: Int,
    val completedAt: Instant?,
)

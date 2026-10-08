package br.com.locasign.contract.infra.persistence.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

/**
 * Entidade de persistência relacional do agregado [Contract] para o Spring Data JDBC (ADR-003).
 *
 * **Responsabilidade:**
 * - Mapear as colunas da tabela `contracts`, incluindo timestamps, controle de concorrência otimista (`@Version rowVersion`) e signatários associados como coleção embutida.
 * - Garantir reescrita atômica do agregado completo em cada operação de salvamento do repositório.
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

/**
 * Entidade de persistência dos signatários vinculados ao contrato de locação (`contract_signers`).
 *
 * **Responsabilidade:**
 * - Representar os dados relacionais do signatário (papel, nome, e-mail, ordem de assinatura e instante de conclusão) como filho agregado da entidade [ContractEntity].
 */
@Table("contract_signers")
data class ContractSignerEntity(
    val role: String,
    val name: String,
    val email: String,
    val signingOrder: Int,
    val completedAt: Instant?,
)

package br.com.locasign.contract.infra.persistence.mappers

import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.contract.infra.persistence.entities.ContractEntity
import br.com.locasign.contract.infra.persistence.entities.ContractSignerEntity
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Email
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Converte o agregado de domínio [Contract] em sua entidade relacional [ContractEntity] para persistência via Spring Data JDBC.
 *
 * **Responsabilidade:**
 * - Desestruturar Value Objects em tipos compatíveis com o banco de dados (UUIDs Java, strings, timestamps e inteiros).
 * - Mapear os signatários associados garantindo integridade relacional.
 */
fun Contract.toEntity(): ContractEntity = ContractEntity(
    id = id.value.toJavaUuid(),
    leaseId = leaseId.value.toJavaUuid(),
    versionNumber = versionNumber,
    status = status.name,
    providerDocumentId = providerDocumentId?.value,
    providerLastModifiedAt = providerLastModifiedAt,
    sentAt = sentAt,
    expiresAt = expiresAt,
    reminderSentAt = reminderSentAt,
    lastReconciledAt = lastReconciledAt,
    cancelReason = cancelReason?.storageValue,
    signedDocumentRef = signedDocumentRef,
    rowVersion = rowVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
    signers = signers.map { it.toEntity() }.toSet(),
)

/**
 * Converte a entidade de domínio [Signer] em sua representação persistível [ContractSignerEntity].
 *
 * **Responsabilidade:**
 * - Extrair e-mail, ordem de assinatura e papel para armazenamento relacional.
 */
fun Signer.toEntity(): ContractSignerEntity = ContractSignerEntity(
    role = role.name,
    name = name,
    email = email.value,
    signingOrder = order.value,
    completedAt = completedAt,
)

/**
 * Reconstrói o agregado de domínio [Contract] a partir da entidade relacional [ContractEntity].
 *
 * **Responsabilidade:**
 * - Reconstituir todos os Value Objects tipados ([ContractId], [LeaseId], [ProviderDocumentId], [CancelReason]) sem disparar novos eventos de domínio.
 * - Ordenar os signatários recuperados por sua ordem estrita de assinatura.
 */
fun ContractEntity.toDomain(): Contract = Contract.restore(
    id = ContractId(id.toKotlinUuid()),
    leaseId = LeaseId(leaseId.toKotlinUuid()),
    versionNumber = versionNumber,
    status = ContractStatus.valueOf(status),
    providerDocumentId = providerDocumentId?.let(ProviderDocumentId::of),
    providerLastModifiedAt = providerLastModifiedAt,
    sentAt = sentAt,
    expiresAt = expiresAt,
    reminderSentAt = reminderSentAt,
    lastReconciledAt = lastReconciledAt,
    cancelReason = cancelReason?.let(CancelReason::fromStorage),
    signedDocumentRef = signedDocumentRef,
    signers = signers.map { it.toDomain() }.sortedBy { it.order.value },
    createdAt = createdAt,
    updatedAt = updatedAt,
    rowVersion = rowVersion,
)

fun ContractSignerEntity.toDomain(): Signer = Signer(
    role = SignerRole.valueOf(role),
    name = name,
    email = Email.fromStorage(email),
    order = SigningOrder.of(signingOrder),
    completedAt = completedAt,
)

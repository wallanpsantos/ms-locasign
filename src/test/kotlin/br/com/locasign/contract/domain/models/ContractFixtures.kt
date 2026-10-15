package br.com.locasign.contract.domain.models

import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate

val DEFAULT_TEST_NOW: Instant = Instant.parse("2026-10-09T10:00:00Z")

fun defaultSigners(): List<Signer> = listOf(
    Signer(SignerRole.TENANT, "Juliana Mendes", Email.of("juliana@example.com"), SigningOrder.of(1)),
    Signer(SignerRole.AGENCY, "Imobiliária Alfa", Email.of("alfa@example.com"), SigningOrder.of(2)),
)

/**
 * Cria uma instância canônica de [LeaseSnapshot] para testes do módulo contract com valores válidos (CPF mod 11).
 */
fun sampleLeaseSnapshot(
    leaseId: LeaseId = LeaseId.new(),
    tenantName: String = "Ana Paula",
    tenantCpf: Cpf = Cpf.of("529.982.247-25"),
    tenantEmail: Email = Email.of("ana@example.com"),
    agencySignerName: String = "Imobiliária Prime",
    agencySignerEmail: Email = Email.of("prime@imobiliaria.com"),
    propertyAddress: String = "Rua Bela Cintra, 500",
    rentAmount: Money = Money.positive("4200.00"),
    startDate: LocalDate = LocalDate.of(2026, 11, 1),
    termMonths: Int = 30,
): LeaseSnapshot = LeaseSnapshot(
    leaseId = leaseId,
    tenantName = tenantName,
    tenantCpf = tenantCpf,
    tenantEmail = tenantEmail,
    agencySignerName = agencySignerName,
    agencySignerEmail = agencySignerEmail,
    propertyAddress = propertyAddress,
    rentAmount = rentAmount,
    startDate = startDate,
    termMonths = termMonths,
)

/**
 * Reconstitui um [Contract] em qualquer status, sem eventos nem histórico pendentes, para montar cenários de teste.
 *
 * Os campos opcionais do ciclo de vida começam vazios, `createdAt` e `updatedAt` valem `now` e o agregado já
 * nasce com versão de persistência, como se tivesse sido lido do banco.
 */
fun restoreContract(
    status: ContractStatus,
    leaseId: LeaseId = LeaseId.new(),
    signers: List<Signer> = defaultSigners(),
    now: Instant = DEFAULT_TEST_NOW,
    id: ContractId = ContractId.new(),
    versionNumber: Int = 1,
    providerDocumentId: ProviderDocumentId? = null,
    providerLastModifiedAt: Instant? = null,
    sentAt: Instant? = null,
    expiresAt: Instant? = null,
    cancelReason: CancelReason? = null,
    signedDocumentRef: String? = null,
    createdAt: Instant = now,
): Contract = Contract.restore(
    id = id,
    leaseId = leaseId,
    versionNumber = versionNumber,
    status = status,
    providerDocumentId = providerDocumentId,
    providerLastModifiedAt = providerLastModifiedAt,
    sentAt = sentAt,
    expiresAt = expiresAt,
    reminderSentAt = null,
    lastReconciledAt = null,
    cancelReason = cancelReason,
    signedDocumentRef = signedDocumentRef,
    signers = signers,
    createdAt = createdAt,
    updatedAt = now,
    rowVersion = 1L,
)

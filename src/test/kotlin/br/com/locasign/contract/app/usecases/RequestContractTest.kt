package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RequestContractTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val clock = FixedBusinessClock(now)
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()
    private val persister = ContractPersister(contracts, publisher)
    private val leases = FakeLeaseLookup()

    private val requestContract = RequestContract(
        leases = leases,
        contracts = contracts,
        persister = persister,
        clock = clock,
        transactions = ImmediateTransactionRunner,
    )

    private val leaseId = LeaseId.new()
    private val leaseSnapshot = LeaseSnapshot(
        leaseId = leaseId,
        tenantName = "Ana Paula",
        tenantCpf = Cpf.of("529.982.247-25"),
        tenantEmail = Email.of("ana@example.com"),
        agencySignerName = "Imobiliária Prime",
        agencySignerEmail = Email.of("prime@imobiliaria.com"),
        propertyAddress = "Rua Bela Cintra, 500",
        rentAmount = Money.positive("4200.00"),
        startDate = LocalDate.of(2026, 11, 1),
        termMonths = 30,
    )

    @Test
    fun `solicitação inicial cria versão 1 em DRAFT e emite ContractRequested (R1, R3)`() {
        leases.register(leaseSnapshot)

        val contractId = requestContract.execute(RequestContractCommand(leaseId))

        val saved = contracts.findById(contractId)
        assertNotNull(saved)
        assertEquals(ContractStatus.DRAFT, saved.status)
        assertEquals(1, saved.versionNumber)
        assertEquals(2, saved.signers.size)
        assertEquals(SignerRole.TENANT, saved.signers[0].role)
        assertEquals(SignerRole.AGENCY, saved.signers[1].role)

        assertEquals(1, publisher.published.size)
        assertIs<ContractRequested>(publisher.published.first())
    }

    @Test
    fun `impede criação de novo contrato se já existe contrato em andamento (R1)`() {
        leases.register(leaseSnapshot)
        requestContract.execute(RequestContractCommand(leaseId))

        // Tentativa de solicitar segundo contrato para a mesma locação
        val ex = assertFailsWith<DomainException.ActiveContractExists> {
            requestContract.execute(RequestContractCommand(leaseId))
        }
        assertEquals(leaseId.toString(), ex.leaseId)
    }

    @Test
    fun `cria versão 2 após cancelamento, expiração ou recusa da versão 1 (R9)`() {
        leases.register(leaseSnapshot)
        val v1Id = requestContract.execute(RequestContractCommand(leaseId))

        // Simula que v1 foi cancelado
        val v1 = contracts.findById(v1Id)!!
        val v1Cancelled = Contract.restore(
            id = v1.id,
            leaseId = v1.leaseId,
            versionNumber = v1.versionNumber,
            status = ContractStatus.CANCELLED,
            providerDocumentId = null,
            providerLastModifiedAt = null,
            sentAt = null,
            expiresAt = null,
            reminderSentAt = null,
            lastReconciledAt = null,
            cancelReason = CancelReason.Requested("Cancelado pelo usuário"),
            signedDocumentRef = null,
            signers = v1.signers,
            createdAt = v1.createdAt,
            updatedAt = now,
            rowVersion = 1L,
        )
        contracts.save(v1Cancelled)

        // Agora deve permitir criar v2
        val v2Id = requestContract.execute(RequestContractCommand(leaseId))
        val v2 = contracts.findById(v2Id)!!
        assertEquals(2, v2.versionNumber)
        assertEquals(ContractStatus.DRAFT, v2.status)
    }

    @Test
    fun `impede criação de novo contrato após contrato anterior concluído (R9, ADR-013)`() {
        leases.register(leaseSnapshot)
        val v1Id = requestContract.execute(RequestContractCommand(leaseId))

        // Simula que v1 foi concluído
        val v1 = contracts.findById(v1Id)!!
        val v1Completed = Contract.restore(
            id = v1.id,
            leaseId = v1.leaseId,
            versionNumber = v1.versionNumber,
            status = ContractStatus.COMPLETED,
            providerDocumentId = null,
            providerLastModifiedAt = null,
            sentAt = null,
            expiresAt = null,
            reminderSentAt = null,
            lastReconciledAt = null,
            cancelReason = null,
            signedDocumentRef = "/path/doc.pdf",
            signers = v1.signers,
            createdAt = v1.createdAt,
            updatedAt = now,
            rowVersion = 1L,
        )
        contracts.save(v1Completed)

        assertFailsWith<DomainException.ActiveContractExists> {
            requestContract.execute(RequestContractCommand(leaseId))
        }
    }
}

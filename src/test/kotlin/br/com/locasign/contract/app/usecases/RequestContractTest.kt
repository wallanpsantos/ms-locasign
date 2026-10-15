package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.FakeLeaseLookup
import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.domain.events.ContractRequested
import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.models.sampleLeaseSnapshot
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.ImmediateTransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
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
    private val leaseSnapshot = sampleLeaseSnapshot(leaseId = leaseId)

    /** Reconstitui a versão salva num status final, simulando o encerramento do ciclo dela. */
    private fun finishContract(
        contractId: ContractId,
        status: ContractStatus,
        cancelReason: CancelReason? = null,
        signedDocumentRef: String? = null,
    ) {
        val current = assertNotNull(contracts.findById(contractId))
        contracts.save(
            restoreContract(
                status = status,
                leaseId = current.leaseId,
                signers = current.signers,
                now = now,
                id = current.id,
                versionNumber = current.versionNumber,
                cancelReason = cancelReason,
                signedDocumentRef = signedDocumentRef,
                createdAt = current.createdAt,
            ),
        )
    }

    @Test
    fun `solicitação inicial cria versão 1 em DRAFT e emite ContractRequested (R1, R3)`() {
        leases.register(leaseSnapshot)

        val contractId = requestContract.execute(RequestContractCommand(leaseId))

        val saved = assertNotNull(contracts.findById(contractId))
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

        val ex = assertFailsWith<DomainException.ActiveContractExists> {
            requestContract.execute(RequestContractCommand(leaseId))
        }
        assertEquals(leaseId.toString(), ex.leaseId)
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["CANCELLED", "EXPIRED", "DECLINED"])
    fun `cria versão 2 após cancelamento, expiração ou recusa da versão 1 (R9)`(finalStatus: ContractStatus) {
        leases.register(leaseSnapshot)
        val v1Id = requestContract.execute(RequestContractCommand(leaseId))
        val cancelReason =
            if (finalStatus == ContractStatus.CANCELLED) CancelReason.Requested("Cancelado pelo usuário") else null
        finishContract(v1Id, finalStatus, cancelReason = cancelReason)

        val v2Id = requestContract.execute(RequestContractCommand(leaseId))
        val v2 = assertNotNull(contracts.findById(v2Id))
        assertEquals(2, v2.versionNumber)
        assertEquals(ContractStatus.DRAFT, v2.status)
        assertEquals(finalStatus, contracts.findById(v1Id)?.status)
    }

    @Test
    fun `impede criação de novo contrato após contrato anterior concluído (R9, ADR-013)`() {
        leases.register(leaseSnapshot)
        val v1Id = requestContract.execute(RequestContractCommand(leaseId))
        finishContract(v1Id, ContractStatus.COMPLETED, signedDocumentRef = "/path/doc.pdf")

        assertFailsWith<DomainException.ActiveContractExists> {
            requestContract.execute(RequestContractCommand(leaseId))
        }
    }
}

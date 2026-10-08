package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException

data class RequestContractCommand(val leaseId: LeaseId)

/**
 * Cria o contrato em `DRAFT` (uma nova versão da locação) e emite `ContractRequested`.
 * Nenhuma chamada externa acontece aqui (princípio 2): a geração roda no consumidor do evento.
 */
class RequestContract(
    private val leases: LeaseLookupPort,
    private val contracts: ContractRepositoryPort,
    private val persister: ContractPersister,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
) {

    fun execute(command: RequestContractCommand): ContractId = transactions.run {
        val lease = leases.find(command.leaseId)
            ?: throw DomainException.NotFound("Locação", command.leaseId.toString())
        if (contracts.existsCompletedContract(command.leaseId)) {
            throw DomainException.ActiveContractExists(
                command.leaseId.toString(),
                "A locação já possui um contrato concluído.",
            )
        }
        if (contracts.existsOpenContract(command.leaseId)) {
            throw DomainException.ActiveContractExists(
                command.leaseId.toString(),
                "Já existe um contrato em andamento para esta locação. Cancele-o antes de gerar uma nova versão.",
            )
        }
        val contract = Contract.request(
            leaseId = command.leaseId,
            versionNumber = contracts.nextVersionNumber(command.leaseId),
            signers = listOf(
                Signer(SignerRole.TENANT, lease.tenantName, lease.tenantEmail, SigningOrder.of(TENANT_ORDER)),
                Signer(
                    SignerRole.AGENCY,
                    lease.agencySignerName,
                    lease.agencySignerEmail,
                    SigningOrder.of(AGENCY_ORDER)
                ),
            ),
            now = clock.now(),
        )
        persister.save(contract)
        contract.id
    }

    private companion object {
        // R3: primeiro o locatário, depois o representante da imobiliária.
        const val TENANT_ORDER = 1
        const val AGENCY_ORDER = 2
    }
}

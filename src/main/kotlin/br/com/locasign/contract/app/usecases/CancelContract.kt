package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.slf4j.LoggerFactory

/**
 * Parâmetros de comando para cancelamento voluntário de um contrato de locação em andamento.
 *
 * **Responsabilidade:**
 * - Conter o identificador do contrato ([contractId]) e a justificativa formal do cancelamento ([reason]).
 */
data class CancelContractCommand(val contractId: ContractId, val reason: String)

/**
 * Caso de uso síncrono responsável por cancelar um contrato por solicitação do corretor ou operador (Regra R9).
 *
 * **Responsabilidade:**
 * - Validar a justificativa de cancelamento e aplicar a transição para `CANCELLED` no agregado [Contract].
 * - Persistir a alteração transacionalmente e, subsequentemente fora da transação, anular o documento no provedor externo em melhor esforço para impedir assinaturas residuais.
 */
class CancelContract(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
    private val persister: ContractPersister,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: CancelContractCommand) {
        val reason = command.reason.trim()
        if (reason.isEmpty() || reason.length > MAX_REASON_LENGTH) {
            throw DomainException.BusinessRuleViolation(
                "reason",
                "O motivo do cancelamento é obrigatório (até $MAX_REASON_LENGTH caracteres).",
            )
        }
        val documentToVoid = transactions.run {
            val contract = contracts.findById(command.contractId)
                ?: throw DomainException.NotFound("Contrato", command.contractId.toString())
            val wasSent = contract.status.progress >= ContractStatus.SENT.progress && !contract.status.isFinal
            contract.cancel(reason, clock.now())
            persister.save(contract)
            contract.providerDocumentId.takeIf { wasSent }
        }
        documentToVoid?.let { provider.cancelDocumentQuietly(it, log, "Contrato cancelado") }
    }

    private companion object {
        const val MAX_REASON_LENGTH = 500
    }
}

package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException

/**
 * Parâmetros de comando para cancelamento voluntário de um contrato de locação em andamento.
 *
 * **Responsabilidade:**
 * - Conter o identificador do contrato ([contractId]) e a justificativa formal do cancelamento ([reason]).
 */
data class CancelContractCommand(val contractId: ContractId, val reason: String)

/**
 * Caso de uso acionado pela requisição HTTP que cancela um contrato por solicitação do corretor ou operador (Regra R9).
 *
 * **Responsabilidade:**
 * - Validar a justificativa de cancelamento e aplicar a transição para `CANCELLED` no agregado [br.com.locasign.contract.domain.models.Contract].
 * - Persistir a alteração e o evento `ContractCancelled` no outbox na mesma transação (R5, R6).
 * - Não chamar a PandaDoc (regra inviolável 9): a anulação do documento é assíncrona e fica com [AnnulProviderDocument], disparado por esse evento (ADR-016).
 */
class CancelContract(
    private val contracts: ContractRepositoryPort,
    private val persister: ContractPersister,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
) {

    fun execute(command: CancelContractCommand) {
        val reason = command.reason.trim()
        if (reason.isEmpty() || reason.length > MAX_REASON_LENGTH) {
            throw DomainException.BusinessRuleViolation(
                "reason",
                "O motivo do cancelamento é obrigatório (até $MAX_REASON_LENGTH caracteres).",
            )
        }
        transactions.run {
            val contract = contracts.findById(command.contractId)
                ?: throw DomainException.NotFound("Contrato", command.contractId.toString())
            contract.cancel(reason, clock.now())
            persister.save(contract)
        }
    }

    private companion object {
        const val MAX_REASON_LENGTH = 500
    }
}

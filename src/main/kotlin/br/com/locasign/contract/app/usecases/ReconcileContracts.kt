package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.slf4j.LoggerFactory

/**
 * Caso de uso agendado e sob demanda responsável por sincronizar ativamente o estado de contratos junto ao provedor externo (Regra R6).
 *
 * **Responsabilidade:**
 * - Mitigar a eventual perda de webhooks consultando periodicamente contratos inalterados há tempo excessivo (`stale`).
 * - Traduzir a fotografia remota ([br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState]) em sinais neutros e submetê-los a [ApplyProviderUpdate].
 * - Fornecer mecanismo de reconciliação pontual forçada para suporte operacional e desenvolvimento.
 */
class ReconcileContracts(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
    private val applyUpdate: ApplyProviderUpdate,
    private val clock: BusinessClock,
    private val settings: ContractSettings,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reconcilia os contratos parados há mais tempo que o limite. Devolve quantos foram consultados. */
    fun executeStale(): Int {
        val staleBefore = clock.now().minus(settings.reconciliationStaleAfter)
        val ids = contracts.findReconciliationCandidateIds(staleBefore, settings.jobBatchSize)
        return ids.count { reconcileSafely(it) }
    }

    /** Reconciliação forçada de um contrato (operação e desenvolvimento). */
    fun executeFor(contractId: ContractId) {
        val exists = transactions.run { contracts.findById(contractId) != null }
        if (!exists) throw DomainException.NotFound("Contrato", contractId.toString())
        reconcile(contractId)
    }

    private fun reconcileSafely(id: ContractId): Boolean = try {
        reconcile(id)
    } catch (e: ProviderException) {
        log.warn("Reconciliação do contrato {} adiada: {}", id, e.message)
        false
    } catch (e: RuntimeException) {
        log.error("Falha na reconciliação do contrato {}", id, e)
        false
    }

    private fun reconcile(id: ContractId): Boolean {
        val documentId = transactions.run { contracts.findById(id)?.providerDocumentId } ?: return false
        val state = provider.fetchState(documentId)

        val signals = buildList<ProviderSignal> {
            if (state.completedRoles.isNotEmpty()) {
                add(ProviderSignal.RecipientCompleted(state.documentId, id, state.completedRoles, state.modifiedAt))
            }
            add(ProviderSignal.StatusChanged(state.documentId, id, state.status, state.modifiedAt))
        }
        applyUpdate.execute(
            ApplyProviderUpdateCommand(
                signals = signals,
                source = ChangeSource.RECONCILIATION,
                sourceEventId = "reconcile:$id:${clock.now().toEpochMilli()}",
                markReconciled = true,
            ),
        )
        return true
    }
}

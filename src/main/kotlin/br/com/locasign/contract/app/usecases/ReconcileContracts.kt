package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.`in`.ReconcileContractPort
import br.com.locasign.contract.app.ports.`in`.ReconciledContractSummary
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException
import org.slf4j.LoggerFactory

/**
 * Caso de uso agendado e sob demanda responsável por sincronizar ativamente o estado de contratos junto ao provedor externo (guia, §6.4).
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
) : ReconcileContractPort {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reconcilia os contratos parados há mais tempo que o limite. Devolve quantos foram consultados. */
    fun executeStale(): Int {
        val staleBefore = clock.now().minus(settings.reconciliationStaleAfter)
        val candidates = contracts.findReconciliationCandidates(staleBefore, settings.jobBatchSize)
        var count = 0
        for (candidate in candidates) {
            try {
                reconcileDocument(candidate.id, candidate.providerDocumentId)
                count++
            } catch (e: ProviderException.RateLimited) {
                log.warn("Reconciliação interrompida por limite de taxa da PandaDoc: {}", e.message)
                break
            } catch (e: ProviderException) {
                log.warn("Reconciliação do contrato {} adiada: {}", candidate.id, e.message)
            } catch (e: RuntimeException) {
                log.error("Falha na reconciliação do contrato {}", candidate.id, e)
            }
        }
        return count
    }

    /** Reconciliação forçada de um contrato (operação e desenvolvimento, ADR-018). */
    override fun executeFor(contractId: ContractId): ReconciledContractSummary {
        val (leaseId, status, documentId) = transactions.run {
            val contract = contracts.findById(contractId)
                ?: throw DomainException.NotFound("Contrato", contractId.toString())
            Triple(contract.leaseId, contract.status, contract.providerDocumentId)
        }
        if (documentId == null) {
            log.info("Contrato {} ainda não possui documento no provedor; reconciliação ignorada", contractId)
            return ReconciledContractSummary(leaseId, status)
        }
        val updatedStatus = reconcileDocument(contractId, documentId) ?: status
        return ReconciledContractSummary(leaseId, updatedStatus)
    }

    private fun reconcileDocument(id: ContractId, documentId: ProviderDocumentId): ContractStatus? {
        val state = provider.fetchState(documentId)

        val signals = buildList {
            if (state.completedRoles.isNotEmpty()) {
                add(ProviderSignal.RecipientCompleted(state.documentId, id, state.completedRoles, state.modifiedAt))
            }
            add(ProviderSignal.StatusChanged(state.documentId, id, state.status, state.modifiedAt))
        }
        return applyUpdate.execute(
            ApplyProviderUpdateCommand(
                signals = signals,
                source = ChangeSource.RECONCILIATION,
                sourceEventId = "reconcile:$id:${clock.now().toEpochMilli()}",
                markReconciled = true,
            ),
        )
    }
}

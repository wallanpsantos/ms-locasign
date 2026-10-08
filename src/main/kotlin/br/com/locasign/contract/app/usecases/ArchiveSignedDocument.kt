package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.app.ports.out.integration.SignedDocumentStoragePort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

data class ArchiveSignedDocumentCommand(val eventId: String, val documentId: ProviderDocumentId)

/**
 * Reação a `document_completed_pdf_ready`: baixa e arquiva o PDF assinado. No sandbox o download
 * não existe (exige chave de produção), então só a referência do documento é guardada.
 */
class ArchiveSignedDocument(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
    private val storage: SignedDocumentStoragePort,
    private val persister: ContractPersister,
    private val processed: ProcessedMessagesPort,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: ArchiveSignedDocumentCommand) {
        val contractId = transactions.run { findCompletedContract(command) } ?: return

        val signed = provider.downloadSigned(command.documentId)
        val (storageKind, location) = when (signed) {
            is SignedDocument.Available -> "FILE" to storage.store(contractId, signed.bytes)
            is SignedDocument.Unavailable -> {
                log.info("Download indisponível para o documento {}: {}", command.documentId, signed.reason)
                "REFERENCE_ONLY" to "provider:${command.documentId}"
            }
        }

        transactions.run {
            if (!processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)) return@run
            val contract = contracts.findById(contractId) ?: return@run
            if (contract.markSignedDocumentArchived(
                    storageKind,
                    location,
                    ChangeSource.WEBHOOK,
                    command.eventId,
                    clock.now()
                )
            ) {
                persister.save(contract)
            }
        }
    }

    private fun findCompletedContract(command: ArchiveSignedDocumentCommand): ContractId? {
        if (processed.isProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)) return null
        val contract = contracts.findByProviderDocumentId(command.documentId)
        if (contract == null || contract.endedWithoutCompletion()) {
            // Sem contrato, ou contrato expirado, cancelado ou recusado: não há PDF assinado a arquivar e
            // retentar nunca resolveria. O evento já está no inbox (R7).
            if (contract != null) log.info(
                "Contrato {} terminou em {}; PDF assinado ignorado",
                contract.id,
                contract.status
            )
            processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)
            return null
        }
        // O PDF só fica pronto depois da conclusão. Se o status ainda não chegou, retenta (backoff do consumidor).
        check(contract.status == ContractStatus.COMPLETED) {
            "Contrato ${contract.id} ainda não está concluído; o PDF será arquivado na próxima tentativa"
        }
        return contract.id
    }

    private fun Contract.endedWithoutCompletion(): Boolean = status.isFinal && status != ContractStatus.COMPLETED
}

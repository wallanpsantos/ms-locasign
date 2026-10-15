package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Caso de uso assíncrono que anula na PandaDoc o documento de um contrato cancelado ou expirado, para que ninguém mais o assine (ADR-016).
 *
 * **Responsabilidade:**
 * - Reagir aos eventos `ContractCancelled` e `ContractExpired`, que `CancelContract` e `ExpireOverdueContracts` já gravam no outbox, e chamar `SignatureProviderPort.cancelDocument` fora da requisição HTTP e dos jobs (regra inviolável 9).
 * - Anular só o documento que chegou a ser enviado: o contrato precisa estar `CANCELLED` ou `EXPIRED`, ter documento no provedor e ter `sentAt`, porque a anulação só vale a partir de `sent`/`viewed` (ADR-012).
 * - Tratar 404/409 (documento ausente, já anulado ou concluído) e as falhas definitivas (`Forbidden`, `Rejected`) como resolvidas, sem retentativa inútil. `RateLimited` e `Unavailable` sobem para o `DefaultErrorHandler`, que retenta e, esgotadas as tentativas, envia para a DLT.
 * - Ser idempotente (R6): a chamada externa fica entre duas transações curtas e o evento só é marcado com `markProcessed` depois dela, para que uma falha não pule uma anulação que não aconteceu.
 */
class AnnulProviderDocument(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
    private val processed: ProcessedMessagesPort,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: ContractEventCommand) {
        val documentId = transactions.run { prepare(command) } ?: return
        try {
            provider.cancelDocument(documentId)
        } catch (e: ProviderException) {
            when (e) {
                // 404/409: o documento não existe mais ou já está anulado ou concluído; não há o que anular.
                is ProviderException.NotReady ->
                    log.info("Documento {} já resolvido no provedor; anulação encerrada: {}", documentId, e.message)

                // Falha definitiva: retentar nunca resolveria e só levaria a mensagem para a DLT.
                is ProviderException.Forbidden, is ProviderException.Rejected ->
                    log.warn("Provedor recusou a anulação do documento {}: {}", documentId, e.message)

                // Falha transitória: o evento continua sem marca e o Kafka retenta.
                is ProviderException.RateLimited, is ProviderException.Unavailable -> throw e
            }
        }
        transactions.run { processed.markProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, command.eventId) }
    }

    /** Devolve o documento a anular, ou `null` (com o evento já marcado) se o evento está duplicado ou não há o que anular. */
    private fun prepare(command: ContractEventCommand): ProviderDocumentId? {
        if (processed.isProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, command.eventId)) return null
        val documentId = contracts.findById(command.contractId)?.documentToAnnul()
        if (documentId == null) {
            log.info("Contrato {} sem documento a anular no provedor; evento descartado", command.contractId)
            processed.markProcessed(ConsumerGroups.DOCUMENT_ANNULMENT, command.eventId)
        }
        return documentId
    }

    /** Antes do envio o documento ainda é rascunho no provedor e ninguém pode assiná-lo: nada a anular (ADR-012). */
    private fun Contract.documentToAnnul(): ProviderDocumentId? =
        providerDocumentId.takeIf { status in ANNULLED_STATUSES && sentAt != null }

    private companion object {
        val ANNULLED_STATUSES = setOf(ContractStatus.CANCELLED, ContractStatus.EXPIRED)
    }
}

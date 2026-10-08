package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.StatusChange
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Caso de uso assíncrono responsável por disparar o envio formal do contrato aos signatários através do provedor externo.
 *
 * **Responsabilidade:**
 * - Consumir o evento `ContractGenerated`, assegurando que o envio só ocorra quando o documento estiver plenamente gerado no provedor.
 * - Despachar a requisição de envio aos signatários via [SignatureProviderPort] com tolerância a reentregas e verificações de idempotência.
 * - Transicionar o contrato para o status `SENT`, calcular a data de expiração regulamentar (Regra R4) e persistir atomicamente via outbox.
 */
class SendContract(
    private val contracts: ContractRepositoryPort,
    private val provider: SignatureProviderPort,
    private val persister: ContractPersister,
    private val processed: ProcessedMessagesPort,
    private val clock: BusinessClock,
    private val settings: ContractSettings,
    private val transactions: TransactionRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: ContractEventCommand) {
        val documentId = transactions.run { prepare(command) } ?: return
        try {
            provider.send(documentId, ProviderSendRequest(SUBJECT, MESSAGE))
        } catch (e: ProviderException.Forbidden) {
            fail(command, "Provedor negou o envio do documento: ${e.message}")
            return
        } catch (e: ProviderException.Rejected) {
            // Pode ser uma reentrega: o envio já ocorreu e a transação anterior não chegou a confirmar.
            if (!alreadySent(documentId)) {
                fail(command, "Provedor rejeitou o envio do documento: ${e.message}")
                return
            }
            log.info("Documento {} já estava enviado; confirmando o envio", documentId)
        }
        transactions.run { complete(command) }
    }

    private fun prepare(command: ContractEventCommand): ProviderDocumentId? {
        if (processed.isProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return null
        val contract = contracts.findById(command.contractId)
        val documentId = contract?.providerDocumentId
        // Só envia a partir de GENERATED. Se o contrato já avançou ou terminou, não há o que enviar.
        if (contract == null || documentId == null || contract.status != ContractStatus.GENERATED) {
            processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)
            return null
        }
        return documentId
    }

    private fun alreadySent(documentId: ProviderDocumentId): Boolean = try {
        provider.fetchState(documentId).status in SENT_OR_BEYOND
    } catch (_: ProviderException) {
        false
    }

    private fun complete(command: ContractEventCommand) {
        if (!processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return
        val contract = contracts.findById(command.contractId) ?: return
        // O webhook `document.sent` pode ter chegado antes; nesse caso o status já avançou.
        if (contract.status.progress >= ContractStatus.SENT.progress || contract.status.isFinal) return
        contract.apply(
            StatusChange(ContractStatus.SENT, ChangeSource.API, command.eventId, note = "Envio confirmado."),
            clock.now(),
            settings.signatureDeadline,
        )
        persister.save(contract)
    }

    private fun fail(command: ContractEventCommand, detail: String) {
        log.warn("Falha no envio do contrato {}: {}", command.contractId, detail)
        transactions.run {
            if (!processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return@run
            val contract = contracts.findById(command.contractId) ?: return@run
            contract.failGeneration(detail, ChangeSource.API, command.eventId, clock.now())
            persister.save(contract)
        }
    }

    private companion object {
        const val SUBJECT = "Contrato de locação para assinatura"
        const val MESSAGE = "Olá! Seu contrato de locação está pronto para assinatura. Obrigado."

        val SENT_OR_BEYOND = setOf(
            ProviderDocumentStatus.SENT,
            ProviderDocumentStatus.VIEWED,
            ProviderDocumentStatus.COMPLETED,
        )
    }
}

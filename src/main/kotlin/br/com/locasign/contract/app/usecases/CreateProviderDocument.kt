package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.LeaseTemplateData
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderRecipient
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory

/**
 * Reação a `ContractRequested`: cria o documento no provedor a partir do modelo.
 *
 * A chamada HTTP acontece entre duas transações curtas, para não segurar conexão de banco durante
 * a chamada externa. Se o processo cair depois da criação e antes da segunda transação, a reentrega
 * da mensagem pode criar um segundo documento no provedor (risco aceito, registrado no guia).
 */
class CreateProviderDocument(
	private val contracts: ContractRepositoryPort,
	private val leases: LeaseLookupPort,
	private val provider: SignatureProviderPort,
	private val persister: ContractPersister,
	private val processed: ProcessedMessagesPort,
	private val clock: BusinessClock,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun execute(command: ContractEventCommand) {
		val request = transactions.run { prepare(command) } ?: return
		val documentId = try {
			provider.createDocument(request)
		} catch (e: ProviderException.Forbidden) {
			failGeneration(command, "Provedor negou a criação do documento: ${e.message}")
			return
		} catch (e: ProviderException.Rejected) {
			failGeneration(command, "Provedor rejeitou a criação do documento: ${e.message}")
			return
		}
		transactions.run { complete(command, documentId) }
	}

	private fun prepare(command: ContractEventCommand): ProviderDocumentRequest? {
		if (processed.isProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return null
		val contract = contracts.findById(command.contractId)
		if (contract == null) {
			log.warn("Contrato {} não encontrado ao criar documento; evento descartado", command.contractId)
			processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)
			return null
		}
		if (contract.status != ContractStatus.DRAFT || contract.hasProviderDocument) {
			processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)
			return null
		}
		val lease = checkNotNull(leases.find(contract.leaseId)) { "Locação ${contract.leaseId} do contrato não existe" }
		return ProviderDocumentRequest(
			contractId = contract.id,
			leaseId = contract.leaseId,
			documentName = "LocaSign contrato ${contract.id} v${contract.versionNumber}",
			recipients = contract.signers.sortedBy { it.order.value }.map {
				ProviderRecipient(it.role, it.name, it.email, it.order.value)
			},
			template = LeaseTemplateData(
				tenantName = lease.tenantName,
				tenantCpf = lease.tenantCpf,
				propertyAddress = lease.propertyAddress,
				rentAmount = lease.rentAmount,
				startDate = lease.startDate,
				termMonths = lease.termMonths,
			),
		)
	}

	private fun complete(command: ContractEventCommand, documentId: ProviderDocumentId) {
		if (!processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return
		val contract = contracts.findById(command.contractId) ?: return
		// O webhook `document.draft` pode ter chegado antes e já ter ligado o documento (via metadata).
		if (contract.attachProviderDocument(documentId, ChangeSource.API, command.eventId, clock.now())) {
			persister.save(contract)
		}
	}

	private fun failGeneration(command: ContractEventCommand, detail: String) {
		log.warn("Falha de geração do contrato {}: {}", command.contractId, detail)
		transactions.run {
			if (!processed.markProcessed(ConsumerGroups.ORCHESTRATOR, command.eventId)) return@run
			val contract = contracts.findById(command.contractId) ?: return@run
			contract.failGeneration(detail, ChangeSource.API, command.eventId, clock.now())
			persister.save(contract)
		}
	}
}

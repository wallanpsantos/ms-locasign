package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderSignal
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.StatusChange
import br.com.locasign.contract.domain.models.TransitionResult
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Atualização externa (webhook ou reconciliação) já traduzida em sinais neutros.
 * Todos os [signals] se referem ao mesmo documento.
 */
data class ApplyProviderUpdateCommand(
	val signals: List<ProviderSignal>,
	val source: ChangeSource,
	val sourceEventId: String,
	val markReconciled: Boolean = false,
)

/**
 * Traduz uma atualização externa em transições do agregado. O agregado decide: este use case nunca
 * escreve o status diretamente (R5). Webhook e reconciliação usam o mesmo caminho.
 */
class ApplyProviderUpdate(
	private val contracts: ContractRepositoryPort,
	private val persister: ContractPersister,
	private val clock: BusinessClock,
	private val settings: ContractSettings,
	private val metrics: MetricsPort,
	private val transactions: TransactionRunner,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun execute(command: ApplyProviderUpdateCommand) {
		transactions.run {
			val first = command.signals.firstOrNull() ?: return@run
			val contract = contracts.findByProviderDocumentId(first.documentId)
				?: first.contractHint?.let(contracts::findById)
			if (contract == null) {
				log.warn("Documento {} não pertence a nenhum contrato conhecido", first.documentId)
				return@run
			}

			val now = clock.now()
			// O webhook pode chegar antes de gravarmos o id do documento: liga pelo `contractHint` (metadata).
			contract.attachProviderDocument(first.documentId, command.source, command.sourceEventId, now)
			command.signals.forEach { handle(contract, it, command, now) }
			if (command.markReconciled) contract.markReconciled(now)

			if (command.markReconciled || contract.hasPendingChanges) persister.save(contract)
		}
	}

	private fun handle(contract: Contract, signal: ProviderSignal, command: ApplyProviderUpdateCommand, now: Instant) {
		when (signal) {
			is ProviderSignal.StatusChanged -> handleStatus(contract, signal, command, now)
			is ProviderSignal.RecipientCompleted -> handleRecipients(contract, signal, command, now)
			is ProviderSignal.CreationFailed ->
				count(contract.failGeneration(signal.detail, command.source, command.sourceEventId, now), ContractStatus.CANCELLED)
			// O arquivamento do PDF é tratado por ArchiveSignedDocument, não altera o status.
			is ProviderSignal.PdfReady -> Unit
			is ProviderSignal.DocumentDeleted -> handleRemoved(contract, "documento removido no provedor", command, now)
		}
	}

	private fun handleStatus(
		contract: Contract,
		signal: ProviderSignal.StatusChanged,
		command: ApplyProviderUpdateCommand,
		now: Instant,
	) {
		fun advanceTo(target: ContractStatus, note: String? = null) = change(
			contract,
			StatusChange(target, command.source, command.sourceEventId, signal.modifiedAt, note),
			now,
		)

		when (signal.status) {
			ProviderDocumentStatus.UPLOADED, ProviderDocumentStatus.OTHER -> Unit
			ProviderDocumentStatus.DRAFT -> advanceTo(ContractStatus.GENERATED)
			ProviderDocumentStatus.SENT -> advanceTo(ContractStatus.SENT)
			ProviderDocumentStatus.VIEWED -> advanceTo(ContractStatus.VIEWED)
			ProviderDocumentStatus.COMPLETED -> advanceTo(ContractStatus.COMPLETED)
			ProviderDocumentStatus.DECLINED -> advanceTo(ContractStatus.DECLINED, DECLINED_NOTE)
			ProviderDocumentStatus.VOIDED -> handleRemoved(contract, "documento anulado no provedor", command, now)
			ProviderDocumentStatus.ERROR ->
				if (contract.status == ContractStatus.DRAFT) {
					val result = contract.failGeneration(ERROR_DETAIL, command.source, command.sourceEventId, now, signal.modifiedAt)
					count(result, ContractStatus.CANCELLED)
				} else {
					handleRemoved(contract, "provedor reportou document.error", command, now)
				}
		}
	}

	private fun handleRecipients(
		contract: Contract,
		signal: ProviderSignal.RecipientCompleted,
		command: ApplyProviderUpdateCommand,
		now: Instant,
	) {
		val roles = signal.completedRoles.sortedBy { contract.signer(it)?.order?.value ?: Int.MAX_VALUE }
		roles.forEach { contract.recordSignerCompleted(it, command.source, command.sourceEventId, now) }
		if (contract.signers.any { it.hasCompleted }) {
			change(
				contract,
				StatusChange(ContractStatus.PARTIALLY_SIGNED, command.source, command.sourceEventId, signal.modifiedAt),
				now,
			)
		}
	}

	/** Só há anomalia se o contrato ainda estiver aberto: em estado final, o pedido foi nosso ou é irrelevante. */
	private fun handleRemoved(contract: Contract, what: String, command: ApplyProviderUpdateCommand, now: Instant) {
		if (contract.status.isFinal) return
		log.warn("Anomalia no contrato {}: {} sem pedido do LocaSign", contract.id, what)
		contract.recordAnomaly("$what sem pedido do LocaSign.", command.source, command.sourceEventId, now)
	}

	private fun change(contract: Contract, change: StatusChange, now: Instant) =
		count(contract.apply(change, now, settings.signatureDeadline), change.target)

	private fun count(result: TransitionResult, target: ContractStatus) {
		when (result) {
			TransitionResult.APPLIED -> metrics.count(Metrics.TRANSITION_APPLIED, "target", target.name)
			TransitionResult.IGNORED -> metrics.count(Metrics.TRANSITION_IGNORED, "target", target.name)
			TransitionResult.NO_CHANGE -> Unit
		}
	}

	private companion object {
		const val ERROR_DETAIL = "O provedor reportou document.error na criação do documento."
		const val DECLINED_NOTE = "Signatário recusou o documento (o provedor não informa o motivo)."
	}
}

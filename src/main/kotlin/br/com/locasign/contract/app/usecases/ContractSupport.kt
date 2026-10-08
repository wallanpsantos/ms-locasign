package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import org.slf4j.Logger
import java.time.Duration

/** Parâmetros de negócio do ciclo de vida, vindos da configuração. */
data class ContractSettings(
	/** R4: prazo para assinatura (padrão de 7 dias). */
	val signatureDeadline: Duration,
	/** R4: lembrete no 3º dia (opcional no MVP). */
	val reminderAfter: Duration,
	/** Tempo sem atualização antes de a reconciliação consultar o provedor. */
	val reconciliationStaleAfter: Duration,
	/** Quantidade máxima de contratos tratados por execução de job. */
	val jobBatchSize: Int,
)

/** Mensagem de evento do contrato entregue a um consumidor (id do envelope e contrato afetado). */
data class ContractEventCommand(val eventId: String, val contractId: ContractId)

/**
 * Anula o documento no provedor em melhor esforço, para que ninguém mais o assine. A transição do
 * contrato já foi gravada: uma falha aqui é registrada, nunca propagada.
 */
internal fun SignatureProviderPort.cancelDocumentQuietly(documentId: ProviderDocumentId, log: Logger, context: String) {
	try {
		cancelDocument(documentId)
	} catch (e: ProviderException) {
		log.warn("{}, mas o documento {} não pôde ser anulado no provedor: {}", context, documentId, e.message)
	}
}

/**
 * Persiste o agregado e publica seus eventos pendentes pelo outbox. Deve ser chamado dentro de uma
 * transação: estado, auditoria e eventos são gravados juntos (princípio 3).
 */
class ContractPersister(
	private val contracts: ContractRepositoryPort,
	private val publisher: ContractEventPublisherPort,
) {
	fun save(contract: Contract) {
		val events = contract.pullEvents()
		contracts.save(contract)
		publisher.publish(events)
	}
}

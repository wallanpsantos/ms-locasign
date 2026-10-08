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

/**
 * Configurações e parâmetros temporais que regem as regras de ciclo de vida dos contratos.
 *
 * **Responsabilidade:**
 * - Centralizar prazos de expiração (R4), janelas para disparo de lembretes (R7), intervalos de reconciliação (R6) e tamanho de lotes de jobs.
 * - Desacoplar valores temporais estáticos do domínio e casos de uso, injetando propriedades externas.
 */
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

/**
 * Comando de transporte de evento Kafka consumido por orquestradores e executores assíncronos do contrato.
 *
 * **Responsabilidade:**
 * - Carregar o identificador do evento original ([eventId]) e o identificador do contrato ([contractId]) para processamento idempotente.
 */
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
 * Utilitário de aplicação responsável por persistir o agregado [Contract] e descarregar seus eventos pendentes via Outbox.
 *
 * **Responsabilidade:**
 * - Garantir atomicidade transacional: a gravação do contrato, seus signatários, histórico e eventos de outbox ocorrem na mesma transação (Princípio 3).
 * - Evitar duplicação de lógica de persistência e outbox entre os múltiplos casos de uso do módulo.
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

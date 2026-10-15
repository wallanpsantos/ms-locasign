package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.valueobjects.ContractId
import java.time.Duration

/**
 * Configurações e parâmetros temporais que regem as regras de ciclo de vida dos contratos.
 *
 * **Responsabilidade:**
 * - Centralizar prazos de expiração (R4), janelas para disparo de lembretes (R4), intervalos de reconciliação (guia, §6.4) e tamanho de lotes de jobs.
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

package br.com.locasign.contract.domain.events

import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.DomainEvent
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Contrato base para todos os eventos de domínio produzidos pelo ciclo de vida do contrato de locação.
 *
 * **Responsabilidade:**
 * - Formar uma hierarquia selada que force o tratamento exaustivo em expressões `when` nos mappers do Kafka.
 * - Identificar o tipo do agregado raiz ([AGGREGATE_TYPE]) e o identificador único da instância para particionamento no broker.
 */
sealed interface ContractEvent : DomainEvent {
    val contractId: ContractId

    override val aggregateType: String get() = AGGREGATE_TYPE
    override val aggregateId: String get() = contractId.toString()

    companion object {
        const val AGGREGATE_TYPE = "Contract"
    }
}

/**
 * Evento de domínio emitido quando uma nova versão do contrato de locação é solicitada e colocada em rascunho (`DRAFT`).
 *
 * **Responsabilidade:**
 * - Notificar os consumidores que a confecção do contrato foi demandada para a locação e versão informadas.
 * - Desencadear de forma assíncrona o fluxo de criação do documento no provedor externo de assinatura.
 */
data class ContractRequested(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    val versionNumber: Int,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o provedor externo aceita a solicitação e cria o documento, retornando seu identificador remoto.
 *
 * **Responsabilidade:**
 * - Vincular o identificador do provedor ([ProviderDocumentId]) ao contrato do sistema.
 * - Desencadear os passos subsequentes de envio para assinatura.
 */
data class ContractDocumentCreated(
    override val contractId: ContractId,
    val providerDocumentId: ProviderDocumentId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o documento é totalmente processado e gerado no provedor externo, alcançando o status `GENERATED`.
 *
 * **Responsabilidade:**
 * - Sinalizar a conclusão da geração do documento remoto com sucesso.
 * - Habilitar o envio do documento aos signatários.
 */
data class ContractGenerated(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o provedor externo reporta falha técnica irreversível na geração do documento.
 *
 * **Responsabilidade:**
 * - Notificar a falha de criação, culminando no cancelamento automático do contrato com motivo técnico detalhado.
 * - Disparar alertas operacionais para análise da causa raiz da falha.
 */
data class ContractGenerationFailed(
    override val contractId: ContractId,
    val detail: String?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o contrato é formalmente enviado aos signatários para início do processo de coleta de assinaturas (`SENT`).
 *
 * **Responsabilidade:**
 * - Registrar o envio do documento e definir a data-limite de expiração calculada (Regra R4).
 * - Disparar o agendamento de verificações de prazo e lembretes de pendência.
 */
data class ContractSent(
    override val contractId: ContractId,
    val expiresAt: Instant?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando um dos signatários abre e visualiza o documento na plataforma de assinatura eletrônica (`VIEWED`).
 *
 * **Responsabilidade:**
 * - Registrar a interação do signatário com o documento para auditoria e acompanhamento de engajamento.
 */
data class ContractViewed(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando um signatário individual conclui com sucesso a assinatura do contrato (`PARTIALLY_SIGNED`).
 *
 * **Responsabilidade:**
 * - Registrar o cumprimento individual da assinatura de uma parte específica ([role]) respeitando a ordem ordinal (R3).
 * - Atualizar a trilha de auditoria e emitir notificações parciais para as partes interessadas.
 */
data class ContractSignerCompleted(
    override val contractId: ContractId,
    val role: SignerRole,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando todas as partes concluíram com sucesso as assinaturas, tornando o contrato concluído (`COMPLETED`).
 *
 * **Responsabilidade:**
 * - Formalizar a finalização do ciclo de assinaturas com validade jurídica integral.
 * - Desencadear os efeitos colaterais pós-assinatura (R8), incluindo arquivamento de PDF e ativação da locação.
 */
data class ContractCompleted(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando qualquer signatário recusa explicitamente a assinatura do contrato de locação (`DECLINED`).
 *
 * **Responsabilidade:**
 * - Interromper o processo de assinatura e transicionar o contrato para estado terminal recusado.
 * - Notificar a imobiliária sobre a recusa para tomada de medidas comerciais.
 */
data class ContractDeclined(
    override val contractId: ContractId,
    val reason: String?,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o prazo regulamentar de assinatura expira sem que todas as partes tenham assinado (`EXPIRED`).
 *
 * **Responsabilidade:**
 * - Encerrar a vigência da proposta de locação e invalidar o documento perante o provedor (Regra R4).
 * - Notificar a imobiliária sobre o vencimento do prazo.
 */
data class ContractExpired(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando o contrato é cancelado voluntariamente por intervenção do operador ou corretor (`CANCELLED`).
 *
 * **Responsabilidade:**
 * - Registrar a rescisão voluntária do processo de assinatura com justificativa formal informada.
 * - Cancelar o documento no provedor externo e notificar as partes envolvidas.
 */
data class ContractCancelled(
    override val contractId: ContractId,
    val reason: String,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando um lembrete de assinatura pendente é enviado aos signatários faltantes (Regra R7).
 *
 * **Responsabilidade:**
 * - Registrar o envio do alerta preventivo de aproximação do prazo de expiração para auditoria e observabilidade.
 */
data class ContractReminderSent(
    override val contractId: ContractId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando a cópia final do documento assinado é baixada e arquivada com sucesso no armazenamento permanente.
 *
 * **Responsabilidade:**
 * - Comprovar a custódia do PDF assinado pelo sistema com a localização física ou URI de recuperação ([location]).
 * - Registrar o cumprimento da etapa de preservação documental pós-conclusão (R8).
 */
data class SignedDocumentArchived(
    override val contractId: ContractId,
    val storage: String,
    val location: String,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

/**
 * Evento emitido quando a locação vinculada é efetivamente ativada como consequência da conclusão do contrato.
 *
 * **Responsabilidade:**
 * - Concretizar a integração de negócio entre os módulos `contract` e `lease` após a assinatura bem-sucedida (R8).
 * - Notificar sistemas a jusante sobre o início formal da vigência do contrato de locação.
 */
data class LeaseActivated(
    override val contractId: ContractId,
    val leaseId: LeaseId,
    override val occurredAt: Instant,
    override val eventId: Uuid = Uuid.random(),
) : ContractEvent

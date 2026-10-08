package br.com.locasign.notification.app.ports.out

import br.com.locasign.notification.domain.Audience

/**
 * Representa um destinatário de notificação com papel no contrato e e-mail já mascarado para preservação de privacidade.
 *
 * **Responsabilidade:**
 * - Proteger dados pessoais (PII/LGPD) garantindo que dados de contato completos não trafeguem fora do módulo de contratos.
 * - Fornecer identificadores mascarados suficientes para emissão de logs e simulação de notificações.
 */
data class NotificationRecipient(val role: String, val emailMasked: String)

/**
 * Porta de saída para resolução de destinatários elegíveis de um contrato conforme o público-alvo requerido.
 *
 * **Responsabilidade:**
 * - Consultar os contatos das partes signatárias associadas a um contrato sem expor dados não mascarados aos eventos Kafka.
 * - Abstrair a recuperação dos dados de contato do contrato para uso pelos casos de uso de notificação.
 */
interface NotificationRecipientsPort {
    fun recipientsOf(contractId: String, audience: Audience): List<NotificationRecipient>
}

/**
 * Porta de saída para persistência de registros históricos de notificações disparadas pelo sistema.
 *
 * **Responsabilidade:**
 * - Registrar em banco de dados a trilha de auditoria de notificações enviadas (ou simuladas), correlacionadas ao contrato e evento gerador.
 * - Garantir rastreabilidade de comunicação com partes interessadas para conformidade legal e suporte operacional.
 */
interface NotificationLogPort {
    fun record(contractId: String, eventId: String, eventType: String, recipientMasked: String)
}

package br.com.locasign.notification.infra.persistence

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipient
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.domain.Audience
import br.com.locasign.shared.domain.valueobjects.Email
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Adaptador de persistência JDBC para resolução dos destinatários de notificação de um contrato.
 *
 * **Responsabilidade:**
 * - Consultar a tabela `contract_signers` filtrando signatários pelo identificador do contrato e escopo de audiência.
 * - Converter dados sensíveis (e-mails) em suas representações mascaradas via [Email.masked], protegendo informações pessoais de contato.
 */
@Repository
class JdbcNotificationRecipientsAdapter(private val jdbc: JdbcClient) : NotificationRecipientsPort {

    override fun recipientsOf(contractId: String, audience: Audience): List<NotificationRecipient> =
        jdbc.sql(
            """
			SELECT role, email FROM contract_signers
			WHERE contract_id = :contractId AND (:allParties OR role = 'AGENCY')
			ORDER BY signing_order
			""".trimIndent(),
        )
            .param("contractId", UUID.fromString(contractId))
            .param("allParties", audience == Audience.ALL_PARTIES)
            .query { rs, _ ->
                NotificationRecipient(
                    rs.getString("role"),
                    Email.fromStorage(rs.getString("email")).masked()
                )
            }
            .list()
}

/**
 * Adaptador de persistência JDBC para registro em log das notificações emitidas pelo sistema.
 *
 * **Responsabilidade:**
 * - Inserir registros na tabela `notifications_log` contendo identificador do contrato, evento gerador e destinatário mascarado.
 * - Garantir a persistência da trilha de auditoria para fins de rastreamento operacional e conformidade legal.
 */
@Repository
class JdbcNotificationLogAdapter(private val jdbc: JdbcClient) : NotificationLogPort {

    override fun record(contractId: String, eventId: String, eventType: String, recipientMasked: String) {
        jdbc.sql(
            """
			INSERT INTO notifications_log (contract_id, event_id, event_type, recipient_masked)
			VALUES (:contractId, :eventId, :eventType, :recipientMasked)
			""".trimIndent(),
        )
            .param("contractId", UUID.fromString(contractId))
            .param("eventId", eventId)
            .param("eventType", eventType)
            .param("recipientMasked", recipientMasked)
            .update()
    }
}

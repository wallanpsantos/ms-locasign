package br.com.locasign.notification.infra.persistence

import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.contract.infra.persistence.adapters.ContractRepositoryAdapter
import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.lease.infra.persistence.adapters.LeaseRepositoryAdapter
import br.com.locasign.notification.app.ports.out.NotificationRecipient
import br.com.locasign.notification.domain.Audience
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import br.com.locasign.support.IntegrationTestSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.toJavaUuid

class JdbcNotificationAdaptersIT(
    @Autowired private val recipients: JdbcNotificationRecipientsAdapter,
    @Autowired private val notificationLog: JdbcNotificationLogAdapter,
    @Autowired private val leases: LeaseRepositoryAdapter,
    @Autowired private val contracts: ContractRepositoryAdapter,
    @Autowired private val jdbc: JdbcClient,
) : IntegrationTestSupport() {

    private val tenantEmail = Email.of("maria@example.com")
    private val agencyEmail = Email.of("alianca@example.com")

    private fun savedContract(): Contract {
        val lease = Lease.register(
            tenant = Tenant("Maria Silva", Cpf.of("529.982.247-25"), tenantEmail),
            agencySigner = AgencySigner("Imobiliária Aliança", agencyEmail),
            propertyAddress = "Rua das Flores, 123",
            rentAmount = Money.positive("3200.00"),
            startDate = LocalDate.of(2026, 11, 1),
            term = LeaseTerm.of(30),
            today = LocalDate.of(2026, 10, 9),
            now = Instant.parse("2026-10-09T10:00:00Z"),
        ).also(leases::save)
        // Gravados fora da ordem de assinatura: a ordem devolvida vem do ORDER BY signing_order do adapter.
        val signers = listOf(
            Signer(SignerRole.AGENCY, "Imobiliária Aliança", agencyEmail, SigningOrder.of(2)),
            Signer(SignerRole.TENANT, "Maria Silva", tenantEmail, SigningOrder.of(1)),
        )
        return Contract.request(lease.id, 1, signers, Instant.parse("2026-10-09T10:00:00Z")).also(contracts::save)
    }

    @Test
    fun `destinatários vêm na ordem de assinatura, com e-mail mascarado e filtrados pelo público (R10)`() {
        val contractId = savedContract().id.value
        val tenant = NotificationRecipient("TENANT", tenantEmail.masked())
        val agency = NotificationRecipient("AGENCY", agencyEmail.masked())

        assertEquals(listOf(tenant, agency), recipients.recipientsOf(contractId, Audience.ALL_PARTIES))
        assertEquals(listOf(agency), recipients.recipientsOf(contractId, Audience.AGENCY_ONLY))
    }

    @Test
    fun `registro grava a notificação com o id do contrato, o evento e o destinatário mascarado`() {
        val contractId = savedContract().id.value
        val eventId = UUID.randomUUID().toString()

        notificationLog.record(contractId, eventId, "ContractSent", agencyEmail.masked())

        val row = jdbc.sql(
            """
			SELECT contract_id, event_type, recipient_masked
			FROM notifications_log WHERE event_id = :eventId
			""".trimIndent(),
        )
            .param("eventId", eventId)
            .query()
            .singleRow()
        assertEquals(contractId.toJavaUuid(), row["contract_id"])
        assertEquals("ContractSent", row["event_type"])
        assertEquals(agencyEmail.masked(), row["recipient_masked"])
    }
}

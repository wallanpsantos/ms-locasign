package br.com.locasign.lease.infra.persistence.adapters

import br.com.locasign.lease.app.ports.out.repository.LeaseQueryPort
import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.app.queries.CurrentContractView
import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.infra.persistence.mappers.toDomain
import br.com.locasign.lease.infra.persistence.mappers.toEntity
import br.com.locasign.lease.infra.persistence.repositories.LeaseJdbcRepository
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.infra.persistence.getInstant
import br.com.locasign.shared.infra.persistence.getRequiredInstant
import br.com.locasign.shared.infra.persistence.singleOrNull
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate
import kotlin.uuid.toJavaUuid

/**
 * Adaptador de persistência para escrita e reconstituição do agregado de locação via Spring Data JDBC.
 *
 * **Responsabilidade:**
 * - Implementar [LeaseRepositoryPort], convertendo o agregado [Lease] em [br.com.locasign.lease.infra.persistence.entities.LeaseEntity] e persistindo através do [LeaseJdbcRepository].
 * - Reconstituir o agregado puro a partir da entidade relacional preservando versionamento otimista.
 */
@Repository
class LeaseRepositoryAdapter(private val repository: LeaseJdbcRepository) : LeaseRepositoryPort {

    override fun save(lease: Lease) {
        repository.save(lease.toEntity())
    }

    override fun findById(id: LeaseId): Lease? =
        repository.findByIdOrNull(id.value.toJavaUuid())?.toDomain()
}

/**
 * Adaptador de infraestrutura para a consulta de leitura da locação e do contrato corrente.
 *
 * **Responsabilidade:**
 * - Implementar [LeaseQueryPort] com SQL explícito em [JdbcClient], sem carregar os agregados de domínio.
 * - Montar a projeção [LeaseDetailView] com a versão mais recente do contrato via LEFT JOIN LATERAL.
 *   A leitura da tabela `contracts`, de outro módulo, é exceção sem ADR (`docs/agents/arquitetura.md` §7).
 */
@Repository
class LeaseQueryAdapter(private val jdbc: JdbcClient) : LeaseQueryPort {

    override fun findDetail(id: LeaseId): LeaseDetailView? =
        jdbc.sql(
            """
            SELECT l.id, l.status, l.tenant_name, l.tenant_cpf, l.tenant_email, l.agency_signer_name, l.agency_signer_email,
                   l.property_address, l.rent_amount, l.start_date, l.term_months, l.created_at, l.activated_at,
                   c.id AS contract_id, c.version_number AS contract_version, c.status AS contract_status, c.expires_at AS contract_expires_at
            FROM leases l
            LEFT JOIN LATERAL (
                SELECT id, version_number, status, expires_at
                FROM contracts
                WHERE lease_id = l.id
                ORDER BY version_number DESC LIMIT 1
            ) c ON true
            WHERE l.id = :id
            """.trimIndent(),
        )
            .param("id", id.value.toJavaUuid())
            .query { rs, _ ->
                val currentContract = rs.getString("contract_id")?.let { id ->
                    CurrentContractView(
                        id = id,
                        versionNumber = rs.getInt("contract_version"),
                        status = rs.getString("contract_status"),
                        expiresAt = rs.getInstant("contract_expires_at"),
                    )
                }
                LeaseDetailView(
                    id = rs.getString("id"),
                    status = rs.getString("status"),
                    tenantName = rs.getString("tenant_name"),
                    tenantCpfMasked = Cpf.fromStorage(rs.getString("tenant_cpf")).masked(),
                    tenantEmail = rs.getString("tenant_email"),
                    agencySignerName = rs.getString("agency_signer_name"),
                    agencySignerEmail = rs.getString("agency_signer_email"),
                    propertyAddress = rs.getString("property_address"),
                    rentAmount = rs.getBigDecimal("rent_amount").toPlainString(),
                    startDate = rs.getObject("start_date", LocalDate::class.java),
                    termMonths = rs.getInt("term_months"),
                    createdAt = rs.getRequiredInstant("created_at"),
                    activatedAt = rs.getInstant("activated_at"),
                    currentContract = currentContract,
                )
            }
            .singleOrNull()
}

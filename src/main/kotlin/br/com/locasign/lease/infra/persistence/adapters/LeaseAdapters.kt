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
 * - Montar a projeção [LeaseDetailView] com a versão mais recente do contrato. A leitura da tabela `contracts`, de outro módulo, é exceção sem ADR (`docs/agents/arquitetura.md` §7).
 */
@Repository
class LeaseQueryAdapter(private val jdbc: JdbcClient) : LeaseQueryPort {

    override fun findDetail(id: LeaseId): LeaseDetailView? {
        val lease = jdbc.sql(
            """
			SELECT id, status, tenant_name, tenant_cpf, tenant_email, agency_signer_name, agency_signer_email,
			       property_address, rent_amount, start_date, term_months, created_at, activated_at
			FROM leases WHERE id = :id
			""".trimIndent(),
        )
            .param("id", id.value.toJavaUuid())
            .query { rs, _ ->
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
                    createdAt = checkNotNull(rs.getInstant("created_at")),
                    activatedAt = rs.getInstant("activated_at"),
                    currentContract = null,
                )
            }
            .optional()
            .orElse(null) ?: return null

        return lease.copy(currentContract = findCurrentContract(id))
    }

    /**
     * Versão mais recente do contrato da locação. Lê `contracts`, tabela do módulo `contract`: exceção sem ADR
     * (docs/agents/arquitetura.md §7), não use como precedente.
     */
    private fun findCurrentContract(leaseId: LeaseId): CurrentContractView? =
        jdbc.sql(
            """
			SELECT id, version_number, status, expires_at
			FROM contracts WHERE lease_id = :leaseId
			ORDER BY version_number DESC LIMIT 1
			""".trimIndent(),
        )
            .param("leaseId", leaseId.value.toJavaUuid())
            .query { rs, _ ->
                CurrentContractView(
                    id = rs.getString("id"),
                    versionNumber = rs.getInt("version_number"),
                    status = rs.getString("status"),
                    expiresAt = rs.getInstant("expires_at"),
                )
            }
            .optional()
            .orElse(null)
}

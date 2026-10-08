package br.com.locasign.lease.infra.persistence.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Registro de persistência da locação (Spring Data JDBC, ADR-003). O id é gerado no domínio; o
 * `@Version` nulo marca a inserção e depois garante o lock otimista.
 */
@Table("leases")
data class LeaseEntity(
    @Id val id: UUID,
    val tenantName: String,
    val tenantCpf: String,
    val tenantEmail: String,
    val agencySignerName: String,
    val agencySignerEmail: String,
    val propertyAddress: String,
    val rentAmount: BigDecimal,
    val startDate: LocalDate,
    val termMonths: Int,
    val status: String,
    val activatedAt: Instant?,
    @Version val rowVersion: Long?,
    val createdAt: Instant,
)

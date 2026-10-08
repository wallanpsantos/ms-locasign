package br.com.locasign.lease.infra.persistence.entities

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Entidade de mapeamento objeto-relacional para persistência de locações na tabela `leases` via Spring Data JDBC.
 *
 * **Responsabilidade:**
 * - Mapear os campos da tabela relacional `leases` (partes, endereço, valor, datas e status).
 * - Suportar chave primária gerada na aplicação ([id]) e controle de concorrência com bloqueio otimista via anotação `@Version` ([rowVersion]).
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

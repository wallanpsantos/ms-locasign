package br.com.locasign.lease.infra.persistence.mappers

import br.com.locasign.lease.domain.models.AgencySigner
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.lease.domain.models.Tenant
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.lease.infra.persistence.entities.LeaseEntity
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

fun Lease.toEntity(): LeaseEntity = LeaseEntity(
    id = id.value.toJavaUuid(),
    tenantName = tenant.name,
    tenantCpf = tenant.cpf.digits,
    tenantEmail = tenant.email.value,
    agencySignerName = agencySigner.name,
    agencySignerEmail = agencySigner.email.value,
    propertyAddress = propertyAddress,
    rentAmount = rentAmount.amount,
    startDate = startDate,
    termMonths = term.months,
    status = status.name,
    activatedAt = activatedAt,
    rowVersion = rowVersion,
    createdAt = createdAt,
)

fun LeaseEntity.toDomain(): Lease = Lease.restore(
    id = LeaseId(id.toKotlinUuid()),
    tenant = Tenant(tenantName, Cpf.fromStorage(tenantCpf), Email.fromStorage(tenantEmail)),
    agencySigner = AgencySigner(agencySignerName, Email.fromStorage(agencySignerEmail)),
    propertyAddress = propertyAddress,
    rentAmount = Money.fromStorage(rentAmount),
    startDate = startDate,
    term = LeaseTerm.fromStorage(termMonths),
    status = LeaseStatus.valueOf(status),
    activatedAt = activatedAt,
    createdAt = createdAt,
    rowVersion = rowVersion,
)

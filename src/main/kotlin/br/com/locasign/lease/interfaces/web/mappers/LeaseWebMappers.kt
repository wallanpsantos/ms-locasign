package br.com.locasign.lease.interfaces.web.mappers

import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.app.usecases.RegisterLeaseCommand
import br.com.locasign.lease.interfaces.web.dto.request.CreateLeaseRequest
import br.com.locasign.lease.interfaces.web.dto.response.AgencySignerResponse
import br.com.locasign.lease.interfaces.web.dto.response.CurrentContractResponse
import br.com.locasign.lease.interfaces.web.dto.response.LeaseResponse
import br.com.locasign.lease.interfaces.web.dto.response.PropertyResponse
import br.com.locasign.lease.interfaces.web.dto.response.TenantResponse

fun CreateLeaseRequest.toCommand(): RegisterLeaseCommand = RegisterLeaseCommand(
    tenantName = tenant.name,
    tenantCpf = tenant.cpf,
    tenantEmail = tenant.email,
    agencySignerName = agencySigner?.name,
    agencySignerEmail = agencySigner?.email,
    propertyAddress = property.address,
    rentAmount = rentAmount,
    startDate = startDate,
    termMonths = termMonths,
)

fun LeaseDetailView.toResponse(): LeaseResponse = LeaseResponse(
    id = id,
    status = status,
    tenant = TenantResponse(tenantName, tenantCpfMasked, tenantEmail),
    agencySigner = AgencySignerResponse(agencySignerName, agencySignerEmail),
    property = PropertyResponse(propertyAddress),
    rentAmount = rentAmount,
    startDate = startDate,
    termMonths = termMonths,
    createdAt = createdAt,
    activatedAt = activatedAt,
    currentContract = currentContract?.let {
        CurrentContractResponse(it.id, it.versionNumber, it.status, it.expiresAt)
    },
)

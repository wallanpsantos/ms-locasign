package br.com.locasign.lease.interfaces.web.dto.response

import java.time.Instant
import java.time.LocalDate

data class LeaseCreatedResponse(val id: String, val status: String)

data class LeaseResponse(
	val id: String,
	val status: String,
	val tenant: TenantResponse,
	val agencySigner: AgencySignerResponse,
	val property: PropertyResponse,
	val rentAmount: String,
	val startDate: LocalDate,
	val termMonths: Int,
	val createdAt: Instant,
	val activatedAt: Instant?,
	val currentContract: CurrentContractResponse?,
)

data class TenantResponse(val name: String, val cpf: String, val email: String)

data class AgencySignerResponse(val name: String, val email: String)

data class PropertyResponse(val address: String)

data class CurrentContractResponse(
	val id: String,
	val versionNumber: Int,
	val status: String,
	val expiresAt: Instant?,
)

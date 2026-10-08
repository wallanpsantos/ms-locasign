package br.com.locasign.lease.interfaces.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * Cadastro de locação. A Bean Validation cobre apenas presença e tamanho (HTTP 400); as regras de
 * negócio (CPF válido, data de início, valor positivo) são do domínio e respondem HTTP 422.
 *
 * Todas as anotações usam `@field:` para garantir que o Hibernate Validator as enxergue.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateLeaseRequest(
	@field:Valid val tenant: TenantRequest,
	@field:Valid val agencySigner: AgencySignerRequest? = null,
	@field:Valid val property: PropertyRequest,
	@field:Schema(description = "Valor do aluguel como string decimal com até 2 casas", example = "2500.00")
	@field:NotBlank val rentAmount: String,
	@field:Schema(description = "Data de início, igual ou posterior a hoje (America/Sao_Paulo)", example = "2026-11-01")
	val startDate: LocalDate,
	@field:Schema(description = "Prazo em meses (1 a 120); padrão 30", example = "30")
	@field:Min(1) @field:Max(120) val termMonths: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TenantRequest(
	@field:NotBlank @field:Size(min = 3, max = 120) val name: String,
	@field:Schema(description = "CPF com ou sem máscara", example = "529.982.247-25")
	@field:NotBlank val cpf: String,
	@field:NotBlank val email: String,
)

/** Opcional: se ausente, usa o signatário padrão da configuração. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AgencySignerRequest(
	@field:NotBlank @field:Size(min = 3, max = 120) val name: String,
	@field:NotBlank val email: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PropertyRequest(
	@field:NotBlank @field:Size(max = 300) val address: String,
)

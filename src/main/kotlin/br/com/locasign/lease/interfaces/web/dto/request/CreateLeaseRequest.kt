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
 * DTO de requisição para cadastro de uma nova locação residencial.
 *
 * **Responsabilidade:**
 * - Capturar e validar a presença e tamanho dos dados informados na requisição HTTP com Bean Validation (`@field:`).
 * - Servir de fronteira de entrada desacoplada dos tipos de domínio.
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

/**
 * DTO de requisição que encapsula os dados cadastrais do locatário.
 *
 * **Responsabilidade:**
 * - Validar limites de tamanho e preenchimento de nome, CPF e e-mail informados na requisição.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TenantRequest(
    @field:NotBlank @field:Size(min = 3, max = 120) val name: String,
    @field:Schema(description = "CPF com ou sem máscara", example = "529.982.247-25")
    @field:NotBlank val cpf: String,
    @field:NotBlank val email: String,
)

/**
 * DTO opcional de requisição com os dados do signatário da imobiliária.
 *
 * **Responsabilidade:**
 * - Capturar nome e e-mail de um signatário específico para a locação caso não se deseje utilizar o padrão global.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AgencySignerRequest(
    @field:NotBlank @field:Size(min = 3, max = 120) val name: String,
    @field:NotBlank val email: String,
)

/**
 * DTO de requisição que encapsula as características do imóvel alugado.
 *
 * **Responsabilidade:**
 * - Capturar e validar os limites de tamanho do endereço do imóvel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PropertyRequest(
    @field:NotBlank @field:Size(max = 300) val address: String,
)

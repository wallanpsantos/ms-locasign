package br.com.locasign.contract.interfaces.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * DTO de requisição para cancelamento formal de um contrato de locação ativo.
 *
 * **Responsabilidade:**
 * - Capturar e validar a justificativa formal de cancelamento informada pelo operador (obrigatória e até 500 caracteres).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CancelContractRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

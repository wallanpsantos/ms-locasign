package br.com.locasign.contract.interfaces.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JsonIgnoreProperties(ignoreUnknown = true)
data class CancelContractRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

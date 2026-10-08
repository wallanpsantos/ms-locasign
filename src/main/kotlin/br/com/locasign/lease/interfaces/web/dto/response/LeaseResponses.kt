package br.com.locasign.lease.interfaces.web.dto.response

import java.time.Instant
import java.time.LocalDate

/**
 * DTO de resposta emitido imediatamente após o cadastro bem-sucedido de uma locação.
 *
 * **Responsabilidade:**
 * - Retornar o identificador universal gerado ([id]) e o status inicial ([status]).
 */
data class LeaseCreatedResponse(val id: String, val status: String)

/**
 * DTO de resposta detalhado com os dados completos de uma locação e seu contrato atual.
 *
 * **Responsabilidade:**
 * - Expor as informações consolidadas da locação e referenciar a versão corrente do contrato em formato JSON para clientes da API.
 */
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

/**
 * DTO de resposta com os dados cadastrais do locatário, contendo CPF mascarado.
 *
 * **Responsabilidade:**
 * - Proteger a privacidade do locatário expondo o CPF em formato ofuscado (`***.XXX.XXX-**`).
 */
data class TenantResponse(val name: String, val cpf: String, val email: String)

/**
 * DTO de resposta com os dados de identificação do signatário da imobiliária.
 *
 * **Responsabilidade:**
 * - Retornar nome e e-mail do representante da imobiliária responsável pela assinatura.
 */
data class AgencySignerResponse(val name: String, val email: String)

/**
 * DTO de resposta com o endereço do imóvel locado.
 *
 * **Responsabilidade:**
 * - Retornar o endereço cadastrado do imóvel.
 */
data class PropertyResponse(val address: String)

/**
 * DTO de resposta resumido com os dados da versão corrente do contrato da locação.
 *
 * **Responsabilidade:**
 * - Retornar status contratual, versão e data limite de expiração da versão ativa ou mais recente.
 */
data class CurrentContractResponse(
    val id: String,
    val versionNumber: Int,
    val status: String,
    val expiresAt: Instant?,
)

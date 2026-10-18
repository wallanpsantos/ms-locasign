package br.com.locasign.lease.app.ports.`in`

import br.com.locasign.lease.domain.valueobjects.LeaseId
import java.time.LocalDate

/**
 * Comando de entrada com os dados necessários para o cadastro inicial de uma locação residencial.
 *
 * **Responsabilidade:**
 * - Encapsular os parâmetros brutos de entrada da locação (locatário, imóvel, valores, datas e signatário da imobiliária).
 */
data class RegisterLeaseCommand(
    val tenantName: String,
    val tenantCpf: String,
    val tenantEmail: String,
    val agencySignerName: String?,
    val agencySignerEmail: String?,
    val propertyAddress: String,
    val rentAmount: String,
    val startDate: LocalDate,
    val termMonths: Int?,
)

/**
 * Porta de entrada do cadastro de locação pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller o registro da locação, sem revelar a classe concreta que aplica R2.
 */
interface RegisterLeasePort {
    fun execute(command: RegisterLeaseCommand): LeaseId
}

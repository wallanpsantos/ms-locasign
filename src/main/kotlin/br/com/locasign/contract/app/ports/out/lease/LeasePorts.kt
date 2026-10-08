package br.com.locasign.contract.app.ports.out.lease

import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.LocalDate

/**
 * Fotografia imutável dos dados da locação requeridos para geração e emissão do contrato.
 *
 * **Responsabilidade:**
 * - Prover uma visão somente-leitura dos dados de locatário, imóvel, valores e signatários para o módulo de contratos.
 * - Manter a soberania do módulo `lease` sobre os dados cadastrais da locação, evitando acoplamento direto de modelos.
 */
data class LeaseSnapshot(
    val leaseId: LeaseId,
    val tenantName: String,
    val tenantCpf: Cpf,
    val tenantEmail: Email,
    val agencySignerName: String,
    val agencySignerEmail: Email,
    val propertyAddress: String,
    val rentAmount: Money,
    val startDate: LocalDate,
    val termMonths: Int,
)

/**
 * Porta de saída para consulta dos dados consolidados de uma locação pelo identificador.
 *
 * **Responsabilidade:**
 * - Permitir que o módulo de contratos obtenha o snapshot cadastral da locação sem violar os limites do monólito modular.
 */
interface LeaseLookupPort {
    fun find(leaseId: LeaseId): LeaseSnapshot?
}

/**
 * Porta de saída para disparo da ativação da locação como consequência da conclusão do contrato (Regra R8).
 *
 * **Responsabilidade:**
 * - Executar a transição da locação para o status ativo de forma desacoplada após a assinatura de todas as partes.
 * - Retornar se a ativação foi efetivamente aplicada nesta chamada (idempotência).
 */
interface LeaseActivationPort {
    fun activate(leaseId: LeaseId): Boolean
}

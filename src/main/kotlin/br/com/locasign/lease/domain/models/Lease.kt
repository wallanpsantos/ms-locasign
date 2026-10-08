package br.com.locasign.lease.domain.models

import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Estados do ciclo de vida da locação imobiliária.
 *
 * **Responsabilidade:**
 * - Representar se a locação está apenas cadastrada ([REGISTERED]) ou formalmente vigente ([ACTIVE]) após assinatura do contrato.
 */
enum class LeaseStatus { REGISTERED, ACTIVE }

/**
 * Agregado raiz que representa uma locação residencial no domínio de negócio.
 *
 * **Responsabilidade:**
 * - Manter as informações estruturais do aluguel (partes envolvidas, endereço do imóvel, valor monetário e vigência).
 * - Controlar a transição atômica para o estado ativo ([activate]) disparada pelas ações pós-assinatura (regra R8).
 * - Garantir a validação completa de dados obrigatórios no momento do cadastro (regra R2).
 */
class Lease private constructor(
    val id: LeaseId,
    val tenant: Tenant,
    val agencySigner: AgencySigner,
    val propertyAddress: String,
    val rentAmount: Money,
    val startDate: LocalDate,
    val term: LeaseTerm,
    status: LeaseStatus,
    activatedAt: Instant?,
    val createdAt: Instant,
    val rowVersion: Long?,
) {
    var status: LeaseStatus = status
        private set

    var activatedAt: Instant? = activatedAt
        private set

    /** Ativa a locação (ação pós-assinatura). Devolve `false` se já estava ativa. */
    fun activate(now: Instant): Boolean {
        if (status == LeaseStatus.ACTIVE) return false
        status = LeaseStatus.ACTIVE
        activatedAt = now
        return true
    }

    companion object {
        private const val MAX_ADDRESS_LENGTH = 300

        /** R2: valida os dados obrigatórios no cadastro. [today] é a data no fuso da operação. */
        fun register(
            tenant: Tenant,
            agencySigner: AgencySigner,
            propertyAddress: String,
            rentAmount: Money,
            startDate: LocalDate,
            term: LeaseTerm,
            today: LocalDate,
            now: Instant,
        ): Lease {
            val address = propertyAddress.trim()
            if (address.isEmpty() || address.length > MAX_ADDRESS_LENGTH) {
                throw DomainException.BusinessRuleViolation(
                    "property.address",
                    "O endereço do imóvel é obrigatório (até $MAX_ADDRESS_LENGTH caracteres).",
                )
            }
            if (startDate.isBefore(today)) {
                throw DomainException.BusinessRuleViolation(
                    "startDate",
                    "A data de início deve ser igual ou posterior à data do cadastro.",
                )
            }
            return Lease(
                id = LeaseId.new(),
                tenant = tenant,
                agencySigner = agencySigner,
                propertyAddress = address,
                rentAmount = rentAmount,
                startDate = startDate,
                term = term,
                status = LeaseStatus.REGISTERED,
                activatedAt = null,
                createdAt = now,
                rowVersion = null,
            )
        }

        /** Reconstitui o agregado a partir da persistência, sem reaplicar regras de cadastro. */
        fun restore(
            id: LeaseId,
            tenant: Tenant,
            agencySigner: AgencySigner,
            propertyAddress: String,
            rentAmount: Money,
            startDate: LocalDate,
            term: LeaseTerm,
            status: LeaseStatus,
            activatedAt: Instant?,
            createdAt: Instant,
            rowVersion: Long?,
        ): Lease = Lease(
            id, tenant, agencySigner, propertyAddress, rentAmount, startDate, term,
            status, activatedAt, createdAt, rowVersion,
        )
    }
}

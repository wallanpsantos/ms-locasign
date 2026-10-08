package br.com.locasign.lease.app.queries

import java.time.Instant
import java.time.LocalDate

/**
 * Projeção de leitura (Read Model / View) dos dados detalhados de uma locação para a camada de apresentação.
 *
 * **Responsabilidade:**
 * - Transportar as informações consolidadas da locação com proteção de privacidade (CPF mascarado).
 * - Expor o estado atual da locação e a referência à versão mais recente do contrato associado ([currentContract]).
 */
data class LeaseDetailView(
    val id: String,
    val status: String,
    val tenantName: String,
    val tenantCpfMasked: String,
    val tenantEmail: String,
    val agencySignerName: String,
    val agencySignerEmail: String,
    val propertyAddress: String,
    val rentAmount: String,
    val startDate: LocalDate,
    val termMonths: Int,
    val createdAt: Instant,
    val activatedAt: Instant?,
    val currentContract: CurrentContractView?,
)

/**
 * Projeção de leitura resumida do contrato atualmente vinculado à locação.
 *
 * **Responsabilidade:**
 * - Transportar status, número da versão e prazo de expiração da versão vigente do contrato conforme regra R9.
 */
data class CurrentContractView(
    val id: String,
    val versionNumber: Int,
    val status: String,
    val expiresAt: Instant?,
)

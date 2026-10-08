package br.com.locasign.lease.app.queries

import java.time.Instant
import java.time.LocalDate

/** Modelo de leitura da locação. O CPF sai sempre mascarado. */
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

/** Versão mais recente do contrato da locação (R9 preserva todas as versões no histórico). */
data class CurrentContractView(
    val id: String,
    val versionNumber: Int,
    val status: String,
    val expiresAt: Instant?,
)

package br.com.locasign.lease.app.ports.`in`

import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.valueobjects.LeaseId

/**
 * Porta de entrada da consulta da locação pela API HTTP.
 *
 * **Responsabilidade:**
 * - Expor à controller a leitura do detalhe da locação, sem revelar a classe concreta da query.
 */
interface GetLeasePort {
    fun execute(leaseId: LeaseId): LeaseDetailView
}

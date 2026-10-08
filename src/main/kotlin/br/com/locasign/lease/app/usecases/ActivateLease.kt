package br.com.locasign.lease.app.usecases

import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException

/**
 * Ativa a locação. Chamado pela ação pós-assinatura do módulo de contratos (R8), dentro da
 * transação dela. Devolve `true` se a ativação ocorreu agora e `false` se já estava ativa.
 */
class ActivateLease(
    private val leases: LeaseRepositoryPort,
    private val clock: BusinessClock,
    private val transactions: TransactionRunner,
) {

    fun execute(leaseId: LeaseId): Boolean = transactions.run {
        val lease = leases.findById(leaseId) ?: throw DomainException.NotFound("Locação", leaseId.toString())
        val changed = lease.activate(clock.now())
        if (changed) leases.save(lease)
        changed
    }
}

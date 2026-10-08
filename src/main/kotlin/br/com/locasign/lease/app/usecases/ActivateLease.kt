package br.com.locasign.lease.app.usecases

import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.domain.DomainException

/**
 * Caso de uso de aplicação para ativação do status de vigência de uma locação.
 *
 * **Responsabilidade:**
 * - Executar a transição de estado da locação para ativo ([LeaseStatus.ACTIVE]) acionada por eventos pós-assinatura (regra R8).
 * - Garantir execução atômica via [TransactionRunner] e idempotência se a locação já estiver ativa.
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

package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.app.usecases.ActivateLease
import br.com.locasign.lease.domain.valueobjects.LeaseId
import org.springframework.stereotype.Component

/**
 * Adaptador de integração que implementa a porta [LeaseLookupPort] consultando o módulo `lease`.
 *
 * **Responsabilidade:**
 * - Prover a visão somente-leitura dos dados da locação ([LeaseSnapshot]) consumindo a porta de repositório do módulo de locações.
 * - Manter a separação de fronteiras no monólito modular, garantindo que o módulo `contract` não acesse tabelas de locação diretamente.
 */
@Component
class LeaseLookupAdapter(private val leases: LeaseRepositoryPort) : LeaseLookupPort {

    override fun find(leaseId: LeaseId): LeaseSnapshot? = leases.findById(leaseId)?.let { lease ->
        LeaseSnapshot(
            leaseId = lease.id,
            tenantName = lease.tenant.name,
            tenantCpf = lease.tenant.cpf,
            tenantEmail = lease.tenant.email,
            agencySignerName = lease.agencySigner.name,
            agencySignerEmail = lease.agencySigner.email,
            propertyAddress = lease.propertyAddress,
            rentAmount = lease.rentAmount,
            startDate = lease.startDate,
            termMonths = lease.term.months,
        )
    }
}

/**
 * Adaptador de integração que implementa a porta [LeaseActivationPort] invocando o caso de uso [ActivateLease].
 *
 * **Responsabilidade:**
 * - Conectar o fluxo de pós-assinatura de contratos à ativação formal da locação de forma limpa e desacoplada.
 */
@Component
class LeaseActivationAdapter(private val activateLease: ActivateLease) : LeaseActivationPort {

    override fun activate(leaseId: LeaseId): Boolean = activateLease.execute(leaseId)
}

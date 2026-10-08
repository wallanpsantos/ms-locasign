package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.lease.LeaseSnapshot
import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.app.usecases.ActivateLease
import br.com.locasign.lease.domain.valueobjects.LeaseId
import org.springframework.stereotype.Component

/** Ponte entre módulos: o contrato enxerga a locação só por esta porta, nunca pelo repositório dela. */
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

@Component
class LeaseActivationAdapter(private val activateLease: ActivateLease) : LeaseActivationPort {

    override fun activate(leaseId: LeaseId): Boolean = activateLease.execute(leaseId)
}

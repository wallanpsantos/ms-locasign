package br.com.locasign.lease.infra.config

import br.com.locasign.lease.app.ports.out.repository.LeaseQueryPort
import br.com.locasign.lease.app.ports.out.repository.LeaseRepositoryPort
import br.com.locasign.lease.app.queries.GetLease
import br.com.locasign.lease.app.usecases.ActivateLease
import br.com.locasign.lease.app.usecases.AgencySignerDefaults
import br.com.locasign.lease.app.usecases.RegisterLease
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Os use cases são classes Kotlin puras; este é o único lugar que os instancia (guia, seção 3.6). */
@Configuration(proxyBeanMethods = false)
class LeaseBeansConfig {

    @Bean
    fun registerLease(
        leases: LeaseRepositoryPort,
        clock: BusinessClock,
        transactions: TransactionRunner,
        properties: LocaSignProperties,
    ): RegisterLease = RegisterLease(
        leases = leases,
        clock = clock,
        transactions = transactions,
        agencyDefaults = AgencySignerDefaults(properties.agencySigner.name, properties.agencySigner.email),
    )

    @Bean
    fun activateLease(
        leases: LeaseRepositoryPort,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): ActivateLease = ActivateLease(leases, clock, transactions)

    @Bean
    fun getLease(queries: LeaseQueryPort): GetLease = GetLease(queries)
}

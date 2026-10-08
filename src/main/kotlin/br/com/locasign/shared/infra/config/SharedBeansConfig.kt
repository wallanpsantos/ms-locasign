package br.com.locasign.shared.infra.config

import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SharedBeansConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun businessClock(clock: Clock, properties: LocaSignProperties): BusinessClock =
        SystemBusinessClock(clock, ZoneId.of(properties.timezone))

    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        SpringTransactionRunner(TransactionTemplate(transactionManager))
}

/** Instantes em UTC; "hoje" no fuso da operação (`APP_TIMEZONE`, padrão America/Sao_Paulo). */
class SystemBusinessClock(private val clock: Clock, private val zone: ZoneId) : BusinessClock {
    override fun now(): Instant = clock.instant()

    override fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
}

/**
 * Implementa a fronteira transacional da camada `app` (ADR-011). Chamadas aninhadas participam da
 * transação em andamento (propagação REQUIRED); exceções não verificadas desfazem a transação.
 */
class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunner {

    override fun <T> run(block: () -> T): T = template.execute { block() }
}

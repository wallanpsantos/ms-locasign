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

/**
 * Configuração Spring dos beans compartilhados e utilitários transversais de infraestrutura.
 *
 * **Responsabilidade:**
 * - Habilitar o agendamento de tarefas (`@EnableScheduling`) e instanciar os adaptadores de relógio de negócio e gerenciamento transacional.
 * - Isolar a instanciação das implementações de infraestrutura em relação às portas de aplicação.
 */
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

/**
 * Implementação do relógio de negócio baseada no relógio do sistema e fuso horário configurado.
 *
 * **Responsabilidade:**
 * - Adaptar o [Clock] padrão da JVM para a porta [BusinessClock], garantindo instantes UTC e datas truncadas no fuso horário operacional.
 */
class SystemBusinessClock(private val clock: Clock, private val zone: ZoneId) : BusinessClock {
    override fun now(): Instant = clock.instant()

    override fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
}

/**
 * Implementação da porta [TransactionRunner] utilizando o `TransactionTemplate` do Spring Framework.
 *
 * **Responsabilidade:**
 * - Executar blocos de código com demarcação transacional de propagação REQUIRED e rollback automático sob exceções, conforme ADR-011.
 */
class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunner {

    override fun <T> run(block: () -> T): T = template.execute { block() }
}

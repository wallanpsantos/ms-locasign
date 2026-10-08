package br.com.locasign.notification.infra.config

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.app.usecases.RecordNotifications
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuração de injeção de dependências do Spring para os casos de uso do módulo de notificações.
 *
 * **Responsabilidade:**
 * - Instanciar explicitamente o caso de uso [RecordNotifications] como classe pura do Kotlin, desacoplada de anotações do Spring.
 * - Conectar as portas de saída aos adaptadores de persistência e transação configurados no container.
 */
@Configuration(proxyBeanMethods = false)
class NotificationBeansConfig {

    @Bean
    fun recordNotifications(
        recipients: NotificationRecipientsPort,
        log: NotificationLogPort,
        processed: ProcessedMessagesPort,
        transactions: TransactionRunner,
    ): RecordNotifications = RecordNotifications(recipients, log, processed, transactions)
}

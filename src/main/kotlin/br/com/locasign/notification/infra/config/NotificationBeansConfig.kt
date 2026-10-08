package br.com.locasign.notification.infra.config

import br.com.locasign.notification.app.ports.out.NotificationLogPort
import br.com.locasign.notification.app.ports.out.NotificationRecipientsPort
import br.com.locasign.notification.app.usecases.RecordNotifications
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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

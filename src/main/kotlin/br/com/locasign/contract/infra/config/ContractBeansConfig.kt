package br.com.locasign.contract.infra.config

import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocumentStoragePort
import br.com.locasign.contract.app.ports.out.lease.LeaseActivationPort
import br.com.locasign.contract.app.ports.out.lease.LeaseLookupPort
import br.com.locasign.contract.app.ports.out.messaging.ContractEventPublisherPort
import br.com.locasign.contract.app.ports.out.repository.ContractQueryPort
import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.app.queries.GetContract
import br.com.locasign.contract.app.queries.GetContractHistory
import br.com.locasign.contract.app.usecases.ApplyProviderUpdate
import br.com.locasign.contract.app.usecases.ArchiveSignedDocument
import br.com.locasign.contract.app.usecases.CancelContract
import br.com.locasign.contract.app.usecases.ContractPersister
import br.com.locasign.contract.app.usecases.ContractSettings
import br.com.locasign.contract.app.usecases.CreateProviderDocument
import br.com.locasign.contract.app.usecases.ExpireOverdueContracts
import br.com.locasign.contract.app.usecases.ProcessProviderWebhookItem
import br.com.locasign.contract.app.usecases.ReceiveProviderWebhook
import br.com.locasign.contract.app.usecases.ReconcileContracts
import br.com.locasign.contract.app.usecases.RequestContract
import br.com.locasign.contract.app.usecases.RunPostSignatureActions
import br.com.locasign.contract.app.usecases.SendContract
import br.com.locasign.contract.app.usecases.SendSignatureReminders
import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.app.ports.OutboxPort
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import br.com.locasign.shared.app.ports.WebhookInboxPort
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Os use cases são classes Kotlin puras; este é o único lugar que os instancia (guia, seção 3.6). */
@Configuration(proxyBeanMethods = false)
class ContractBeansConfig {

    @Bean
    fun contractSettings(properties: LocaSignProperties): ContractSettings = ContractSettings(
        signatureDeadline = Duration.ofDays(properties.contract.signatureDeadlineDays),
        reminderAfter = Duration.ofDays(properties.contract.reminderAfterDays),
        reconciliationStaleAfter = properties.contract.reconciliationStaleAfter,
        jobBatchSize = properties.jobs.batchSize,
    )

    @Bean
    fun contractPersister(
        contracts: ContractRepositoryPort,
        publisher: ContractEventPublisherPort,
    ): ContractPersister = ContractPersister(contracts, publisher)

    @Bean
    fun requestContract(
        leases: LeaseLookupPort,
        contracts: ContractRepositoryPort,
        persister: ContractPersister,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): RequestContract = RequestContract(leases, contracts, persister, clock, transactions)

    @Bean
    fun createProviderDocument(
        contracts: ContractRepositoryPort,
        leases: LeaseLookupPort,
        provider: SignatureProviderPort,
        persister: ContractPersister,
        processed: ProcessedMessagesPort,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): CreateProviderDocument =
        CreateProviderDocument(contracts, leases, provider, persister, processed, clock, transactions)

    @Bean
    fun sendContract(
        contracts: ContractRepositoryPort,
        provider: SignatureProviderPort,
        persister: ContractPersister,
        processed: ProcessedMessagesPort,
        clock: BusinessClock,
        settings: ContractSettings,
        transactions: TransactionRunner,
    ): SendContract = SendContract(contracts, provider, persister, processed, clock, settings, transactions)

    @Bean
    fun applyProviderUpdate(
        contracts: ContractRepositoryPort,
        persister: ContractPersister,
        clock: BusinessClock,
        settings: ContractSettings,
        metrics: MetricsPort,
        transactions: TransactionRunner,
    ): ApplyProviderUpdate = ApplyProviderUpdate(contracts, persister, clock, settings, metrics, transactions)

    @Bean
    fun archiveSignedDocument(
        contracts: ContractRepositoryPort,
        provider: SignatureProviderPort,
        storage: SignedDocumentStoragePort,
        persister: ContractPersister,
        processed: ProcessedMessagesPort,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): ArchiveSignedDocument =
        ArchiveSignedDocument(contracts, provider, storage, persister, processed, clock, transactions)

    @Bean
    fun processProviderWebhookItem(
        gateway: ProviderWebhookGateway,
        applyUpdate: ApplyProviderUpdate,
        archive: ArchiveSignedDocument,
        processed: ProcessedMessagesPort,
        transactions: TransactionRunner,
    ): ProcessProviderWebhookItem = ProcessProviderWebhookItem(gateway, applyUpdate, archive, processed, transactions)

    @Bean
    fun receiveProviderWebhook(
        gateway: ProviderWebhookGateway,
        inbox: WebhookInboxPort,
        outbox: OutboxPort,
        clock: BusinessClock,
        metrics: MetricsPort,
        transactions: TransactionRunner,
    ): ReceiveProviderWebhook = ReceiveProviderWebhook(gateway, inbox, outbox, clock, metrics, transactions)

    @Bean
    fun cancelContract(
        contracts: ContractRepositoryPort,
        provider: SignatureProviderPort,
        persister: ContractPersister,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): CancelContract = CancelContract(contracts, provider, persister, clock, transactions)

    @Bean
    fun expireOverdueContracts(
        contracts: ContractRepositoryPort,
        provider: SignatureProviderPort,
        persister: ContractPersister,
        clock: BusinessClock,
        settings: ContractSettings,
        transactions: TransactionRunner,
    ): ExpireOverdueContracts = ExpireOverdueContracts(contracts, provider, persister, clock, settings, transactions)

    @Bean
    fun sendSignatureReminders(
        contracts: ContractRepositoryPort,
        persister: ContractPersister,
        clock: BusinessClock,
        settings: ContractSettings,
        transactions: TransactionRunner,
    ): SendSignatureReminders = SendSignatureReminders(contracts, persister, clock, settings, transactions)

    @Bean
    fun reconcileContracts(
        contracts: ContractRepositoryPort,
        provider: SignatureProviderPort,
        applyUpdate: ApplyProviderUpdate,
        clock: BusinessClock,
        settings: ContractSettings,
        transactions: TransactionRunner,
    ): ReconcileContracts = ReconcileContracts(contracts, provider, applyUpdate, clock, settings, transactions)

    @Bean
    fun runPostSignatureActions(
        contracts: ContractRepositoryPort,
        leases: LeaseLookupPort,
        leaseActivation: LeaseActivationPort,
        actions: PostSignatureActionsPort,
        persister: ContractPersister,
        processed: ProcessedMessagesPort,
        clock: BusinessClock,
        transactions: TransactionRunner,
    ): RunPostSignatureActions =
        RunPostSignatureActions(contracts, leases, leaseActivation, actions, persister, processed, clock, transactions)

    @Bean
    fun getContract(queries: ContractQueryPort): GetContract = GetContract(queries)

    @Bean
    fun getContractHistory(queries: ContractQueryPort): GetContractHistory = GetContractHistory(queries)
}

# Task List: Redocumentação de Classes com KDoc Estruturado

Este documento contém a lista completa e ordenada de tarefas para a remoção das descrições antigas e inserção de novos
KDocs estruturados em todas as classes, interfaces, objetos e enums do `ms-locasign`.

O padrão exigido para cada tipo declarado é:

```kotlin
/**
 * [O que a classe/interface/objeto faz em termos de fluxo de negócio e comportamento técnico].
 *
 * **Responsabilidade:**
 * - [Responsabilidade 1 / Papel na arquitetura hexagonal].
 * - [Responsabilidade 2 / Garantias, invariantes ou regras de negócio].
 */
```

---

## Phase 1: Módulo Shared (Fundação Transversal)

### Task 1: Documentar Domínio Compartilhado (Value Objects e Exceções)

**Description:** Substituir ou adicionar KDocs estruturados nas classes e tipos do domínio base compartilhado
(`DomainEvent`, `DomainException` e subclasses, `Cpf`, `Email`, `Money`), explicando o que cada tipo faz e sua
responsabilidade em encapsular invariantes primitivos do domínio.

**Acceptance criteria:**

- [x] Todas as classes e interfaces em `shared/domain/` possuem KDoc com seção de responsabilidade.
- [x] `DomainEvent` e a hierarquia `DomainException` (`BusinessRuleViolation`, `NotFound`, `ActiveContractExists`,
  `ContractFinal`) detalham sua responsabilidade de erro de negócio.
- [x] Value objects `Cpf`, `Email` e `Money` documentam a validação de formato e garantias de imutabilidade.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** None

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/domain/DomainEvent.kt`
- `src/main/kotlin/br/com/locasign/shared/domain/DomainException.kt`
- `src/main/kotlin/br/com/locasign/shared/domain/valueobjects/Cpf.kt`
- `src/main/kotlin/br/com/locasign/shared/domain/valueobjects/Email.kt`
- `src/main/kotlin/br/com/locasign/shared/domain/valueobjects/Money.kt`

**Estimated scope:** M (5 files)

---

### Task 2: Documentar Aplicação Compartilhada (Portas de Infraestrutura - Parte 1)

**Description:** Documentar as portas da camada de aplicação do módulo compartilhado responsáveis por tempo de negócio,
contexto de correlação, métricas, outbox e mensagens processadas.

**Acceptance criteria:**

- [x] Portas `BusinessClock`, `CorrelationContext`, `MetricsPort`, `OutboxPort` e `ProcessedMessagesPort` recebem KDocs
  detalhando papel arquitetural como contratos abstratos desacoplados de infraestrutura.
- [x] Objetos utilitários associados (`ContextKeys`, `Metrics`, `OutboxMessage`) documentam suas responsabilidades.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 1

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/app/ports/BusinessClock.kt`
- `src/main/kotlin/br/com/locasign/shared/app/ports/CorrelationContext.kt`
- `src/main/kotlin/br/com/locasign/shared/app/ports/MetricsPort.kt`
- `src/main/kotlin/br/com/locasign/shared/app/ports/OutboxPort.kt`
- `src/main/kotlin/br/com/locasign/shared/app/ports/ProcessedMessagesPort.kt`

**Estimated scope:** M (5 files)

---

### Task 3: Documentar Aplicação Compartilhada (Portas de Infraestrutura - Parte 2 e Mensageria)

**Description:** Documentar as portas restantes da aplicação compartilhada (`TransactionRunner`, `WebhookInboxPort`),
além das constantes de tópicos/grupos Kafka (`Messaging.kt`) e tratamento de erros de mensageria (`MessagingErrors.kt`).

**Acceptance criteria:**

- [x] `TransactionRunner` documenta a responsabilidade de delimitação transacional sem poluição de anotações no domínio
  (ADR-011).
- [x] `WebhookInboxPort` detalha o armazenamento de requisições idempotentes de webhook em raw bytes.
- [x] `Topics`, `ConsumerGroups`, `UnreadableMessageException` e `DeadLetterReplayPort` possuem KDocs claros.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 2

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/app/ports/TransactionRunner.kt`
- `src/main/kotlin/br/com/locasign/shared/app/ports/WebhookInboxPort.kt`
- `src/main/kotlin/br/com/locasign/shared/app/Messaging.kt`
- `src/main/kotlin/br/com/locasign/shared/app/MessagingErrors.kt`

**Estimated scope:** M (4 files)

---

### Task 4: Documentar Infraestrutura Compartilhada (Propriedades e Configurações de Beans)

**Description:** Documentar as classes de configuração Spring do módulo compartilhado, incluindo Kafka, OpenAPI,
propriedades centralizadas de configuração (`LocaSignProperties`) e configuração de beans padrão (`SharedBeansConfig`).

**Acceptance criteria:**

- [x] `KafkaConfig`, `OpenApiConfig`, `SharedBeansConfig` (e classes internas de clock e transaction runner) recebem
  KDocs descrevendo o provisionamento de infraestrutura.
- [x] `LocaSignProperties` e todas as suas classes filhas de configuração mapeiam as variáveis de ambiente e suas
  responsabilidades de parametrização.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 3

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/infra/config/KafkaConfig.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/config/LocaSignProperties.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/config/OpenApiConfig.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/config/SharedBeansConfig.kt`

**Estimated scope:** M (4 files)

---

### Task 5: Documentar Infraestrutura Compartilhada (Observabilidade, Agendamento e Mensageria)

**Description:** Adicionar KDoc às classes de mensageria ativa (`OutboxRelay`, `DeadLetterReplayer`), saúde de Kafka
(`KafkaHealthIndicator`), adaptadores de métricas/contexto (`ObservabilityAdapters.kt`) e rotina de limpeza
(`HousekeepingJob.kt`).

**Acceptance criteria:**

- [x] `OutboxRelay` e `DeadLetterReplayer` explicitam suas responsabilidades no repasse transacional e recuperação de
  falhas em DLQ.
- [x] `KafkaHealthIndicator` e adaptadores de métricas/MDC detalham seu papel na observabilidade e rastreabilidade
  distribuída.
- [x] `HousekeepingJob` documenta a limpeza de registros antigos no banco de dados.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 4

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/infra/messaging/DeadLetterReplayer.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/messaging/OutboxRelay.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/observability/KafkaHealthIndicator.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/observability/ObservabilityAdapters.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/scheduling/HousekeepingJob.kt`

**Estimated scope:** M (5 files)

---

### Task 6: Documentar Infraestrutura Compartilhada (Persistência e Adaptadores JDBC)

**Description:** Documentar os adaptadores de persistência de mensageria (`JdbcMessagingAdapters.kt`), que implementam
outbox, inbox e idempotência, além das funções de suporte a JDBC (`JdbcSupport.kt`).

**Acceptance criteria:**

- [x] `JdbcOutboxAdapter`, `JdbcProcessedMessagesAdapter` e `JdbcWebhookInboxAdapter` documentam as operações
  transacionais no PostgreSQL.
- [x] `JdbcSupport.kt` documenta sua responsabilidade como ponte de conversões de tipos SQL/Kotlin.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 5

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/infra/persistence/JdbcMessagingAdapters.kt`
- `src/main/kotlin/br/com/locasign/shared/infra/persistence/JdbcSupport.kt`

**Estimated scope:** S (2 files)

---

### Task 7: Documentar Interfaces Compartilhadas (Web, Filtros, Tratamento de Erros e Segurança)

**Description:** Documentar adaptadores de entrada HTTP/REST compartilhados: `AdminController`, `ApiExceptionHandler`,
`CorrelationIdFilter`, `OperatorAccessGuard` e envelope de eventos (`EventEnvelope.kt`).

**Acceptance criteria:**

- [x] `AdminController` detalha a responsabilidade de expor operações de manutenção restritas.
- [x] `ApiExceptionHandler` documenta a tradução de exceções de domínio e sistema para Problem Details (RFC 7807).
- [x] `CorrelationIdFilter` e `OperatorAccessGuard` documentam propagação de rastreamento e proteção de endpoints
  administrativos.
- [x] `EventEnvelope` documenta o formato canônico de publicação e leitura de eventos no Kafka.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 6

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/shared/interfaces/messaging/EventEnvelope.kt`
- `src/main/kotlin/br/com/locasign/shared/interfaces/web/AdminController.kt`
- `src/main/kotlin/br/com/locasign/shared/interfaces/web/ApiExceptionHandler.kt`
- `src/main/kotlin/br/com/locasign/shared/interfaces/web/CorrelationIdFilter.kt`
- `src/main/kotlin/br/com/locasign/shared/interfaces/web/OperatorAccessGuard.kt`

**Estimated scope:** M (5 files)

---

### Checkpoint: Shared Module

- [x] Todos os 30 arquivos do módulo `shared` compilam sem advertências ou erros.
- [x] Todas as classes e interfaces em `br.com.locasign.shared` possuem KDoc no formato estruturado.

---

## Phase 2: Módulo Lease (Ciclo de Vida de Locação)

### Task 8: Documentar Domínio de Locação (Agregado Lease, Partes e Value Objects)

**Description:** Documentar o agregado raiz `Lease`, enum `LeaseStatus`, partes da locação (`Tenant`, `AgencySigner`) e
os value objects `LeaseId` e `LeaseTerm`.

**Acceptance criteria:**

- [x] `Lease` documenta a representação do contrato imobiliário no negócio e invariante de ativação (ação
  pós-assinatura).
- [x] `Tenant`, `AgencySigner`, `LeaseId` e `LeaseTerm` detalham suas responsabilidades e validações (R2).

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Checkpoint: Shared Module

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/lease/domain/models/Lease.kt`
- `src/main/kotlin/br/com/locasign/lease/domain/models/LeaseParties.kt`
- `src/main/kotlin/br/com/locasign/lease/domain/valueobjects/LeaseId.kt`
- `src/main/kotlin/br/com/locasign/lease/domain/valueobjects/LeaseTerm.kt`

**Estimated scope:** M (4 files)

---

### Task 9: Documentar Aplicação de Locação (Casos de Uso, Portas de Saída e Visões)

**Description:** Documentar os casos de uso `RegisterLease` e `ActivateLease`, a consulta `GetLease`, a projeção
`LeaseDetailView` e as portas `LeaseRepositoryPort` e `LeaseQueryPort`.

**Acceptance criteria:**

- [x] `RegisterLease` documenta a orquestração do cadastro e validações de negócio da locação.
- [x] `ActivateLease` documenta a transição de estado disparada após conclusão das assinaturas.
- [x] Portas e projeções de consulta documentam seus contratos de persistência e leitura.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 8

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/lease/app/ports/out/repository/LeasePorts.kt`
- `src/main/kotlin/br/com/locasign/lease/app/queries/GetLease.kt`
- `src/main/kotlin/br/com/locasign/lease/app/queries/LeaseDetailView.kt`
- `src/main/kotlin/br/com/locasign/lease/app/usecases/ActivateLease.kt`
- `src/main/kotlin/br/com/locasign/lease/app/usecases/RegisterLease.kt`

**Estimated scope:** M (5 files)

---

### Task 10: Documentar Interfaces Web de Locação (Controller REST, DTOs, Mappers e OpenAPI)

**Description:** Documentar `LeaseController`, contrato OpenAPI `LeaseApi`, DTOs de requisição
(`CreateLeaseRequest.kt`), DTOs de resposta (`LeaseResponses.kt`) e mappers web (`LeaseWebMappers.kt`).

**Acceptance criteria:**

- [x] `LeaseController` e `LeaseApi` documentam os endpoints REST de cadastro e busca de locações.
- [x] DTOs de requisição e resposta documentam seus esquemas de transferência de dados e validações Bean Validation
  (`@field:`).
- [x] Mappers documentam a conversão bidirecional entre DTOs e modelos de aplicação.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 9

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/lease/interfaces/web/LeaseController.kt`
- `src/main/kotlin/br/com/locasign/lease/interfaces/web/openapi/LeaseApi.kt`
- `src/main/kotlin/br/com/locasign/lease/interfaces/web/dto/request/CreateLeaseRequest.kt`
- `src/main/kotlin/br/com/locasign/lease/interfaces/web/dto/response/LeaseResponses.kt`
- `src/main/kotlin/br/com/locasign/lease/interfaces/web/mappers/LeaseWebMappers.kt`

**Estimated scope:** M (5 files)

---

### Task 11: Documentar Infraestrutura de Locação (Configuração, Entidades, Mappers e Repositório JDBC)

**Description:** Documentar a configuração Spring `LeaseBeansConfig`, entidade JDBC `LeaseEntity`, repositório Spring
Data `LeaseJdbcRepository`, adaptadores `LeaseAdapters.kt` e mappers de entidade `LeaseEntityMappers.kt`.

**Acceptance criteria:**

- [x] `LeaseBeansConfig` documenta a injeção e instanciação explícita dos casos de uso de locação.
- [x] `LeaseEntity` e `LeaseJdbcRepository` documentam o esquema da tabela `leases` e consultas relacionais.
- [x] `LeaseRepositoryAdapter` e `LeaseQueryAdapter` documentam a implementação das portas de saída.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 10

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/lease/infra/config/LeaseBeansConfig.kt`
- `src/main/kotlin/br/com/locasign/lease/infra/persistence/adapters/LeaseAdapters.kt`
- `src/main/kotlin/br/com/locasign/lease/infra/persistence/entities/LeaseEntity.kt`
- `src/main/kotlin/br/com/locasign/lease/infra/persistence/mappers/LeaseEntityMappers.kt`
- `src/main/kotlin/br/com/locasign/lease/infra/persistence/repositories/LeaseJdbcRepository.kt`

**Estimated scope:** M (5 files)

---

### Checkpoint: Lease Module

- [x] Todos os 19 arquivos do módulo `lease` compilam com sucesso.
- [x] Nenhuma classe do módulo `lease` permanece sem KDoc estruturado.

---

## Phase 3: Módulo Notification (Comunicação de Eventos)

### Task 12: Documentar Domínio e Aplicação de Notificações (Política, Portas e Casos de Uso)

**Description:** Documentar a política de notificação (`NotificationPolicy.kt`), portas de destinatários e log
(`NotificationPorts.kt`) e o caso de uso `RecordNotifications.kt`.

**Acceptance criteria:**

- [x] `NotificationPolicy` documenta as regras de determinação de público-alvo (locatário, imobiliária, ambos) para cada
  evento.
- [x] `NotificationRecipientsPort` e `NotificationLogPort` detalham suas funções no envio e auditoria de notificações.
- [x] `RecordNotifications` documenta o registro e simulação de notificações enviadas.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Checkpoint: Lease Module

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/notification/domain/NotificationPolicy.kt`
- `src/main/kotlin/br/com/locasign/notification/app/ports/out/NotificationPorts.kt`
- `src/main/kotlin/br/com/locasign/notification/app/usecases/RecordNotifications.kt`

**Estimated scope:** S (3 files)

---

### Task 13: Documentar Infraestrutura e Interfaces de Notificações (Configuração, Adaptadores JDBC e Consumidor Kafka)

**Description:** Documentar `NotificationBeansConfig`, adaptadores JDBC `JdbcNotificationAdapters.kt` e o consumidor
Kafka `NotificationConsumer.kt`.

**Acceptance criteria:**

- [x] `NotificationConsumer` documenta o consumo assíncrono de eventos de contrato com idempotência transacional.
- [x] `JdbcNotificationRecipientsAdapter` e `JdbcNotificationLogAdapter` documentam o acesso aos destinatários e
  gravação em tabela de log.
- [x] `NotificationBeansConfig` documenta a amarração dos beans do módulo.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 12

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/notification/infra/config/NotificationBeansConfig.kt`
- `src/main/kotlin/br/com/locasign/notification/infra/persistence/JdbcNotificationAdapters.kt`
- `src/main/kotlin/br/com/locasign/notification/interfaces/messaging/NotificationConsumer.kt`

**Estimated scope:** S (3 files)

---

### Checkpoint: Notification Module

- [x] Todos os 6 arquivos do módulo `notification` compilam com sucesso.
- [x] Notificações e políticas documentadas integralmente em conformidade com R1-R10.

---

## Phase 4: Módulo Contract - Domínio e Aplicação

### Task 14: Documentar Domínio de Contratos (Agregado Contract, Status e Value Objects)

**Description:** Documentar a entidade agregada `Contract`, seus estados `ContractStatus` (e classes de origem de
alteração), e os value objects `ContractValueObjects.kt` (`ContractId`, `ProviderDocumentId`, `SigningOrder`).

**Acceptance criteria:**

- [x] `Contract` documenta seu papel como raiz de agregação e fonte da verdade absoluta sobre o ciclo de vida
  documental.
- [x] `ContractStatus` mapeia o diagrama de transições de estados e origens de evento (`ChangeSource`).
- [x] Value objects de contrato documentam suas validações e encapsulamentos de identificadores.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Checkpoint: Notification Module

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/domain/models/Contract.kt`
- `src/main/kotlin/br/com/locasign/contract/domain/models/ContractStatus.kt`
- `src/main/kotlin/br/com/locasign/contract/domain/valueobjects/ContractValueObjects.kt`

**Estimated scope:** S (3 files)

---

### Task 15: Documentar Domínio de Contratos (Partes, Serviços de Transição e Eventos de Domínio)

**Description:** Documentar os componentes do contrato em `ContractParts.kt` (`Signer`, `CancelReason`, etc.), o serviço
de domínio `ContractTransitionPolicy.kt` e toda a família de eventos em `ContractEvent.kt`.

**Acceptance criteria:**

- [x] `ContractTransitionPolicy` documenta a matriz formal de transição de estados e proteção contra retrocessos ou
  concorrência.
- [x] `Signer` e motivos de cancelamento documentam os papéis dos signatários e causas de encerramento prematuro.
- [x] Todos os eventos selados de `ContractEvent` documentam as mudanças de estado emitidas para outbox.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 14

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/domain/models/ContractParts.kt`
- `src/main/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicy.kt`
- `src/main/kotlin/br/com/locasign/contract/domain/events/ContractEvent.kt`

**Estimated scope:** S (3 files)

---

### Task 16: Documentar Portas de Saída e Modelos de Provedor de Contratos

**Description:** Documentar as portas de integração com provedores de assinatura (`IntegrationPorts.kt`), modelos
canônicos de provedor (`ProviderModels.kt`), erros tipados (`ProviderErrors.kt`) e porta de locação (`LeasePorts.kt`).

**Acceptance criteria:**

- [x] `SignatureProviderPort` e `SignedDocumentStoragePort` documentam os contratos agnósticos a fornecedores externos.
- [x] Modelos de provedor e exceções (`ProviderErrors`) detalham as estruturas canônicas de dados e falhas mapeadas.
- [x] `LeaseLookupPort` e `LeaseActivationPort` detalham o isolamento hexagonal de contratos em relação a locações.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 15

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/app/ports/out/integration/IntegrationPorts.kt`
- `src/main/kotlin/br/com/locasign/contract/app/ports/out/integration/ProviderErrors.kt`
- `src/main/kotlin/br/com/locasign/contract/app/ports/out/integration/ProviderModels.kt`
- `src/main/kotlin/br/com/locasign/contract/app/ports/out/lease/LeasePorts.kt`

**Estimated scope:** M (4 files)

---

### Task 17: Documentar Portas de Repositório, Mensageria e Visões de Contratos

**Description:** Documentar portas de persistência (`ContractPorts.kt`), publisher de eventos
(`ContractEventPublisherPort.kt`) e visões de consulta em `ContractViews.kt`.

**Acceptance criteria:**

- [x] `ContractRepositoryPort` e `ContractQueryPort` documentam os contratos de leitura e escrita do agregado e
  histórico.
- [x] `ContractEventPublisherPort` documenta a emissão via outbox.
- [x] `ContractDetailView`, `SignerView`, `GetContract` e `GetContractHistory` documentam o suporte a leituras
  desacopladas.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 16

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/app/ports/out/messaging/ContractEventPublisherPort.kt`
- `src/main/kotlin/br/com/locasign/contract/app/ports/out/repository/ContractPorts.kt`
- `src/main/kotlin/br/com/locasign/contract/app/queries/ContractViews.kt`

**Estimated scope:** S (3 files)

---

### Task 18: Documentar Casos de Uso de Contratos (Criação, Emissão e Suporte)

**Description:** Documentar `ContractSupport.kt`, `RequestContract`, `CreateProviderDocument`, `SendContract` e
`ApplyProviderUpdate`.

**Acceptance criteria:**

- [x] `RequestContract` documenta o ponto de partida do contrato e emissão do evento inicial.
- [x] `CreateProviderDocument` e `SendContract` detalham o pipeline assíncrono de criação no parceiro externo e envio
  para assinatura.
- [x] `ApplyProviderUpdate` documenta a aplicação protegida de atualizações recebidas.
- [x] `ContractPersister` e `ContractSettings` detalham a persistência transacional de agregado + histórico + outbox.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 17

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/app/usecases/ContractSupport.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/RequestContract.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/CreateProviderDocument.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/SendContract.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/ApplyProviderUpdate.kt`

**Estimated scope:** M (5 files)

---

### Task 19: Documentar Casos de Uso de Contratos (Webhooks, Arquivamento e Pós-Assinatura)

**Description:** Documentar `ReceiveProviderWebhook`, `ProcessProviderWebhookItem`, `ArchiveSignedDocument` e
`RunPostSignatureActions`.

**Acceptance criteria:**

- [x] `ReceiveProviderWebhook` documenta a recepção rápida no inbox HTTP com validação HMAC.
- [x] `ProcessProviderWebhookItem` documenta o processamento desacoplado a partir da fila Kafka.
- [x] `ArchiveSignedDocument` documenta o download e armazenamento seguro do documento final assinado.
- [x] `RunPostSignatureActions` documenta os efeitos colaterais de ativação da locação e notificação aos envolvidos.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 18

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/app/usecases/ReceiveProviderWebhook.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/ProcessProviderWebhookItem.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/ArchiveSignedDocument.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/RunPostSignatureActions.kt`

**Estimated scope:** M (4 files)

---

### Task 20: Documentar Casos de Uso de Contratos (Cancelamento, Expiração, Reconciliação e Lembretes)

**Description:** Documentar `CancelContract`, `ExpireOverdueContracts`, `ReconcileContracts` e `SendSignatureReminders`.

**Acceptance criteria:**

- [x] `CancelContract` documenta o cancelamento administrativo ou por desistência com aviso ao provedor.
- [x] `ExpireOverdueContracts` documenta a expiração automática de contratos que atingiram o prazo limite.
- [x] `ReconcileContracts` documenta a reconciliação periódica contra eventuais perdas de webhooks.
- [x] `SendSignatureReminders` documenta o envio de notificações de lembrete a signatários pendentes.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 19

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/app/usecases/CancelContract.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/ExpireOverdueContracts.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/ReconcileContracts.kt`
- `src/main/kotlin/br/com/locasign/contract/app/usecases/SendSignatureReminders.kt`

**Estimated scope:** M (4 files)

---

### Checkpoint: Contract Domain and Application

- [x] Todos os arquivos de domínio e aplicação de `contract` compilam sem erro.
- [x] Casos de uso e portas de contratos com KDocs rigorosos e fiéis às regras R1-R10.

---

## Phase 5: Módulo Contract - Infraestrutura

### Task 21: Documentar Integração PandaDoc (Cliente HTTP, Rate Limiter, Erros e Mappers)

**Description:** Documentar `PandaDocClient`, `SlidingWindowRateLimiter`, exceções em `PandaDocErrors.kt` e mappers em
`PandaDocMappers.kt`.

**Acceptance criteria:**

- [x] `PandaDocClient` documenta as chamadas HTTP à API PandaDoc via Spring HTTP Interface/RestClient.
- [x] `SlidingWindowRateLimiter` detalha o controle estrito de requisições por segundo para evitar HTTP 429.
- [x] `PandaDocErrors` e `PandaDocMappers` documentam a conversão entre o mundo PandaDoc e o modelo da aplicação.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Checkpoint: Contract Domain and Application

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/PandaDocClient.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/SlidingWindowRateLimiter.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/exceptions/PandaDocErrors.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/mappers/PandaDocMappers.kt`

**Estimated scope:** M (4 files)

---

### Task 22: Documentar Integração PandaDoc (Adaptador de Assinatura, Gateway de Webhook e DTOs)

**Description:** Documentar `PandaDocSignatureProviderAdapter`, `PandaDocWebhookGateway`, DTOs de requisição
(`PandaDocRequests.kt`) e DTOs de resposta (`PandaDocResponses.kt`).

**Acceptance criteria:**

- [x] `PandaDocSignatureProviderAdapter` documenta a implementação de `SignatureProviderPort`.
- [x] `PandaDocWebhookGateway` documenta a verificação de assinatura HMAC sobre bytes brutos do webhook.
- [x] DTOs de requisição e resposta documentam os formatos serializados esperados pela PandaDoc.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 21

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/adapters/PandaDocSignatureProviderAdapter.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/adapters/PandaDocWebhookGateway.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/dto/request/PandaDocRequests.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/pandadoc/dto/response/PandaDocResponses.kt`

**Estimated scope:** M (4 files)

---

### Task 23: Documentar Persistência de Contratos (Entidades, Mappers, Repositório JDBC e Adaptador)

**Description:** Documentar `ContractEntities.kt` (`ContractEntity`, `ContractSignerEntity`),
`ContractEntityMappers.kt`, `ContractJdbcRepository` e `ContractRepositoryAdapter`.

**Acceptance criteria:**

- [x] `ContractEntity` e signatários documentam o mapeamento relacional das tabelas `contracts` e `contract_signers`.
- [x] `ContractJdbcRepository` e `ContractRepositoryAdapter` detalham as queries SQL, locking otimista (`rowVersion`) e
  reconstituição do agregado.
- [x] `ContractEntityMappers` documenta a tradução entre entidade de banco e o agregado puro `Contract`.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 22

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/infra/persistence/entities/ContractEntities.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/persistence/mappers/ContractEntityMappers.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/persistence/repositories/ContractJdbcRepository.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/persistence/adapters/ContractRepositoryAdapter.kt`

**Estimated scope:** M (4 files)

---

### Task 24: Documentar Persistência e Storage de Contratos (Adapters de Consulta, Bridge de Locação e Armazenamento em Disco)

**Description:** Documentar `ContractQueryAdapter`, `LeaseBridgeAdapters.kt` (`LeaseLookupAdapter`,
`LeaseActivationAdapter`) e `FileSystemSignedDocumentStorage`.

**Acceptance criteria:**

- [x] `ContractQueryAdapter` documenta a execução de queries otimizadas de leitura do contrato e histórico.
- [x] `LeaseBridgeAdapters` documenta as pontes JDBC que realizam lookup e ativação da locação sem dependência direta de
  código entre módulos.
- [x] `FileSystemSignedDocumentStorage` documenta o arquivamento seguro e atômico do PDF em disco local.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 23

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/infra/persistence/adapters/ContractQueryAdapter.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/persistence/adapters/LeaseBridgeAdapters.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/storage/FileSystemSignedDocumentStorage.kt`

**Estimated scope:** M (3 files)

---

### Task 25: Documentar Mensageria, Agendamento e Configurações de Contratos (Publisher Outbox, Payloads, Jobs e Beans)

**Description:** Documentar `OutboxContractEventPublisher`, `ContractEventPayloads.kt`, jobs agendados em
`ContractJobs.kt`, `ContractBeansConfig` e `PandaDocConfig`.

**Acceptance criteria:**

- [x] `OutboxContractEventPublisher` documenta o enfileiramento transacional de eventos de contrato na outbox.
- [x] `ContractJobs` (`ReconciliationJob`, `ExpirationJob`, `ReminderJob`) documenta os agendamentos automáticos com
  anotações `@Scheduled`.
- [x] `ContractBeansConfig` e `PandaDocConfig` documentam a injeção e montagem dos componentes de contratos e PandaDoc.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 24

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/infra/messaging/ContractEventPayloads.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/messaging/OutboxContractEventPublisher.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/scheduling/ContractJobs.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/config/ContractBeansConfig.kt`
- `src/main/kotlin/br/com/locasign/contract/infra/config/PandaDocConfig.kt`

**Estimated scope:** M (5 files)

---

### Checkpoint: Contract Infrastructure

- [x] Todos os arquivos de infraestrutura de `contract` compilam sem erro.
- [x] Total conformidade com o ADR-012 (PandaDoc) e ADR-011 (Transações).

---

## Phase 6: Módulo Contract - Interfaces e Ponto de Entrada da Aplicação

### Task 26: Documentar Interfaces Web REST de Contratos (Controller, OpenAPI e DTOs)

**Description:** Documentar `ContractController`, `ContractApi`, DTO de cancelamento (`CancelContractRequest.kt`), DTOs
de resposta (`ContractResponses.kt`) e mappers web (`ContractWebMappers.kt`).

**Acceptance criteria:**

- [x] `ContractController` e `ContractApi` documentam as operações REST de solicitação, detalhamento, histórico e
  cancelamento de contratos.
- [x] DTOs de requisição e resposta documentam os contratos JSON expostos para o cliente.
- [x] `ContractWebMappers` documenta a conversão entre DTOs e entidades/visões de aplicação.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Checkpoint: Contract Infrastructure

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/interfaces/web/ContractController.kt`
- `src/main/kotlin/br/com/locasign/contract/interfaces/web/openapi/ContractApi.kt`
- `src/main/kotlin/br/com/locasign/contract/interfaces/web/dto/request/CancelContractRequest.kt`
- `src/main/kotlin/br/com/locasign/contract/interfaces/web/dto/response/ContractResponses.kt`
- `src/main/kotlin/br/com/locasign/contract/interfaces/web/mappers/ContractWebMappers.kt`

**Estimated scope:** M (5 files)

---

### Task 27: Documentar Webhooks de Provedor, Consumidores Kafka e Aplicação Principal (LocaSignApplication)

**Description:** Documentar `PandaDocWebhookController`, os consumidores Kafka em `ContractConsumers.kt`
(`OrchestratorConsumer`, `PostSignatureConsumer`, `ProviderEventsConsumer`) e a classe de inicialização Spring Boot
`LocaSignApplication.kt`.

**Acceptance criteria:**

- [x] `PandaDocWebhookController` documenta o endpoint seguro de recepção de webhooks HMAC sem bloqueio síncrono.
- [x] `ContractConsumers.kt` documenta a orquestração assíncrona, garantia de idempotência e pós-processamento de
  assinaturas.
- [x] `LocaSignApplication.kt` documenta o ponto de entrada da aplicação Spring Boot e fuso horário padronizado em UTC.

**Verification:**

- [x] Build succeeds: `./gradlew compileKotlin`

**Dependencies:** Task 26

**Files likely touched:**

- `src/main/kotlin/br/com/locasign/contract/interfaces/webhook/pandadoc/PandaDocWebhookController.kt`
- `src/main/kotlin/br/com/locasign/contract/interfaces/messaging/ContractConsumers.kt`
- `src/main/kotlin/br/com/locasign/LocaSignApplication.kt`

**Estimated scope:** S (3 files)

---

### Checkpoint: Complete Application

- [x] `./gradlew compileKotlin` executa com sucesso total.
- [x] Todos os 109 arquivos de código fonte Kotlin do projeto possuem KDocs atualizados e estruturados.
- [x] Nenhuma alteração de lógica de execução foi introduzida.
- [x] Workspace limpo e preparado para conferência humana via `git status` e `git diff`.

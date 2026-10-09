# LocaSign — Plano da Suíte de Testes Automatizados

> **Propósito:** Documento de especificação e estratégia da suíte de testes automatizados do LocaSign.  
> **Referência Técnica:** Complementa o [`plano-de-negocio.md`](plano-de-negocio.md) (regras R1 a R10), a [
`arquitetura-tecnica.md`](arquitetura-tecnica.md) (seção 15) e formaliza as diretrizes da seção 6 do [
`AGENTS.md`](../AGENTS.md).  
> **Status:** Aprovado para execução faseada.

---

## 1. Visão Geral e Princípios da Suíte

A suíte de testes do **LocaSign** é planejada sob a perspectiva de engenharia de software pragmática para sistemas
distribuídos de missão crítica, priorizando:

1. **Confiabilidade e Determinismo:** Zero testes intermitentes (*flaky tests*); sem `Thread.sleep()`; tempo controlado
   estritamente via `BusinessClock` configurável.
2. **Isolamento por Camadas:** Testes unitários puros para `domain` e `app` sem Spring ApplicationContext, sem Mockito e
   sem dependência de containers.
3. **Fidelidade de Persistência:** Testes de persistência executados exclusivamente contra PostgreSQL real via
   Testcontainers (`postgres:18.6`) com as migrations Flyway V1 reais; proibição de bancos em memória (H2/HSQLDB).
4. **Isolamento de Terceiros:** Nenhuma chamada à API real da PandaDoc (nem mesmo em sandbox); simulação com WireMock
   standalone em porta dinâmica.
5. **Garantia de Arquitetura:** Verificação contínua das fronteiras hexagonais e regras de dependência modular com
   ArchUnit para JUnit 6.
6. **Contrato de CI Inviolável:** `./gradlew test` executa a suíte completa no pipeline de integração contínua; atalhos
   rápidos (`testFast` / `--tests "*Test"`) aceleram o ciclo local do desenvolvedor.

---

## 2. Diagnóstico da Base de Código e Stack

### 2.1 Stack Diagnosticada

| Tecnologia / Componente | Versão Efetiva            | Origem / Mecanismo                   | Papel na Suíte de Testes                               |
|-------------------------|---------------------------|--------------------------------------|--------------------------------------------------------|
| **Kotlin**              | `2.4.20`                  | `gradle/libs.versions.toml`          | Linguagem base dos testes idiomáticos.                 |
| **Java Toolchain**      | `25` (LTS)                | `build.gradle.kts`                   | Runtime da JVM para execução de testes.                |
| **Gradle**              | `9.7.1`                   | Wrapper oficial                      | Orquestrador de tarefas e compilação.                  |
| **Spring Boot**         | `4.1.1`                   | `gradle/libs.versions.toml`          | Framework e autoconfigurações modulares de teste.      |
| **JUnit**               | Jupiter `6.0.3`           | Gerenciado pelo Spring Boot BOM      | Plataforma de execução de testes.                      |
| **Jackson**             | Linha 3 (`tools.jackson`) | `jackson-module-kotlin`              | Serialização/desserialização JSON (`JsonMapper`).      |
| **PostgreSQL**          | `18.6`                    | `compose.yml` (`postgres:18.6`)      | Testcontainers de banco relacional com Flyway V1.      |
| **Apache Kafka**        | `4.3.1` (KRaft)           | `compose.yml` (`apache/kafka:4.3.1`) | Testcontainers de mensageria com `@ServiceConnection`. |
| **Testcontainers**      | `2.0.5`                   | Gerenciado pelo Spring Boot BOM      | Gerenciamento de containers de teste.                  |

### 2.2 Estrutura de Pacotes e Componentes Mapeados

O LocaSign é um monólito modular hexagonal estruturado sob `br.com.locasign`:

```text
src/main/kotlin/br/com/locasign/
├── shared/          # Transversal: DomainException, VOs (Cpf, Email, Money), BusinessClock,
│                    # TransactionRunner, Outbox/Inbox, Relay, Telemetria, Segurança (OperatorAccessGuard)
├── lease/           # Domínio de Locação: agregado Lease, VOs (LeaseId, LeaseTerm), use cases
│                    # RegisterLease, ActivateLease, query GetLease, REST API LeaseController
├── contract/        # Domínio de Contratos: agregado Contract, máquina de estados, use cases
│                    # RequestContract, CreateProviderDocument, SendContract, ReceiveProviderWebhook,
│                    # ProcessProviderWebhookItem, ApplyProviderUpdate, ArchiveSignedDocument,
│                    # CancelContract, ExpireOverdueContracts, ReconcileContracts, SendSignatureReminders,
│                    # RunPostSignatureActions, REST API ContractController, WebhookController,
│                    # adapters PandaDoc, mensageria e jobs agendados
└── notification/    # Notificações simuladas: NotificationPolicy, use case RecordNotifications,
                     # consumidor Kafka NotificationConsumer e log de notificações
```

---

## 3. Mudanças nas Dependências e no Gradle

### 3.1 Version Catalog (`gradle/libs.versions.toml`)

Entradas adicionais no catálogo de dependências:

```toml
[versions]
kotlin = "2.4.20"
spring-boot = "4.1.1"
spring-dependency-management = "1.1.7"
springdoc = "3.1.0"
mockk = "1.14.11"
springmockk = "5.0.1"
wiremock = "3.13.2"
archunit = "1.5.1"
awaitility = "4.3.0"

[libraries]
springdoc-webmvc-ui = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
mockk-jvm = { module = "io.mockk:mockk-jvm", version.ref = "mockk" }
springmockk = { module = "com.ninja-squad:springmockk", version.ref = "springmockk" }
wiremock-standalone = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
archunit-junit6 = { module = "com.tngtech.archunit:archunit-junit6", version.ref = "archunit" }
awaitility-kotlin = { module = "org.awaitility:awaitility-kotlin", version.ref = "awaitility" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

### 3.2 Bloco `dependencies {}` no `build.gradle.kts`

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc.webmvc.ui)
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Dependências de teste modulares do Spring Boot 4.1
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Novas dependências propostas via ADR-014
    testImplementation(libs.mockk.jvm)
    testImplementation(libs.springmockk)
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.archunit.junit6)
    testImplementation(libs.awaitility.kotlin)
}
```

### 3.3 Cobertura com JaCoCo (Compatibilidade Java 25)

```kotlin
plugins {
    // ... plugins existentes ...
    jacoco
}

jacoco {
    toolVersion = "0.8.14" // Versão mínima oficial com suporte a bytecode Java 25
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/dto/**",
                    "**/openapi/**",
                    "**/entities/**",
                    "**/LocaSignApplicationKt.class"
                )
            }
        })
    )
}
```

### 3.4 Configuração de Tarefas de Teste no Gradle

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "UTC")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Atalho rápido: executa testes sem Docker (unitários, slices, arquitetura)
tasks.register<Test>("testFast") {
    group = "verification"
    description = "Executa apenas testes sem Docker (unitários, fakes, slices e arquitetura)."
    useJUnitPlatform {
        excludeTags("integration", "e2e")
    }
}

// Atalho de integração: executa testes dependentes de Testcontainers
tasks.register<Test>("testIntegration") {
    group = "verification"
    description = "Executa apenas testes de integração e ponta a ponta que utilizam Testcontainers."
    useJUnitPlatform {
        includeTags("integration", "e2e")
    }
}
```

---

## 4. Matriz Completa de Testes

| Prioridade | Camada            | Classe de Produção                                                       | Arquivo de Teste Proposto                                                                               | Cenários Principais                                                                                                                                                                                                                                                                           | Infraestrutura                                                                |
|------------|-------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| **P0**     | Domain            | `ContractTransitionPolicy`                                               | `src/test/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicyTest.kt`              | Matriz R5: imutabilidade de estados finais (`COMPLETED`, `DECLINED`, `EXPIRED`, `CANCELLED`); rejeição de regressão ordinal; aceitação de avanço e saltos monotônicos (ADR-010); `NoOp` para mesmo status.                                                                                    | JUnit Jupiter, `kotlin.test` (Sem Spring/Docker)                              |
| **P0**     | Domain            | `Contract`                                                               | `src/test/kotlin/br/com/locasign/contract/domain/models/ContractTest.kt`                                | Ordem de signatários R3 (Locatário antes da Imobiliária); aplicação de transição acumulando evento e histórico; registro de `IGNORED_TRANSITION` e descarte de eventos do provedor defasados (`modifiedAt < lastApplied`); anotação de expiração e lembrete; imutabilidade de contrato final. | JUnit Jupiter, `kotlin.test` (Sem Spring/Docker)                              |
| **P0**     | Domain            | `Cpf`, `Email`, `Money`                                                  | `src/test/kotlin/br/com/locasign/shared/domain/valueobjects/ValueObjectsTest.kt`                        | Validação módulo 11 de CPF; rejeição de dígitos repetidos; formatação e `masked()`; normalização em minúsculas de email; precisão decimal de `Money` (BRL) e rejeição de montante zero ou negativo (R2).                                                                                      | JUnit Jupiter, `kotlin.test` (Sem Spring/Docker)                              |
| **P0**     | Domain            | `Lease`, `LeaseTerm`                                                     | `src/test/kotlin/br/com/locasign/lease/domain/models/LeaseTest.kt`                                      | Validação de dados obrigatórios no cadastro R2 (endereço não vazio, prazo de 1 a 120 meses, data de início `>= today`); transição idempotente para `ACTIVE` (R8).                                                                                                                             | JUnit Jupiter, `kotlin.test` (Sem Spring/Docker)                              |
| **P0**     | App               | `RequestContract`                                                        | `src/test/kotlin/br/com/locasign/contract/app/usecases/RequestContractTest.kt`                          | Rejeição R1 se houver contrato em andamento (`ActiveContractExists`); rejeição se houver contrato concluído (ADR-013); numeração incremental de versão (R9); emissão de `ContractRequested` no outbox; execução na transação.                                                                 | Fakes em memória, `ImmediateTransactionRunner`                                |
| **P0**     | App               | `ReceiveProviderWebhook`                                                 | `src/test/kotlin/br/com/locasign/contract/app/usecases/ReceiveProviderWebhookTest.kt`                   | Rejeição com `InvalidSignature` para HMAC inválido sem gravar no inbox; gravação no inbox de payload bruto R7; deduplicação por `deliveryId`; geração de N mensagens no outbox com `deliveryId:index`; tolerância a JSON malformado sem falhar o recebimento.                                 | Fakes em memória, MockK                                                       |
| **P0**     | App               | `ProcessProviderWebhookItem`                                             | `src/test/kotlin/br/com/locasign/contract/app/usecases/ProcessProviderWebhookItemTest.kt`               | Idempotência estrita R6 via `markProcessed`: evento duplicado não aplica efeito; descarte de eventos desconhecidos com commit de marcação; despacho para `ApplyProviderUpdate` ou `ArchiveSignedDocument`.                                                                                    | Fakes em memória, MockK                                                       |
| **P0**     | Web Slice         | `PandaDocWebhookController`                                              | `src/test/kotlin/br/com/locasign/contract/interfaces/webhook/pandadoc/PandaDocWebhookControllerTest.kt` | HTTP 200 para webhooks aceitos ou duplicados; HTTP 401 para assinatura inválida; garantia de que HTTP 410 nunca é retornado; propagação de correlation ID.                                                                                                                                    | `@WebMvcTest`, `MockMvcTester`, `@MockkBean`                                  |
| **P0**     | Web Slice         | `OperatorAccessGuard`, `AdminController`                                 | `src/test/kotlin/br/com/locasign/shared/interfaces/web/OperatorAccessGuardTest.kt`                      | HTTP 403 se `ADMIN_TOKEN` não estiver configurado (fail-closed); HTTP 403 se `X-Admin-Token` for ausente ou inválido; HTTP 200 com token correto; comparação segura com hash em tempo constante (sem timing-leak).                                                                            | `@WebMvcTest`, `MockMvcTester`, `@MockkBean`                                  |
| **P0**     | Infra Persistence | `ContractRepositoryAdapter`                                              | `src/test/kotlin/br/com/locasign/contract/infra/persistence/adapters/ContractRepositoryAdapterIT.kt`    | Constraint `ux_contracts_one_active_per_lease` traduzida para `DomainException.ActiveContractExists` (409); persistência e reconstituição completa de agregado, signatários e histórico com Spring Data JDBC e Flyway real.                                                                   | `@DataJdbcTest`, Testcontainers PostgreSQL (`postgres:18.6`)                  |
| **P0**     | Infra Persistence | `JdbcMessagingAdapters`                                                  | `src/test/kotlin/br/com/locasign/shared/infra/persistence/JdbcMessagingAdaptersIT.kt`                   | Atomicidade do outbox; unicidade de `webhook_inbox.delivery_id`; chave primária composta de `processed_messages (consumer_group, event_id)` garantindo idempotência R6 no banco relacional.                                                                                                   | `@DataJdbcTest`, Testcontainers PostgreSQL                                    |
| **P0**     | Architecture      | Limites Hexagonais e Módulos                                             | `src/test/kotlin/br/com/locasign/architecture/HexagonalArchitectureTest.kt`                             | Regras da seção 4.6 do AGENTS.md: `domain` livre de Spring, Jackson, JDBC, Kafka; use cases desacoplados de anotações; `shared` não importa módulos; `lease` e `contract` respeitam grafo unidirecional.                                                                                      | ArchUnit JUnit 6 (Sem Docker)                                                 |
| **P1**     | App               | `CreateProviderDocument`, `SendContract`                                 | `src/test/kotlin/br/com/locasign/contract/app/usecases/OrchestrationUseCasesTest.kt`                    | Padrão de duas transações curtas do ADR-011 (sem conexão de banco aberta durante chamada externa); tratamento de `ProviderException.Rejected` cancelando o contrato (`failGeneration`).                                                                                                       | Fakes em memória, MockK                                                       |
| **P1**     | App               | `ApplyProviderUpdate`                                                    | `src/test/kotlin/br/com/locasign/contract/app/usecases/ApplyProviderUpdateTest.kt`                      | Aplicação em lote de sinais do provedor (`StatusChanged`, `RecipientCompleted`, `CreationFailed`, `DocumentDeleted`); sincronização de signatários; cancelamento em falha de geração.                                                                                                         | Fakes em memória                                                              |
| **P1**     | App               | `ExpireOverdueContracts`, `SendSignatureReminders`, `ReconcileContracts` | `src/test/kotlin/br/com/locasign/contract/app/usecases/ScheduledUseCasesTest.kt`                        | Transição de contratos vencidos para `EXPIRED` (R4); disparo único de lembrete no 3º dia; reconciliação de contratos sem atualização com anulação em melhor esforço.                                                                                                                          | Fakes em memória, `FixedBusinessClock`                                        |
| **P1**     | Web Slice         | `ContractController`, `LeaseController`                                  | `src/test/kotlin/br/com/locasign/interfaces/web/ControllersWebMvcTest.kt`                               | HTTP 201 Created com cabeçalho `Location` no cadastro de locação; HTTP 202 Accepted em solicitação e cancelamento de contratos; HTTP 400 com Bean Validation (`@field:`); HTTP 404, 409 e 422 mapeados pelo `ApiExceptionHandler`.                                                            | `@WebMvcTest`, `MockMvcTester`, `@MockkBean`                                  |
| **P1**     | Infra PandaDoc    | `PandaDocSignatureProviderAdapter`                                       | `src/test/kotlin/br/com/locasign/contract/infra/pandadoc/PandaDocSignatureProviderAdapterTest.kt`       | Retentativa com backoff exponencial sob HTTP 429 (limite de taxa); tradução de erros 404/409 para `ProviderException.NotReady` e 403 para `Forbidden`; controle de download habilitado/desabilitado.                                                                                          | WireMock Standalone na mesma JVM (Sem Docker)                                 |
| **P1**     | Infra Messaging   | `OutboxRelay`, `KafkaConfig`                                             | `src/test/kotlin/br/com/locasign/shared/infra/messaging/OutboxRelayIT.kt`                               | Publicação de registros pendentes do outbox no Kafka com `SKIP LOCKED`; preservação de ordem por chave; Dead Letter Topic (`.dlt`) após 3 retentativas falhas; replay da DLT via `DeadLetterReplayer`.                                                                                        | `@SpringBootTest`, Testcontainers PostgreSQL e Kafka                          |
| **P1**     | E2E               | Fluxo Crítico Completo                                                   | `src/test/kotlin/br/com/locasign/e2e/ContractLifecycleE2EIT.kt`                                         | Ciclo de vida ponta a ponta: Cadastro de locação -> Solicitação de contrato -> Outbox Relay -> Consumidor Orchestrator cria e envia doc (WireMock) -> Webhook de conclusão assinado recebido -> PostSignature ativa locação e arquiva documento -> Status final `COMPLETED`.                  | `@SpringBootTest(RANDOM_PORT)`, `RestTestClient`, PostgreSQL, Kafka, WireMock |
| **P2**     | Infra Storage     | `FileSystemSignedDocumentStorage`                                        | `src/test/kotlin/br/com/locasign/contract/infra/storage/FileSystemSignedDocumentStorageTest.kt`         | Gravação atômica de PDF assinado em disco; prevenção de path traversal; integridade do arquivo binário.                                                                                                                                                                                       | JUnit Jupiter `@TempDir` (Sem Spring/Docker)                                  |
| **P2**     | Infra Scheduling  | `HousekeepingJob`                                                        | `src/test/kotlin/br/com/locasign/shared/infra/scheduling/HousekeepingJobIT.kt`                          | Limpeza de registros operacionais com mais de 30 dias de retenção (`outbox_events`, `webhook_inbox`, `processed_messages`).                                                                                                                                                                   | `@DataJdbcTest`, Testcontainers PostgreSQL                                    |
| **P2**     | Domain            | `NotificationPolicy`, `RecordNotifications`                              | `src/test/kotlin/br/com/locasign/notification/NotificationFlowTest.kt`                                  | Determinação de audiência por evento; mascaramento de dados nos registros de notificação simulada.                                                                                                                                                                                            | Fakes em memória (Sem Spring/Docker)                                          |

---

## 5. Rascunho Formal do ADR Proposto

```markdown
# ADR-014: Estratégia de testes automatizados e adoção de bibliotecas de teste

- **Status:** proposta
- **Relação com o guia:** complementa a seção 15 do guia técnico e formaliza as decisões da seção 6 do AGENTS.md

## Contexto

O microsserviço LocaSign possui 80 classes Kotlin de produção com alta densidade de regras de negócio críticas (R1 a
R10), máquina de estados com transições ordinais estritas (ADR-010), outbox transacional, recebimento de webhooks com
verificação criptográfica HMAC e mensageria assíncrona Kafka.

Atualmente, não existe nenhum teste automatizado implementado no repositório (`src/test` inexistente). O Spring Boot
4.1.1 gerencia JUnit 6.0.3, Testcontainers 2.0.5 e Mockito 5.23.0, mas bibliotecas essenciais para testes idiomáticos em
Kotlin, simulação de HTTP externo e verificação de regras arquiteturais não fazem parte do BOM do Boot.

## Decisão

Adotar uma suíte de testes com separação estrita de camadas e incluir as seguintes dependências no Version Catalog
(`gradle/libs.versions.toml`) e `build.gradle.kts`:

1. **MockK (`io.mockk:mockk-jvm:1.14.11`) e SpringMockK (`com.ninja-squad:springmockk:5.0.1`):** biblioteca padrão de
   mocks para Kotlin e integração `@MockkBean` em slices `@WebMvcTest`.
2. **WireMock Standalone (`org.wiremock:wiremock-standalone:3.13.2`):** servidor HTTP embutido na mesma JVM em porta
   dinâmica para simular as respostas da PandaDoc, sem poluir o classpath com dependências de terceiros.
3. **ArchUnit JUnit 6 (`com.tngtech.archunit:archunit-junit6:1.5.1`):** validação automatizada das regras hexagonais e
   fronteiras de pacotes diretamente no JUnit 6.
4. **Awaitility Kotlin (`org.awaitility:awaitility-kotlin:4.3.0`):** DSL para asserções assíncronas em testes de outbox
   e Kafka, alinhada à versão gerenciada pelo Boot.
5. **JaCoCo (`toolVersion = "0.8.14"`):** plugin nativo configurado explicitamente para compatibilidade com bytecode
   Java 25.
6. **Convenção de Execução:** Sufixo `*Test` para testes sem Docker (rápidos) e `*IT` para testes com Testcontainers
   (`postgres:18.6` e `apache/kafka-native:4.3.1`). Manter `./gradlew test` como comando principal que executa todos os
   testes.

## Alternativas Consideradas

- **Manter Mockito no lugar de MockK:** descartado por requerer mocks mais verbosos em Kotlin, suporte imperfeito a
  tipos selados/inline e conflito com a proposta de uso idiomático de Kotlin no projeto.
- **Utilizar MockRestServiceServer em vez de WireMock:** descartado porque o MockRestServiceServer acopla o teste ao
  `RestClient.Builder` interno e não exercita completamente o pipeline HTTP real do adapter.
- **Konsist em vez de ArchUnit:** descartado por não possuir maturidade e integração consolidada com o ecossistema JUnit
  6 no Spring Boot 4.

## Consequências

- **Positivas:** feedback ultrarrápido no desenvolvimento (testes de domínio e use cases rodam em milissegundos sem
  Docker); isolamento total do provedor PandaDoc; proteção contra regressões arquiteturais na CI.
- **Negativas:** necessidade de manter 5 dependências adicionais declaradas no Version Catalog fora do BOM do Spring
  Boot.
```

---

## 6. Primeiro Incremento Recomendado — P0 (Zero Docker)

Para o primeiro lote de implementação, recomenda-se iniciar pelo núcleo de maior retorno de negócio, sem envolver
containers:

1. **Configuração Mínima:**
    - Adicionar `io.mockk:mockk-jvm:1.14.11` no Version Catalog e `build.gradle.kts`.
    - Criar `src/test/kotlin/br/com/locasign/shared/app/fakes/SharedFakes.kt` com `ImmediateTransactionRunner` e
      `FixedBusinessClock`.
2. **Testes de Domínio:**
    - `ContractTransitionPolicyTest`: matriz completa de transições de status (R5 e ADR-010).
    - `ContractTest`: criação em `DRAFT`, ordem de signatários (R3), acumulação de eventos e imutabilidade de estados
      finais.
    - `ValueObjectsTest`: validação de CPF (módulo 11 e mascaramento R10) e Money (valores positivos em BRL R2).
3. **Teste de Caso de Uso:**
    - `RequestContractTest`: unicidade de contrato ativo por locação (R1), controle de nova versão (R9) e gravação no
      outbox.

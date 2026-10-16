# Arquitetura Técnica Detalhada (Monólito Modular Hexagonal)

> **Origem:** Este documento detalha a seção 4 original do `AGENTS.md`. Em caso de conflito, as regras do núcleo do [
`AGENTS.md`](../../AGENTS.md) prevalecem.

Pacote raiz `br.com.locasign`, com os módulos de negócio `lease`, `contract` e `notification` e o transversal `shared`.
Todos têm as camadas `domain`, `app`, `interfaces` e `infra`. O projeto é um único módulo Gradle: a fronteira entre
camadas é o pacote, e `internal` não isola camadas.

## 1. Camadas e direção de dependência

`interfaces → app → domain ← infra` (`infra` também depende de `app`; guia, §3.1).

| Camada       | Pode importar                                                                                                                                                                                                                                                                                                    | Nunca importa                                                                               |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `domain`     | `kotlin.*` (inclusive `kotlin.uuid.Uuid`), `java.time`, `java.math`, `shared.domain` e, em `contract`, `lease.domain.valueobjects.LeaseId`                                                                                                                                                                       | Spring, Jackson, JDBC, Kafka, SLF4J, nomes da PandaDoc, `app`, `interfaces`, `infra`        |
| `app`        | `domain` (em `contract`, também `LeaseId`), `shared.domain`, `shared.app` (portas transversais, `Topics`, `ConsumerGroups`), stdlib/JDK e a fachada de log `org.slf4j`                                                                                                                                           | Spring, Jackson, JDBC, Kafka, `jakarta.*`, `interfaces`, `infra`                            |
| `interfaces` | `app`, `domain` (em `contract`, também `LeaseId`), `shared.domain`, `shared.app`, `shared.interfaces`, Spring Web, Spring Kafka, `jakarta.validation`, `jakarta.servlet`, anotações do springdoc (`io.swagger.v3.oas.annotations`), Jackson 3 (`tools.jackson.*` e `com.fasterxml.jackson.annotation.*`) e SLF4J | `infra`, inclusive `LocaSignProperties` (use `@param:Value`, como em `OperatorAccessGuard`) |
| `infra`      | `app` (portas; use cases só em `config/`, para registrar os beans, em `scheduling/`, para os jobs delegarem, e nas pontes entre módulos), `domain`, `shared`, Spring, Jackson 3, JDBC, Kafka e SLF4J                                                                                                             | `interfaces`                                                                                |

- **`domain`:** Kotlin puro. Não declara portas. Erro de negócio é subtipo da hierarquia selada
  `shared.domain.DomainException` (não existe `domain/exceptions` por módulo).
- **`app`:** use cases e queries são classes Kotlin sem anotação, com dependências no construtor, registradas uma a uma
  como `@Bean` em `<módulo>/infra/config/<Módulo>BeansConfig.kt`. Não existe `ports/in`: controllers, consumers e jobs
  injetam a classe concreta e chamam `execute(...)`.
    - O `data class <Nome>Command` fica no arquivo do use case. A exceção é o `ContractEventCommand`, compartilhado
      pelos use cases que reagem a um `ContractEvent`, que fica em `ContractSupport.kt`.
    - Jobs chamam `execute()` sem argumento, e `ReconcileContracts` expõe `executeStale()` e `executeFor(id)`.
    - As portas de saída ficam em `app/ports/out/<categoria>/`, onde a categoria é `repository`, `integration`,
      `messaging` ou o nome do módulo fornecedor. As transversais ficam em `shared/app/ports`; a exceção é o
      `DeadLetterReplayPort`, em `shared/app/MessagingErrors.kt`.
    - Erros de integração de portas que não são de domínio ficam em `app/ports/out/integration/ProviderErrors.kt`
      (incluindo `MalformedWebhookPayload` e `ProviderException`).
    - Transação só via `TransactionRunner` (ADR-011).
- **`interfaces` (driving):** `web/` (controller que implementa a interface OpenAPI de `web/openapi/`, mais
  `dto/request`, `dto/response` e `mappers/`), `webhook/pandadoc/` (receptor HTTP) e `messaging/` (`@KafkaListener`). Os
  componentes transversais ficam em `shared/interfaces`: `ApiExceptionHandler`, `CorrelationIdFilter`,
  `OperatorAccessGuard`, `AdminController` e `EnvelopeReader`.
- **`infra` (driven + composição):** implementa as portas de `app` e reúne `persistence/` (Spring Data JDBC e
  `JdbcClient`), `messaging/`, `pandadoc/`, `storage/`, `config/` (beans), `scheduling/` e as pontes entre módulos. Os
  jobs `@Scheduled` são adapters driving, mas ficam em `infra/scheduling` (guia, §3.4). A exceção é o `OutboxRelay`,
  agendado em `shared/infra/messaging`.

## 2. Árvore de pacotes real (`src/main/kotlin/br/com/locasign/`)

Se um subpacote não aparece aqui, ele não existe. Não o crie só por simetria.

```text
shared/                              # transversal; não importa nenhum módulo
├── domain/                          # DomainEvent, DomainException (selada, única do sistema)
│   └── valueobjects/                # Cpf, Email, Money (ADR-013)
├── app/                             # Topics, ConsumerGroups, UnreadableMessageException, DeadLetterReplayPort
│   └── ports/                       # TransactionRunner, BusinessClock, OutboxPort, ProcessedMessagesPort,
│                                    #   WebhookInboxPort, MetricsPort (+Metrics), CorrelationContext (+ContextKeys)
├── interfaces/{web,messaging}/      # ApiExceptionHandler, CorrelationIdFilter, OperatorAccessGuard, AdminController | EventEnvelope, EnvelopeReader
└── infra/
    ├── config/                      # SharedBeansConfig (+SpringTransactionRunner, SystemBusinessClock), KafkaConfig (tópicos + DLT),
    │                                #   LocaSignProperties, OpenApiConfig
    ├── persistence/                 # JdbcMessagingAdapters (outbox, inbox, processed_messages), JdbcSupport
    ├── messaging/                   # OutboxRelay, DeadLetterReplayer (os produtores Kafka da aplicação)
    ├── observability/               # métricas, MdcCorrelationContext, KafkaHealthIndicator
    └── scheduling/                  # HousekeepingJob
lease/
├── domain/{models,valueobjects}                          # Lease, Tenant, AgencySigner | LeaseId, LeaseTerm
├── app/{usecases,queries,ports/out/repository}           # RegisterLease, ActivateLease | GetLease | LeaseRepositoryPort, LeaseQueryPort
├── interfaces/web/{dto/request,dto/response,mappers,openapi}
└── infra/{config,persistence/{adapters,entities,mappers,repositories}}
contract/
├── domain/{models,events,services,valueobjects}          # Contract | ContractEvent | ContractTransitionPolicy | ContractId...
├── app/
│   ├── usecases/                    # um use case por arquivo; ContractSupport.kt (ContractPersister, ContractSettings, ContractEventCommand)
│   ├── queries/                     # GetContract, GetContractHistory + views
│   └── ports/out/{repository,integration,messaging,lease} # integration: IntegrationPorts.kt, ProviderErrors.kt (MalformedWebhookPayload), ProviderModels.kt
├── interfaces/
│   ├── web/{dto/request,dto/response,mappers,openapi}
│   ├── webhook/pandadoc/            # PandaDocWebhookController
│   └── messaging/                   # ContractConsumers.kt (OrchestratorConsumer, PostSignatureConsumer, DocumentAnnulmentConsumer, ProviderEventsConsumer)
└── infra/
    ├── config/                      # ContractBeansConfig, PandaDocConfig
    ├── persistence/{adapters,entities,mappers,repositories}   # adapters/LeaseBridgeAdapters.kt = ponte para lease
    ├── pandadoc/                    # PandaDocClient (@HttpExchange), SlidingWindowRateLimiter
    │   └── {adapters,dto/request,dto/response,mappers,exceptions}
    ├── messaging/                   # OutboxContractEventPublisher, ContractEventPayloads
    ├── scheduling/                  # ReconciliationJob, ExpirationJob, ReminderJob
    └── storage/                     # FileSystemSignedDocumentStorage
notification/                        # enxuto, com pacotes planos
├── domain/  app/{usecases,ports/out}  interfaces/messaging/ (NotificationConsumer.kt)  infra/{config,persistence}
```

Migrations ficam em `src/main/resources/db/migration/`.

## 3. Comunicação entre módulos

Grafo permitido, sem ciclos: `contract → lease`, `notification ⇢ contract` (por Kafka) e todos `→ shared`. `shared` não
importa módulos, `lease` não importa `contract` nem `notification`, `notification` não importa `lease` nem `contract`, e
`contract` não importa `notification`.

- **Reações assíncronas** vão por evento no outbox e no Kafka (padrão preferido; guia, §1). Exemplo: `notification`
  consome `locasign.contract.events.v1` com grupo próprio.
- **Acesso síncrono** a outro módulo passa por uma porta do consumidor em `<consumidor>/app/ports/out/<fornecedor>/`,
  com tipos próprios (ex.: `LeaseSnapshot`), implementada por uma ponte em `<consumidor>/infra/`. Só a ponte importa
  `<fornecedor>.app.*`. Referência: `LeaseLookupPort` e `LeaseActivationPort`, implementadas em
  `contract/infra/persistence/adapters/LeaseBridgeAdapters.kt`, que roda na transação do chamador.
- **Value object de outro módulo:** só identificadores. Hoje apenas `LeaseId` é usado em `contract`.
- Nunca escreva em tabela de outro módulo nem reconstitua o agregado de outro módulo com SQL próprio.
- Toda dependência nova entre módulos (outro VO, outra ponte, outro SELECT cruzado) exige ADR.

## 4. Fronteira com a PandaDoc

- A PandaDoc fica atrás de duas portas em `contract/app/ports/out/integration/IntegrationPorts.kt`:
  `SignatureProviderPort` (criar, consultar, enviar, anular e baixar documentos) e `ProviderWebhookGateway` (validar o
  HMAC sobre os bytes brutos, dividir o array e traduzir cada item para `ProviderSignal`).
- As duas são implementadas só em `contract/infra/pandadoc/adapters/` e registradas como `@Bean` em `PandaDocConfig`.
  Endpoints, DTOs e nomes de status ou eventos da PandaDoc ficam em `contract/infra/pandadoc/`. O `RestClient` (URL
  base, timeouts e header `Authorization: API-Key`) é montado em `contract/infra/config/PandaDocConfig.kt`, e as
  propriedades, inclusive os nomes das roles, ficam em `LocaSignProperties.PandaDocProperties`. Exceção existente:
  `ApplyProviderUpdate` cita `document.error` em textos de histórico.
- O receptor `contract/interfaces/webhook/pandadoc/PandaDocWebhookController` conhece só o protocolo de entrega (rota
  `/webhooks/pandadoc`, query `signature`, header `X-PandaDoc-Webhook-Event-Id`). Ele repassa os bytes ao use case
  `ReceiveProviderWebhook`, que valida o HMAC pelo gateway. Não valide HMAC nem faça parse no controller.
- **Anulação do documento (ADR-016):** é assíncrona. `CancelContract` e `ExpireOverdueContracts` só gravam
  `ContractCancelled` e `ContractExpired` no outbox, na mesma transação da transição, e não recebem
  `SignatureProviderPort`. O `DocumentAnnulmentConsumer` (grupo `locasign-document-annulment`, em
  `locasign.contract.events.v1`) reage a esses eventos e delega a `AnnulProviderDocument`, que chama
  `SignatureProviderPort.cancelDocument` entre duas transações curtas. Só anula contrato `CANCELLED` ou `EXPIRED` com
  documento e `sentAt`. HTTP 404/409 e falhas definitivas contam como resolvidos; `RateLimited` e `Unavailable` sobem
  para o `DefaultErrorHandler` (retentativa e DLT). Outro ponto que precise anular um documento segue esse desenho, e
  não a requisição.
- Antes de implementar uma chamada nova, consulte `https://developers.pandadoc.com/llms.txt` (acrescente `.md` às URLs
  das páginas) e o ADR-012.

## 5. Checklist: novo caso de uso de ponta a ponta

Referência: `RequestContract`. O `POST /api/v1/leases/{leaseId}/contracts` cria o contrato em `DRAFT` e grava
`ContractRequested` no outbox; o `OrchestratorConsumer` reage chamando `CreateProviderDocument`.

1. **Domínio:** a regra fica no agregado (método ou factory no `companion`), que valida invariantes e acumula evento e
   histórico (`Contract.request`). Erro novo de negócio vira subtipo de `DomainException` e ganha um ramo em
   `ApiExceptionHandler.handleDomain`; o compilador obriga.
2. **Porta de saída** (só se nenhuma existente servir): interface em `app/ports/out/<categoria>/`, com tipos de domínio
   ou modelos neutros, nunca DTO de infra.
3. **Use case:** `app/usecases/<Verbo><Substantivo>.kt`, com o `data class <Nome>Command` e uma classe que recebe as
   portas, `BusinessClock` e `TransactionRunner` e expõe `execute(command)`. O corpo roda em `transactions.run { }`.
   Contrato é salvo com `ContractPersister.save`, que grava agregado, histórico e outbox na mesma transação.
4. **Bean:** `@Bean` explícito em `<Módulo>BeansConfig.kt`.
5. **Adapter driven** (se houver porta nova): `@Repository` em `infra/persistence/adapters/`, usando Spring Data JDBC
   para o agregado e `JdbcClient` para consultas, com conversões em `infra/persistence/mappers/`. Violação de constraint
   de regra de negócio é traduzida para `DomainException` (ex.: `ux_contracts_one_active_per_lease` vira
   `ActiveContractExists`).
6. **Adapter driving:**
    - HTTP: método no controller que implementa `openapi/<Recurso>Api`. O DTO de entrada usa `@field:` e é convertido
      por extension function em `mappers/`. Operação assíncrona responde 202 com `Location`. Não capture
      `DomainException`: o `ApiExceptionHandler` faz o mapeamento.
    - Kafka: listener em `interfaces/messaging/` (detalhes em [`docs/agents/regras-de-codigo.md`](regras-de-codigo.md));
      a idempotência fica no use case.
    - Job: classe em `infra/scheduling/`.
7. **Migration**, se o schema mudar: nova versão em `db/migration/`.
8. **Testes:** consulte [`docs/agents/testes.md`](testes.md).
9. **Fronteiras:** teste automatizado `HexagonalArchitectureTest` e checagem da seção 6.
10. **Docs:** atualize as tabelas de use cases (guia §4.5) e de endpoints (guia §9.2 e `README.md`) quando mudarem.
    Desvio relevante do guia exige ADR.

## 6. Checagem de fronteiras arquiteturais

O projeto conta com o teste automatizado `HexagonalArchitectureTest`
(`src/test/kotlin/br/com/locasign/architecture/HexagonalArchitectureTest.kt`,
ADR-014), que executa 13 regras ArchUnit durante `./gradlew test` ou `./gradlew build`. Ele verifica isolamento de
domínio,
camadas e grafo de dependência entre módulos diretamente no bytecode.

**Limite técnico do ArchUnit:** o ArchUnit inspeciona dependências de classes no bytecode compilado. No Kotlin,
constantes primitivas (`const val`) sofrem inlining no chamador e value classes inline podem sofrer name mangling e
apagamento de tipo em certas assinaturas, não gerando referências rastreáveis no bytecode. Por isso, a busca textual
complementar abaixo deve ser executada antes de concluir tarefas que criem, movam ou alterem pacotes.

No PowerShell ou Git Bash, execute a partir da raiz do projeto:

```powershell
# Verificação no PowerShell (cada comando deve retornar vazio; código de saída 1 do git grep):
git grep -n -E "^import (org\.springframework|tools\.jackson|com\.fasterxml|org\.slf4j|org\.apache\.kafka|java\.sql|jakarta)" -- "src/main/kotlin/**/domain/**"
git grep -n -E "^import br\.com\.locasign\.\w+\.(app|interfaces|infra)\." -- "src/main/kotlin/**/domain/**"
git grep -n -E "^import (org\.springframework|tools\.jackson|com\.fasterxml|org\.apache\.kafka|java\.sql|jakarta)" -- "src/main/kotlin/**/app/**"
git grep -n -E "^import br\.com\.locasign\.\w+\.(infra|interfaces)\." -- "src/main/kotlin/**/app/**"
git grep -n -E "^import br\.com\.locasign\.\w+\.infra\." -- "src/main/kotlin/**/interfaces/**"
git grep -n -E "^import br\.com\.locasign\.\w+\.interfaces\." -- "src/main/kotlin/**/infra/**"
git grep -n -E "^import br\.com\.locasign\.(lease|contract|notification)\." -- "src/main/kotlin/br/com/locasign/shared/**"
git grep -n -E "^import br\.com\.locasign\.(contract|notification)\." -- "src/main/kotlin/br/com/locasign/lease/**"
git grep -n -E "^import br\.com\.locasign\.(lease|contract)\." -- "src/main/kotlin/br/com/locasign/notification/**"
git grep -n -E "^import br\.com\.locasign\.notification\." -- "src/main/kotlin/br/com/locasign/contract/**"
git grep -n -E "^import br\.com\.locasign\.lease\.domain\.(models|valueobjects\.LeaseTerm)" -- "src/main/kotlin/br/com/locasign/contract/**"

# A única ocorrência permitida para import de lease em contract é a ponte LeaseBridgeAdapters.kt:
git grep -n -E "^import br\.com\.locasign\.lease\.(app|infra|interfaces)\." -- "src/main/kotlin/br/com/locasign/contract/**"
```

## 7. Exceções existentes: não use como precedente

- `POST /api/v1/contracts/{id}/reconcile` (restrito a operador) chama `SignatureProviderPort.fetchState` dentro da
  requisição (`ReconcileContracts`). O guia prevê esse endpoint (§4.5 e §9.2), mas o princípio 2 do §1 proíbe chamada
  externa na requisição. Não remova o endpoint: a contradição do guia está pendente de ADR.
- SELECT em tabela de outro módulo: `LeaseQueryAdapter` lê `contracts`, e `JdbcNotificationRecipientsAdapter` lê
  `contract_signers`. Não há ADR.
- `AdminController` chama a porta `DeadLetterReplayPort` direto, sem use case (implementa a interface `AdminApi` em
  `openapi/`).
- `HousekeepingJob` (`shared/infra/scheduling`) apaga linhas de `outbox_events`, `webhook_inbox` e `processed_messages`
  direto com `JdbcClient`, sem use case.
- Mapeamentos fora de `mappers/`: `ContractEvent.toPayload()` (`infra/messaging/ContractEventPayloads.kt`),
  `RestClientException.toProviderException` (`infra/pandadoc/exceptions/`) e a conversão `Lease → LeaseSnapshot` dentro
  de `LeaseBridgeAdapters`.

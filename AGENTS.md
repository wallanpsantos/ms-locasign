# LocaSign: Diretrizes Universais para Agentes de IA

Serviço backend que automatiza o ciclo de vida de contratos de locação residencial: gera o contrato, envia para
assinatura eletrônica na PandaDoc, acompanha o ciclo por webhooks e dispara as ações pós-assinatura.

> **Propósito do repositório:** projeto de estudo de engenharia de software pragmática com Kotlin, Spring Boot,
> PostgreSQL, Apache Kafka e Docker.  
> **Stack fixada (fontes: `gradle/libs.versions.toml`, `build.gradle.kts`, `gradle/wrapper/`, `compose.yml`):** Kotlin
> 2.4.20, Java 25 (toolchain), Spring Boot 4.1.1 (Spring Framework 7, Jackson 3), Gradle 9.7.1, PostgreSQL 18.6 e Apache
> Kafka 4.3.1 em modo KRaft (broker local; o cliente `kafka-clients` vem do BOM do Boot, na linha 4.2).  
> **Público deste arquivo:** agentes de IA (Claude Code, Codex, Cursor, GitHub Copilot, Antigravity, Windsurf, Aider
> etc.) e pessoas desenvolvedoras atuando no repositório.

---

## 1. Leitura obrigatória e precedência

Antes de analisar, planejar ou implementar, leia na ordem:

1. **`docs/plano-de-negocio.md`**: o quê e o porquê. As regras **R1 a R10** (seção 8) prevalecem sobre qualquer outra
   decisão.
2. **`docs/arquitetura-tecnica.md`**: o como. Seções mais usadas: arquitetura (§3), glossário (§4.1), máquina de estados
   (§4.3), PandaDoc (§5), webhooks (§6), Kafka (§7), PostgreSQL (§8), API (§9) e testes (§15).
3. **ADRs**: os ADRs 001 a 010 existem só como tabela na seção 18 do guia. Do ADR-011 em diante, cada um é um arquivo em
   `docs/adr/` (`0011-...md`):
    - **ADR-011:** porta `TransactionRunner` no lugar do decorator transacional (ajusta a seção 3.6 do guia).
    - **ADR-012:** comportamentos confirmados da PandaDoc. Resolve a maioria dos itens "(confirmar)" do guia; os aliases
      de e-mail no sandbox continuam não verificados, e os itens de springdoc (§2) e da API de resiliência do Spring 7
      (§5.7) não estão nele. Item "(confirmar)" sem resposta no ADR-012 deve ser verificado na documentação oficial
      antes de implementar.
    - **ADR-013:** ajustes de implementação (localização de value objects, colunas extras, token de operador, health).

Em caso de conflito: R1–R10 > ADRs > guia (guia, §0). Este arquivo detalha a seção 17 do guia ("conteúdo do `AGENTS.md`
") e só diverge dela nos pontos listados na seção 1.1. Toda decisão técnica relevante que mude o guia exige ADR. Não
altere o código só para casar com um trecho desatualizado do guia.

### 1.1 Divergências conhecidas entre o guia e o repositório

Siga o repositório e não crie arquivos só para casar com o guia. Só a divergência do `TransactionRunner` tem ADR
(ADR-011); as demais estão pendentes de registro.

| Guia                                                                                                                                                                                                                                                                     | Repositório                                                                                                         |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `docker-compose.yml`, `application.yml`, `application-local.yml` (§3.4)                                                                                                                                                                                                  | `compose.yml`, `application.yaml`, `application-local.yaml`                                                         |
| `.env.example` (§3.4 e §11; também citado no `compose.yml`)                                                                                                                                                                                                              | não existe; as variáveis estão na tabela "Configuração" do `README.md`                                              |
| Decorator transacional e use cases instanciados em `infra/config/beans` (§3.6)                                                                                                                                                                                           | `TransactionRunner` (ADR-011); use cases registrados em `<módulo>/infra/config/<Módulo>BeansConfig.kt`              |
| `app/ports/in`, `domain/exceptions`, `lease/domain/events`, `interfaces/web/{controllers,advice}`, `interfaces/messaging/{dto,mappers}`, `infra/messaging/{dto,mappers}`, `infra/pandadoc/clients`, `infra/config/{beans,kafka,database,pandadoc,openapi}` (§3.3 e §3.4) | não existem (árvore real na seção 4.2)                                                                              |
| `app` depende só de `domain` (§3.1 e §17)                                                                                                                                                                                                                                | `app` também usa `shared.app` e a fachada de log `org.slf4j`                                                        |
| Receptor do webhook valida o HMAC e grava no inbox (§3.4)                                                                                                                                                                                                                | o controller só repassa os bytes; `ReceiveProviderWebhook` valida pelo `ProviderWebhookGateway` e grava (seção 4.4) |
| Limitador de taxa "token bucket" (§5.7)                                                                                                                                                                                                                                  | `SlidingWindowRateLimiter`: janela deslizante de 1 minuto por operação                                              |
| Regra de dependência verificada por ArchUnit/Konsist (§3.1 e §15)                                                                                                                                                                                                        | não há teste de arquitetura; use a checagem da seção 4.6                                                            |
| MockK e WireMock "gerenciados pelo Boot" (§2 e §15)                                                                                                                                                                                                                      | fora do BOM do Boot 4.1 e fora do build                                                                             |
| `postgres:18` (§2, §10.1 e §15)                                                                                                                                                                                                                                          | `postgres:18.6`                                                                                                     |
| Commits pequenos feitos pelo agente (§17)                                                                                                                                                                                                                                | o agente não commita (seção 3)                                                                                      |

---

## 2. Comandos

Pré-requisitos: JDK 25 instalado (o build não provisiona toolchain) e Docker em execução. Agentes acrescentam
`--console=plain` a todo comando Gradle (ex.: `./gradlew build --console=plain`). No PowerShell, use `.\gradlew.bat` e
aspas duplas (`--args="..."`, `--tests "..."`); no Git Bash, use `./gradlew`.

```bash
# Build e testes
./gradlew build                  # gate de pronto e da CI: compila, roda `test` e gera build/libs/app.jar
./gradlew compileKotlin          # checagem rápida do código de produção (compila sem warnings)
./gradlew test                   # todos os testes; os *IT sobem PostgreSQL e Kafka via Testcontainers (exige Docker)
./gradlew test --tests "*Test"   # só os testes que não usam Docker (convenção da seção 6.4)
./gradlew test --tests "br.com.locasign.contract.domain.services.ContractTransitionPolicyTest" --rerun
./gradlew dependencies --configuration testRuntimeClasspath
./gradlew dependencyInsight --dependency kafka-clients --configuration runtimeClasspath

# Ambiente local
docker compose up -d                                        # PostgreSQL 18.6 + Kafka 4.3.1 (KRaft), portas só em 127.0.0.1
./gradlew bootRun --args='--spring.profiles.active=local'   # app no host (8080): /actuator/health e /swagger-ui.html
docker compose --profile tools up -d                        # + Kafka UI em http://localhost:8081
docker compose --profile app up -d --build                  # + app em container (não rode bootRun junto; logs em JSON ECS)
docker compose --profile tunnel up -d                       # + túnel HTTPS (URL em `docker compose logs tunnel`); com --profile app, defina TUNNEL_TARGET=app:8080
docker compose down                                         # passe os mesmos --profile usados no up
```

- **Sem `src/test`, `:test` termina `NO-SOURCE`**, e até um `--tests` sem correspondência passa. Confira na saída que os
  testes realmente rodaram.
- Relatório de testes: `build/reports/tests/test/index.html` (XML em `build/test-results/test/`).
- `docker compose down -v` apaga banco, tópicos e PDFs arquivados. Rode só a pedido explícito.
- A CI não constrói a imagem Docker. Ao mexer no `Dockerfile` ou no build, valide com
  `docker compose --profile app build`.
- Webhook assinado sem a PandaDoc (Git Bash, com `openssl` e `curl`):
  `PANDADOC_WEBHOOK_SHARED_KEY=segredo bash scripts/send-signed-webhook.sh http/samples/document-state-changed.json`. A
  app precisa estar rodando com a mesma chave. Repita com `DELIVERY_ID=<fixo>` para testar a deduplicação. O roteiro de
  demonstração está em `http/locasign.http`.

---

## 3. Modo de trabalho do agente

- **Objetivo didático:** antes de gerar arquivos grandes ou refatorar, descreva o plano em poucas linhas. Ao concluir
  cada etapa, resuma os conceitos de Kotlin, Spring, Kafka ou PostgreSQL empregados.
- **Definição de pronto:**
    1. em funcionalidade nova ou correção de bug, o teste novo falhava antes da mudança e passa depois (seção 6); em
       refatoração, os testes existentes continuam verdes;
    2. `./gradlew build` está verde com Docker ligado e, se há testes, a saída mostra `:test` executando, sem
       `NO-SOURCE`;
    3. `./gradlew compileKotlin` não emite warnings novos;
    4. a checagem de fronteiras da seção 4.6 volta vazia.

  Mudança só em documentação dispensa os itens 1 e 2.
- **Git:** não execute `git commit` nem `git push` por conta própria; esta regra prevalece sobre a seção 17 do guia.
  Deixe as mudanças no workspace para revisão humana (`git status`, `git diff`). Se o usuário pedir commit, faça um por
  capacidade, com mensagem em pt-BR no padrão do histórico: `<gitmoji> <tipo>[(<escopo>)]: <descrição no presente>`, com
  escopo opcional (ex.: `🐛 fix(ci): corrige caminho do jar`, `📚 docs: atualiza guias do projeto`). Nunca inclua
  `Co-Authored-By` nem atribuição a IA em commits ou PRs. Tags e releases são ação humana.
- **Dependências e versões:** regras na seção 7.1. Mudança feita pelo agente em dependência, JDK, wrapper ou imagem
  exige ADR.
- **ADRs:** toda decisão técnica relevante que desvie do guia ou o expanda (guia, §0) vira
  `docs/adr/NNNN-titulo-em-kebab-case.md`. O número tem quatro dígitos e é o maior existente em `docs/adr/` mais um.
  Siga o modelo do ADR-011: título `# ADR-0NN: ...`, linhas `- **Status:**` e `- **Relação com o guia:**`, e seções
  Contexto, Decisão e Consequências. ADR criado por agente nasce com status `proposta`; aceitá-lo é decisão humana. O
  ADR-013 (tabela de ajustes pequenos) já está `aceita`: só acrescente linha a ele com confirmação do usuário.
- **Planos em `docs/tasks/`:** `plan.md` guarda o plano e as decisões da iniciativa, e `todo.md` o checklist com
  critérios de aceite, onde o progresso é marcado (`[x]`). Ao executar uma tarefa de lá, respeite a ordem e as
  dependências e rode a verificação indicada. Os "ADR-KDOC-00x" do `plan.md` são decisões locais do plano, não ADRs
  formais. Hoje os dois descrevem a iniciativa de KDoc, já concluída (todo o `todo.md` em `[x]`); os `[ ]` do `plan.md`
  não indicam trabalho pendente. Pergunte antes de sobrescrever esses arquivos com outra iniciativa.
- **Variáveis de ambiente:** não crie nem edite arquivos `.env*`. A referência é a tabela "Configuração" do `README.md`,
  junto com os placeholders `${VAR:padrão}` do `application.yaml` (seção 5.3, "Propriedades").

---

## 4. Arquitetura (monólito modular hexagonal)

Pacote raiz `br.com.locasign`, com os módulos de negócio `lease`, `contract` e `notification` e o transversal `shared`.
Todos têm as camadas `domain`, `app`, `interfaces` e `infra`. O projeto é um único módulo Gradle: a fronteira entre
camadas é o pacote, e `internal` não isola camadas.

### 4.1 Camadas e direção de dependência

`interfaces → app → domain ← infra` (`infra` também depende de `app`; guia, §3.1).

| Camada       | Pode importar                                                                                                                                                                                                                                                                                                    | Nunca importa                                                                               |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `domain`     | `kotlin.*` (inclusive `kotlin.uuid.Uuid`), `java.time`, `java.math`, `shared.domain` e, em `contract`, `lease.domain.valueobjects.LeaseId`                                                                                                                                                                       | Spring, Jackson, JDBC, Kafka, SLF4J, nomes da PandaDoc, `app`, `interfaces`, `infra`        |
| `app`        | `domain` (em `contract`, também `LeaseId`), `shared.domain`, `shared.app` (portas transversais, `Topics`, `ConsumerGroups`), stdlib/JDK e a fachada de log `org.slf4j` (divergência do guia, seção 1.1)                                                                                                          | Spring, Jackson, JDBC, Kafka, `jakarta.*`, `interfaces`, `infra`                            |
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
    - Transação só via `TransactionRunner` (ADR-011).
- **`interfaces` (driving):** `web/` (controller que implementa a interface OpenAPI de `web/openapi/`, mais
  `dto/request`, `dto/response` e `mappers/`), `webhook/pandadoc/` (receptor HTTP) e `messaging/` (`@KafkaListener`). Os
  componentes transversais ficam em `shared/interfaces`: `ApiExceptionHandler`, `CorrelationIdFilter`,
  `OperatorAccessGuard`, `AdminController` e `EnvelopeReader`.
- **`infra` (driven + composição):** implementa as portas de `app` e reúne `persistence/` (Spring Data JDBC e
  `JdbcClient`), `messaging/`, `pandadoc/`, `storage/`, `config/` (beans), `scheduling/` e as pontes entre módulos. Os
  jobs `@Scheduled` são adapters driving, mas ficam em `infra/scheduling` (guia, §3.4). A exceção é o `OutboxRelay`,
  agendado em `shared/infra/messaging`.

### 4.2 Árvore de pacotes real (`src/main/kotlin/br/com/locasign/`)

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
│   └── ports/out/{repository,integration,messaging,lease}
├── interfaces/{web/{dto/request,dto/response,mappers,openapi},webhook/pandadoc,messaging}
└── infra/
    ├── config/                      # ContractBeansConfig, PandaDocConfig
    ├── persistence/{adapters,entities,mappers,repositories}   # adapters/LeaseBridgeAdapters.kt = ponte para lease
    ├── pandadoc/                    # PandaDocClient (@HttpExchange), SlidingWindowRateLimiter
    │   └── {adapters,dto/request,dto/response,mappers,exceptions}
    ├── messaging/                   # OutboxContractEventPublisher, ContractEventPayloads
    ├── scheduling/                  # ReconciliationJob, ExpirationJob, ReminderJob
    └── storage/                     # FileSystemSignedDocumentStorage
notification/                        # enxuto, com pacotes planos
├── domain/  app/{usecases,ports/out}  interfaces/messaging/  infra/{config,persistence}
```

Migrations ficam em `src/main/resources/db/migration/`.

### 4.3 Comunicação entre módulos

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

### 4.4 Fronteira com a PandaDoc

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
- Antes de implementar uma chamada nova, consulte `https://developers.pandadoc.com/llms.txt` (acrescente `.md` às URLs
  das páginas) e o ADR-012.

### 4.5 Checklist: novo caso de uso de ponta a ponta

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
    - Kafka: listener em `interfaces/messaging/` (seção 5.3, "Kafka"); a idempotência fica no use case (seção 5.4).
    - Job: classe em `infra/scheduling/` (seção 5.3, "Jobs").
7. **Migration**, se o schema mudar: nova versão em `db/migration/` (seção 5.6).
8. **Testes:** seção 6.
9. **Fronteiras:** checagem da seção 4.6.
10. **Docs:** atualize as tabelas de use cases (guia §4.5) e de endpoints (guia §9.2 e `README.md`) quando mudarem.
    Desvio relevante do guia exige ADR.

### 4.6 Checagem de fronteiras (enquanto não houver teste de arquitetura)

Antes de concluir uma tarefa que crie ou mova classes, rode cada linha abaixo como uma busca do Grep (ripgrep) com
`path` = `src/main/kotlin`: a primeira coluna vai no parâmetro `glob` e a segunda é a regex. Copie as regex deste bloco
como estão (com `|` simples). O resultado esperado é **vazio**; a única exceção é a última linha, que só pode casar em
`contract/infra/persistence/adapters/LeaseBridgeAdapters.kt`.

```text
**/domain/**                 ^import (org\.springframework|tools\.jackson|com\.fasterxml|org\.slf4j|org\.apache\.kafka|java\.sql|jakarta)
**/domain/**                 ^import br\.com\.locasign\.\w+\.(app|interfaces|infra)\.
**/app/**                    ^import (org\.springframework|tools\.jackson|com\.fasterxml|org\.apache\.kafka|java\.sql|jakarta)
**/app/**                    ^import br\.com\.locasign\.\w+\.(infra|interfaces)\.
**/interfaces/**             ^import br\.com\.locasign\.\w+\.infra\.
**/infra/**                  ^import br\.com\.locasign\.\w+\.interfaces\.
**/locasign/shared/**        ^import br\.com\.locasign\.(lease|contract|notification)\.
**/locasign/lease/**         ^import br\.com\.locasign\.(contract|notification)\.
**/locasign/notification/**  ^import br\.com\.locasign\.(lease|contract)\.
**/locasign/contract/**      ^import br\.com\.locasign\.notification\.
**/locasign/contract/**      ^import br\.com\.locasign\.lease\.domain\.(models|valueobjects\.LeaseTerm)
**/locasign/contract/**      ^import br\.com\.locasign\.lease\.(app|infra|interfaces)\.
```

Os globs de módulo levam o prefixo `**/locasign/`: `contract/**` não casa nenhum arquivo a partir de `src/main/kotlin`,
e `**/lease/**` também pegaria `contract/app/ports/out/lease/`. Uma busca vazia só prova algo se o glob casa arquivos;
em caso de dúvida, faça um controle positivo (ex.: `^import org\.slf4j` em `**/app/**` encontra os use cases). O
objetivo continua sendo um teste automatizado (guia, §15). ArchUnit e Konsist não estão no build, e adicioná-los exige
ADR (seção 7.1).

### 4.7 Exceções existentes: não use como precedente

- `POST /api/v1/contracts/{id}/cancel` anula o documento na PandaDoc dentro da requisição, depois do commit
  (`CancelContract`). O ADR-012 decide anular em melhor esforço no cancelamento, mas não diz onde a chamada acontece.
  Fazê-la na requisição contraria a seção 5.4 e está pendente de correção ou ADR. A anulação em si deve continuar.
- `POST /api/v1/contracts/{id}/reconcile` (restrito a operador) chama `SignatureProviderPort.fetchState` dentro da
  requisição (`ReconcileContracts`). O guia prevê esse endpoint (§4.5 e §9.2), mas o princípio 2 do §1 proíbe chamada
  externa na requisição. Não remova o endpoint: a contradição do guia está pendente de ADR.
- SELECT em tabela de outro módulo: `LeaseQueryAdapter` lê `contracts`, e `JdbcNotificationRecipientsAdapter` lê
  `contract_signers`. Não há ADR.
- `AdminController` chama a porta `DeadLetterReplayPort` direto, sem use case. Ele também declara `@Tag`/`@Operation` na
  própria classe, sem interface em `openapi/`, e aninha o DTO `ReplayResponse`.
- `HousekeepingJob` (`shared/infra/scheduling`) apaga linhas de `outbox_events`, `webhook_inbox` e `processed_messages`
  direto com `JdbcClient`, sem use case.
- Mapeamentos fora de `mappers/`: `ContractEvent.toPayload()` (`infra/messaging/ContractEventPayloads.kt`),
  `RestClientException.toProviderException` (`infra/pandadoc/exceptions/`) e a conversão `Lease → LeaseSnapshot` dentro
  de `LeaseBridgeAdapters`.

---

## 5. Regras de código

### 5.1 Nomes, idioma e KDoc

- Siga o glossário da seção 4.1 do guia. Código, identificadores e schema SQL (`snake_case`; guia, §8.1) ficam em
  inglês. KDoc, logs, mensagens de exceção e documentação ficam em português.
- As roles e variáveis do modelo da PandaDoc (`Locatario`, `Imobiliaria`, `Locatario.CPF`...) não têm acento e precisam
  bater exatamente com o modelo (guia, §5).
- Todo tipo de topo (class, interface, object, enum) tem KDoc em pt-BR no formato abaixo. A convenção vem do
  ADR-KDOC-001 de `docs/tasks/plan.md`, ainda sem ADR formal, e hoje cobre todos os tipos. Mantenha a cobertura ao
  criar, mover ou dividir tipos:

  ```kotlin
  /**
   * O que o tipo faz no fluxo de negócio e tecnicamente.
   *
   * **Responsabilidade:**
   * - Papel na arquitetura hexagonal.
   * - Invariantes ou regras (R1–R10, ADRs) que garante.
   */
  ```

  Membros com comportamento não óbvio ganham KDoc de uma linha citando a regra, o ADR ou a seção do guia. Não use
  adjetivos vazios ("robusto", "perfeito").

### 5.2 Kotlin idiomático

- **Proibido `!!`**, inclusive em testes. Alternativas já usadas no código: smart cast depois de checagem, elvis com
  saída (`?: return`, `?: throw DomainException.NotFound(...)`), `?.let { }` e `checkNotNull`. O `assertNotNull(x)` do
  `kotlin.test` devolve o valor não nulo.
- **`when` exaustivo:** sobre `sealed class`/`sealed interface` e sobre `enum` do projeto, o `when` lista todos os
  casos, inclusive os que não fazem nada (`-> Unit`), **sem `else`**. Sujeito anulável ganha um ramo `null ->`
  explícito. `else` só é aceito com sujeito aberto (`String`, `JsonNode`, exceção de biblioteca) ou em `when` sem
  sujeito.
- **Mapeamentos:** conversões entre camadas são extension functions explícitas em `mappers/` (`interfaces/web/mappers`,
  `infra/persistence/mappers`, `infra/pandadoc/mappers`), com nomes `toCommand()`, `toResponse()`, `toEntity()`/
  `toDomain()` e `toPandaDoc()`/`toState()`. MapStruct e mapeadores por reflexão são proibidos (ADR-008). Ids de path
  são convertidos no controller (`ContractId.parse`); dados de cadastro chegam ao command como `String` e viram value
  objects no use case, para que o `field` do erro aponte para o JSON. As exceções existentes estão na seção 4.7.
- **Value objects:** `@JvmInline value class`.
    - Os que validam entrada (`Cpf`, `Email`, `Money`, `LeaseTerm`, `ProviderDocumentId`, `SigningOrder`) têm
      `private constructor` e factory no `companion`. A factory `of(raw, field = "<caminho.json>")` normaliza e lança
      `DomainException.BusinessRuleViolation(field, "<mensagem em pt-BR>")`; em `Money`, ela se chama
      `positive(raw, field)`. O `field` vira a propriedade `field` do Problem Details 422, então passe o caminho do
      campo no JSON (ex.: `"tenant.cpf"`). `ProviderDocumentId.of` e `SigningOrder.of` não recebem `field`.
    - `fromStorage(...)` reconstitui um valor já validado e só é usado em `infra/persistence`.
    - Os Ids (`ContractId`, `LeaseId`) envolvem `kotlin.uuid.Uuid` com construtor público, usado pelos mappers de
      persistência; não o torne privado. Eles oferecem `new()` e `parse(raw)` (texto inválido vira `NotFound`, ou seja,
      404). Só `ContractId` tem `parseOrNull(raw)`.
- **Agregados** (`Contract`, `Lease`): `class` (nunca `data class`, cujo `copy()` contorna invariantes) com
  `private constructor`, uma factory de criação (`Contract.request`, `Lease.register`) e `restore(...)` para
  reconstituir sem eventos. Estado mutável só como `var` com `private set`. Coleções são expostas como `List` somente
  leitura. Eventos e histórico pendentes ficam em explicit backing fields e são lidos por `pullEvents()`/
  `pullHistory()`.
- **Exceções:**
    - Regra de negócio lança subtipo de `DomainException`: `BusinessRuleViolation` vira 422, `NotFound` vira 404,
      `ActiveContractExists` e `ContractFinal` viram 409.
    - **Não use `require(...)`/`IllegalArgumentException` para regra de negócio:** o handler genérico do
      `ApiExceptionHandler` transforma essas exceções em 500.
    - `check`/`checkNotNull`/`error` ficam para invariantes técnicas. Num consumidor Kafka, eles provocam retentativa e,
      esgotadas as tentativas, DLT.
    - Falhas de integração usam a hierarquia selada `ProviderException`: `NotReady`, `RateLimited` e `Unavailable` são
      transitórias; `Forbidden` e `Rejected` são definitivas. O `DefaultErrorHandler` não faz essa distinção: só
      `UnreadableMessageException` (mensagem ilegível) é não retentável e vai direto para a DLT. Para evitar
      retentativas inúteis seguidas de DLT, o use case captura `Forbidden`/`Rejected` e encerra o fluxo, como
      `CreateProviderDocument` e `SendContract` fazem (cancelam o contrato com `failGeneration`).
- **Tempo:** nunca chame `Instant.now()`, `LocalDate.now()` ou `System.currentTimeMillis()` em `domain` ou `app`. O
  domínio recebe `now`/`today` por parâmetro. Os use cases usam `BusinessClock`: `now()` devolve um instante UTC e
  `today()` usa o fuso `locasign.timezone` (padrão `America/Sao_Paulo`). Em `infra`, injete o bean `java.time.Clock`
  (UTC); a única exceção existente é o gauge do `OutboxRelay`, que usa `Instant.now()`. A JVM roda em UTC (`main` e
  Dockerfile), e o banco usa `timestamptz`.
- **Ids:** `kotlin.uuid.Uuid` em `domain` e `app`; `java.util.UUID` nunca entra nessas camadas. Ele aparece em
  `infra/persistence`, convertido com `toJavaUuid()`/`toKotlinUuid()`, e no `CorrelationIdFilter`. `Uuid.random()` gera
  UUID v4.
- **Imutabilidade:** commands, views, DTOs, eventos, entidades e properties são `data class` com `val`, atualizados com
  `copy()`. Não use `data class` com propriedade `ByteArray`, cujo `equals` compara referência: use `class`. Casos sem
  estado de hierarquias seladas são `data object`. Políticas sem estado são `object`. Tipos `Mutable*` ficam só em
  variáveis locais ou backing fields privados.
- **Nulabilidade:** portas devolvem `T?`, nunca `Optional`. Nos adapters, use `findByIdOrNull` e converta o `Optional`
  de APIs Java na hora. Injeção sempre por construtor (`private val`), sem `lateinit` e sem `@Autowired`.
- **Estilo:** não há ktlint, detekt nem `.editorconfig`, então siga o estilo vigente:
    - indentação com 4 espaços no Kotlin de `src/`, e com tabulação no `build.gradle.kts` e no SQL dentro de raw
      strings;
    - trailing comma em listas multilinha e SQL multilinha com `trimIndent()`;
    - logger `private val log = LoggerFactory.getLogger(javaClass)`, com placeholders `{}` em vez de string template;
    - constantes em `private companion object` (`const val`) ou `private const val` de topo;
    - catálogos compartilhados como `object` (`Topics`, `ConsumerGroups`, `Metrics`).

### 5.3 Spring Boot 4 e Spring Framework 7

- **Starters modulares** (declarados sem versão): `spring-boot-starter-webmvc` (não `-web`), `-restclient`,
  `-validation`, `-actuator`, `-data-jdbc`, `-flyway` (+ `flyway-database-postgresql`) e `-kafka`. No Boot 4 a
  autoconfiguração é dividida por módulo: `flyway-core` ou `spring-kafka` sozinhos não autoconfiguram nada, então use
  sempre o starter.
- **Proibido sem ADR:** JPA/Hibernate/`jakarta.persistence.*` (ADR-003), WebFlux (ADR-007; o projeto é MVC com virtual
  threads), OpenFeign (ADR-004), MapStruct (ADR-008), Spring Security (API sem autenticação no MVP), `@RetryableTopic` e
  ShedLock. O guia (§5.7) admite os recursos de resiliência do Spring 7 (`@Retryable`, `RetryTemplate`), mas o projeto
  não os usa: a retentativa de 429 fica no `call` do adapter, e as demais ficam no `DefaultErrorHandler`. Adotá-los muda
  esse desenho e exige ADR.
- **Jackson 3:** o bean é `tools.jackson.databind.json.JsonMapper`; a exceção de parse é
  `tools.jackson.core.JacksonException`. As anotações continuam em `com.fasterxml.jackson.annotation.*`. **Armadilha:**
  o Jackson 2 (`com.fasterxml.jackson.databind`) chega ao classpath de compilação via springdoc. O import compila, mas
  não há bean do Jackson 2: nunca importe `com.fasterxml.jackson.databind.*`, `.core.*` nem `.module.kotlin.*`, e não
  use `ObjectMapper`. No 3.x, `JsonNode` usa `isString`, `stringValue()`, `asString()` e `properties()`. `java.time` já
  vem no `jackson-databind` 3: não adicione `jackson-datatype-jsr310`.
- **Montagem de beans:** toda `@Configuration` usa `proxyBeanMethods = false`, com dependências recebidas como
  parâmetros do método `@Bean`. Use cases não levam anotação (`@Service`, `@Component`, `@Transactional`, `@Async`):
  continuam classes finais, e o plugin `kotlin-spring` só abre classes anotadas. Adapters JDBC usam `@Repository`, os
  demais adapters usam `@Component`, e os da PandaDoc são criados em `PandaDocConfig`.
- **Transações:** só `TransactionRunner.run { }` (`TransactionTemplate`, propagação REQUIRED; ADR-011). Nunca use
  `@Transactional`. Chamada à PandaDoc nunca fica dentro do bloco: use duas transações curtas com a chamada HTTP entre
  elas, como em `CreateProviderDocument` (a idempotência desse padrão está na seção 5.4). `transactions.run { }` é
  método da porta, não a scope function `run`, e o lambda não é `inline`: para sair cedo, use `return@run`.
- **Propriedades:**
    - Fonte tipada: `LocaSignProperties` (`@ConfigurationProperties(prefix = "locasign")`, data class imutável com
      defaults).
    - Propriedade nova: (1) campo com default em `LocaSignProperties`; (2) chave `${VARIAVEL:default}` no
      `application.yaml` (ajustes internos sem variável de ambiente ficam com valor literal, como
      `locasign.outbox.batch-size`); (3) linha na tabela do `README.md`; (4) `services.app.environment` no
      `compose.yml`, se o container precisar de outro valor.
    - Exceção: propriedade lida só em `interfaces`, que não importa `infra`, não entra em `LocaSignProperties`. Leia-a
      com `@param:Value("\${locasign.<chave>:<default>}")`, como `locasign.admin.token` no `OperatorAccessGuard`.
    - Em strings de anotação, escape o placeholder: `"\${locasign.jobs.expiration.interval:PT15M}"`.
    - `enabled`/`interval` dos jobs e `locasign.outbox.relay-interval` são lidos por placeholder (`@Scheduled`,
      `@ConditionalOnProperty`). Ao mudar um default, sincronize `application.yaml`, `LocaSignProperties` e o default do
      placeholder.
    - O `spring-boot-configuration-processor` não roda sobre Kotlin (não há kapt): documente a propriedade no KDoc e no
      README.
- **Kafka:**
    - Nomes canônicos em `shared/app/Messaging.kt`. Tópico novo: constante em `Topics` (e em `Topics.ALL`) mais os beans
      `NewTopic` do tópico e da DLT em `shared/infra/config/KafkaConfig.kt`; o broker não cria tópicos sozinho. Grupo
      novo: constante em `ConsumerGroups`.
    - Listener: `@Component` em `<módulo>/interfaces/messaging/`, com
      `@KafkaListener(topics = [Topics.X], groupId = ConsumerGroups.Y)` e parâmetro `String`. Leia com `EnvelopeReader`
      e delegue ao use case; o listener não abre transação nem acessa o banco.
    - Payload é `String` com JSON do `JsonMapper`. Não configure `JsonSerializer`/`JsonDeserializer` do Spring Kafka.
    - Erros: um único `DefaultErrorHandler`, que faz a entrega original e mais 3 retentativas (esperas de 1 s, 2 s e 4
      s) antes de publicar em `<tópico>.dlt`. O Boot só o aplica se ele for o único `CommonErrorHandler`: não declare
      outro.
    - Publicação: código novo publica gravando no outbox, nunca injetando `KafkaTemplate`. Só `OutboxRelay` e
      `DeadLetterReplayer` publicam, além do `DeadLetterPublishingRecoverer` montado em `KafkaConfig`, que envia para a
      DLT. Producer com `acks=all` e idempotência; consumer com `ack-mode: record` e sem auto-commit.
- **Spring Data JDBC (nunca JPA):**
    - Entidade `data class` com `@Table` em `infra/persistence/entities` e id `java.util.UUID` atribuído pelo domínio.
    - `@Version val rowVersion: Long?` vale `null` em agregado novo, e é isso que faz o `save()` executar INSERT, já que
      o id vem do domínio. No INSERT, o Spring Data JDBC grava a versão inicial; a cada UPDATE, confere a versão e a
      incrementa (lock otimista). A `OptimisticLockingFailureException` vira 409. Não remova o `@Version` nem o
      inicialize com 0.
    - `@MappedCollection` apaga e regrava as filhas a cada `save`.
    - Repositório `CrudRepository<Entity, UUID>`. SQL próprio usa `JdbcClient` com parâmetros nomeados (não há
      `JdbcTemplate`), com `Instant` convertido por `toDb()` e lido por `getInstant()`.
- **Web:**
    - Erros saem como Problem Details (RFC 9457) pelo `ApiExceptionHandler`, com `type` `/problems/<slug>`. Use cases
      lançam `DomainException`, nunca `ResponseStatusException`. Use `HttpStatus.UNPROCESSABLE_CONTENT`;
      `UNPROCESSABLE_ENTITY` está deprecated.
    - OpenAPI (springdoc 3.1.0): `@Tag`, `@Operation` e `@ApiResponse` ficam na interface `openapi/<Recurso>Api`, e as
      anotações MVC ficam no controller. Campos usam `@field:Schema`, e o receptor de webhook é `@Hidden`.
    - **Alvos de anotação em parâmetros de construtor:**
        - Validação usa `@field:` (`@field:NotBlank`, `@field:Valid` em objetos aninhados), com `@Valid @RequestBody` no
          controller.
        - `@JsonProperty` e `@Value` usam `@param:`.
        - DTOs de entrada levam `@JsonIgnoreProperties(ignoreUnknown = true)`.
    - Cadastro responde 201 com `Location`. Comando assíncrono responde 202 com `Location`.
    - Token de operador: só as operações do ADR-013 chamam `OperatorAccessGuard.requireOperator()`. São elas
      `POST /api/v1/contracts/{id}/reconcile` (consome cota da PandaDoc) e
      `POST /api/v1/admin/dead-letters/{topic}/replay` (republica mensagens). O header `X-Admin-Token` precisa ser igual
      a `ADMIN_TOKEN`; sem token configurado, ou com header ausente ou errado, a resposta é 403. Proteger outra operação
      exige ADR.
- **Jobs:** `@Component` em `<módulo>/infra/scheduling/`, com
  `@ConditionalOnProperty(prefix = "locasign.jobs.<job>", name = ["enabled"], matchIfMissing = true)` e
  `@Scheduled(fixedDelayString = "\${locasign.jobs.<job>.interval:...}", initialDelayString = "\${locasign.jobs.<job>.interval:...}")`.
  O corpo delega a um use case dentro de `try { } catch (e: Exception) { log.error(...) }`, para que uma falha não
  interrompa o agendamento. Instância única, sem lock distribuído. O `OutboxRelay` não tem chave `enabled` e roda desde
  a subida.
- **Observabilidade:**
    - O Actuator expõe só `health`, `info` e `metrics` (health com `show-details: when-authorized`, e `always` no perfil
      `local`; ADR-013). Não amplie a exposição sem ADR.
    - Contadores via `MetricsPort.count(Metrics.X, ...)`, com nomes em `Metrics` e sem dados pessoais em tags. Gauges
      não passam pela porta: o `OutboxRelay` registra o seu direto no `MeterRegistry`, em `infra`.
    - Escrita no MDC só via `CorrelationContext.with(...)`. A única leitura direta (`MDC.get`) fica no
      `JdbcOutboxAdapter`, que copia `correlationId`/`causationId` para o envelope. Fora do perfil `local`, os logs saem
      em JSON ECS.

### 5.4 Consistência: agregado, outbox, idempotência e webhooks

- **Agregado `Contract`:** toda mudança de status passa por ele (`ContractTransitionPolicy`, R5, ADR-010). Nunca
  atualize status direto no banco. Em `contract`, persista sempre com `ContractPersister.save(contract)`, nunca com
  `contracts.save` direto, ou os eventos se perdem.
- **Outbox:** todo evento de domínio vai para `outbox_events` na mesma transação do agregado. Use cases nunca publicam
  no Kafka.
- **Idempotência (R6):** o use case acionado pelo consumidor chama
  `ProcessedMessagesPort.markProcessed(ConsumerGroups.X, eventId)` como primeira instrução do `transactions.run { }` que
  grava o efeito. Retorno `false` indica duplicata: saia sem efeito (`return@run`).
    - Em use case de duas transações, com a chamada à PandaDoc no meio (`CreateProviderDocument`, `SendContract`,
      `ArchiveSignedDocument`), a primeira transação só consulta `isProcessed(...)` e o estado do agregado. Ela chama
      `markProcessed` apenas ao descartar o evento. O `markProcessed` do efeito abre a segunda transação, depois da
      chamada HTTP.
    - **Nunca marque o evento antes da chamada externa:** se ela falhar, a retentativa do Kafka pularia um efeito que
      nunca aconteceu.
    - `event_id` é `text` (ADR-013).
- **Eventos de domínio:** o `eventType` é o nome simples da classe e viaja no envelope. Renomear um `ContractEvent`
  quebra consumidores e mensagens já gravadas: trate como mudança de contrato (tópicos `.v1`, ADR-009). Ao criar um
  evento (`data class` em `contract/domain/events/ContractEvent.kt`), o `when` de `ContractEvent.toPayload()` obriga a
  tratá-lo. O roteamento, porém, é por `String`: atualize à mão `OrchestratorConsumer`, `PostSignatureConsumer` e
  `NotificationPolicy.audienceFor`.
- **Webhook (inbox, R7):**
    - O controller recebe o corpo como `ByteArray`. `ReceiveProviderWebhook` valida o HMAC-SHA256 (hex, query
      `signature`) sobre esses bytes, em tempo constante e antes de qualquer parse.
    - Assinatura ausente ou inválida: **401**, sem gravar nada (guia, §6.2; o ADR-012 mantém 401 em vez do 403 sugerido
      pela PandaDoc). Com `PANDADOC_WEBHOOK_SHARED_KEY` vazia, o gateway recusa toda entrega com 401. Como a PandaDoc
      não reenvia (guia, §6.1), configure a chave antes de cadastrar o webhook, ou as entregas reais se perdem.
    - Assinatura válida: na mesma transação, grava o corpo bruto em `webhook_inbox`, deduplicado por `delivery_id`
      (header `X-PandaDoc-Webhook-Event-Id` ou SHA-256 do corpo). Só se a entrega for nova, grava uma linha em
      `outbox_events` por item do array, com id `deliveryId:índice`.
    - Responde **200**, inclusive para duplicata, JSON malformado e evento desconhecido. Nenhuma regra de negócio roda
      na requisição: os itens são processados depois, via Kafka, por `ProcessProviderWebhookItem` (ADR-006).
    - **Nunca responda 410**: a PandaDoc desativa webhooks que recebem 410.
- **PandaDoc fora da requisição do usuário** (guia, §1, princípio 2): chamadas à API só em consumidores Kafka e em jobs
  `@Scheduled`, sempre via `SignatureProviderPort`. Não crie chamadas síncronas novas; as exceções existentes estão na
  seção 4.7. Operação nova da PandaDoc segue quatro passos:
    1. operação com tipos neutros em `SignatureProviderPort` (modelos em `ProviderModels.kt`);
    2. método em `PandaDocClient`;
    3. DTOs em `infra/pandadoc/dto` e conversões em `infra/pandadoc/mappers`;
    4. implementação em `PandaDocSignatureProviderAdapter`, dentro de `call("<operação>") { }`.

  O `call` aplica o `SlidingWindowRateLimiter` (janela de 1 minuto por operação, `PANDADOC_RATE_LIMIT_PER_MINUTE` = 8,
  espera máxima de 30 s) e retenta o HTTP 429 até 3 vezes (2 s, 4 s, 8 s). Nos consumidores, as demais falhas sobem para
  o `DefaultErrorHandler`, inclusive `Forbidden`/`Rejected` não capturadas e o `RateLimited` do limitador local depois
  dos 30 s. Nos jobs, a falha é registrada e a próxima execução tenta de novo (`ReconcileContracts` captura
  `ProviderException` por contrato).

### 5.5 Segurança e dados pessoais

- Nunca registre em log segredos (`PANDADOC_API_KEY`, `PANDADOC_WEBHOOK_SHARED_KEY`, `ADMIN_TOKEN`) nem CPF ou e-mail
  completos. Use `Cpf.masked()` e `Email.masked()`.
- **Armadilha:** `Cpf.toString()` já mascara, mas `Email.toString()` devolve o endereço **completo**. O `toString()`
  gerado de `Signer`, `Tenant`, `AgencySigner`, `LeaseSnapshot`, `ProviderRecipient` e `ProviderDocumentRequest` também
  o inclui.
    - Vários `data class` guardam CPF ou e-mail completos como `String`, e neles o mascaramento do `Cpf` não vale. São
      eles: `RegisterLeaseCommand`, `LeaseDetailView`, `SignerView`, os DTOs `CreateLeaseRequest`/`TenantRequest`/
      `AgencySignerRequest`, as entidades `LeaseEntity`/`ContractSignerEntity` e os DTOs da PandaDoc (o token
      `Locatario.CPF` usa `Cpf.formatted()`).
    - Nunca logue esses objetos inteiros nem interpole `Email`, CPF em texto ou `Cpf.formatted()` em logs ou mensagens
      de exceção.
- Payloads de eventos de contrato (`ContractEvent.toPayload()`), chaves Kafka, nomes de tópico e tags de métrica não
  carregam CPF nem e-mail (guia, §7.3 e §14).
- **Exceção conhecida, sem ADR:** os itens de webhook vão crus para o outbox e para `locasign.pandadoc.webhooks.v1` e
  contêm `recipients[].email`. Não logue `payloadJson` nem o valor dessas mensagens, e não crie consumidores que
  propaguem esses dados.
- Use só dados fictícios (R10): CPF válido como `529.982.247-25` e e-mails `@example.com`.

### 5.6 Migrations (Flyway)

- Arquivos em `src/main/resources/db/migration/V<n>__<descricao>.sql`, onde `n` é a maior versão existente mais um.
- Migration aplicada é **imutável**: correção vira nova versão.
- Siga o guia, §8.1:
    - tabelas e colunas em `snake_case`, em inglês;
    - status como `text` com `CHECK`;
    - instantes em `timestamptz` UTC e dinheiro em `numeric(12,2)`;
    - payloads em `jsonb`, exceto o corpo bruto do webhook, guardado em `text` para preservar os bytes (R7);
    - chaves primárias `uuid` geradas no domínio.
- Convenção da V1, fora do guia: constraints e índices nomeados com os prefixos `ck_`, `uq_`, `ux_` e `ix_`. Não
  renomeie `ux_contracts_one_active_per_lease` nem `uq_contracts_lease_version`: o `ContractRepositoryAdapter` procura
  esses nomes na mensagem de erro para responder 409 em vez de 500.

---

## 6. Testes automatizados

A estratégia oficial está no guia, §15. Esta seção a traduz para o classpath real. Se ainda não houver testes, quem
escrever o primeiro estabelece o padrão: siga estas convenções.

### 6.1 Stack disponível (BOM do Boot 4.1; confira com `./gradlew dependencies --configuration testRuntimeClasspath`)

| Biblioteca                                                                                  | Uso                                                                                      |
|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| JUnit Jupiter 6 + `junit-jupiter-params`                                                    | motor e testes parametrizados (`@ParameterizedTest`, `@EnumSource`, `@CsvSource`)        |
| `kotlin-test-junit5`                                                                        | `kotlin.test.Test`, `assertEquals`, `assertIs`, `assertFailsWith`, `assertNotNull`       |
| AssertJ e Awaitility                                                                        | asserções fluentes (base do `MockMvcTester`) e espera de efeitos assíncronos             |
| Mockito 5 (`mockito-core`, `-junit-jupiter`)                                                | só em slices Spring; o mock maker inline mocka classes finais                            |
| Spring Test 7                                                                               | `MockMvcTester`, `@MockitoBean`, `@TestBean`, `MockRestServiceServer`, `@Sql`            |
| Testcontainers 2 (`-junit-jupiter`, `-postgresql`, `-kafka`) + `spring-boot-testcontainers` | PostgreSQL e Kafka reais com `@ServiceConnection`                                        |
| `spring-kafka-test`                                                                         | `KafkaTestUtils` para ler tópicos (o guia escolheu Testcontainers, não `@EmbeddedKafka`) |

**Fora do build:** MockK, WireMock, ArchUnit, Konsist e mockito-kotlin, que também não estão no BOM, e os bancos
embarcados H2 e HSQLDB, que o BOM gerencia mas o projeto não usa. Adicionar qualquer um exige ADR (seção 7.1).
Persistência se testa em PostgreSQL real.

### 6.2 Imports corretos no Boot 4 e no Testcontainers 2

Não copie imports do Boot 3 nem do Testcontainers 1.x.

| Uso                                                          | Pacote                                                                                                                       |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `@WebMvcTest`, `@AutoConfigureMockMvc`                       | `org.springframework.boot.webmvc.test.autoconfigure`                                                                         |
| `@DataJdbcTest`                                              | `org.springframework.boot.data.jdbc.test.autoconfigure`                                                                      |
| `@SpringBootTest`, `@TestConfiguration`                      | `org.springframework.boot.test.context`                                                                                      |
| `@ServiceConnection`                                         | `org.springframework.boot.testcontainers.service.connection`                                                                 |
| `@MockitoBean`, `@MockitoSpyBean` (`@MockBean` foi removido) | `org.springframework.test.context.bean.override.mockito`                                                                     |
| `@TestBean`                                                  | `org.springframework.test.context.bean.override.convention`                                                                  |
| `MockMvcTester`                                              | `org.springframework.test.web.servlet.assertj`                                                                               |
| `MockRestServiceServer`                                      | `org.springframework.test.web.client`                                                                                        |
| `PostgreSQLContainer` (não genérico)                         | `org.testcontainers.postgresql` (o de `org.testcontainers.containers` está deprecated)                                       |
| `KafkaContainer`                                             | `org.testcontainers.kafka` (o de `org.testcontainers.containers` está deprecated e não tem `@ServiceConnection` no Boot 4.1) |
| `@Testcontainers`, `@Container`                              | `org.testcontainers.junit.jupiter`                                                                                           |

### 6.3 O que testar em cada camada

| Camada                                                   | Como                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | Docker |
|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| `domain`                                                 | JUnit + `kotlin.test`, sem Spring e sem mocks. Matriz de `ContractStatus` com `@ParameterizedTest`/`@EnumSource`. `Contract.restore(...)` monta o agregado em qualquer status; verifique `pullEvents()` e `pullHistory()`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | não    |
| `app` (use cases)                                        | Instancie o use case direto, com fakes em memória das portas, um `TransactionRunner` que só executa o bloco (ADR-011) e um `BusinessClock` fixo. Sem Spring e sem Mockito. Fakes não fazem rollback: atomicidade se prova no `*IT`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | não    |
| `interfaces/web`                                         | `@WebMvcTest(XController::class)` + `MockMvcTester`. Forneça os use cases com `@MockitoBean` ou `@TestBean`. O slice carrega o `CorrelationIdFilter`, que exige `CorrelationContext`: use `@Import(MdcCorrelationContext::class)`. Importe ou mocke o `OperatorAccessGuard` quando o controller usá-lo.                                                                                                                                                                                                                                                                                                                                                                                                                                          | não    |
| `infra/pandadoc`                                         | `MockRestServiceServer.bindTo(builder)` sobre um `RestClient.Builder`, depois `HttpServiceProxyFactory` para criar o `PandaDocClient`. Monte o `RestClient` no próprio teste: não reaproveite `PandaDocConfig.pandaDocRestClient`, que chama `.requestFactory(...)` no mesmo builder e troca o mock por HTTP real. Injete `sleeper` falso no `PandaDocSignatureProviderAdapter` e `sleeper`/`nanoTime` falsos no `SlidingWindowRateLimiter`. Cenários do guia (§15): criação assíncrona (`uploaded` → `draft`), 404 antes do draft, 429 com retentativa, 403 e timeout. O guia prevê WireMock, que está fora do build: o primeiro teste deste adapter vem com um ADR `proposta` que registra a escolha entre `MockRestServiceServer` e WireMock. | não    |
| `infra/persistence` e migrations                         | `@DataJdbcTest` + `@Import(<Adapter>::class)` + PostgreSQL com `@ServiceConnection`; o slice roda o Flyway. Adapters que pedem `JsonMapper` (ex.: `JdbcOutboxAdapter`) vão para `@SpringBootTest`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | sim    |
| webhook ponta a ponta, outbox, relay, consumidores e DLT | `@SpringBootTest` (+ `@AutoConfigureMockMvc` no webhook) com PostgreSQL e Kafka via `@ServiceConnection`. Efeitos assíncronos com `Awaitility.await().atMost(...).untilAsserted { }`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | sim    |

### 6.4 Localização e nomes (convenção nova, ainda sem ADR: registre-a como ADR `proposta` junto com o primeiro teste)

- `src/test/kotlin` espelha os pacotes de `main`. Exemplo:
  `src/test/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicyTest.kt`.
- Sufixo `*Test` para testes sem Docker e `*IT` para testes com Testcontainers. A task `test` roda os dois; o sufixo
  serve para filtrar (`--tests "*Test"`).
- Métodos: uma frase em português entre crases, citando a regra, como ``fun `estado final é imutável (R5)`()``. Na JVM,
  nomes entre crases não aceitam `.` `:` `;` `/` `<` `>` `[` `]` nem `\`: escreva "de SENT para COMPLETED", não "SENT ->
  COMPLETED".
- Fakes ficam em `src/test/kotlin/br/com/locasign/<módulo>/app/fakes/`, e os das portas transversais em
  `.../shared/app/fakes/`. Prefixos: `InMemory*` (guarda estado), `Fixed*` (valor fixo), `NoOp*` (ignora a chamada) e
  `Immediate*` (executa direto).
- Configuração de teste compartilhada (containers): `src/test/kotlin/br/com/locasign/support/`. Fixtures em
  `src/test/resources/<assunto>/`, partindo de `http/samples/`.
- Nunca crie `src/test/resources/application.yaml`: ele substitui o de `main` no classpath. Use o atributo `properties`
  do `@SpringBootTest` ou `application-test.yaml` com `@ActiveProfiles("test")`.
- `main()` fixa a JVM em UTC, mas os testes não passam por ela. Ao criar o primeiro teste que dependa de fuso, adicione
  `systemProperty("user.timezone", "UTC")` em `tasks.withType<Test>` no `build.gradle.kts`.

### 6.5 Cobertura prioritária (R1 a R10)

Toda mudança que toque uma destas regras vem com o teste correspondente:

| Regra        | Teste mínimo                                                                                                                                                                                                                                                                                                                                                                                             | Nível        |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| R1           | `RequestContract` lança `ActiveContractExists`; o índice `ux_contracts_one_active_per_lease` vira `ActiveContractExists`; a API responde 409 `/problems/active-contract-exists`.                                                                                                                                                                                                                         | app, IT, web |
| R2           | `Cpf.of`, `Money.positive` (aluguel maior que zero), `LeaseTerm.of` (1 a 120 meses, padrão 30) e `Lease.register` (nome e endereço obrigatórios; data de início anterior a `today` vira 422). Um `@WebMvcTest` prova que a Bean Validation com `@field:` dispara e devolve 400 `/problems/validation` (guia, §9.3 e §9.4).                                                                               | domain, web  |
| R3           | `Contract.request` rejeita signatários fora da ordem: primeiro o locatário, depois a imobiliária.                                                                                                                                                                                                                                                                                                        | domain       |
| R4           | Expiração com `BusinessClock` fixo antes e depois do prazo de 7 dias; lembrete no 3º dia.                                                                                                                                                                                                                                                                                                                | domain, app  |
| R5 e ADR-010 | Matriz completa de `ContractTransitionPolicy.decide`: estado final imutável, sem regressão, salto para a frente aceito, mesmo status = `NoOp`. `Contract.apply` grava `IGNORED_TRANSITION` e ignora atualização mais antiga que a última aplicada.                                                                                                                                                       | domain       |
| R6           | O mesmo `eventId` processado duas vezes gera um único efeito; `markProcessed` devolve `false` na segunda chamada; falha na chamada externa não deixa o evento marcado.                                                                                                                                                                                                                                   | app, IT      |
| R7 e webhook | `webhook_inbox.raw_body` guarda o corpo exatamente como chegou, inclusive com JSON malformado ou evento desconhecido. HMAC sobre os bytes exatos é aceito; o mesmo JSON reserializado com a assinatura original recebe 401 e nada é gravado. Entrega repetida recebe 200 sem nova linha no inbox nem no outbox. Array com N itens gera N linhas no outbox (`deliveryId:índice`). Nenhuma resposta é 410. | app, web, IT |
| Outbox       | Falha dentro de `transactions.run { }` depois de `outbox.append(...)` não deixa linha em `outbox_events`.                                                                                                                                                                                                                                                                                                | IT           |
| R8           | `RunPostSignatureActions` não age se o contrato não está `COMPLETED`.                                                                                                                                                                                                                                                                                                                                    | app          |
| R9           | Depois de `DECLINED`, `EXPIRED` ou `CANCELLED`, `RequestContract` cria a versão seguinte (`nextVersionNumber`) e as anteriores continuam no histórico; depois de `COMPLETED`, lança `ActiveContractExists` (409, ADR-013).                                                                                                                                                                               | app, IT      |
| R10          | `Cpf.toString()` e `Cpf.masked()` não expõem o CPF; logs e notificações usam `Email.masked()`.                                                                                                                                                                                                                                                                                                           | domain, IT   |

### 6.6 Integração: regras e armadilhas

- Containers só via `@ServiceConnection`, nunca apontando para o PostgreSQL ou o Kafka do `compose.yml`
  (`localhost:5432` e `localhost:9092` são os defaults do `application.yaml`). Para o PostgreSQL, use `postgres:18.6`, a
  mesma tag do compose (seção 1.1). Para o Kafka, o guia (§15) escolheu `apache/kafka-native:4.3.1`; usar a imagem do
  compose (`apache/kafka:4.3.1`) seria desvio e exige ADR. As classes do Testcontainers 2 aceitam as duas.
- Declare os containers como `@Bean @ServiceConnection` numa `@TestConfiguration` em `support/` e importe-a nas classes
  de teste. O cache de contexto do Spring reaproveita o container entre classes. Varie o mínimo possível de
  `@MockitoBean`, `@TestBean` e `properties`, porque cada combinação cria um contexto novo (o job de CI tem limite de 20
  minutos).
- Container como campo da classe precisa ser estático: `companion object` com `@JvmStatic`, `@Container` e
  `@ServiceConnection`, e `@Testcontainers` na classe.
- `@DataJdbcTest` é `@Transactional` e faz rollback. Para provar atomicidade (outbox ou `processed_messages` desfeitos
  quando o efeito falha), use `@SpringBootTest` e limpe as tabelas no `@BeforeEach`.
- Em todo `@SpringBootTest`:
    - substitua `SignatureProviderPort` (bean `signatureProvider`) por um fake; nenhum teste automatizado chama a
      PandaDoc, nem o sandbox;
    - desligue os quatro jobs: `locasign.jobs.reconciliation.enabled=false`, `locasign.jobs.expiration.enabled=false`,
      `locasign.jobs.reminder.enabled=false` e `locasign.jobs.housekeeping.enabled=false`;
    - use `locasign.outbox.relay-interval=200ms` (o `OutboxRelay` roda sempre);
    - defina `locasign.pandadoc.webhook-shared-key=<chave de teste>` (com a chave vazia, todo webhook recebe 401);
    - aponte `locasign.archive.directory` para um diretório temporário;
    - sem container de Kafka, use `spring.kafka.listener.auto-startup=false`.
- DLT: o `DefaultErrorHandler` faz a entrega original e mais 3 retentativas (1 s, 2 s, 4 s), ou seja, 4 entregas;
  `UnreadableMessageException` vai direto para a DLT. Dê uns 15 s ao Awaitility antes de ler o `.dlt` com
  `KafkaTestUtils`.
- Não use `@Testcontainers(disabledWithoutDocker = true)`: sem Docker o teste seria pulado e a CI ficaria verde sem
  testar nada.

### 6.7 O que não fazer em testes

- Não adicione bibliotecas de teste sem ADR (seção 6.1).
- Não use Spring, Mockito nem containers em testes de `domain` e `app`.
- Não use `Thread.sleep`. Use Awaitility para efeitos assíncronos e os parâmetros `sleeper`/`nanoTime` para limitador e
  backoff.
- Não use `!!` nem dados reais.
- Não desative (`@Disabled`), não apague e não afrouxe um teste para o build passar. Corrija a causa.

### 6.8 Esqueletos de referência

Estes esqueletos foram compilados e executados contra o código atual (inclusive o `*IT` com PostgreSQL via
Testcontainers).

```kotlin
// src/test/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicyTest.kt
package br.com.locasign.contract.domain.services

import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.ContractStatus.COMPLETED
import br.com.locasign.contract.domain.models.ContractStatus.SENT
import br.com.locasign.contract.domain.models.ContractStatus.VIEWED
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContractTransitionPolicyTest {

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["COMPLETED", "DECLINED", "EXPIRED", "CANCELLED"])
    fun `estado final é imutável (R5)`(terminal: ContractStatus) {
        ContractStatus.entries.filter { it != terminal }.forEach { target ->
            assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(terminal, target))
        }
    }

    @Test
    fun `status não regride (R5)`() {
        assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(VIEWED, SENT))
    }

    @Test
    fun `salto para a frente é aceito (ADR-010)`() {
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(SENT, COMPLETED))
    }
}
```

```kotlin
// src/test/kotlin/br/com/locasign/shared/app/fakes/SharedFakes.kt
package br.com.locasign.shared.app.fakes

import br.com.locasign.shared.app.ports.BusinessClock
import br.com.locasign.shared.app.ports.ProcessedMessagesPort
import br.com.locasign.shared.app.ports.TransactionRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** ADR-011: só executa o bloco. Não faz rollback; a atomicidade se testa nos *IT. */
object ImmediateTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}

class FixedBusinessClock(
    var instant: Instant,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) : BusinessClock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = LocalDate.ofInstant(instant, zone)
}

class InMemoryProcessedMessages : ProcessedMessagesPort {
    private val processed = mutableSetOf<Pair<String, String>>()
    override fun isProcessed(consumerGroup: String, eventId: String) = (consumerGroup to eventId) in processed
    override fun markProcessed(consumerGroup: String, eventId: String) = processed.add(consumerGroup to eventId)
}
```

```kotlin
// src/test/kotlin/br/com/locasign/support/PostgresTestcontainers.kt
package br.com.locasign.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestcontainers {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18.6")
}
```

```kotlin
// src/test/kotlin/br/com/locasign/shared/infra/persistence/JdbcProcessedMessagesAdapterIT.kt
package br.com.locasign.shared.infra.persistence

import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.support.PostgresTestcontainers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.context.annotation.Import
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJdbcTest
@Import(PostgresTestcontainers::class, JdbcProcessedMessagesAdapter::class)
class JdbcProcessedMessagesAdapterIT(@Autowired private val processed: JdbcProcessedMessagesAdapter) {

    @Test
    fun `o mesmo evento é registrado uma única vez por grupo (R6)`() {
        assertTrue(processed.markProcessed(ConsumerGroups.ORCHESTRATOR, "evt-1"))
        assertFalse(processed.markProcessed(ConsumerGroups.ORCHESTRATOR, "evt-1"))
        assertTrue(processed.isProcessed(ConsumerGroups.ORCHESTRATOR, "evt-1"))
    }
}
```

---

## 7. Build, dependências e CI

### 7.1 Governança de dependências

- **Origem das versões:** `gradle/libs.versions.toml` fixa só Kotlin, Spring Boot, o plugin
  `io.spring.dependency-management` e o springdoc. Plugins entram por `alias(libs.plugins.*)`. Todo o resto (starters,
  Jackson 3, Flyway, driver PostgreSQL, `kafka-clients`, JUnit, Mockito, AssertJ, Testcontainers) vem do BOM
  `spring-boot-dependencies` e é declarado em `build.gradle.kts` **sem versão**. O plugin Kotlin 2.4.20 sobrepõe o
  Kotlin 2.3.x do BOM (guia, §2, nota sobre Kotlin 2.4.20).
- **Exige ADR quando o agente faz a mudança:** adicionar, remover ou trocar a versão de uma dependência, inclusive as
  bibliotecas de teste citadas no guia que estão fora do build (MockK, WireMock, ArchUnit, Konsist). Também exigem ADR
  mudar a toolchain Java, o wrapper do Gradle ou as imagens (`eclipse-temurin`, `postgres`, `apache/kafka`) e
  sobrescrever versão gerenciada pelo BOM.
- **Como adicionar, depois do ADR:**
    1. Declare a coordenada sem versão e rode
       `./gradlew dependencyInsight --dependency <artefato> --configuration <runtimeClasspath|testRuntimeClasspath>`. Se
       resolver, o BOM gerencia: mantenha sem versão.
    2. Se não resolver, adicione `[versions]` + `[libraries]` no catálogo e use `libs.<alias>`, como no precedente
       `libs.springdoc.webmvc.ui`.
    3. Nunca fixe versão de artefato gerenciado pelo BOM (nada de versão na coordenada, `extra["...version"]` ou
       `resolutionStrategy.force`).
    4. Testcontainers está na linha 2.x: os artefatos se chamam `org.testcontainers:testcontainers-<módulo>`.
- Atualizações de rotina chegam por PRs semanais do Dependabot, revisados e aceitos por humanos; a exigência de ADR
  desta seção vale para mudanças feitas pelo agente. O agente não atualiza versões por conta própria. Ao revisar um
  desses PRs, rode `./gradlew build` e confira os contratos da seção 7.2.

### 7.2 Contratos de build (não quebre sem atualizar os consumidores)

- **Artefato único `build/libs/app.jar`:** o `bootJar` é renomeado para `app.jar` e a task `jar` está desabilitada. O
  caminho é consumido pelo `Dockerfile`, pelo `gradle.yml` (upload com `if-no-files-found: error`) e pelo `release.yml`.
  Renomear ou reativar `jar` exige atualizar os três.
- **Dockerfile:** o estágio de build copia só `gradlew`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/` e `src/` e
  roda `bootJar -x test`. Criar `gradle.properties`, `buildSrc/` ou subprojetos exige ajustar o `COPY`.
- **Compilador:** o Spring 7 anota nulidade com JSpecify, que o Kotlin aplica como tipos (ex.: `ResponseEntity.body` é
  `T?`). `-Xjsr305=strict` cobre bibliotecas que ainda usam JSR-305; não remova a flag. O plugin `kotlin-spring` já abre
  as classes anotadas pelo Spring: não escreva `open`.
- **Wrapper:** Gradle 9.7.1, com o `gradle-wrapper.jar` validado na CI. Atualize só com
  `./gradlew wrapper --gradle-version <x>` (e ADR). `.gitattributes` fixa LF em `gradlew` e CRLF em `*.bat`.
- **Módulo único:** `settings.gradle.kts` só define `rootProject.name`. Os módulos de negócio são pacotes, não
  subprojetos.
- `springBoot { buildInfo() }` expõe versão e horário do build em `/actuator/info`.

### 7.3 CI (GitHub Actions)

| Arquivo                                               | Gatilho                                        | O que faz                                                                                                                                                                                                                                             |
|-------------------------------------------------------|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.github/workflows/gradle.yml`                        | push e PR para `main`/`develop`, tags `v*.*.*` | JDK 25 + `./gradlew build --stacktrace` (`clean build` em `main` e tags); publica `app.jar`; publica relatórios de teste quando falha; em push, submete o grafo de dependências                                                                       |
| `.github/workflows/release.yml`                       | tags `v*.*.*`                                  | `./gradlew clean build` + GitHub Release com `app.jar`                                                                                                                                                                                                |
| `.github/workflows/dependency-review.yml`             | PR para `main`/`develop`                       | reprova dependência com vulnerabilidade `high` ou maior que o grafo do GitHub enxergue no PR. Para Gradle, o grafo só existe por submissão, e o `gradle.yml` só submete em push: dependências Gradle adicionadas no PR não são avaliadas por este job |
| `.github/dependabot.yml` (configuração, não workflow) | semanal, às segundas                           | Gradle: só as versões do catálogo (as do BOM não aparecem), com minor/patch agrupados e major em PR próprio; Actions: todas num único PR; imagem base do `Dockerfile`: um PR por atualização                                                          |

- `./gradlew build` local reproduz o job principal. A CI roda em Linux: atenção a maiúsculas em caminhos e a finais de
  linha.
- Ao editar workflows, mantenha `permissions: contents: read` no topo e eleve só no job que precisa. Fixe actions de
  terceiros por SHA com comentário `# vX.Y.Z`; as `actions/*` oficiais usam tag.

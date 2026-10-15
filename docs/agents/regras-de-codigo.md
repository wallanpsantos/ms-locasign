# Regras de Código e Padrões de Implementação

> **Origem:** Este documento detalha a seção 5 original do `AGENTS.md`. Em caso de conflito, as regras do núcleo do [
`AGENTS.md`](../../AGENTS.md) prevalecem.

## 1. Nomes, idioma e KDoc

- Siga o glossário da seção 4.1 do guia. Código, identificadores e schema SQL (`snake_case`; guia, §8.1) ficam em
  inglês. KDoc, logs, mensagens de exceção e documentação ficam em português.
- As roles e variáveis do modelo da PandaDoc (`Locatario`, `Imobiliaria`, `Locatario.CPF`...) não têm acento e precisam
  bater exatamente com o modelo (guia, §5).
- Todo tipo de topo (class, interface, object, enum) tem KDoc em pt-BR no formato abaixo. A convenção foi formalizada no
  **ADR-015** (`docs/adr/0015-convencao-de-kdoc.md`) e cobre todos os tipos do projeto. Mantenha a cobertura ao
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

## 2. Kotlin idiomático

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
  objects no use case, para que o `field` do erro aponte para o JSON. As exceções existentes estão documentadas em
  [`docs/agents/arquitetura.md`](arquitetura.md) §7.
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

## 3. Spring Boot 4 e Spring Framework 7

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
  elas, como em `CreateProviderDocument` (a idempotência desse padrão está na seção 4). `transactions.run { }` é
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
    - OpenAPI (springdoc 3.1.1, definido no catálogo): `@Tag`, `@Operation` e `@ApiResponse` ficam na interface
      `openapi/<Recurso>Api`, e as anotações MVC ficam no controller. Campos usam `@field:Schema`, e o receptor de
      webhook é `@Hidden`.
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

## 4. Consistência: agregado, outbox, idempotência e webhooks

- **Agregado `Contract`:** toda mudança de status passa por ele (`ContractTransitionPolicy`, R5, ADR-010). Nunca
  atualize status direto no banco. Em `contract`, persista sempre com `ContractPersister.save(contract)`, nunca com
  `contracts.save` direto, ou os eventos se perdem.
- **Outbox:** todo evento de domínio vai para `outbox_events` na mesma transação do agregado. Use cases nunca publicam
  no Kafka.
- **Idempotência (R6):** o use case acionado pelo consumidor chama
  `ProcessedMessagesPort.markProcessed(ConsumerGroups.X, eventId)` como primeira instrução do `transactions.run { }` que
  grava o efeito. Retorno `false` indica duplicata: saia sem efeito (`return@run`).
    - Em use case de duas transações, com a chamada à PandaDoc no meio (`CreateProviderDocument`, `SendContract`,
      `ArchiveSignedDocument`, `AnnulProviderDocument`), a primeira transação só consulta `isProcessed(...)` e o estado
      do agregado. Ela chama
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
  `@Scheduled`, sempre via `SignatureProviderPort`. Não crie chamadas síncronas novas; as exceções existentes estão em
  [`docs/agents/arquitetura.md`](arquitetura.md) §7. Operação nova da PandaDoc segue quatro passos:
    1. operação com tipos neutros em `SignatureProviderPort` (modelos em `ProviderModels.kt`);
    2. método em `PandaDocClient`;
    3. DTOs em `infra/pandadoc/dto` e conversões em `infra/pandadoc/mappers`;
    4. implementação em `PandaDocSignatureProviderAdapter`, dentro de `call("<operação>") { }`.

  O `call` aplica o `SlidingWindowRateLimiter` (janela de 1 minuto por operação, `PANDADOC_RATE_LIMIT_PER_MINUTE` = 8,
  espera máxima de 30 s) e retenta o HTTP 429 até 3 vezes (2 s, 4 s, 8 s). Nos consumidores, as demais falhas sobem para
  o `DefaultErrorHandler`, inclusive `Forbidden`/`Rejected` não capturadas e o `RateLimited` do limitador local depois
  dos 30 s. Nos jobs, a falha é registrada e a próxima execução tenta de novo (`ReconcileContracts` captura
  `ProviderException` por contrato).

## 5. Segurança e dados pessoais

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

## 6. Migrations (Flyway)

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

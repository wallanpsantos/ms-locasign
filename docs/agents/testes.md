# Estratégia de Testes Automatizados e Convenções

> **Origem:** Este documento detalha a seção 6 original do `AGENTS.md`. Em caso de conflito, as regras do núcleo do [
`AGENTS.md`](../../AGENTS.md) prevalecem.

A estratégia oficial está no guia (§15) e no ADR-014. A suíte de testes do projeto conta com testes automatizados
cobrindo todas as camadas (domínio, use cases, slices web, persistência, mensageria, integração ponta a ponta e
arquitetura hexagonal). Quem escreve novos testes deve seguir rigorosamente as convenções e padrões consolidados
abaixo.

## 1. Stack de testes (BOM do Boot 4.1 + ADR-014)

Confira a resolução efetiva com `./gradlew dependencies --configuration testRuntimeClasspath`.

| Biblioteca                                                                                  | Origem        | Uso no projeto                                                                                  |
|---------------------------------------------------------------------------------------------|---------------|-------------------------------------------------------------------------------------------------|
| JUnit Jupiter 6 + `junit-jupiter-params`                                                    | BOM do Boot   | Motor de testes e testes parametrizados (`@ParameterizedTest`, `@EnumSource`)                   |
| `kotlin-test-junit5`                                                                        | Plugin Kotlin | Asserções Kotlin (`assertEquals`, `assertIs`, `assertFailsWith`, `assertNotNull`)               |
| AssertJ e Awaitility                                                                        | BOM do Boot   | Asserções fluentes (base do `MockMvcTester`) e espera assíncrona (`await().untilAsserted`)      |
| Mockito 5 (`mockito-core`, `-junit-jupiter`)                                                | BOM do Boot   | Mocking pontual em slices Spring; helper `any()` em `br.com.locasign.support.MockitoSupport.kt` |
| Spring Test 7                                                                               | BOM do Boot   | `MockMvcTester`, `@MockitoBean`, `@TestBean`, `@Sql`                                            |
| Testcontainers 2 (`-junit-jupiter`, `-postgresql`, `-kafka`) + `spring-boot-testcontainers` | BOM do Boot   | PostgreSQL 18.6 e Kafka 4.3.1 reais com `@ServiceConnection`                                    |
| `spring-kafka-test`                                                                         | BOM do Boot   | Utilitários de teste para Kafka                                                                 |
| WireMock (`wiremock-standalone:3.13.2`)                                                     | ADR-014       | Servidor HTTP mock para simulação determinística da API PandaDoc                                |
| ArchUnit (`archunit-junit5:1.5.1`)                                                          | ADR-014       | Validação estática de regras arquiteturais hexagonais e fronteiras de pacotes                   |

**Fora do build:** MockK, Konsist e `mockito-kotlin` (que estão fora do BOM e fora do build), além dos bancos
embarcados H2 e HSQLDB. Adicionar qualquer dependência nova exige ADR formal. Persistência relacional é testada
exclusivamente contra PostgreSQL real via Testcontainers.

## 2. Imports corretos no Boot 4 e no Testcontainers 2

Não utilize anotações ou classes legadas do Boot 3 nem do Testcontainers 1.x:

| Uso                                                          | Pacote                                                                                                                       |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `@WebMvcTest`, `@AutoConfigureMockMvc`                       | `org.springframework.boot.webmvc.test.autoconfigure`                                                                         |
| `@DataJdbcTest`                                              | `org.springframework.boot.data.jdbc.test.autoconfigure`                                                                      |
| `@SpringBootTest`, `@TestConfiguration`                      | `org.springframework.boot.test.context`                                                                                      |
| `@ServiceConnection`                                         | `org.springframework.boot.testcontainers.service.connection`                                                                 |
| `@MockitoBean`, `@MockitoSpyBean` (`@MockBean` foi removido) | `org.springframework.test.context.bean.override.mockito`                                                                     |
| `@TestBean`                                                  | `org.springframework.test.context.bean.override.convention`                                                                  |
| `MockMvcTester`                                              | `org.springframework.test.web.servlet.assertj`                                                                               |
| `PostgreSQLContainer` (não genérico)                         | `org.testcontainers.postgresql` (o de `org.testcontainers.containers` está deprecated)                                       |
| `KafkaContainer`                                             | `org.testcontainers.kafka` (o de `org.testcontainers.containers` está deprecated e não tem `@ServiceConnection` no Boot 4.1) |
| `@Testcontainers`, `@Container`                              | `org.testcontainers.junit.jupiter`                                                                                           |
| `WireMockServer`                                             | `com.github.tomakehurst.wiremock.WireMockServer`                                                                             |
| `@AnalyzeClasses`, `@ArchTest`                               | `com.tngtech.archunit.junit`                                                                                                 |

## 3. O que testar em cada camada

| Camada                                                   | Como                                                                                                                                                                                                                                                                                                                                                                                     | Docker |
|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| `domain`                                                 | JUnit + `kotlin.test`, sem Spring e sem mocks. Matriz de `ContractStatus` com `@ParameterizedTest`/`@EnumSource`. `Contract.restore(...)` monta o agregado em qualquer status; verifique `pullEvents()` e `pullHistory()`.                                                                                                                                                               | não    |
| `app` (use cases)                                        | Instancie o use case direto, com fakes em memória das portas, um `TransactionRunner` imediato (`ImmediateTransactionRunner`, ADR-011) e um `BusinessClock` fixo (`FixedBusinessClock`). Sem Spring e sem Mockito. Fakes não fazem rollback: atomicidade transacional é comprovada nos testes de integração (`*IT`).                                                                      | não    |
| `interfaces/web`                                         | `@WebMvcTest(XController::class)` + `MockMvcTester`. Forneça os use cases com `@MockitoBean` ou `@TestBean`. O slice carrega o `CorrelationIdFilter`, que exige `CorrelationContext`: use `@Import(MdcCorrelationContext::class)`. Importe ou mocke o `OperatorAccessGuard` quando o controller exigir token de operador.                                                                | não    |
| `infra/pandadoc`                                         | Teste com WireMock (`PandaDocSignatureProviderAdapterTest`): suba `WireMockServer(wireMockConfig().dynamicPort())`, construa o `RestClient` com `BufferingClientHttpRequestFactory` e instancie o cliente via `HttpServiceProxyFactory`. Injete `sleeper` e `nanoTime` falsos no `SlidingWindowRateLimiter` para testar cenários de rate limiting (429) e retentativas sem pausas reais. | não    |
| `infra/persistence` e migrations                         | Teste herda de `IntegrationTestSupport` ou usa `@DataJdbcTest` com PostgreSQL real via `@ServiceConnection`; o slice executa o Flyway e valida queries e mapeamentos. Adapters que pedem serialização JSON completa (ex.: outbox) rodam em `@SpringBootTest`.                                                                                                                            | sim    |
| webhook ponta a ponta, outbox, relay, consumidores e DLT | Herde de `IntegrationTestSupport` (`@SpringBootTest` com PostgreSQL e Kafka reais). Valide efeitos assíncronos com `Awaitility.await().atMost(...).untilAsserted { }`.                                                                                                                                                                                                                   | sim    |
| fluxo E2E completo                                       | `LocaSignE2EIT`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` com PostgreSQL e Kafka via Testcontainers e WireMock apontado como endpoint da PandaDoc via `@DynamicPropertySource`.                                                                                                                                                                                                   | sim    |
| arquitetura e fronteiras                                 | `HexagonalArchitectureTest`: regras estáticas ArchUnit garantindo isolamento entre camadas, pacotes e dependências de infraestrutura.                                                                                                                                                                                                                                                    | não    |

## 4. Localização e nomes (ADR-014)

- **Espelhamento:** `src/test/kotlin` espelha rigorosamente a estrutura de pacotes de `src/main/kotlin`. Exemplo:
  `src/test/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicyTest.kt`.
- **Sufixos:**
    - `*Test`: testes sem dependência de Docker (execução rápida: unitários, slices web, testes de adapter com WireMock
      e regras ArchUnit).
    - `*IT`: testes de integração que exigem Testcontainers (PostgreSQL 18.6 e Kafka 4.3.1).
      A task `./gradlew test` executa ambos. O filtro `./gradlew test --tests "*Test"` executa apenas os rápidos.
- **Nomenclatura de métodos:** frases descritivas em português entre crases, referenciando regras de negócio ou
  decisões:
  ``fun `estado final é imutável (R5)`()``. Na JVM, crases não aceitam caracteres como `.` `:` `;` `/` `<` `>` `[` `]`
  ou `\`:
  escreva "de SENT para COMPLETED", e não "SENT -> COMPLETED".
- **Fakes em memória:** localizados em `src/test/kotlin/br/com/locasign/<módulo>/app/fakes/` (específicos do módulo) e
  `src/test/kotlin/br/com/locasign/shared/app/fakes/` (transversais). Prefixos padrão: `InMemory*` (armazena estado em
  memória),
  `Fixed*` (retorna valor predefinido), `NoOp*` (ignora chamadas) e `Immediate*` (executa blocos imediatamente sem
  efeito colateral).
- **Suporte de testes:** utilitários centralizados em `src/test/kotlin/br/com/locasign/support/`:
    - `IntegrationTestSupport.kt`: classe base para testes de integração com Spring Boot e containers.
    - `TestcontainersSupport.kt`: configuração reutilizável de containers PostgreSQL e Kafka.
    - `MockitoSupport.kt`: função utilitária `any()` para matchers tipados com verificação de não-nulidade do Kotlin.
- **Perfil de testes:** configurações em `src/test/resources/application-test.yaml`, ativado via
  `@ActiveProfiles("test")`.
  Nunca crie `src/test/resources/application.yaml`, pois ele substituiria integralmente a configuração principal no
  classpath.
- **Fuso horário:** `build.gradle.kts` fixa `systemProperty("user.timezone", "UTC")` nas tarefas de teste para garantir
  reprodutibilidade independente do fuso horário da máquina hospedeira.

## 5. Cobertura prioritária (R1 a R10)

Toda alteração que toque regras de negócio deve ser acompanhada do teste automatizado correspondente:

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

## 6. Integração: regras e armadilhas

- **Containers via `@ServiceConnection`:** nunca aponte testes para as portas `5432` ou `9092` do `compose.yml` local.
  O container PostgreSQL usa a imagem `postgres:18.6` e o container Kafka usa `apache/kafka:4.3.1`, ambos centralizados
  em `TestcontainersSupport`.
- **Reaproveitamento de contexto Spring:** para acelerar a suíte de testes de integração, herde de
  `IntegrationTestSupport`.
  A classe base fornece o contexto `@SpringBootTest`, o perfil `test` e a substituição do bean `signatureProvider` pelo
  fake `FixedSignatureProvider`. Evite introduzir variações isoladas de `@MockitoBean` ou `@TestBean` em subclasses,
  pois
  cada variação força o Spring TestContext Framework a inicializar um novo ApplicationContext.
- **Isolamento de dados e rollback:** `@DataJdbcTest` executa sob `@Transactional` com rollback automático. No entanto,
  testes de mensageria, outbox relay e consumers assíncronos rodam sob `@SpringBootTest` (sem rollback automático):
  limpe
  as tabelas afetadas nos métodos `@BeforeEach` quando necessário.
- **Configuração padrão do perfil `test` (`application-test.yaml`):**
    - Jobs agendados desativados (`locasign.jobs.*.enabled=false`).
    - Intervalo do `OutboxRelay` reduzido (`locasign.outbox.relay-interval=200ms`).
    - Chave de assinatura HMAC configurada (`locasign.pandadoc.webhook-shared-key=e2e-webhook-secret-key-32bytes-len`).
    - Diretório de arquivamento apontado para temporário
      (`locasign.archive.directory=${java.io.tmpdir}/locasign-test-archive`).
- **Dead Letter Queue (DLT):** o `DefaultErrorHandler` efetua 1 entrega inicial mais 3 retentativas com backoff (1s, 2s,
  4s).
  Ao testar mensagens venenosas ou [UnreadableMessageException], aguarde a conclusão das tentativas via Awaitility antes
  de consumir o tópico `.dlt`.
- **Proibição de pular testes sem Docker:** não utilize anotações como `@Testcontainers(disabledWithoutDocker = true)`.
  Sem Docker, os testes de integração devem falhar explicitamente para evitar falsos positivos na esteira de CI.

## 7. O que não fazer em testes

- **Não adicione dependências de teste sem ADR formal:** MockK, Konsist e frameworks adicionais permanecem fora do
  build.
- **Não introduza Spring nem Mockito em testes de `domain` e `app`:** use instanciação pura e fakes em memória.
- **Não utilize `Thread.sleep`:** utilize `Awaitility.await().untilAsserted { ... }` para testes assíncronos e passe
  funções/lambdas falsas de `sleeper` e `nanoTime` em componentes com temporização ou rate limiting.
- **Não use `!!` (force unwrap) nem dados reais de produção:** utilize geradores e validadores dos próprios value
  objects.
- **Não desative (`@Disabled`) nem afrouxe testes:** se um teste falhar, investigue e corrija a causa raiz.

## 8. Arquivos de referência da suíte existente

Em vez de copiar trechos de código estáticos para esta documentação, consulte diretamente os arquivos da suíte
automatizada existente no repositório (`src/test/kotlin/br/com/locasign/`):

- **Domínio puro com testes parametrizados:**
  `src/test/kotlin/br/com/locasign/contract/domain/services/ContractTransitionPolicyTest.kt`
- **Use case com fakes em memória e sem Spring:**
  `src/test/kotlin/br/com/locasign/contract/app/usecases/RequestContractTest.kt`
- **Implementações de fakes e runners imediatos:**
  `src/test/kotlin/br/com/locasign/shared/app/fakes/SharedFakes.kt` e
  `src/test/kotlin/br/com/locasign/contract/app/fakes/ContractFakes.kt`
- **Infraestrutura centralizada de Testcontainers:**
  `src/test/kotlin/br/com/locasign/support/TestcontainersSupport.kt`
- **Classe base de integração com perfil `test` e fake de assinatura:**
  `src/test/kotlin/br/com/locasign/support/IntegrationTestSupport.kt`
- **Validação de subida da base e banco relacional:**
  `src/test/kotlin/br/com/locasign/support/IntegrationTestSupportIT.kt`
- **Adapter HTTP com WireMock (sem `MockRestServiceServer`):**
  `src/test/kotlin/br/com/locasign/contract/infra/pandadoc/adapters/PandaDocSignatureProviderAdapterTest.kt`
- **Fluxo ponta a ponta E2E com Testcontainers e WireMock:**
  `src/test/kotlin/br/com/locasign/LocaSignE2EIT.kt`
- **Regras estáticas de arquitetura hexagonal com ArchUnit:**
  `src/test/kotlin/br/com/locasign/architecture/HexagonalArchitectureTest.kt`

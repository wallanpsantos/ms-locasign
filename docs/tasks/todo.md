# Checklist de Implementação de Testes

- [x] **Fase 1: Governança, ADR-014 e Dependências**
    - [x] Redigir `docs/adr/0014-estrategia-e-ferramentas-de-testes.md` com status `proposta`.
    - [x] Atualizar `gradle/libs.versions.toml` com `wiremock = "3.13.2"` e `archunit = "1.5.1"`.
    - [x] Atualizar `build.gradle.kts` com as dependências e `systemProperty("user.timezone", "UTC")`.
    - [x] Validar resolução das dependências com `./gradlew dependencyInsight`.

- [x] **Fase 2: Fakes Compartilhados e Infraestrutura de Teste**
    - [x] Implementar fakes de aplicação em `br.com.locasign.shared.app.fakes` (`ImmediateTransactionRunner`,
      `FixedBusinessClock`, `InMemoryProcessedMessages`, `InMemoryOutboxPort`).
    - [x] Implementar fakes de contratos e locações em `br.com.locasign.contract.app.fakes` e
      `br.com.locasign.lease.app.fakes`.
    - [x] Implementar `@TestConfiguration` em `br.com.locasign.support.TestcontainersSupport` para PostgreSQL 18.6 e
      Kafka 4.3.1.

- [x] **Fase 3: Testes Unitários de Domínio e Aplicação (Sem Spring, Sem Docker)**
    - [x] Implementar `ContractTransitionPolicyTest` (R5, ADR-010).
    - [x] Implementar testes de value objects: `CpfTest`, `EmailTest`, `MoneyTest`, `LeaseTermTest`.
    - [x] Implementar `ContractTest` (agregado, R3, R4).
    - [x] Implementar `LeaseTest` (modelo de locação, R2).
    - [x] Implementar testes de use cases: `RegisterLeaseTest`, `RequestContractTest` (R1, R9),
      `RunPostSignatureActionsTest` (R8).
    - [x] Executar `./gradlew test --tests "*Test"` e validar sucesso (57 testes passando).

- [x] **Fase 4: Testes de Fatias Web (`@WebMvcTest`)**
    - [x] Implementar `LeaseControllerTest` com `MockMvcTester` (validação DTO, 400 `/problems/validation`, 201
      Created).
    - [x] Implementar `ContractControllerTest` com `MockMvcTester` (cancelamento, 409
      `/problems/active-contract-exists`, 404).
    - [x] Implementar `PandaDocWebhookControllerTest` (HMAC válido -> 200, HMAC inválido/ausente -> 401).
    - [x] Executar `./gradlew test --tests "*Test"` e validar sucesso (69 testes passando).

- [x] **Fase 5: Testes de API Externa com WireMock**
    - [x] Implementar `PandaDocSignatureProviderAdapterTest` utilizando `WireMockServer` (upload, polling de status,
      envio, rate limit 429, download protegido).
    - [x] Executar `./gradlew test --tests "*PandaDocSignatureProviderAdapterTest"` e validar sucesso.

- [x] **Fase 6: Testes de Arquitetura com ArchUnit**
    - [x] Implementar `HexagonalArchitectureTest` com ArchUnit cobrindo as 10 regras de fronteiras do `AGENTS.md` §4.6.
    - [x] Executar `./gradlew test --tests "*HexagonalArchitectureTest"` e validar conformidade arquitetural.

- [x] **Fase 7: Testes de Integração com Testcontainers (`*IT`)**
    - [x] Implementar `JdbcProcessedMessagesAdapterIT` (R6).
    - [x] Implementar `JdbcOutboxAdapterIT` (outbox, atomicidade transacional).
    - [x] Implementar `LeaseRepositoryAdapterIT` e `ContractRepositoryAdapterIT` (PostgreSQL 18.6 real, constraints e
      índices).
    - [x] Executar testes de integração com Testcontainers.

- [x] **Fase 8: Testes End-to-End (E2E)**
    - [x] Implementar `LocaSignE2EIT` cobrindo o ciclo de vida completo assíncrono: cadastro de locação -> emissão de
      contrato -> publicação Kafka via relay -> webhook PandaDoc -> pós-assinatura.
    - [x] Executar testes E2E com sucesso.

- [x] **Fase 9: Auditoria Final e Verificação**
    - [x] Verificar ausência de warnings em `compileKotlin`.
    - [x] Verificar checagem de fronteiras da seção 4.6.
    - [x] Inspecionar `git status` e `git diff` (sem commits).

# Plano de Implementação da Suíte de Testes Automatizados

Iniciativa de implementação completa da pirâmide e esteira de testes do LocaSign, cobrindo Testes Unitários (domínio,
aplicação, fatias web), Testes de Integração com Testcontainers (PostgreSQL 18.6 e Kafka 4.3.1 em KRaft), Testes de API
Externa com WireMock (PandaDoc client/adapter), Testes E2E (fluxo ponta a ponta assíncrono via outbox/Kafka/webhooks) e
Testes de Arquitetura com ArchUnit (verificação das fronteiras hexagonais).

## 1. Escopo e Objetivos

1. **Governança & ADRs:**
    - Registrar ADR-014 (proposta) definindo a convenção de testes (`*Test` e `*IT`), inclusão de WireMock e ArchUnit no
      version catalog (`gradle/libs.versions.toml`) e adoção de `apache/kafka:4.3.1` (KRaft) em paridade com o Compose
      local.
2. **Dependências & Build:**
    - Adicionar `wiremock = "3.13.2"` e `archunit = "1.5.1"` ao version catalog.
    - Declarar `testImplementation(libs.wiremock)` e `testImplementation(libs.archunit.junit5)` e
      `org.awaitility:awaitility` em `build.gradle.kts`.
    - Fixar `systemProperty("user.timezone", "UTC")` em `tasks.withType<Test>`.
3. **Fakes de Teste & Suporte (`shared/app/fakes`, `contract/app/fakes`, `lease/app/fakes`, `support/`):**
    - Implementar fakes em memória em conformidade com as portas (`ImmediateTransactionRunner`, `FixedBusinessClock`,
      `InMemoryProcessedMessages`, `InMemoryOutboxPort`, `InMemoryContractRepository`, `InMemoryLeaseRepository`,
      `FakeSignatureProvider`).
    - Implementar `@TestConfiguration` reutilizável em `br.com.locasign.support` para `@ServiceConnection` do PostgreSQL
      18.6 e Kafka 4.3.1.
4. **Testes Unitários:**
    - **Domínio:**
        - `ContractTransitionPolicyTest`: matriz completa de transições de status (R5, ADR-010: imutabilidade de estados
          finais, rejeição de regressão, salto permitido, idempotência de mesmo estado).
        - `ValueObjectsTest`: validações e mascaramento de `Cpf`, `Email` (R10), `Money` e limites de `LeaseTerm`
          (1..120 meses, R2).
        - `ContractTest`: agregado `Contract`, validação de ordenação de signatários (locatário antes da imobiliária,
          R3), aplicação de eventos, geração de histórico, cálculo de expiração (R4).
        - `LeaseTest`: invariantes de criação de `Lease` (R2: data de início não retroativa a `today`, campos
          obrigatórios).
    - **Aplicação (Use Cases com Fakes):**
        - `RegisterLeaseTest` e `ActivateLeaseTest`.
        - `RequestContractTest`: unicidade de contrato ativo (R1: `ActiveContractExists`), versionamento incremental
          após estados terminais de falha (R9).
        - `ApplyContractEventTest`, `ProcessProviderWebhookItemTest` e `RunPostSignatureActionsTest`: idempotência (R6),
          guarda de execução pós-assinatura apenas para `COMPLETED` (R8).
    - **Web / Fatias MVC (`@WebMvcTest`):**
        - `LeaseControllerTest`: validação de request DTOs (`@field:NotNull`, `@field:ValidCpf`, `@field:PositiveMoney`)
          gerando 400 `/problems/validation` e respostas esperadas.
        - `ContractControllerTest`: endpoints REST de cancelamento e consultas, tratamento de erros 409 e 404.
        - `PandaDocWebhookControllerTest`: validação do HMAC-SHA256 (rejeição com 401 para assinatura inválida ou
          ausente, 200 com preservação íntegra do corpo bruto).
5. **Testes de API Externa com WireMock:**
    - `PandaDocSignatureProviderAdapterTest`: simulação do servidor HTTP PandaDoc via WireMock.
        - Criação de documento (upload multipart -> status `uploaded` -> polling `draft`).
        - Envio de documento (`POST /documents/{id}/send`).
        - Tratamento de rate limiting (HTTP 429) com retry e backoff.
        - Tratamento de erros HTTP 403, 404 (antes do draft virando `ProviderException.NotReady`) e 500.
        - Download de PDF assinado (`GET /documents/{id}/download-protected`).
6. **Testes de Integração com Testcontainers (`*IT`):**
    - `JdbcProcessedMessagesAdapterIT`: idempotência a nível de banco relacional PostgreSQL 18.6 real (R6).
    - `JdbcOutboxAdapterIT`: persistência e atomicidade transacional do outbox (R6, ADR-011).
    - `LeaseRepositoryAdapterIT` e `ContractRepositoryAdapterIT`: persistência das entidades relacionais e mapeamento de
      constraints únicas para exceções de domínio (ex: `ux_contracts_one_active_per_lease` virando
      `ActiveContractExists`).
7. **Testes End-to-End (E2E):**
    - `LocaSignE2EIT`: fluxo completo em `@SpringBootTest` com PostgreSQL e Kafka reais via Testcontainers.
        - Registro de locação (`POST /api/v1/leases`).
        - Emissão de contrato (`POST /api/v1/contracts`).
        - Despacho pelo `OutboxRelay` para tópico Kafka `locasign.contract.events.v1`.
        - Recebimento de webhook simulado assinado com HMAC válido.
        - Atualização de status e acionamento de pós-assinatura.
8. **Testes de Arquitetura (ArchUnit):**
    - `HexagonalArchitectureTest`: verificação estrita das 10 regras de fronteiras arquiteturais definidas no
      `AGENTS.md` §4.6.

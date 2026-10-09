# ADR-014: estratégia de testes, convenção de localização e inclusão de WireMock e ArchUnit

- **Status:** proposta
- **Relação com o guia:** complementa a seção 15 ("Estratégia de testes") e formaliza a seção 6 do `AGENTS.md`

## Contexto

O guia técnico (§15) estabelece uma estratégia de testes em múltiplos níveis (domínio, aplicação, adapters de
integração, webhooks, persistência/Kafka com Testcontainers e arquitetura com ArchUnit/Konsist). No entanto:

1. O projeto não possuía a suíte de testes inicial em `src/test/`.
2. As bibliotecas WireMock (para simulação da API HTTP da PandaDoc) e ArchUnit (para garantia automatizada das
   fronteiras hexagonais) constavam no guia, mas estavam fora do catálogo de dependências e do build.
3. A convenção de nomenclatura e separação (`*Test` para testes unitários/rápidos sem Docker e `*IT` para testes de
   integração com Testcontainers) precisava ser formalizada.
4. Para testes com Kafka via Testcontainers, o guia indicava `apache/kafka-native:4.3.1`, enquanto o ambiente de
   desenvolvimento e o `compose.yml` utilizam `apache/kafka:4.3.1` (KRaft padrão, imagem já presente localmente).

## Decisão

1. **Dependências de teste:**
    - Adicionar `wiremock = "3.13.2"` em `gradle/libs.versions.toml` (`org.wiremock:wiremock`).
    - Adicionar `archunit = "1.5.1"` em `gradle/libs.versions.toml` (`com.tngtech.archunit:archunit-junit5`).
    - Adicionar `org.awaitility:awaitility` (gerenciado pelo BOM do Spring Boot 4.1.1) em `build.gradle.kts` para
      asserções assíncronas.
2. **Convenção de localização e nomenclatura:**
    - Pacotes em `src/test/kotlin` espelham estritamente os pacotes de `src/main/kotlin`.
    - Sufixo `*Test` para testes unitários de domínio, aplicação, fatias web (`@WebMvcTest`), WireMock e arquitetura
      (ArchUnit), que executam rapidamente e não dependem de Docker.
    - Sufixo `*IT` para testes de integração e ponta a ponta que exigem contêineres reais via Testcontainers.
    - Nomes de métodos de teste como frases em português entre crases, citando as regras de negócio correspondentes (ex:
      ``fun `estado final é imutável (R5)`()``).
3. **Containers de teste e fuso:**
    - Usar `postgres:18.6` e `apache/kafka:4.3.1` via `@ServiceConnection` em uma `@TestConfiguration` reutilizável em
      `br.com.locasign.support.TestcontainersSupport`.
    - Configurar `systemProperty("user.timezone", "UTC")` nas tarefas de teste no `build.gradle.kts`.

## Consequências

- O ciclo de feedback rápido de desenvolvimento local pode ser executado via `./gradlew test --tests "*Test"` sem
  necessidade de inicializar contêineres Docker.
- A conformidade com a arquitetura hexagonal e as regras de importação do `AGENTS.md` (§4.6) passa a ser verificada de
  forma contínua e determinística via ArchUnit.
- A integração com a API externa da PandaDoc é testada em profundidade (incluindo retentativas 429 e fluxos de erro) sem
  acionar chamadas reais ao sandbox externo.
- A paridade entre os testes de integração e o ambiente de contêineres local (`compose.yml`) é mantida para PostgreSQL e
  Apache Kafka.

# LocaSign: Diretrizes Universais para Agentes de IA

Serviço backend que automatiza o ciclo de vida de contratos de locação residencial: gera o contrato, envia para
assinatura eletrônica na PandaDoc, acompanha o ciclo por webhooks e dispara as ações pós-assinatura.

> **Propósito do repositório:** projeto de estudo de engenharia de software pragmática com Kotlin, Spring Boot,
> PostgreSQL, Apache Kafka e Docker.  
> **Stack fixada (fontes: `gradle/libs.versions.toml`, `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`,
`compose.yml`):** Kotlin
> 2.4.21, Java 25 (toolchain), Spring Boot 4.1.1 (Spring Framework 7, Jackson 3), Gradle 9.8.1, PostgreSQL 18.6 e Apache
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
    - **ADR-014:** estratégia de testes automatizados, convenção de localização e inclusão de WireMock e ArchUnit
      (`proposta`).
    - **ADR-015:** convenção estruturada de documentação técnica de classes com KDoc (`proposta`).
4. **Documentos de detalhamento em `docs/agents/`:**
    - [`divergencias-do-guia.md`](docs/agents/divergencias-do-guia.md): divergências catalogadas entre o guia técnico e
      o repositório.
    - [`arquitetura.md`](docs/agents/arquitetura.md): camadas, árvore de pacotes, pontes, fronteira PandaDoc, checklist
      de use cases e exceções.
    - [`regras-de-codigo.md`](docs/agents/regras-de-codigo.md): nomes, convenção de KDoc, Kotlin idiomático, Spring Boot
      4/Spring 7, consistência, segurança e Flyway.
    - [`testes.md`](docs/agents/testes.md): pirâmide de testes (234 testes), classpath/BOM, Testcontainers, WireMock,
      ArchUnit, `IntegrationTestSupport`.
    - [`build-e-ci.md`](docs/agents/build-e-ci.md): governança de dependências, contratos de build (`app.jar`),
      workflows de CI e Dependabot.

Em caso de conflito: R1–R10 > ADRs > regras do núcleo do `AGENTS.md` > documentos de detalhe em `docs/agents/` > guia
(guia, §0).
Siga sempre o repositório quando divergir do guia técnico (detalhes em [
`docs/agents/divergencias-do-guia.md`](docs/agents/divergencias-do-guia.md)).
Toda decisão técnica relevante que mude o guia exige ADR. Não altere o código só para casar com um trecho desatualizado
do guia.

### 1.1 Divergências conhecidas entre o guia e o repositório

Siga o repositório e não crie arquivos só para casar com o guia. O detalhamento completo das divergências conhecidas
está documentado em [`docs/agents/divergencias-do-guia.md`](docs/agents/divergencias-do-guia.md).

---

## 2. Comandos

Pré-requisitos: JDK 25 instalado (o build não provisiona toolchain) e Docker em execução. Agentes acrescentam
`--console=plain` a todo comando Gradle (ex.: `./gradlew build --console=plain`). No PowerShell, use `.\gradlew.bat` e
aspas duplas (`--args="..."`, `--tests "..."`); no Git Bash, use `./gradlew`.

```bash
# Build e testes
./gradlew build                  # gate de pronto e da CI: compila sem warnings, roda todos os testes e gera build/libs/app.jar
./gradlew compileKotlin          # checagem rápida do código de produção (compila sem warnings)
./gradlew test                   # todos os testes; os *IT sobem PostgreSQL 18.6 e Kafka 4.3.1 via Testcontainers (exige Docker)
./gradlew test --tests "*Test"   # só os testes que não usam Docker (unitários, web slices, wiremock e archunit)
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

- **Atenção aos filtros de teste:** no Gradle, um `--tests` sem correspondência passa com sucesso. Confira sempre na
  saída o número de testes efetivamente executados.
- Relatório de testes: `build/reports/tests/test/index.html` (XML em `build/test-results/test/`).
- `docker compose down -v` apaga banco, tópicos e PDFs arquivados. Rode só a pedido explícito.
- A CI não constrói a imagem Docker. Ao mexer no `Dockerfile` ou no build, valide com
  `docker compose --profile app build`.
- Webhook assinado sem a PandaDoc (Git Bash, com `openssl` e `curl`):
  `PANDADOC_WEBHOOK_SHARED_KEY=segredo bash scripts/send-signed-webhook.sh http/samples/document-state-changed.json`. A
  app precisa estar rodando com a mesma chave. Repita com `DELIVERY_ID=<fixo>` para testar a deduplicação. O roteiro de
  demonstração está em `http/locasign.http`.
- **Buscas no código:** caso a ferramenta `rg` (ripgrep) não esteja disponível no PATH do terminal/PowerShell, utilize
  `git grep -n -E "<padrao>"` a partir da raiz do repositório.

---

## 3. Modo de trabalho do agente

- **Objetivo didático:** antes de gerar arquivos grandes ou refatorar, descreva o plano em poucas linhas. Ao concluir
  cada etapa, resuma os conceitos de Kotlin, Spring, Kafka ou PostgreSQL empregados.
- **Definição de pronto:**
    1. em funcionalidade nova ou correção de bug, o teste novo falhava antes da mudança e passa depois; em
       refatoração, os testes existentes continuam verdes;
    2. `./gradlew build` está verde com Docker ligado e a suíte de testes executa com 100% de aprovação;
    3. `./gradlew compileKotlin` não emite warnings novos;
    4. a checagem automatizada de arquitetura (`HexagonalArchitectureTest`) e a verificação de fronteiras passam com
       sucesso.

  Mudança só em documentação dispensa os itens 1 e 2.
- **Git:** não execute `git commit` nem `git push` por conta própria; esta regra prevalece sobre a seção 17 do guia.
  Deixe as mudanças no workspace para revisão humana (`git status`, `git diff`). Se o usuário pedir commit, faça um por
  capacidade, com mensagem em pt-BR no padrão do histórico: `<gitmoji> <tipo>[(<escopo>)]: <descrição no presente>`, com
  escopo opcional (ex.: `🐛 fix(ci): corrige caminho do jar`, `📚 docs: atualiza guias do projeto`). Nunca inclua
  `Co-Authored-By` nem atribuição a IA em commits ou PRs. Tags e releases são ação humana.
- **Dependências e versões:** regras em [`docs/agents/build-e-ci.md`](docs/agents/build-e-ci.md). Mudança feita pelo
  agente em dependência, JDK, wrapper ou imagem
  exige ADR.
- **ADRs:** toda decisão técnica relevante que desvie do guia ou o expanda (guia, §0) vira
  `docs/adr/NNNN-titulo-em-kebab-case.md`. O número tem quatro dígitos e é o maior existente em `docs/adr/` mais um.
  Siga o modelo do ADR-011: título `# ADR-0NN: ...`, linhas `- **Status:**` e `- **Relação com o guia:**`, e seções
  Contexto, Decisão e Consequências. ADR criado por agente nasce com status `proposta`; aceitá-lo é decisão humana. O
  ADR-013 (tabela de ajustes pequenos) já está `aceita`: só acrescente linha a ele com confirmação do usuário.
- **Planos em `docs/tasks/`:** `plan.md` guarda o plano e as decisões da iniciativa, e `todo.md` o checklist com
  critérios de aceite, onde o progresso é marcado (`[x]`). Ao executar uma tarefa de lá, respeite a ordem e as
  dependências e rode a verificação indicada. Pergunte antes de sobrescrever esses arquivos com outra iniciativa.
- **Variáveis de ambiente:** não crie nem edite arquivos `.env*`. A referência é a tabela "Configuração" do `README.md`,
  junto com os placeholders `${VAR:padrão}` do `application.yaml` (detalhes em [
  `docs/agents/regras-de-codigo.md`](docs/agents/regras-de-codigo.md)).

---

## 4. Princípios Fundamentais

Diretrizes de engenharia aplicadas de forma idiomática em Kotlin 2.4+ e Spring Boot 4.1.1+:

| Princípio          | Aplicação idiomática no LocaSign                                                                                                                                                                                                                            | Limite explícito                                                                                                                  |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| **SRP**            | Um use case por arquivo com `execute` (`RequestContract`); controllers apenas adaptam HTTP; `ContractTransitionPolicy` centraliza regras de transição; mapeamentos em `mappers/`.                                                                           | Não fragmentar classes coesas ou value objects só para reduzir linhas.                                                            |
| **OCP**            | Extensão por novas portas (`SignatureProviderPort`), sem alterar use cases. Classes `final` por padrão (sem `open` especulativo). `sealed` com `when` exaustivo força tratamento explícito (`ApiExceptionHandler.handleDomain`, `ContractEvent.toPayload`). | Não criar hierarquias ou padrões Strategy especulativos para casos únicos.                                                        |
| **LSP**            | Fakes e adapters cumprem estritamente o contrato da porta: `markProcessed` devolve `false` em duplicata; ausência é `T?` (nunca exceção nem `Optional`); `ImmediateTransactionRunner` executa sem rollback (atomicidade se prova em `*IT`).                 | O contrato expresso no KDoc da porta é soberano; implementação que o quebra é bug.                                                |
| **ISP**            | Portas pequenas e orientadas ao consumidor: segregação entre `LeaseLookupPort` e `LeaseActivationPort`, ou `SignatureProviderPort` e `ProviderWebhookGateway`.                                                                                              | Não criar portas genéricas nem interfaces de método único sem consumidores independentes reais.                                   |
| **DIP**            | Camada `app` declara portas em `app/ports/out`; `infra` implementa. Composição explícita via `@Configuration(proxyBeanMethods = false)` em `<Módulo>BeansConfig`. Verificado por `HexagonalArchitectureTest`.                                               | Sem `ports/in`: controllers injetam a classe concreta do use case, simplificando o design.                                        |
| **DRY**            | Centralização de conhecimento canônico: catálogo de tópicos em `Topics`, grupos em `ConsumerGroups`, métricas em `Metrics`, propriedades em `LocaSignProperties` e persistência em `ContractPersister.save`.                                                | Não compartilhar entidades entre módulos (`LeaseSnapshot` desacopla módulos de propósito). Testes toleram repetição para clareza. |
| **KISS**           | Transações explícitas via `TransactionRunner.run { }` em vez de AOP/decorators; conversões simples por extension functions em vez de MapStruct; consultas com `JdbcClient` e SQL explícito.                                                                 | Simplicidade não elimina requisitos críticos: idempotência (R6), outbox e HMAC constante (R7) são obrigatórios.                   |
| **YAGNI**          | Proibido criar subpacotes por simetria, flags sem uso, abstrações genéricas ou parâmetros para o futuro; instância única dispensa locks distribuídos ou ShedLock; zero dependências sem ADR.                                                                | Requisitos documentados (R1–R10, ADRs) não constituem especulação.                                                                |
| **Boy Scout Rule** | Deixar o trecho tocado pela tarefa mais limpo do que estava: remover `!!` residuais, imports mortos, KDoc ausente ou nomes confusos, sem alterar comportamento.                                                                                             | Restrito ao escopo direto da tarefa. Proibido refatorar módulos vizinhos ou alterar exceções documentadas sem ADR prévio.         |
| **SLAP**           | O método `execute(...)` de use cases (`RequestContract`, `ApplyProviderUpdate`) deve ser lido no mesmo nível de abstração (validar → aplicar no agregado → persistir), delegando particularidades a funções privadas e extensões.                           | Não fragmentar fluxos sequenciais coesos em microfunções sem ganho de clareza.                                                    |

### 4.1 Regra de precedência dos princípios

Em qualquer decisão técnica, a ordem hierárquica de prevalência é estrita:

1. **Regras de Negócio R1 a R10** ([`docs/plano-de-negocio.md`](docs/plano-de-negocio.md) §8)
2. **ADRs (Architecture Decision Records)** ([`docs/adr/`](docs/adr/))
3. **Regras Invioláveis do Núcleo** (§6 deste arquivo)
4. **Princípios Fundamentais** (§4 deste arquivo)
5. **Documentos de Detalhe** ([`docs/agents/`](docs/agents/)) e **Guia Técnico** ([
   `docs/arquitetura-tecnica.md`](docs/arquitetura-tecnica.md))

Princípios de design (como DRY ou Boy Scout) nunca justificam afrouxar regras de negócio, introduzir dependências sem
ADR, violar a governança de Git ou remover exceções arquiteturais documentadas.

---

## 5. Arquitetura em resumo (monólito modular hexagonal)

O sistema organiza-se em um monólito modular estruturado no pacote raiz `br.com.locasign`:

- **Módulos:** de negócio (`lease`, `contract`, `notification`) e transversal (`shared`).
- **Camadas:** `interfaces → app → domain ← infra` (`infra` também depende de `app`).
    - `domain`: Kotlin puro sem frameworks nem Spring. Agregados (`Contract`, `Lease`), value objects e erros derivados
      de `DomainException`.
    - `app`: casos de uso (`<Verbo><Substantivo>.kt`), queries e portas de saída (`app/ports/out/`). Agnóstica de
      Spring/JPA/Kafka.
    - `interfaces`: controllers REST, receptores de webhook e `@KafkaListener`.
    - `infra`: persistência com Spring Data JDBC e `JdbcClient`, integração PandaDoc (`RestClient`), mensageria Kafka,
      storage e jobs `@Scheduled`.
- **Comunicação entre módulos:** `contract → lease` (síncrona, via portas e ponte `LeaseBridgeAdapters.kt`),
  `notification ⇢ contract` (assíncrona, via eventos Kafka) e todos `→ shared`. Sem dependências cíclicas.
- **Fronteira com a PandaDoc:** isolada atrás de `SignatureProviderPort` e `ProviderWebhookGateway`. Chamadas externas
  são assíncronas e ocorrem fora da requisição HTTP do usuário.

> **Leitura obrigatória:** antes de criar use cases, portas, pontes ou mover pacotes, leia [
`docs/agents/arquitetura.md`](docs/agents/arquitetura.md).

---

## 6. Regras invioláveis

As restrições técnicas abaixo são de observância mandatória em todo o repositório:

1. **Governança de Git:** proibido executar `git commit` ou `git push` (diretamente ou via subagentes). Mudanças
   permanecem
   no workspace para revisão humana (`git diff` / `git status`).
2. **Null-safety:** proibido utilizar `!!` (force unwrap), inclusive em testes.
3. **Controle de fluxo exaustivo:** proibido utilizar `else` em `when` sobre `sealed class`, `sealed interface` ou
   `enum` do projeto. Sujeito anulável exige ramo `null ->` explícito.
4. **Transações e anotações proibidas:** proibido utilizar `@Transactional` (use exclusivamente
   `TransactionRunner.run { }`).
   Proibido utilizar JPA/Hibernate (`jakarta.persistence.*`), Spring WebFlux, OpenFeign, MapStruct, Spring Security,
   `@RetryableTopic` e ShedLock sem ADR aprovado.
5. **Publicação em mensageria:** proibido injetar `KafkaTemplate` em código de negócio. Publicações ocorrem gravando
   eventos em `outbox_events` na mesma transação do agregado. Apenas `OutboxRelay`, `DeadLetterReplayer` e recuperador
   DLT publicam.
6. **Manipulação de tempo:** proibido invocar `Instant.now()`, `LocalDate.now()` ou `System.currentTimeMillis()` nas
   camadas
   `domain` e `app`. Injete e utilize `BusinessClock`.
7. **Identificadores UUID:** proibido utilizar `java.util.UUID` nas camadas `domain` e `app` (utilize
   `kotlin.uuid.Uuid`).
   `java.util.UUID` restringe-se a `infra/persistence` e filtros web.
8. **Validações de negócio:** proibido utilizar `require(...)` ou `IllegalArgumentException` para regras de negócio (use
   subtipos de `DomainException`).
9. **Isolamento de chamadas externas:** proibido efetuar chamadas síncronas a APIs externas (PandaDoc) no fluxo de
   requisições HTTP do cliente. Devem ocorrer em consumers Kafka ou jobs agendados (salvo exceções registradas no
   ADR-012/ADR-013).
10. **Respostas a webhooks:** proibido responder **HTTP 410** para a PandaDoc. Responda 200 após autenticação HMAC e
    persistência bruta no inbox (`webhook_inbox`).
11. **Dados pessoais (LGPD):** proibido registrar em logs ou mensagens de exceção CPF ou e-mail completos. Utilize
    `Cpf.masked()` e `Email.masked()`.
12. **Imutabilidade de migrations:** proibido modificar scripts de migração Flyway já criados e aplicados. Correções
    exigem novo arquivo sequencial em `src/main/resources/db/migration/`.
13. **Governança de dependências:** proibido adicionar dependências, alterar a toolchain Java ou modificar versões
    gerenciadas sem ADR formal.
14. **Arquivos de ambiente:** proibido criar ou modificar arquivos `.env*`. Configurações residem no `README.md` e
    `application.yaml`.

> **Leitura obrigatória:** antes de implementar use cases, controllers, entidades JDBC ou rotinas com dados pessoais,
> leia [`docs/agents/regras-de-codigo.md`](docs/agents/regras-de-codigo.md).

---

## 7. Testes automatizados em resumo

A suíte conta com 234 testes automatizados cobrindo todas as camadas e regras de negócio R1 a R10:

- **Convenção de sufixos e execução:**
    - `*Test`: testes rápidos sem Docker (unitários de domínio sem frameworks, use cases com fakes em memória,
      slices web `@WebMvcTest`, testes HTTP de adapters com WireMock e validação de arquitetura com ArchUnit).
    - `*IT`: testes de integração que exigem Testcontainers (PostgreSQL 18.6 e Kafka 4.3.1).
- **Padrão de integração (`IntegrationTestSupport`):**
    - Herde de `IntegrationTestSupport` para reaproveitar contexto `@SpringBootTest(webEnvironment = NONE)` com perfil
      `test`
      (`application-test.yaml`) e containers centralizados em `TestcontainersSupport`.
    - O bean de assinatura é substituído automaticamente por `FixedSignatureProvider` para isolar a PandaDoc.
- **Teste ponta a ponta (E2E):** `LocaSignE2EIT` valida o fluxo completo integrando Testcontainers e WireMock.
- **Validação arquitetural:** `HexagonalArchitectureTest` valida 13 regras estáticas do monólito hexagonal via ArchUnit.
- **Proibições em testes:**
    - Proibido adicionar bibliotecas fora do build (MockK, Konsist, `mockito-kotlin`).
    - Proibido utilizar Spring ou Mockito em testes de `domain` e `app` (use instanciação direta e fakes em memória).
    - Proibido utilizar `Thread.sleep` (utilize Awaitility para validações assíncronas).
    - Proibido utilizar `@Disabled` ou afrouxar asserções para fazer o build passar.
    - Proibido desabilitar testes sem Docker (`disabledWithoutDocker = true`).

> **Leitura obrigatória:** antes de criar ou refatorar testes unitários, slices ou integração, leia [
`docs/agents/testes.md`](docs/agents/testes.md).

---

## 8. Build, dependências e CI em resumo

- **Contratos de build:**
    - `bootJar` gera o artefato executável único renomeado para `build/libs/app.jar` (task legada `jar` desativada),
      consumido pelo Dockerfile e pela CI.
    - O projeto utiliza Java 25 (toolchain) e Gradle Wrapper 9.8.1.
- **Governança de dependências:**
    - Catálogo `gradle/libs.versions.toml`: Kotlin (`2.4.21`), Spring Boot (`4.1.1`), `io.spring.dependency-management`
      (`1.1.7`), springdoc (`3.1.1`), WireMock (`3.13.2`, ADR-014) e ArchUnit (`1.5.1`, ADR-014).
    - Demais dependências vêm do BOM `spring-boot-dependencies` sem versão no `build.gradle.kts`.
    - Qualquer nova dependência, alteração de wrapper ou imagem base exige ADR.
- **Integração Contínua (GitHub Actions):**
    - Workflows em `.github/workflows/`: `gradle.yml` (build, testes e geração do jar), `release.yml` e
      `dependency-review.yml`.
    - O comando `.\gradlew.bat build --console=plain` (com Docker ativo) reproduz integralmente a validação da CI.

> **Leitura obrigatória:** antes de propor dependências, atualizar bibliotecas ou modificar arquivos de build e CI,
> leia [`docs/agents/build-e-ci.md`](docs/agents/build-e-ci.md).

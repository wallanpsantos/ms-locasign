# LocaSign: Diretrizes Universais para Agentes de IA

Serviço backend que automatiza o ciclo de vida de contratos de locação residencial: gera o contrato, envia para
assinatura eletrônica na PandaDoc, acompanha o ciclo por webhooks e dispara as ações pós-assinatura.

> **Propósito do repositório:** projeto de estudo de engenharia de software pragmática com Kotlin, Spring Boot,
> PostgreSQL, Apache Kafka e Docker.  
> **Stack fixada (fontes: `gradle/libs.versions.toml`, `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`,
`compose.yml`):**
> Kotlin 2.4.21, Java 25 (toolchain Temurin), Spring Boot 4.1.1 (Spring Framework 7, Jackson 3), Gradle 9.8.1,
> PostgreSQL 18.6 e
> Apache Kafka 4.3.1 em modo KRaft (broker local; o cliente `kafka-clients` vem do BOM do Boot, na linha 4.2).  
> **Público deste arquivo:** agentes de IA (Claude Code, Codex, Cursor, GitHub Copilot, Antigravity, Windsurf, Aider
> etc.)
> e pessoas desenvolvedoras atuando no repositório.

---

## Sumário

1. [Leitura Obrigatória e Precedência](#1-leitura-obrigatória-e-precedência)
    -
    1.1 [Quadro de Referência Rápida das Regras Canônicas (R1 a R10)](#11-quadro-de-referência-rápida-das-regras-canônicas-r1-a-r10)
    -
    1.2 [Divergências Conhecidas entre o Guia e o Repositório](#12-divergências-conhecidas-entre-o-guia-e-o-repositório)
2. [Comandos de Desenvolvimento e Validação](#2-comandos-de-desenvolvimento-e-validação)
    - 2.1 [Execução Multiplataforma (PowerShell vs Bash)](#21-execução-multiplataforma-powershell-vs-bash)
    - 2.2 [Demonstração com a Coleção Bruno em 4 Pastas](#22-demonstração-com-a-coleção-bruno-em-4-pastas)
3. [Modo de Trabalho do Agente e Governança de Tarefas](#3-modo-de-trabalho-do-agente-e-governança-de-tarefas)
    - 3.1 [Ciclo de Vida de Planos em `docs/tasks/`](#31-ciclo-de-vida-de-planos-em-docstasks)
4. [Princípios Fundamentais](#4-princípios-fundamentais)
    - 4.1 [Regra de Precedência dos Princípios](#41-regra-de-precedência-dos-princípios)
5. [Arquitetura em Resumo (Monólito Modular Hexagonal)](#5-arquitetura-em-resumo-monólito-modular-hexagonal)
6. [Regras Invioláveis do Núcleo](#6-regras-invioláveis-do-núcleo)
7. [Testes Automatizados em Resumo](#7-testes-automatizados-em-resumo)
8. [Build, Dependências e CI em Resumo](#8-build-dependências-e-ci-em-resumo)

---

## 1. Leitura obrigatória e precedência

Antes de analisar, planejar ou implementar, leia na ordem:

1. [**`docs/plano-de-negocio.md`**](docs/plano-de-negocio.md): o quê e o porquê. As regras **R1 a R10** (seção 8)
   prevalecem sobre qualquer outra decisão.
2. [**`docs/arquitetura-tecnica.md`**](docs/arquitetura-tecnica.md): o como. Seções mais usadas: arquitetura (§3),
   glossário (§4.1), máquina de estados (§4.3), PandaDoc (§5), webhooks (§6), Kafka (§7), PostgreSQL (§8), API (§9) e
   testes (§15).
3. **ADRs (Architecture Decision Records):** os ADRs 001 a 010 existem só como tabela na seção 18 do guia. Do ADR-011 em
   diante, cada um é um arquivo em [`docs/adr/`](docs/adr/):
    - [**ADR-011:** Porta
      `TransactionRunner` no lugar do decorator transacional](docs/adr/0011-transaction-runner-no-lugar-de-decorator.md)
      (ajusta a seção 3.6 do guia).
    - [**ADR-012:** Comportamentos confirmados da PandaDoc](docs/adr/0012-confirmacoes-da-pandadoc.md) (validação HMAC
      sobre bytes brutos, retentativas em 409/404, download protegido).
    - [**ADR-013:** Ajustes de implementação em relação ao guia](docs/adr/0013-ajustes-em-relacao-ao-guia.md)
      (localização de value objects, colunas extras, token de operador `X-Admin-Token`, health).
    - [
      **ADR-014:** Estratégia de testes automatizados, convenção de localização e inclusão de WireMock e ArchUnit](docs/adr/0014-estrategia-e-ferramentas-de-testes.md)
      (`aceita`).
    - [
      **ADR-015:** Convenção estruturada de documentação técnica de classes com KDoc](docs/adr/0015-convencao-de-kdoc.md)
      (`aceita`).
4. **Documentos de detalhamento em [`docs/agents/`](docs/agents/):**
    - [**`divergencias-do-guia.md`**](docs/agents/divergencias-do-guia.md): divergências catalogadas entre o guia
      técnico e o repositório.
    - [**`arquitetura.md`**](docs/agents/arquitetura.md): camadas, árvore de pacotes, pontes, fronteira PandaDoc,
      checklist de use cases e exceções.
    - [**`regras-de-codigo.md`**](docs/agents/regras-de-codigo.md): nomes, convenção de KDoc, Kotlin idiomático, Spring
      Boot 4/Spring 7, consistência, segurança e Flyway.
    - [**`testes.md`**](docs/agents/testes.md): pirâmide de testes (234 testes), classpath/BOM, Testcontainers,
      WireMock, ArchUnit, `IntegrationTestSupport`.
    - [**`build-e-ci.md`**](docs/agents/build-e-ci.md): governança de dependências, contratos de build (`app.jar`),
      workflows de CI e Dependabot.

Em caso de conflito: **R1–R10 > ADRs > regras do núcleo do `AGENTS.md` > documentos de detalhe em `docs/agents/` > guia
técnico** (guia, §0).  
Siga sempre o repositório quando divergir do guia técnico (detalhes em [
`docs/agents/divergencias-do-guia.md`](docs/agents/divergencias-do-guia.md)).  
Toda decisão técnica relevante que mude o guia exige ADR. Não altere o código só para casar com um trecho desatualizado
do guia.

### 1.1 Quadro de Referência Rápida das Regras Canônicas (R1 a R10)

As regras abaixo são invariantes absolutos de negócio ([`docs/plano-de-negocio.md`](docs/plano-de-negocio.md) §8):

| Código  | Tema Principal                   | Descrição Mandatória                                                                                                 | Consequência Técnica / Resposta HTTP                                                                            |
|:-------:|----------------------------------|----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| **R1**  | Unicidade de Contrato Ativo      | Uma locação só pode ter um contrato em estado não terminal por vez.                                                  | Rejeição imediata com `HTTP 409 Conflict` (`/problems/active-contract-exists`) e índice parcial único no banco. |
| **R2**  | Dados Obrigatórios da Locação    | Locatário com CPF válido (algoritmo mod 11), endereço, aluguel positivo (> 0), data presente/futura e prazo.         | Rejeição de domínio devolvendo `HTTP 422 Unprocessable` (`/problems/business-rule`) e Bean Validation (`400`).  |
| **R3**  | Ordem Sequencial de Assinatura   | Primeiro assina o locatário; somente após a conclusão dele, o representante da imobiliária assina.                   | Orquestrado na PandaDoc via signing order sequencial com papéis `Locatario` e `Imobiliaria`.                    |
| **R4**  | Prazos e Lembretes               | Prazo máximo de 7 dias para assinatura, com lembrete automático disparado no 3º dia.                                 | Jobs `@Scheduled` (`ExpirationJob` e `ReminderJob`) atuando sobre prazos calculados via `BusinessClock`.        |
| **R5**  | Imutabilidade e Não Regressão    | O status nunca regride. Estados finais (`COMPLETED`, `CANCELLED`, `DECLINED`, `EXPIRED`) são estritamente imutáveis. | Rejeição com `HTTP 409 Conflict` (`/problems/contract-final`). Eventos tardios geram `IGNORED_TRANSITION`.      |
| **R6**  | Idempotência Estrita             | A recepção repetida do mesmo evento ou webhook nunca produz efeito colateral duplicado.                              | Consumidores com controle de versão/deduplicação (`markProcessed`) e transições condicionais seguras.           |
| **R7**  | Gravação Bruta no Inbox          | Todo webhook da PandaDoc é gravado bruto no banco antes de qualquer processamento de negócio.                        | Gravação atômica em `webhook_inbox` + `outbox_events` devolvendo `HTTP 200 OK` rápido (nunca `410`).            |
| **R8**  | Gatilho das Ações Pós-Assinatura | Ativação da locação, vistoria e primeira cobrança só ocorrem quando o contrato alcança `COMPLETED`.                  | Consumidor Kafka reativo disparado estritamente pela chegada do evento de conclusão do contrato.                |
| **R9**  | Correção de Contrato             | Alterações após o envio exigem cancelamento formal com justificativa e emissão de nova versão (v2, v3).              | O contrato atual transiciona para `CANCELLED` (histórico preservado) e um novo `DRAFT` é criado na locação.     |
| **R10** | Proteção de Dados (LGPD)         | Uso exclusivo de dados fictícios. CPF e e-mail nunca podem ser logados ou expostos abertos.                          | Uso estrito dos value objects com mascaramento: `Cpf.masked()` e `Email.masked()` em logs e DTOs.               |

### 1.2 Divergências conhecidas entre o guia e o repositório

Siga sempre o código e os testes do repositório; não crie classes ou pacotes desnecessários apenas para casar com o guia
técnico. O detalhamento completo das divergências está catalogado em [
`docs/agents/divergencias-do-guia.md`](docs/agents/divergencias-do-guia.md).

---

## 2. Comandos de Desenvolvimento e Validação

Pré-requisitos: JDK 25 instalado (o build não provisiona toolchain) e Docker em execução. Agentes acrescentam
`--console=plain` a todo comando Gradle para gerar saídas legíveis sem sobrecarga de caracteres de controle.

### 2.1 Execução Multiplataforma (PowerShell vs Bash)

#### No Windows (PowerShell 7+):

```powershell
# Compilação e checagem rápida do código de produção (sem warnings)
.\gradlew.bat compileKotlin --console=plain

# Executa apenas os testes rápidos que não sobem Docker (unitários, web slices, wiremock e archunit)
.\gradlew.bat test --tests "*Test" --console=plain

# Executa todos os testes (sobe PostgreSQL 18.6 e Kafka 4.3.1 KRaft via Testcontainers; exige Docker ativo)
.\gradlew.bat test --console=plain

# Gate de pronto e da CI: compila sem warnings, roda todos os testes e gera build/libs/app.jar
.\gradlew.bat build --console=plain

# Execução local da infraestrutura e da aplicação no host (porta 8080)
docker compose up -d                                             # PostgreSQL 18.6 + Kafka 4.3.1 (KRaft)
.\gradlew.bat bootRun --args="--spring.profiles.active=local"    # aplicação com perfil local

# Perfis adicionais do Docker Compose
docker compose --profile tools up -d                             # Kafka UI em http://localhost:8081
docker compose --profile app up -d --build                       # sobe a aplicação compilada em container
docker compose --profile tunnel up -d                            # túnel HTTPS para webhooks da PandaDoc
docker compose down                                              # encerra serviços preservando os volumes
```

#### No Linux / macOS (Bash):

```bash
# Compilação e checagem rápida do código de produção (sem warnings)
./gradlew compileKotlin --console=plain

# Executa apenas os testes rápidos que não sobem Docker (unitários, web slices, wiremock e archunit)
./gradlew test --tests "*Test" --console=plain

# Executa todos os testes (sobe PostgreSQL 18.6 e Kafka 4.3.1 KRaft via Testcontainers; exige Docker ativo)
./gradlew test --console=plain

# Gate de pronto e da CI: compila sem warnings, roda todos os testes e gera build/libs/app.jar
./gradlew build --console=plain

# Execução local da infraestrutura e da aplicação no host (porta 8080)
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'

# Encerramento seguro
docker compose down
```

- **Atenção aos filtros de teste:** no Gradle, um `--tests` sem correspondência passa com sucesso. Confira sempre na
  saída o número de testes efetivamente executados.
- **Relatório de testes:** HTML gerado em `build/reports/tests/test/index.html` (XMLs em `build/test-results/test/`).
- **Destruição de volumes:** `docker compose down -v` apaga banco, tópicos e PDFs arquivados. Execute somente sob pedido
  humano explícito.
- **Webhook assinado sem a PandaDoc:**
  `PANDADOC_WEBHOOK_SHARED_KEY=segredo bash scripts/send-signed-webhook.sh scripts/samples/document-state-changed.json`.  
  A app precisa estar executando com a mesma chave. Repita com `DELIVERY_ID=<fixo>` para validar a idempotência e
  deduplicação (R6/R7).
- **Buscas no código:** caso a ferramenta `rg` (ripgrep) não esteja disponível no PATH do terminal/PowerShell, utilize
  `git grep -n -E "<padrao>"` a partir da raiz do repositório.

### 2.2 Demonstração com a Coleção Bruno em 4 Pastas

O repositório inclui a coleção oficial pronta para o **Bruno** em [
`http/demonstracao-do-plano-de-negocio`](http/demonstracao-do-plano-de-negocio), organizada visualmente em **4 pastas
sequenciais por jornadas**:

1. `01-fluxo-feliz/` (8 requisições): cobre o ciclo de vida completo de ponta a ponta (cadastro de locação, solicitação
   de contrato, acompanhamento, webhook assinado, cancelamento e emissão de v2). Possui scripts pós-resposta que
   capturam e propagam automaticamente `leaseId` e `contractId`.
2. `02-cenarios-de-erro/` (13 requisições): exercita deterministicamente todas as validações de domínio e erros da RFC
   9457 (400, 401 HMAC, 404 isolados com UUIDs nulos, 409 de unicidade R1, 409 de imutabilidade R5 e 422 de
   CPF/data/aluguel R2).
3. `03-operacao-e-resiliencia/` (6 requisições): rotas de operador protegidas pelo header `X-Admin-Token` (reconciliação
   manual com PandaDoc e replay de mensagens de DLT do Kafka).
4. `04-infraestrutura-e-observabilidade/` (4 requisições): `/actuator/health`, especificação OpenAPI `/v3/api-docs` e
   métricas Micrometer dimensionais (`locasign.webhook.received` e `locasign.contract.transition.applied`).

---

## 3. Modo de trabalho do agente

- **Objetivo didático:** antes de gerar arquivos grandes ou refatorar, descreva o plano em poucas linhas. Ao concluir
  cada etapa, resuma os conceitos de Kotlin, Spring, Kafka ou PostgreSQL empregados.
- **Definição de pronto (DoD):**
    1. Em funcionalidade nova ou correção de bug, o teste novo falhava antes da mudança e passa depois; em refatoração,
       os testes existentes continuam verdes;
    2. `./gradlew build` está verde com Docker ligado e a suíte de testes executa com 100% de aprovação (234 testes);
    3. `./gradlew compileKotlin` não emite warnings novos;
    4. A checagem automatizada de arquitetura (`HexagonalArchitectureTest`) e a verificação de fronteiras passam com
       sucesso.  
       *Mudança restrita a documentação dispensa os itens 1 e 2.*
- **Governança de Git:** **NUNCA** execute `git commit` ou `git push` por conta própria (diretamente ou via subagentes);
  esta regra prevalece sobre a seção 17 do guia técnico. Deixe todas as modificações no workspace para revisão humana
  calma via `git status` e `git diff`. Se a pessoa desenvolvedora solicitar expressamente a mensagem de commit, sugira
  no formato padronizado do histórico: `<gitmoji> <tipo>[(<escopo>)]: <descrição no presente>` em pt-BR (ex.:
  `✨ feat(contract): implementa regra R1 com índice parcial`, `📚 docs: atualiza AGENTS.md`). Nunca inclua
  `Co-Authored-By` ou referências à IA em commits ou PRs.
- **Dependências e versões:** regras em [`docs/agents/build-e-ci.md`](docs/agents/build-e-ci.md). Qualquer alteração de
  dependência, toolchain JDK, imagem de container ou wrapper Gradle **exige ADR**.
- **ADRs (Architecture Decision Records):** toda decisão técnica relevante que desvie do guia técnico ou o expanda vira
  `docs/adr/NNNN-titulo-em-kebab-case.md` com número de 4 dígitos sequencial. Siga a estrutura canônica dos ADRs
  existentes. ADR criado por agente nasce com status `proposta`; aceitá-lo é decisão humana exclusiva. ADRs
  implementados nunca são removidos e passam ao status `aceita`.
- **Variáveis de ambiente:** não crie nem edite arquivos `.env*`. A referência canônica reside na tabela de configuração
  do `README.md`, junto aos placeholders `${VAR:padrao}` do `application.yaml` (detalhes em [
  `docs/agents/regras-de-codigo.md`](docs/agents/regras-de-codigo.md)).

### 3.1 Ciclo de Vida de Planos em `docs/tasks/`

Os arquivos em `docs/tasks/` (`plan.md` e `todo.md`) constituem a memória de curto prazo para execução de demandas de
múltiplos passos:

1. **Início de Iniciativa:** Ao iniciar uma tarefa complexa, crie ou atualize `docs/tasks/plan.md` (decisões de design,
   fases e riscos) e `docs/tasks/todo.md` (checklist executável com critérios de aceitação e comandos de verificação).
2. **Proteção de Trabalho Concorrente:** Se existirem tarefas não concluídas (`[ ]`) de uma iniciativa anterior nesses
   arquivos, **pare e pergunte** antes de sobrescrevê-los, evitando perder contexto de trabalho em andamento.
3. **Execução Incremental:** Conforme cada tarefa for concluída e verificada, marque-a como `[x]`.
4. **Finalização da Demanda:** Uma vez concluídos todos os itens da iniciativa e revisados pelo usuário, o backlog
   executado pode ser arquivado ou substituído pela próxima demanda solicitada.

---

## 4. Princípios Fundamentais

Diretrizes de engenharia aplicadas de forma idiomática em Kotlin 2.4+ e Spring Boot 4.1.1+:

| Princípio     | Aplicação Idiomática no LocaSign                                                                                                                                                                                        | Limite Explícito                                                                                                                          |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| **SRP**       | Um use case por arquivo com método `execute` (`RequestContract`); controllers apenas adaptam HTTP; `ContractTransitionPolicy` centraliza regras de transição; mapeamentos em `mappers/`.                                | Não fragmentar classes coesas ou value objects só para reduzir linhas de código.                                                          |
| **OCP**       | Extensão por novas portas (`SignatureProviderPort`), sem alterar use cases. Classes `final` por padrão (sem `open` especulativo). `sealed` com `when` exaustivo força tratamento explícito.                             | Não criar hierarquias ou padrões Strategy especulativos para casos de uso únicos.                                                         |
| **LSP**       | Fakes e adapters cumprem estritamente o contrato da porta: `markProcessed` devolve `false` em duplicata; ausência é `T?` (nunca exceção nem `Optional`); `ImmediateTransactionRunner` executa sem rollback.             | O contrato expresso no KDoc da porta é soberano; implementação que o viola é bug.                                                         |
| **ISP**       | Portas pequenas e orientadas ao consumidor: segregação entre `LeaseLookupPort` e `LeaseActivationPort`, ou `SignatureProviderPort` e `ProviderWebhookGateway`.                                                          | Não criar portas genéricas nem interfaces de método único sem consumidores independentes reais.                                           |
| **DIP**       | Camada `app` declara portas em `app/ports/out`; `infra` implementa. Composição explícita via `@Configuration(proxyBeanMethods = false)` em `<Modulo>BeansConfig`. Verificado por `HexagonalArchitectureTest`.           | Sem `ports/in`: controllers injetam diretamente a classe concreta do use case, simplificando o design.                                    |
| **DRY**       | Centralização de conhecimento canônico: catálogo de tópicos em `Topics`, grupos em `ConsumerGroups`, métricas em `Metrics`, propriedades em `LocaSignProperties` e persistência em `ContractPersister.save`.            | Não compartilhar entidades entre módulos (`LeaseSnapshot` desacopla módulos de propósito). Testes toleram duplicação pontual por clareza. |
| **KISS**      | Transações explícitas via `TransactionRunner.run { }` em vez de AOP/decorators; conversões simples por extension functions em vez de MapStruct; consultas com `JdbcClient` e SQL explícito.                             | Simplicidade não elimina requisitos críticos: idempotência (R6), outbox e HMAC constante (R7) são obrigatórios.                           |
| **YAGNI**     | Proibido criar subpacotes por simetria, flags sem uso, abstrações genéricas ou parâmetros para o futuro; instância única dispensa locks distribuídos ou ShedLock; zero dependências sem ADR.                            | Requisitos formalmente documentados (R1–R10, ADRs) não constituem especulação.                                                            |
| **Boy Scout** | Deixar o trecho tocado pela tarefa mais limpo do que estava: remover `!!` residuais, imports mortos, KDoc ausente ou nomes confusos, sem alterar comportamento.                                                         | Restrito ao escopo direto da tarefa. Proibido refatorar módulos vizinhos sem ADR prévio.                                                  |
| **SLAP**      | O método `execute(...)` de use cases (`RequestContract`, `ApplyProviderUpdate`) deve ser lido no mesmo nível de abstração (validar ➔ aplicar no agregado ➔ persistir), delegando particularidades a funções privadas. | Não fragmentar fluxos sequenciais coesos em microfunções sem ganho real de legibilidade.                                                  |

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
- **Camadas concêntricas:** `interfaces ➔ app ➔ domain ⮌ infra` (`infra` também implementa portas de `app`).
    - `domain`: Kotlin puro sem frameworks nem anotações Spring. Agregados (`Contract`, `Lease`), value objects e erros
      derivados de `DomainException`.
    - `app`: casos de uso (`<Verbo><Substantivo>.kt`), queries e portas de saída (`app/ports/out/`). Totalmente
      agnóstica de frameworks de persistência ou mensageria.
    - `interfaces`: adaptadores de entrada — controllers REST, receptores de webhook e `@KafkaListener`.
    - `infra`: adaptadores de saída — persistência com Spring Data JDBC e `JdbcClient`, integração com PandaDoc
      (`RestClient`), mensageria Kafka, storage local e jobs `@Scheduled`.
- **Comunicação entre módulos:** `contract ➔ lease` (síncrona, via portas e ponte `LeaseBridgeAdapters.kt`),
  `notification ⇢ contract` (assíncrona, via eventos Kafka) e todos `➔ shared`. Dependências cíclicas entre módulos são
  terminantemente proibidas.
- **Fronteira com a PandaDoc:** rigorosamente isolada atrás de `SignatureProviderPort` e `ProviderWebhookGateway`.
  Chamadas externas são assíncronas e ocorrem fora da requisição HTTP do usuário.

> **Leitura obrigatória:** antes de criar use cases, portas, pontes ou mover pacotes, leia [
`docs/agents/arquitetura.md`](docs/agents/arquitetura.md).

---

## 6. Regras invioláveis do núcleo

As restrições técnicas abaixo são de observância mandatória em todo o repositório:

1. **Governança de Git:** Proibido executar `git commit` ou `git push` (diretamente ou via subagentes). Todas as
   alterações permanecem no workspace para revisão humana via `git diff` e `git status`.
2. **Null-safety:** Proibido utilizar `!!` (force unwrap), inclusive em código de testes.
3. **Controle de fluxo exaustivo:** Proibido utilizar `else` em expressões `when` sobre `sealed class`,
   `sealed interface` ou `enum` do projeto. Sujeito anulável exige o ramo `null ->` explícito.
4. **Transações e anotações proibidas:** Proibido utilizar `@Transactional` (utilize exclusivamente
   `TransactionRunner.run { }` conforme o [ADR-011](docs/adr/0011-transaction-runner-no-lugar-de-decorator.md)).
   Proibido utilizar JPA/Hibernate (`jakarta.persistence.*`), Spring WebFlux, OpenFeign, MapStruct, Spring Security,
   `@RetryableTopic` e ShedLock sem ADR aprovado.
5. **Publicação em mensageria:** Proibido injetar `KafkaTemplate` em código de negócio ou casos de uso. Publicações
   ocorrem gravando eventos em `outbox_events` na mesma transação atômica do agregado. Apenas `OutboxRelay`,
   `DeadLetterReplayer` e recuperadores de DLT publicam diretamente no broker.
6. **Manipulação de tempo:** Proibido invocar `Instant.now()`, `LocalDate.now()` ou `System.currentTimeMillis()` nas
   camadas `domain` e `app`. Injete e utilize o `BusinessClock`.
7. **Identificadores UUID:** Proibido utilizar `java.util.UUID` nas camadas `domain` e `app` (utilize a API padrão
   `kotlin.uuid.Uuid` introduzida no Kotlin moderno). O uso de `java.util.UUID` restringe-se estritamente à camada de
   persistência JDBC e filtros web.
8. **Validações de negócio:** Proibido utilizar `require(...)` ou `IllegalArgumentException` para regras de negócio
   (utilize subtipos tipados de `DomainException`).
9. **Isolamento de chamadas externas:** Proibido efetuar chamadas síncronas a APIs externas (PandaDoc) no fluxo de
   requisições HTTP do cliente. Devem ocorrer assincronamente em consumers Kafka ou jobs agendados.
10. **Respostas a webhooks:** Proibido responder **HTTP 410** para a PandaDoc. Responda `HTTP 200 OK` rápido após
    autenticação HMAC e persistência bruta no inbox (`webhook_inbox`).
11. **Dados pessoais (LGPD):** Proibido registrar em logs ou mensagens de exceção CPF ou e-mail completos. Utilize
    sempre os value objects mascarados: `Cpf.masked()` e `Email.masked()`.
12. **Imutabilidade de migrations:** Proibido modificar scripts de migração Flyway já criados e aplicados. Correções
    exigem um novo arquivo sequencial versionado em `src/main/resources/db/migration/`.
13. **Governança de dependências:** Proibido adicionar dependências, alterar a toolchain Java ou modificar versões
    gerenciadas sem ADR formal aprovado.
14. **Documentação Técnica Mandatória (KDoc Estruturado):** Toda classe, interface, objeto e enum do projeto deve seguir
    a convenção de KDoc estruturado formalizada no [ADR-015](docs/adr/0015-convencao-de-kdoc.md), explicitando
    comportamento técnico, responsabilidade única (SRP) e garantias/regras de negócio.
15. **Arquivos de ambiente:** Proibido criar ou modificar arquivos `.env*`. Configurações residem exclusivamente na
    tabela do `README.md` e nos defaults de `application.yaml`.
16. **Restrições Estritas de Testes:** Proibido adicionar bibliotecas fora do build (MockK, Konsist, `mockito-kotlin`),
    utilizar `@Disabled`, recorrer a `Thread.sleep` (use Awaitility) ou desabilitar testes sem Docker
    (`disabledWithoutDocker = true`).

> **Leitura obrigatória:** antes de implementar use cases, controllers, entidades JDBC ou rotinas com dados pessoais,
> leia [`docs/agents/regras-de-codigo.md`](docs/agents/regras-de-codigo.md).

---

## 7. Testes automatizados em resumo

A suíte conta com **234 testes automatizados** cobrindo todas as camadas e regras de negócio R1 a R10:

- **Convenção de sufixos e execução:**
    - `*Test`: testes rápidos sem Docker (unitários de domínio sem frameworks, use cases com fakes em memória, slices
      web `@WebMvcTest`, testes HTTP de adapters com WireMock e validação de arquitetura com ArchUnit).
    - `*IT`: testes de integração que exigem Testcontainers (PostgreSQL 18.6 e Kafka 4.3.1 KRaft).
- **Padrão de integração (`IntegrationTestSupport`):**
    - Herde de `IntegrationTestSupport` para reaproveitar contexto `@SpringBootTest(webEnvironment = NONE)` com perfil
      `test` (`application-test.yaml`) e containers centralizados em `TestcontainersSupport`.
    - O bean de assinatura é substituído automaticamente por `FixedSignatureProvider` para isolar a PandaDoc.
- **Teste ponta a ponta (E2E):** `LocaSignE2EIT` valida o fluxo assíncrono completo integrando Testcontainers e
  WireMock.
- **Validação arquitetural:** `HexagonalArchitectureTest` valida 13 regras estáticas do monólito hexagonal via ArchUnit.
- **Proibições em testes:**
    - Proibido utilizar Spring ou Mockito em testes de `domain` e `app` (utilize instanciação direta e fakes em
      memória).
    - Proibido afrouxar asserções ou mascarar exceções para fazer o build passar.

> **Leitura obrigatória:** antes de criar ou refatorar testes unitários, slices ou integração, leia [
`docs/agents/testes.md`](docs/agents/testes.md).

---

## 8. Build, dependências e CI em resumo

- **Contratos de build:**
    - `bootJar` gera o artefato executável único renomeado para `build/libs/app.jar` (task legada `jar` desativada),
      consumido pelo Dockerfile e pela CI.
    - O projeto utiliza Java 25 (toolchain Temurin) e Gradle Wrapper 9.8.1.
- **Governança de dependências:**
    - Catálogo `gradle/libs.versions.toml`: Kotlin (`2.4.21`), Spring Boot (`4.1.1`), `io.spring.dependency-management`
      (`1.1.7`), springdoc (`3.1.1`), WireMock (`3.13.2`, ADR-014) e ArchUnit (`1.5.1`, ADR-014).
    - Demais dependências provêm do BOM `spring-boot-dependencies` sem versão no `build.gradle.kts`.
    - Qualquer nova dependência, alteração de wrapper ou imagem base exige ADR formal.
- **Integração Contínua (GitHub Actions):**
    - Workflows em `.github/workflows/`: `gradle.yml` (build, testes e geração do jar), `release.yml` e
      `dependency-review.yml`.
    - O comando `.\gradlew.bat build --console=plain` (com Docker ativo) reproduz integralmente a validação da CI.

> **Leitura obrigatória:** antes de propor dependências, atualizar bibliotecas ou modificar arquivos de build e CI,
> leia [`docs/agents/build-e-ci.md`](docs/agents/build-e-ci.md).

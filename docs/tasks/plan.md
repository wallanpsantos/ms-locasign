# Plano de Implementação: atualização do `AGENTS.md` e Princípios Fundamentais

> Iniciativa anterior (suíte de testes automatizados, 39 itens concluídos) substituída com autorização do usuário em
> 2026-10-13. O conteúdo anterior continua no histórico do Git (`git log -- docs/tasks/plan.md`).
> Checklist executável: [`todo.md`](todo.md).

## 1. Visão geral

O `AGENTS.md` descreve um repositório anterior à suíte de testes: diz que não há `src/test`, que ArchUnit e WireMock
estão fora do build, mostra esqueletos de teste que não existem mais e cita versões antigas. Ele também tem 84 KB (889
linhas). Nesta sessão, o carregador de regras do Antigravity cortou o arquivo depois de ~24 KB
(`<truncated 60498 bytes>`), então as seções 4.4 a 7 não chegam ao agente.

A iniciativa:

1. corrige todo conteúdo desatualizado, com evidência (arquivo, linha ou comando) para cada correção;
2. divide o arquivo em um **núcleo enxuto** (`AGENTS.md`) e **detalhes** em `docs/agents/*.md`;
3. acrescenta a seção **Princípios Fundamentais** (SOLID, DRY, KISS, YAGNI, Boy Scout Rule, SLAP), aplicada a Kotlin
   2.4+ e Spring Boot 4.1.1+ com exemplos do próprio código;
4. formaliza a convenção de KDoc no ADR-015 (`proposta`), cuja referência atual (ADR-KDOC-001) deixou de existir;
5. atualiza todas as referências cruzadas quebradas pela renumeração.

Mudança de documentação, com uma exceção: KDoc e comentários em arquivos de `src/test` que citam seções do
`AGENTS.md`. Por isso a verificação final inclui `./gradlew build`.

## 2. Decisões de arquitetura

| ID    | Decisão                                                                                                                                  | Motivo                                                                                                                                                        |
|-------|------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DA-01 | Plano em `docs/tasks/`, sobrescrevendo a iniciativa de testes já concluída.                                                              | Escolha do usuário; segue a convenção do `AGENTS.md` §3.                                                                                                      |
| DA-02 | Núcleo `AGENTS.md` com no máximo **22 000 bytes** (UTF-8); detalhes em `docs/agents/`.                                                   | O corte observado foi em ~24 000 bytes. A margem cobre crescimento futuro. Outros agentes têm limites próprios (ver riscos).                                  |
| DA-03 | Regras invioláveis ficam **no núcleo**, nunca só nos arquivos de detalhe.                                                                | Agente que não segue links ainda precisa conhecer as proibições (`!!`, `@Transactional`, JPA, `KafkaTemplate`, PandaDoc na requisição, 410, PII em log etc.). |
| DA-04 | Primeiro **mover** o texto (diff só de localização), depois **corrigir** (diff só de conteúdo).                                          | Diff revisável e prova de que nada se perdeu na divisão.                                                                                                      |
| DA-05 | Princípios entram como nova seção antes de Arquitetura e Regras, com renumeração e atualização de referências.                           | Escolha do usuário: os princípios orientam as regras, não o contrário.                                                                                        |
| DA-06 | Princípios **não** sobrepõem R1–R10, ADRs nem regras do núcleo. Em conflito (ex.: DRY × isolamento de módulo), vence a regra específica. | Evita que "Boy Scout" ou "DRY" justifiquem mudar exceções documentadas, criar dependência entre módulos ou alterar comportamento sem ADR.                     |
| DA-07 | Arquivos de detalhe referenciam o código real (caminhos) em vez de copiar trechos longos ou versões.                                     | Os esqueletos copiados no §6.8 envelheceram (ex.: `PostgresTestcontainers`, removido no commit `3ca4ec1`). Aplicação de DRY à própria documentação.           |
| DA-08 | Convenção de KDoc formalizada no ADR-015 (`proposta`), recuperando o texto do ADR-KDOC-001 do histórico do Git.                          | Escolha do usuário; o `AGENTS.md` §3 exige ADR para convenção que expande o guia.                                                                             |
| DA-09 | Preservar codificação UTF-8 e o final de linha atual do `AGENTS.md` (CRLF). Arquivos novos seguem o `.gitattributes`.                    | Evita diff de linha inteira e problemas na CI (Linux).                                                                                                        |

### 2.1 Estrutura-alvo

**`AGENTS.md` (núcleo)**

| Nova seção | Conteúdo                                                                                                     | Origem atual             |
|------------|--------------------------------------------------------------------------------------------------------------|--------------------------|
| cabeçalho  | propósito, stack com a fonte de cada versão                                                                  | cabeçalho                |
| §1         | leitura obrigatória, precedência, mapa de `docs/agents/`, regra "siga o repositório quando divergir do guia" | §1 (sem a tabela 1.1)    |
| §2         | comandos (PowerShell e Git Bash)                                                                             | §2                       |
| §3         | modo de trabalho do agente (definição de pronto, Git, ADR, planos, variáveis de ambiente)                    | §3                       |
| §4         | **Princípios Fundamentais** (novo)                                                                           | —                        |
| §5         | arquitetura em resumo: camadas, grafo de módulos, onde fica cada coisa                                       | §4.1, §4.3 (condensados) |
| §6         | regras invioláveis (lista curta, com link para o detalhe)                                                    | §5 (extrato)             |
| §7         | testes em resumo: níveis, sufixos, `IntegrationTestSupport`, proibições                                      | §6 (extrato)             |
| §8         | build e CI em resumo: contratos de build e governança de dependências                                        | §7 (extrato)             |

**`docs/agents/` (detalhes, numeração própria a partir de 1)**

| Arquivo                   | Origem atual                                                                      |
|---------------------------|-----------------------------------------------------------------------------------|
| `divergencias-do-guia.md` | §1.1                                                                              |
| `arquitetura.md`          | §4.1 a §4.7 (camadas, árvore, módulos, PandaDoc, checklist, fronteiras, exceções) |
| `regras-de-codigo.md`     | §5.1 a §5.6                                                                       |
| `testes.md`               | §6.1 a §6.8                                                                       |
| `build-e-ci.md`           | §7.1 a §7.3                                                                       |

### 2.2 Conteúdo-guia da seção de Princípios Fundamentais

Formato: uma tabela (princípio | como se aplica em Kotlin 2.4+ / Spring Boot 4.1+ neste repositório | limite) e, no
fim, a regra de precedência da DA-06. Orçamento: até ~4 000 bytes. Cada exemplo precisa existir no código; os marcados
com "(confirmar)" são verificados na Tarefa 8.

| Princípio      | Aplicação idiomática esperada                                                                                                                                                                                                                                                                                                    | Limite                                                                                                                                                                   |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SRP            | Um use case por arquivo com `execute`; controller só traduz HTTP; `ContractTransitionPolicy` isola a decisão de transição; mapeamento em extension functions de `mappers/`.                                                                                                                                                      | Não dividir classe coesa só para reduzir linhas.                                                                                                                         |
| OCP            | Variação por nova implementação de porta, sem tocar no use case. Classes Kotlin são `final` por padrão: estender por composição, nunca com `open` especulativo. `sealed` + `when` exaustivo é "fechado de propósito": o compilador aponta os pontos a atualizar (`ApiExceptionHandler.handleDomain`, `ContractEvent.toPayload`). | Não criar hierarquia ou estratégia para um único caso.                                                                                                                   |
| LSP            | Fakes e adapters cumprem o contrato da porta: `markProcessed` devolve `false` na duplicata; ausência é `T?`, nunca exceção nem `Optional`; o fake de `TransactionRunner` não faz rollback, então atomicidade só se prova em `*IT`.                                                                                               | O contrato fica no KDoc da porta; implementação que o quebra é bug, não variação.                                                                                        |
| ISP            | Portas pequenas, do ponto de vista do consumidor: `LeaseLookupPort` × `LeaseActivationPort`, `SignatureProviderPort` × `ProviderWebhookGateway`.                                                                                                                                                                                 | Não criar porta "genérica" nem porta por método sem consumidor real.                                                                                                     |
| DIP            | `app` declara portas em `app/ports/out`; `infra` implementa; composição explícita em `<Módulo>BeansConfig` (`@Bean`, `proxyBeanMethods = false`, injeção por construtor). ArchUnit verifica a direção.                                                                                                                           | Sem `ports/in`: controllers dependem da classe concreta do use case (camada interna), decisão consciente (YAGNI).                                                        |
| DRY            | Um lugar para cada conhecimento: `Topics`, `ConsumerGroups`, `Metrics`, `LocaSignProperties`, `ContractPersister.save`.                                                                                                                                                                                                          | Não compartilhar tipos entre módulos para evitar duplicação (`LeaseSnapshot` duplica de propósito). Testes podem repetir para ler melhor. Extrair na terceira repetição. |
| KISS           | `TransactionRunner` em vez de decorator ou AOP; extension functions em vez de MapStruct; `JdbcClient` com SQL explícito.                                                                                                                                                                                                         | Simples não é ingênuo: idempotência, outbox e HMAC continuam obrigatórios.                                                                                               |
| YAGNI          | Não criar subpacote por simetria, flag ou parâmetro "para o futuro", nem dependência sem ADR; instância única dispensa ShedLock.                                                                                                                                                                                                 | Requisito documentado (R1–R10, ADR) não é especulação.                                                                                                                   |
| Boy Scout Rule | Deixar melhor o trecho já tocado pela tarefa: tirar `!!`, import morto, KDoc faltante, nome ruim, sem mudar comportamento.                                                                                                                                                                                                       | Não expandir o escopo nem "corrigir" as exceções documentadas sem ADR; melhoria fora do escopo vai para o resumo final como sugestão.                                    |
| SLAP           | `execute()` lido como roteiro no mesmo nível de abstração (validar → carregar → aplicar no agregado → persistir); detalhes em funções privadas ou extension functions com nome de intenção (confirmar exemplo).                                                                                                                  | Não fragmentar em funções de uma linha sem ganho de leitura.                                                                                                             |

## 3. Inventário inicial de divergências (já confirmadas)

Evidência coletada nesta sessão. A Tarefa 1 completa o inventário com o restante das afirmações do arquivo.

| #   | Trecho atual do `AGENTS.md`                                                                 | Estado real                                                                                                                   | Evidência                                                                       |
|-----|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| D01 | Kotlin 2.4.20 (cabeçalho, §7.1)                                                             | Kotlin 2.4.21                                                                                                                 | `gradle/libs.versions.toml:2`                                                   |
| D02 | Gradle 9.7.1 (cabeçalho, §7.2)                                                              | Gradle 9.8.1                                                                                                                  | `gradle/wrapper/gradle-wrapper.properties`                                      |
| D03 | springdoc 3.1.0 (§5.3)                                                                      | springdoc 3.1.1                                                                                                               | `gradle/libs.versions.toml:5`                                                   |
| D04 | ADRs listados só até 013 (§1)                                                               | Existe o ADR-014 (`proposta`): testes, WireMock, ArchUnit, `apache/kafka:4.3.1`                                               | `docs/adr/0014-estrategia-e-ferramentas-de-testes.md`                           |
| D05 | "Sem `src/test`, `:test` termina `NO-SOURCE`" (§2); "Se ainda não houver testes" (§6)       | `src/test` tem 26 arquivos, inclusive `*Test`, `*IT` e E2E                                                                    | `src/test/kotlin/br/com/locasign/**`                                            |
| D06 | `docs/tasks/` descreve a iniciativa de KDoc (§3); KDoc vem do ADR-KDOC-001 (§5.1)           | `docs/tasks/` passou a descrever a suíte de testes; o ADR-KDOC-001 não existe mais no repositório                             | `git grep ADR-KDOC` só encontra o `AGENTS.md`                                   |
| D07 | "Não há teste de arquitetura" (§1.1, §4.6)                                                  | `HexagonalArchitectureTest` (ArchUnit, 13 regras); limite: value classes e `const val` não aparecem no bytecode               | `src/test/kotlin/br/com/locasign/architecture/HexagonalArchitectureTest.kt`     |
| D08 | MockK, WireMock, ArchUnit fora do build (§1.1, §6.1, §7.1)                                  | WireMock (`wiremock-standalone` 3.13.2), ArchUnit 1.5.1 e Awaitility no build; MockK e mockito-kotlin continuam fora          | `build.gradle.kts:44-46`, `gradle/libs.versions.toml:6-12`                      |
| D09 | Catálogo fixa só Kotlin, Boot, dependency-management e springdoc (§7.1)                     | Também WireMock e ArchUnit                                                                                                    | `gradle/libs.versions.toml`                                                     |
| D10 | Kafka de teste `apache/kafka-native:4.3.1`; usar `apache/kafka` "exige ADR" (§6.6)          | `apache/kafka:4.3.1`, decidido no ADR-014                                                                                     | `support/TestcontainersSupport.kt:18`                                           |
| D11 | Adapter PandaDoc testado com `MockRestServiceServer`; ADR pendente (§6.3)                   | Testado com WireMock (`PandaDocSignatureProviderAdapterTest`), decidido no ADR-014                                            | `contract/infra/pandadoc/adapters/PandaDocSignatureProviderAdapterTest.kt`      |
| D12 | Persistência com `@DataJdbcTest` (§6.3); esqueleto `JdbcProcessedMessagesAdapterIT` (§6.8)  | Nenhum `@DataJdbcTest`; os `*IT` herdam `IntegrationTestSupport`; o IT real é `JdbcMessagingAdaptersIT`                       | `git grep @DataJdbcTest -- src/test` vazio                                      |
| D13 | Esqueleto `support/PostgresTestcontainers.kt` (§6.8)                                        | Removido; containers em `TestcontainersSupport` (Postgres e Kafka)                                                            | commit `3ca4ec1`                                                                |
| D14 | Convenção `*Test`/`*IT` "ainda sem ADR" (§6.4); `user.timezone` "ao criar o primeiro teste" | Formalizada no ADR-014; `systemProperty("user.timezone", "UTC")` já está no build                                             | `build.gradle.kts:72`                                                           |
| D15 | Configuração de `@SpringBootTest` descrita como lista manual (§6.6)                         | Centralizada em `application-test.yaml` (perfil `test`) e `IntegrationTestSupport` (`@TestBean` para `signatureProvider`)     | `src/test/resources/application-test.yaml`, `support/IntegrationTestSupport.kt` |
| D16 | Não documentado                                                                             | Helper `any()` para Mockito em Kotlin (`support/MockitoSupport.kt`); exceção `MalformedWebhookPayload` em `ProviderErrors.kt` | arquivos citados                                                                |
| D17 | §4.6 manda usar "Grep (ripgrep)"                                                            | `rg` não está no PATH do PowerShell neste ambiente; falta alternativa (`git grep -n -E`) para agentes sem a ferramenta Grep   | execução nesta sessão                                                           |
| D18 | Tamanho                                                                                     | 84 486 bytes; carregador do Antigravity cortou em ~24 000 bytes                                                               | contexto desta sessão                                                           |
| D19 | GitHub Actions `actions/upload-artifact` atualizada de `7.0.1` para `7.0.2` (PR #2)         | `.github/workflows/gradle.yml` e commit `407c022`                                                                             | `.github/workflows/gradle.yml:33,40`                                            |

Confirmado **sem divergência** (amostra): exceções do §4.7 (`CancelContract` anula fora da transação via
`cancelDocumentQuietly`; `ReconcileContracts` chama `fetchState`; `LeaseAdapters` lê `contracts`;
`JdbcNotificationAdapters` lê `contract_signers`; `AdminController` aninha `ReplayResponse`); `Instant.now()` só no
`OutboxRelay`; `MDC.get` só no `JdbcOutboxAdapter`; hierarquias seladas `DomainException` e `ProviderException`;
imagens `postgres:18.6`, `apache/kafka:4.3.1`, `eclipse-temurin:25`; CI com JDK 25 e limite de 20 minutos.

### 3.1 Referências externas ao `AGENTS.md` que a renumeração quebra

| Arquivo                                                                     | Referência atual                        |
|-----------------------------------------------------------------------------|-----------------------------------------|
| `src/test/kotlin/br/com/locasign/architecture/HexagonalArchitectureTest.kt` | §4.1, §4.3, §4.6 (KDoc, linhas 19 e 26) |
| `src/test/kotlin/br/com/locasign/support/IntegrationTestSupport.kt`         | seção 6.6 (linha 14)                    |
| `src/test/kotlin/br/com/locasign/support/IntegrationTestSupportIT.kt`       | seção 6.6 (linha 18)                    |
| `src/test/kotlin/br/com/locasign/contract/app/fakes/ContractFakes.kt`       | seção 6.6 (linha 93)                    |
| `src/test/kotlin/br/com/locasign/LocaSignE2EIT.kt`                          | seção 6.6 (linhas 40-41)                |
| `src/test/resources/application-test.yaml`                                  | seção 6.6 (linha 1)                     |
| `README.md`                                                                 | §4.6 (linha 197); seção de KDoc         |
| `docs/adr/0014-estrategia-e-ferramentas-de-testes.md`                       | seção 6, seção 6.6, §4.6                |
| `docs/arquitetura-tecnica.md`                                               | linha 134 e §17                         |

Fora do escopo (registros históricos, não atualizar): `docs/plano-de-testes.md` e o histórico do Git.

## 4. Lista de tarefas

Detalhes, critérios de aceite e verificação em [`todo.md`](todo.md).

### Fase 1: inventário e fundações

- [x] Tarefa 1: completar o inventário de divergências
- [x] Tarefa 2: ADR-015 (`proposta`) com a convenção de KDoc
- [x] Tarefa 3: mover o texto atual para `docs/agents/` sem alterar conteúdo

### Checkpoint 1: fundações

- [x] Nenhum texto perdido na divisão; links do núcleo resolvem; revisão humana

### Fase 2: correção de conteúdo

- [x] Tarefa 4: núcleo §1 a §3 (stack, leitura obrigatória, comandos, modo de trabalho)
- [x] Tarefa 5: `docs/agents/testes.md` reflete a suíte real e o ADR-014
- [x] Tarefa 6: `arquitetura.md` e `divergencias-do-guia.md`
- [x] Tarefa 7: `regras-de-codigo.md` e `build-e-ci.md`

### Checkpoint 2: conteúdo correto

- [x] Nenhuma divergência D01–D18 pendente; revisão humana

### Fase 3: princípios e núcleo

- [x] Tarefa 8: seção §4 Princípios Fundamentais
- [x] Tarefa 9: resumos do núcleo (§5 a §8) dentro do orçamento de bytes

### Fase 4: referências e validação

- [x] Tarefa 10: referências em `src/test` e no perfil de teste
- [x] Tarefa 11: referências em README, ADR-014 e guia
- [x] Tarefa 12: validação final (links, termos obsoletos, tamanho, build, revisão com contexto limpo)

### Checkpoint final

- [x] Todos os critérios atendidos; pronto para revisão humana e commit manual

### Paralelização

- Tarefas 1 e 2 são independentes entre si.
- Tarefas 4 a 7 tocam arquivos diferentes e podem rodar em paralelo depois da Tarefa 3.
- Tarefas 10 e 11 são independentes entre si, mas dependem da numeração final (Tarefa 9).

## 5. Riscos e mitigações

| Risco                                                                                                                                            | Impacto | Mitigação                                                                                                                        |
|--------------------------------------------------------------------------------------------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------|
| Outros agentes têm limites próprios para o `AGENTS.md` (ex.: o Codex tem limite configurável de bytes; valor padrão não verificado nesta sessão) | Alto    | Orçamento de 22 000 bytes (DA-02); regras invioláveis no núcleo (DA-03); confirmar limites na documentação oficial na Tarefa 12. |
| Agente lê só o núcleo e ignora `docs/agents/`                                                                                                    | Alto    | DA-03; cada resumo do núcleo diz explicitamente quando ler o arquivo de detalhe ("antes de escrever teste, leia ...").           |
| Perda de informação ao condensar                                                                                                                 | Médio   | DA-04 (mover antes de corrigir); verificação de conservação na Tarefa 3.                                                         |
| Referências quebradas por renumeração                                                                                                            | Médio   | Mapa da seção 3.1; busca por `AGENTS.md` e `§` na Tarefa 12.                                                                     |
| Princípios usados para justificar refatorações fora do escopo ou violar ADR                                                                      | Médio   | DA-06 e limites explícitos por princípio (seção 2.2).                                                                            |
| Documentação envelhece de novo (versões, esqueletos)                                                                                             | Médio   | DA-07: apontar para arquivos-fonte; versões só no cabeçalho, com a fonte de cada uma.                                            |
| Alterar ADR-014 (`proposta`) muda um registro de decisão                                                                                         | Baixo   | Só trocar referências de seção, sem alterar a decisão; listar a mudança no resumo para o humano.                                 |

## 6. Questões em aberto

- O ADR-014 continua `proposta`. Se já foi aceito, o humano atualiza o status; o `AGENTS.md` o tratará como "em vigor no
  build, aguardando aceite formal" enquanto isso.
- Orçamento de 22 000 bytes para o núcleo é aceitável, ou prefere um teto menor?
- As demais divergências do §1.1 (hoje "pendentes de registro") devem ganhar ADR nesta iniciativa ou ficar para outra?
  Este plano só as atualiza, sem criar ADRs novos além do 015.

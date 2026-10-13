# Checklist: atualização do `AGENTS.md` e Princípios Fundamentais

Plano, decisões (DA-xx), inventário (Dxx) e riscos em [`plan.md`](plan.md). Marque `[x]` ao concluir cada item.

Convenções de verificação (PowerShell, na raiz do repositório):

- Tamanho do núcleo: `(Get-Item AGENTS.md).Length` (bytes).
- Busca de termos: `git grep -n -E "<regex>" -- AGENTS.md docs/agents`.
- Definição de pronto: mudança só em documentação dispensa os itens 1 e 2 do `AGENTS.md` §3. As Tarefas 10 e 12 tocam
  `src/test` e exigem `.\gradlew.bat build --console=plain`.

---

## Fase 1: inventário e fundações

### Tarefa 1: completar o inventário de divergências

**Descrição:** verificar cada afirmação factual do `AGENTS.md` ainda não coberta pela seção 3 do `plan.md` (caminhos,
nomes de classe, propriedades, comportamentos de §4.4 a §7) e registrar as novas divergências como D19 em diante, com
evidência.

**Critérios de aceite:**

- [x] Toda afirmação verificável das seções 4.4 a 7 foi conferida; divergências novas estão na tabela da seção 3 do
  `plan.md`, cada uma com arquivo e linha ou comando.
- [x] Afirmações não verificáveis estaticamente (ex.: "o Kotlin 2.4.21 sobrepõe o 2.3.x do BOM") foram conferidas com o
  comando indicado ou marcadas "não verificado".

**Verificação:**

- [x] `.\gradlew.bat dependencyInsight --dependency kotlin-stdlib --configuration runtimeClasspath --console=plain`
  confirma a versão efetiva do Kotlin.
- [x] Revisão manual: nenhuma linha da tabela sem evidência.

**Dependências:** nenhuma. **Arquivos:** `docs/tasks/plan.md`. **Tamanho:** S.

### Tarefa 2: ADR-015 (`proposta`) com a convenção de KDoc

**Descrição:** recuperar do histórico o texto do ADR-KDOC-001 e registrá-lo como ADR formal, no modelo do ADR-011.

**Critérios de aceite:**

- [x] `docs/adr/0015-convencao-de-kdoc.md` existe, com título `# ADR-015: ...`, `- **Status:** proposta`,
  `- **Relação com o guia:**` e as seções Contexto, Decisão e Consequências.
- [x] A decisão descreve o formato vigente (resumo + **Responsabilidade:**, pt-BR, sem adjetivos vazios, KDoc de uma
  linha em membros não óbvios) e cita o commit de origem do ADR-KDOC-001.

**Verificação:**

- [x] `git log --oneline -S "ADR-KDOC-001" -- docs/tasks/plan.md` encontra o commit de origem.
- [x] Revisão manual: o texto não contradiz os KDocs existentes (amostra de 5 classes).

**Dependências:** nenhuma. **Arquivos:** `docs/adr/0015-convencao-de-kdoc.md`. **Tamanho:** XS.

### Tarefa 3: mover o texto atual para `docs/agents/` sem alterar conteúdo

**Descrição:** criar os cinco arquivos de detalhe da seção 2.1 do `plan.md` com o texto atual copiado literalmente (só
cabeçalhos renumerados a partir de 1) e substituir essas seções no `AGENTS.md` por um parágrafo com link.

**Critérios de aceite:**

- [x] Existem `docs/agents/divergencias-do-guia.md`, `arquitetura.md`, `regras-de-codigo.md`, `testes.md` e
  `build-e-ci.md`, cada um com uma linha inicial dizendo de que seção do `AGENTS.md` veio e que o núcleo prevalece em
  conflito.
- [x] Fora os cabeçalhos, o texto movido é idêntico ao original (nenhuma correção nesta tarefa).
- [x] O `AGENTS.md` aponta para cada arquivo com link relativo válido.

**Verificação:**

- [x] Conservação: `git diff --word-diff --stat` e conferência por amostragem de que cada parágrafo removido do
  `AGENTS.md` aparece em `docs/agents/`.
- [x] Links: todo `](docs/agents/...)` do `AGENTS.md` aponta para arquivo existente.

**Dependências:** nenhuma (pode começar junto com 1 e 2). **Arquivos:** `AGENTS.md` + 5 arquivos novos. **Tamanho:** M
(mecânico).

## Checkpoint 1: fundações

- [x] Tarefas 1 a 3 concluídas.
- [x] Inventário completo e ADR-015 criado.
- [x] Revisão humana do diff de movimentação antes de qualquer correção de conteúdo.

---

## Fase 2: correção de conteúdo

### Tarefa 4: núcleo §1 a §3 (stack, leitura obrigatória, comandos, modo de trabalho)

**Descrição:** corrigir D01, D02, D04, D05, D06, D17 e as divergências novas desta área no núcleo.

**Critérios de aceite:**

- [x] Cabeçalho com Kotlin 2.4.21, Gradle 9.8.1 e a fonte de cada versão; §1 lista ADR-014 e ADR-015 e o mapa de
  `docs/agents/`.
- [x] §2 não diz mais que não há `src/test`; mantém o aviso de que `--tests` sem correspondência passa; oferece
  `git grep -n -E` como alternativa ao ripgrep.
- [x] §3 descreve `docs/tasks/` sem citar iniciativa específica e remove a menção ao ADR-KDOC.

**Verificação:**

- [x] `git grep -n -E "2\.4\.20|9\.7\.1|ADR-KDOC|NO-SOURCE" -- AGENTS.md` sem resultado (ou só em contexto correto,
  justificado).

**Dependências:** 1, 2, 3. **Arquivos:** `AGENTS.md`. **Tamanho:** S.

### Tarefa 5: `docs/agents/testes.md` reflete a suíte real e o ADR-014

**Descrição:** reescrever a parte desatualizada do guia de testes (D05, D08, D10–D16), substituindo os esqueletos
copiados por referências aos arquivos reais (DA-07).

**Critérios de aceite:**

- [x] Stack de teste igual ao `build.gradle.kts` (WireMock, ArchUnit, Awaitility dentro; MockK e mockito-kotlin fora,
  com o helper `any()` de `MockitoSupport.kt`).
- [x] `*IT` descritos pelo padrão real: herdar `IntegrationTestSupport`, perfil `test` em `application-test.yaml`,
  `TestcontainersSupport` com `postgres:18.6` e `apache/kafka:4.3.1`, exceção do `LocaSignE2EIT` com WireMock.
- [x] Nenhum trecho de código copiado que não compile contra o código atual; exemplos apontam para arquivos existentes.

**Verificação:**

- [x] 
  `git grep -n -E "PostgresTestcontainers|JdbcProcessedMessagesAdapterIT|kafka-native|ainda não houver testes|MockRestServiceServer\.bindTo" -- docs/agents/testes.md`
  sem resultado.
- [x] Todo caminho `src/test/...` citado existe (`Test-Path`).

**Dependências:** 3. **Arquivos:** `docs/agents/testes.md`. **Tamanho:** M.

### Tarefa 6: `arquitetura.md` e `divergencias-do-guia.md`

**Descrição:** registrar o `HexagonalArchitectureTest` como verificação principal de fronteiras, com a busca textual
como complemento (D07, D17); conferir a árvore de pacotes e as exceções; atualizar as linhas da tabela de divergências
(D07, D08).

**Critérios de aceite:**

- [x] A checagem de fronteiras diz que o ArchUnit roda no `./gradlew test`, explica seu limite (value classes e
  `const val`) e mantém as regex como complemento, com a forma `git grep` para PowerShell.
- [x] A árvore de pacotes confere com `src/main/kotlin` (inclui `ProviderErrors.kt` e `MalformedWebhookPayload`).
- [x] A tabela de divergências não afirma mais que faltam ArchUnit e WireMock; cita o ADR-014 onde ele resolveu a
  divergência.

**Verificação:**

- [x] `git grep -n -E "não há teste de arquitetura|fora do build" -- docs/agents` só retorna linhas corretas.
- [x] Cada regex da checagem de fronteiras, rodada com `git grep -n -E` em `src/main/kotlin`, devolve o resultado
  esperado (vazio, salvo a ponte `LeaseBridgeAdapters`).

**Dependências:** 3. **Arquivos:** `docs/agents/arquitetura.md`, `docs/agents/divergencias-do-guia.md`. **Tamanho:** S.

### Tarefa 7: `regras-de-codigo.md` e `build-e-ci.md`

**Descrição:** corrigir D03, D06 (KDoc → ADR-015), D09, D02 e as divergências novas dessas áreas.

**Critérios de aceite:**

- [x] KDoc cita o ADR-015; springdoc aparece sem número de versão fixo (fonte: catálogo) ou com 3.1.1.
- [x] Governança de dependências lista o catálogo real (Kotlin, Boot, dependency-management, springdoc, WireMock,
  ArchUnit) e o wrapper 9.8.1.
- [x] Tabela de CI confere com `.github/workflows/*.yml` e `.github/dependabot.yml`.

**Verificação:**

- [x] `git grep -n -E "3\.1\.0|9\.7\.1|ADR-KDOC" -- docs/agents` sem resultado.

**Dependências:** 2, 3. **Arquivos:** `docs/agents/regras-de-codigo.md`, `docs/agents/build-e-ci.md`. **Tamanho:** S.

## Checkpoint 2: conteúdo correto

- [x] Tarefas 4 a 7 concluídas; todas as divergências D01–D18 (e as novas da Tarefa 1) resolvidas ou justificadas.
- [x] Revisão humana do diff de conteúdo.

---

## Fase 3: princípios e núcleo

### Tarefa 8: seção §4 Princípios Fundamentais

**Descrição:** escrever a seção a partir do conteúdo-guia da seção 2.2 do `plan.md`: tabela princípio × aplicação
idiomática em Kotlin 2.4+ / Spring Boot 4.1+ × limite, mais a regra de precedência (DA-06).

**Critérios de aceite:**

- [x] Cobre SRP, OCP, LSP, ISP, DIP, DRY, KISS, YAGNI, Boy Scout Rule e SLAP, cada um com ao menos um exemplo real
  do código e um limite explícito.
- [x] Contém a regra de precedência: princípios não sobrepõem R1–R10, ADRs nem as regras invioláveis; Boy Scout fica
  restrito ao trecho tocado e não altera comportamento nem as exceções documentadas.
- [x] Ocupa no máximo ~4 000 bytes.

**Verificação:**

- [x] Todo símbolo citado existe: `git grep -n -w "<Símbolo>" -- src/main/kotlin` para cada um.
- [x] Revisão manual: nenhum exemplo marcado "(confirmar)" no texto final.

**Dependências:** checkpoint 2. **Arquivos:** `AGENTS.md`. **Tamanho:** S.

### Tarefa 9: resumos do núcleo (§5 a §8) dentro do orçamento de bytes

**Descrição:** substituir os parágrafos-link da Tarefa 3 por resumos: arquitetura (camadas, grafo de módulos),
regras invioláveis, testes e build/CI, cada um dizendo quando o arquivo de detalhe é leitura obrigatória.

**Critérios de aceite:**

- [x] §6 lista todas as proibições hoje espalhadas (`!!`, `else` em `when` selado, `@Transactional`, JPA/WebFlux/
  OpenFeign/MapStruct/Spring Security, `KafkaTemplate` fora do relay, `Instant.now()` em `domain`/`app`,
  `java.util.UUID` em `domain`/`app`, `require` para regra de negócio, PandaDoc na requisição, resposta 410, PII em
  log, migration aplicada alterada, dependência sem ADR, `git commit`/`git push`, `.env*`).
- [x] Numeração final: §1 a §8 conforme a seção 2.1 do `plan.md`; nenhuma referência interna aponta para seção
  inexistente.
- [x] `(Get-Item AGENTS.md).Length` ≤ 22 000 (efetivo: 21.070 bytes).

**Verificação:**

- [x] `git grep -n -o -E "seção [0-9]+(\.[0-9]+)?|§[0-9]+(\.[0-9]+)?" -- AGENTS.md` conferido contra os títulos.
- [x] Medição de bytes registrada no resumo da tarefa (21.070 bytes).

**Dependências:** 8. **Arquivos:** `AGENTS.md`. **Tamanho:** M.

---

## Fase 4: referências e validação

### Tarefa 10: referências em `src/test` e no perfil de teste

**Descrição:** trocar as citações a seções antigas do `AGENTS.md` (seção 3.1 do `plan.md`) pelas novas localizações.
Só KDoc e comentários.

**Critérios de aceite:**

- [x] `HexagonalArchitectureTest.kt`, `IntegrationTestSupport.kt`, `IntegrationTestSupportIT.kt`, `ContractFakes.kt`,
  `LocaSignE2EIT.kt` e `application-test.yaml` citam a seção ou o arquivo de destino correto.
- [x] Nenhuma linha de código executável alterada.

**Verificação:**

- [x] `git diff --stat -- src/test` mostra só esses arquivos; o diff contém apenas linhas de comentário.
- [x] `.\gradlew.bat test --tests "*Test" --console=plain` verde e com testes executados.

**Dependências:** 9. **Arquivos:** 5 arquivos `.kt` + 1 `.yaml` (comentários). **Tamanho:** S.

### Tarefa 11: referências em README, ADR-014 e guia

**Descrição:** atualizar as citações de seção nos documentos vivos, sem alterar decisões.

**Critérios de aceite:**

- [x] `README.md` cita a nova localização da checagem de fronteiras e o ADR-015 na seção de KDoc.
- [x] `docs/adr/0014-...md` aponta para `docs/agents/testes.md` e para a checagem de fronteiras nova; decisão e status
  inalterados.
- [x] `docs/arquitetura-tecnica.md` §17 ganha uma nota apontando para o núcleo e `docs/agents/`, sem reescrever o guia.

**Verificação:**

- [x] `git grep -n -E "AGENTS\.md.{0,40}(§|seção) ?[0-9]" -- README.md docs/adr docs/arquitetura-tecnica.md`: todas
  as ocorrências apontam para seções existentes.

**Dependências:** 9. **Arquivos:** `README.md`, `docs/adr/0014-estrategia-e-ferramentas-de-testes.md`,
`docs/arquitetura-tecnica.md`. **Tamanho:** S.

### Tarefa 12: validação final

**Descrição:** verificar links, termos obsoletos, tamanho e build, e submeter o núcleo a uma revisão com contexto
limpo.

**Critérios de aceite:**

- [x] Todos os links relativos de `AGENTS.md` e `docs/agents/*.md` resolvem para arquivos existentes.
- [x] Nenhum termo obsoleto do inventário aparece fora de contexto histórico.
- [x] Um subagente com contexto limpo, lendo só o `AGENTS.md`, responde corretamente a perguntas de controle (ex.:
  "como publico um evento?", "onde fica o teste de fronteiras?", "posso usar `@Transactional`?", "que imagem de Kafka
  uso em teste?", "o que a Boy Scout Rule permite aqui?").
- [x] Limites de tamanho de arquivo de instrução dos agentes citados no cabeçalho foram conferidos na documentação
  oficial, ou registrados como "não verificado".

**Verificação:**

- [x] 
  `git grep -n -E "2\.4\.20|9\.7\.1|3\.1\.0|ADR-KDOC|PostgresTestcontainers|kafka-native|não há teste de arquitetura" -- AGENTS.md docs/agents`
  sem resultado indevido.
- [x] `(Get-Item AGENTS.md).Length` ≤ 22 000 (efetivo: 21.070 bytes).
- [x] `.\gradlew.bat build --console=plain` verde, com `:test` executando (112 testes aprovados).
- [x] `git status` mostra só os arquivos previstos no plano.

**Dependências:** 10, 11. **Arquivos:** nenhum novo (correções pontuais, se a validação apontar). **Tamanho:** S.

## Checkpoint final

- [x] Todos os critérios de aceite das Tarefas 1 a 12 atendidos.
- [x] Resumo final com os conceitos aplicados e a lista de arquivos alterados para revisão (`git diff`).
- [x] Nenhum `git commit`/`git push` executado; commit fica a cargo do humano.

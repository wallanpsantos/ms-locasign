# Plano: simplificação do código produtivo

Iniciativa de simplificação de todo o código de `src/main` (Kotlin e `application*.yaml`; as migrations são imutáveis e
ficam fora), sem mudar comportamento. O checklist executável, com critérios de aceite e verificação por tarefa, está em
[`todo.md`](todo.md).

- **Data da análise:** 2026-10-13.
- **Commit base:** a análise rodou em `672a2ad` (`📚 docs: reestrutura AGENTS.md e adiciona princípios fundamentais`),
  cujo `src/main` é o mesmo de `07bd715`. Antes da entrega, a `main` recebeu `061b8ec` e `abb0ed6` (formatação dos logs
  no perfil local), que só alteram `src/main/resources/application-local.yaml`, arquivo que nenhuma tarefa toca. A
  referência para conferir desvios passa a ser `abb0ed6`.
- **Situação:** Fase 0 e Fase 1 (T00 a T03) executadas em 2026-10-14, sem commit, com revisão adversarial e teste de
  mutação (19 de 20 mutantes mortos; o sobrevivente, o valor de `NO_SIGNATURE_DEADLINE`, é equivalente). Próxima: Fase 2
  (T04).

## 1. Objetivo e escopo

Deixar o código mais fácil de ler e manter, preservando exatamente o comportamento observável: tipos e mensagens de
exceção (que viram o `detail` do Problem Details), ordem de efeitos, fronteiras de transação, SQL, JSON, `eventType`,
nomes de tópicos e propriedades, textos de log e registro de beans.

Fora do escopo: correção de bugs, mudança de contrato público (REST, Kafka, schema, propriedades), correção das exceções
arquiteturais de `docs/agents/arquitetura.md` §7 e qualquer item que exija ADR. Esses achados estão na seção 7, para
decisão em outra iniciativa.

## 2. Como o plano foi produzido

1. O código produtivo (cerca de 7.200 linhas em 105 arquivos) foi dividido em seis fatias: domínio de todos os módulos,
   `contract/app`, `contract/infra/pandadoc` com `contract/infra/config`, os demais adapters e as interfaces de
   `contract`, `lease` e `notification` (exceto o domínio) e `shared` com o YAML.
2. Um analista por fatia leu todos os arquivos e propôs simplificações, cada uma com prova de preservação de
   comportamento, testes que cobrem o trecho e pontos que devem ficar como estão.
3. Três verificadores adversariais, um por par de fatias, abriram o código citado e tentaram refutar cada achado
   (critérios do `AGENTS.md` §4 e §6 e de `docs/agents/*.md` no `672a2ad`).
4. As fatias de PandaDoc, adapters de `contract`, `lease`/`notification` e `shared` passaram por **duas rodadas
   independentes de análise**. Os achados que só a primeira rodada viu, sem passar pelo verificador, foram conferidos no
   código pelo coordenador; a coluna "Verificação" da seção 4 indica quem verificou cada um.

Resultado: 27 simplificações de código aceitas, agrupadas em 22 tarefas (uma delas opcional), 4 tarefas de KDoc, 6
propostas descartadas (seção 5) e 21 achados fora de escopo (seção 7). Antes da entrega, três revisores adversariais
conferiram o próprio plano e o checklist contra o código; as 23 correções que apontaram já estão incorporadas.

## 3. Decisões da iniciativa

| ID    | Decisão                                                                                                                                                                                                                                                                                                                                                           |
|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DS-01 | O plano fica em `docs/tasks/simplificacao/`, sem sobrescrever `docs/tasks/plan.md` e `todo.md` (iniciativa do `AGENTS.md`, concluída). O `AGENTS.md` §3 pede confirmação antes de sobrescrevê-los; se preferir o local padrão, basta mover os dois arquivos.                                                                                                      |
| DS-02 | Comportamento idêntico é critério de aceite de toda tarefa. Na dúvida sobre preservação, a mudança não entra.                                                                                                                                                                                                                                                     |
| DS-03 | **Rede de segurança antes da mudança.** Quando o trecho não tem teste que o exercite, a tarefa começa com um teste de caracterização que passa no código atual e continua passando depois. Testes existentes não são alterados, exceto o ajuste mecânico do fake `FakeLeaseActivation` na T16; acrescentar casos novos a classes de teste existentes é permitido. |
| DS-04 | Ordem: linha de base, domínio, aplicação, adapters e interfaces de `contract`, `lease`/`notification`/`shared`, KDoc e verificação final. O KDoc vem por último para descrever o código já simplificado e não reescrever blocos que outras tarefas mudam.                                                                                                         |
| DS-05 | O agente não commita (`AGENTS.md` §3 e §6, item 1). Se o usuário pedir commits, um por tarefa: `♻️ refactor(<escopo>): ...` para código, `🧪 test(<escopo>): ...` quando a tarefa só acrescentar testes e `📚 docs(<escopo>): ...` para KDoc.                                                                                                                     |
| DS-06 | Sobreposições resolvidas: a troca por `isAwaitingSignatures` vale só para `CancelContract` (T04), porque em `SendContract.complete` seria preferência; as três propostas de `aggregateUuid()` viram uma só (T14), com KDoc restrito a envelopes cujo agregado tem id UUID; o KDoc de `OutboxRow` fica com a T18; o KDoc de `lease` (T25) vem depois de T16 e T17. |
| DS-07 | Nenhuma tarefa exige ADR nem muda contrato público. Se a execução revelar o contrário, a tarefa para e o ponto vai para a seção 7.                                                                                                                                                                                                                                |
| DS-08 | KDoc novo cita as regras pela localização atual (`docs/agents/regras-de-codigo.md §N`, `docs/agents/arquitetura.md §N`, `AGENTS.md §N`), nunca as seções antigas do `AGENTS.md` (4.x e 5.x), que deixaram de existir no `672a2ad`.                                                                                                                                |
| DS-09 | Os fakes novos seguem `docs/agents/testes.md` §4 (`InMemory*`, `Fixed*`, `NoOp*`, `Immediate*`; o projeto também usa `Recording*`) e ficam em `<módulo>/app/fakes/`. O `FixedSignatureProvider` não muda, porque é o bean do `IntegrationTestSupport`; quem precisar registrar chamadas ao provedor cria um `RecordingSignatureProvider`.                         |

## 4. Inventário dos achados aceitos

Risco: B = baixo, M = médio. Verificação: **V** = verificador adversarial; **V2** = confirmado nas duas rodadas e pelo
verificador; **C** = conferido pelo coordenador (só a primeira rodada viu).

| Tarefa  | Arquivo(s) principal(is)                                                                               | Simplificação                                                                                                                         | Risco | Verificação    |
|---------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|-------|----------------|
| T01     | `contract/domain/models/Contract.kt`                                                                   | Um helper `appendHistory` e o atalho `recordInfo` no lugar de quatro construções de `StatusHistoryEntry`                              | B     | V              |
| T02     | `Contract.kt`                                                                                          | `effectiveCancelReason()` remove o ramo `null` inalcançável de `eventFor`; constantes `DEFAULT_CANCEL_NOTE` e `NO_SIGNATURE_DEADLINE` | B     | V              |
| T03     | `ContractParts.kt`, `ContractEntityMappers.kt`                                                         | Formato da coluna `cancel_reason` sai do domínio e vai para os mappers de persistência                                                | B     | V              |
| T04     | `contract/app/usecases/CancelContract.kt`                                                              | `isAwaitingSignatures` no lugar da aritmética de `progress`                                                                           | B     | V              |
| T05     | `ApplyProviderUpdate.kt`                                                                               | Helper `failGeneration(...)` paralelo a `change(...)`                                                                                 | B     | V              |
| T06     | `ReceiveProviderWebhook.kt`                                                                            | Extrai `outboxMessage(...)` (5 níveis de aninhamento) e usa `HexFormat` no SHA-256                                                    | B     | V              |
| T07     | `CreateProviderDocument.kt`                                                                            | Separa as guardas de idempotência de `prepare` da montagem `documentRequest(...)`                                                     | B     | V              |
| T08     | `ArchiveSignedDocument.kt`                                                                             | Segunda transação vira `complete(...)`, como nos irmãos de duas transações                                                            | M     | V (ajustado)   |
| T09     | `contract/infra/pandadoc/dto/response/PandaDocResponses.kt`                                            | Remove campos de resposta nunca lidos, inclusive `RecipientResponse.email` (R10)                                                      | B     | V2             |
| T10     | `PandaDocSignatureProviderAdapter.kt`, `PandaDocMappers.kt`                                            | `ProviderSendRequest.toPandaDoc()` em `mappers/`                                                                                      | B     | V2             |
| T11     | `ContractQueryAdapter.kt` → `PostSignatureActionsAdapter.kt`                                           | Adapter de escrita sai do arquivo do adapter de leitura                                                                               | B     | V2             |
| T12     | `PandaDocWebhookController.kt`                                                                         | `HttpStatus.UNAUTHORIZED` no lugar de `const val UNAUTHORIZED = 401`                                                                  | B     | V2             |
| T13     | `contract/interfaces/web/mappers/ContractWebMappers.kt`                                                | Argumentos nomeados em `SignerView`/`HistoryEntryView.toResponse()` (até 7 `String` seguidos)                                         | B     | C              |
| T14     | `shared/interfaces/messaging/EventEnvelope.kt` e os dois consumidores                                  | `EventEnvelope.aggregateUuid()` no lugar da validação duplicada em `contract` e `notification`                                        | B     | V2             |
| T15     | `notification/**`                                                                                      | `contractId` tipado como `Uuid` até o adapter (sem `UUID.fromString`); porta renomeada para `notificationLog`                         | B     | V2             |
| T16     | `ActivateLease.kt`, `contract/app/ports/out/lease/LeasePorts.kt`, ponte, fake                          | Remove o retorno `Boolean` que nenhum chamador lê                                                                                     | B     | C              |
| T17     | `LeaseAdapters.kt`, `LeaseWebMappers.kt`                                                               | `findCurrentContract(...)` nomeia a leitura cruzada; `CurrentContractView.toResponse()`                                               | B     | V2             |
| T18     | `shared/infra/messaging/OutboxRelay.kt`                                                                | Remove `OutboxRow.eventType` (lido e nunca usado) e extrai `lockNextBatch()`                                                          | B     | V2             |
| T19     | `DeadLetterReplayer.kt`                                                                                | Extrai a montagem do registro republicado (5 níveis de aninhamento)                                                                   | B     | V2             |
| T20     | `ApiExceptionHandler.kt`                                                                               | Um `problemDetail(...)` e um `validationProblem(...)` no lugar de três montagens à mão                                                | B     | V2             |
| T21     | `CorrelationIdFilter.kt`, `OperatorAccessGuard.kt`                                                     | `private companion object` (constantes sem uso externo)                                                                               | B     | V              |
| T22     | `contract/infra/pandadoc/SlidingWindowRateLimiter.kt`                                                  | **Opcional.** Extrai `tryReserve(...)` e tira o `return` não local de dentro do `synchronized`                                        | B     | C (divergente) |
| T23–T26 | KDoc de `contract` (domínio e app), `contract` (infra e interfaces), `lease`/`notification` e `shared` | Corrige regras citadas erradas, afirmações falsas, links que não resolvem e adjetivos vazios (ADR-015)                                | B     | V e C          |

Sobre a T22: as duas rodadas discordaram. A primeira propôs a extração; a segunda considerou o código já direto. Fica
como opcional, condicionada ao teste de caracterização do limitador.

## 5. Propostas descartadas (não reabrir)

| Proposta                                                                                    | Motivo                                                                                                                                                                                                             |
|---------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Juntar as três rejeições de `Cpf.of` numa checagem `hasValidCheckDigits`                    | São guard clauses lineares, cobertas pelo `ValueObjectsTest`; o nome proposto engana (sequências repetidas passam no módulo 11). Fragmentaria um value object só para reduzir linhas (`AGENTS.md` §4, SRP e SLAP). |
| Trocar a guarda de `SendContract.complete` por `isAwaitingSignatures \|\| isFinal`          | Equivalente, mas é um complemento disfarçado de "não está em DRAFT nem GENERATED"; o comentário atual descreve a forma com `progress`. Preferência.                                                                |
| Extrair o download de `ArchiveSignedDocument` para `storeSignedPdf(): Pair<String, String>` | O `Pair` esconde qual componente é o tipo e qual é a localização; os irmãos mantêm a chamada externa no `execute`. Fragmentação sem ganho (SLAP).                                                                  |
| Tornar `formatBrl`/`formatTerm` privados em `PandaDocMappers`                               | Só muda o modificador; o `HexagonalArchitectureTest` já impede o reuso por outros módulos. Churn. Pode entrar pela regra do escoteiro se a T10 já estiver editando o arquivo.                                      |
| Reescrever `ContractEvent.toPayload()` com `mapOf(...) + when`                              | Troca de estilo num contrato Kafka (ADR-009) sem teste do conteúdo; o `MutableMap` local é permitido.                                                                                                              |
| Trocar links KDoc totalmente qualificados por links curtos                                  | Churn; o link qualificado é forma aceita no projeto (`ContractBeansConfig`).                                                                                                                                       |

## 6. Pontos avaliados e mantidos (cerca de Chesterton)

Parecem complexos, mas têm razão de existir. A próxima sessão não deve reabri-los como simplificação.

- **`ContractBeansConfig` (cerca de 190 linhas de `@Bean`)** e os demais `*BeansConfig`: registro explícito exigido por
  `docs/agents/arquitetura.md` §1 e §5; trocar por `@Import`, varredura ou `BeanRegistrar` muda nomes de beans e exige
  ADR. O nome `signatureProvider` é contrato com `IntegrationTestSupport` (`@TestBean`).
- **Construtor privado de `Contract` e `restore(...)` com 16 parâmetros:** formato obrigatório de agregado
  (`docs/agents/regras-de-codigo.md` §2).
- **`failGeneration`/`fail` quase iguais em `CreateProviderDocument` e `SendContract`:** extrair exigiria uma função com
  cinco dependências ou um bean novo; o bloco explícito mostra o `markProcessed` como primeira instrução
  (`regras-de-codigo.md` §4).
- **Prólogo `markProcessed` + `findById ?: return` nos use cases de duas transações:** é o idioma literal da regra de
  idempotência; um helper com lambda esconderia a "primeira instrução".
- **`TransitionDecision`, `TransitionResult` e `HistoryOutcome`:** papéis distintos (decisão com motivo, resultado para
  o
  chamador, valor persistido com `CHECK`).
- **Quatro jobs com `try/catch` quase idênticos (os três de `ContractJobs` e o `HousekeepingJob`):** formato prescrito
  para jobs (`regras-de-codigo.md` §3); o `IntegrationTestSupportIT` confere a ausência de cada bean.
- **`ContractEvent.toPayload()` com um ramo por evento, inclusive os vazios:** o `when` exaustivo obriga a tratar evento
  novo; o `linkedMapOf` fixa `contractId` como primeira chave.
- **Roteamento de consumidores por literal `"ContractRequested"` etc.:** o `eventType` é contrato de mensagem (ADR-009);
  `::class.simpleName` esconderia que renomear quebra mensagens gravadas.
- **`completedRoles` no gateway vs. `toState` no mapper:** um tolera `JsonNode` malformado, o outro usa DTO tipado;
  unificar muda a tolerância a webhooks estranhos.
- **`DecimalFormat` criado a cada chamada em `formatBrl`:** `DecimalFormat` não é thread-safe.
- **`try/catch` de `Unavailable` com `cause is Unauthorized` no download:** 401 do sandbox no download protegido
  (ADR-012).
- **`SendDocumentRequest.silent` e `StatusChangeRequest.notifyRecipients` sempre `false`:** saem no JSON; removê-los
  muda
  o corpo enviado.
- **`OutboxRelay`:** envio dentro da transação com `FOR UPDATE SKIP LOCKED`, `do-while` para escoar acúmulo, gauge com
  `Instant.now()` (exceção documentada) e `catch` que devolve `0.0`.
- **`KafkaConfig` com quatro beans `NewTopic` e um único `DefaultErrorHandler`:** regra de Kafka do projeto.
- **Campos não lidos de `EventEnvelope` (`schemaVersion`, `occurredAt`, `aggregateType`, `causationId`):** documentam o
  envelope canônico; removê-los muda a tolerância da desserialização.
- **`ContractRepositoryAdapter.isActiveContractViolation`:** a causa raiz é a única que traz o nome da constraint.
- **`ContractQueryAdapter.findHistory` com `EXISTS` separado:** a porta distingue contrato inexistente (404) de
  histórico
  vazio.
- **`sortedBy { it.order.value }` em `ContractEntityMappers`:** `@MappedCollection` sobre `Set` não preserva ordem (R3).
- **Defaults explícitos no `application.yaml`** (`acks`, idempotência, `enable-auto-commit`, `concurrency`): documentam
  garantias exigidas e protegem contra mudança de default.
- **`requireValidName` não devolve o nome aparado (`2dad603`)** e **`PandaDocErrors` (`3143011`):** simplificados
  recentemente; não reabrir.

## 7. Fora de escopo: achados para outras iniciativas

Nenhum destes itens é simplificação: todos mudam comportamento, contrato ou documentação externa. "Confirmado" indica
que
um verificador conferiu a evidência no código; "conferido" indica conferência do coordenador; "não verificado" indica
que só um analista apontou.

### 7.1 Possíveis bugs (corrigir com teste que falhe antes, `AGENTS.md` §3)

| ID | Local                                                    | Achado                                                                                                                                                                                                                                                                                                   | Situação   |
|----|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| B1 | `OutboxRelay.kt:80-93` e `markFailed`                    | A ordem por (tópico, chave) só é garantida dentro de um lote: a linha que falha recebe `available_at` futuro, mas as seguintes da mesma chave continuam selecionáveis e podem ser publicadas antes dela.                                                                                                 | confirmado |
| B2 | `ApplyProviderUpdate.kt:52-62`                           | Correlação por `contractHint` sem conferir `providerDocumentId`: sinais de um documento órfão (retentativa de criação) podem mover o status de um contrato já ligado a outro documento.                                                                                                                  | confirmado |
| B3 | `SendContract.kt:100-104`                                | `SENT_OR_BEYOND` não inclui `DECLINED`: numa reentrega com recusa já feita, o contrato termina como falha de geração e o webhook `DECLINED` é ignorado.                                                                                                                                                  | confirmado |
| B4 | `LeaseController.kt:41`                                  | `fromCurrentRequest()` copia a query string para o `Location` do 201.                                                                                                                                                                                                                                    | confirmado |
| B5 | `SlidingWindowRateLimiter.kt:31-35`                      | Com `PANDADOC_RATE_LIMIT_PER_MINUTE` ≤ 0, `window.first()` lança `NoSuchElementException` em toda chamada (vai para retentativas e DLT); não há validação na subida.                                                                                                                                     | conferido  |
| B6 | `RegisterLease.kt:85-94`, `LeaseParties.kt:41`           | Signatário padrão mal configurado (`AGENCY_SIGNER_*`) responde 422 com `field` `agencySigner.email` ou `agencySigner.name`, campos que o cliente não enviou (`Email.of(email, "agencySigner.email")` com o valor padrão).                                                                                | conferido  |
| B7 | `ReceiveProviderWebhook.kt:69`, `WebhookInboxPort.kt:12` | O corpo é decodificado com `toString(Charsets.UTF_8)` antes de `inbox.store`, que recebe `String`; bytes inválidos viram U+FFFD e o `raw_body` deixa de ser byte a byte (R7). Corrigir mexe na porta e no schema (ADR).                                                                                  | conferido  |
| B8 | `DeadLetterReplayer.kt:46-63`, `KafkaConfig.kt:64-67`    | O replay de uma mensagem ilegível não termina: republicada no tópico original, ela vira `UnreadableMessageException` nos três grupos do tópico, volta à DLT na hora (multiplicada por 3) e é relida pela mesma chamada de `replay`, que só sai depois de duas leituras vazias de 2 s; o lock fica preso. | conferido  |

### 7.2 Dados pessoais (R10, `regras-de-codigo.md` §5)

| ID | Local                                                  | Achado                                                                                                                                                                            | Situação   |
|----|--------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| P1 | `PandaDocWebhookGateway.kt:114-123`                    | `failureDetail` trunca mas não mascara e-mails, ao contrário de `PandaDocErrors.sanitized()`; o texto chega ao payload Kafka de `ContractGenerationFailed`, o que a regra proíbe. | confirmado |
| P2 | `ContractQueryAdapter.kt:37`, `LeaseAdapters.kt:83-85` | `GET /contracts/{id}` e `GET /leases/{id}` devolvem e-mails completos numa API sem autenticação. Mascarar muda o contrato REST: decisão de produto.                               | confirmado |

### 7.3 Robustez e observabilidade

| ID | Local                                                    | Achado                                                                                                                                                                                                                                                       | Situação       |
|----|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| O1 | `OutboxRelay.kt:56-102`                                  | Com o broker fora, a transação do lote pode durar cerca de `batchSize × sendTimeout` (100 × 10 s); um envio que estoura o `get` ainda pode ser entregue depois.                                                                                              | confirmado     |
| O2 | `NotificationConsumer.kt:31`                             | Não põe `contractId` no MDC, ao contrário dos consumidores de `contract`.                                                                                                                                                                                    | confirmado     |
| O3 | `LeaseAdapters.kt:31`, `ContractRepositoryAdapter.kt:39` | `save` descarta a entidade devolvida pelo Spring Data JDBC, e o agregado mantém o `rowVersion` antigo (`val`): um segundo `save` da mesma instância tentaria outro INSERT (agregado novo) ou falharia no lock otimista (armadilha latente, sem fluxo atual). | confirmado     |
| O4 | `OperatorAccessGuard.kt:27-34`                           | O 403 sai com `type: about:blank`, fora do padrão `/problems/<slug>`.                                                                                                                                                                                        | não verificado |
| O5 | `KafkaHealthIndicator.kt:18`                             | Mensagem de log em inglês (`"Kafka health check failed"`, passada ao construtor de `AbstractHealthIndicator`), contra `regras-de-codigo.md` §1.                                                                                                              | conferido      |

### 7.4 Documentação e contrato

| ID | Local                                                 | Achado                                                                                                                                                                                                                              | Situação   |
|----|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| D1 | `README.md` (tabela Configuração), `application.yaml` | Faltam no README `OUTBOX_RELAY_INTERVAL`, `CONTRACT_REMINDER_AFTER_DAYS` e `JOB_*_ENABLED`; `outbox.send-timeout`, `outbox.retention`, `pandadoc.connect-timeout`/`read-timeout`/`tenant-role`/`agency-role` não têm chave no YAML. | confirmado |
| D2 | `ContractApi.kt:50-52`                                | `reconcile` não documenta o 403 do token de operador. Corrigir altera o OpenAPI publicado.                                                                                                                                          | confirmado |
| D3 | `ContractQueryAdapter.kt:61`                          | O GET de contrato devolve `cancel_reason` cru, com os prefixos internos `REQUESTED: `/`GENERATION_FAILED: `: o formato de persistência virou contrato REST implícito (a T03 o preserva).                                            | confirmado |
| D4 | `docs/arquitetura-tecnica.md:718`                     | A tabela de schema lista `notifications_log` sem a coluna `event_id`, que existe na V1.                                                                                                                                             | confirmado |
| D5 | `docs/plano-de-testes.md:227` e outras linhas         | Caminho errado do teste do adapter da PandaDoc e referências a MockK/`@MockkBean`, fora do build.                                                                                                                                   | confirmado |
| D6 | `src/test/.../ContractFakes.kt:78-87`                 | `InMemoryPostSignatureActions` deduplica por (contrato, ação, status), e o banco por (contrato, ação): o fake não cumpre à risca o contrato da porta (LSP).                                                                         | confirmado |

As exceções arquiteturais já documentadas em `docs/agents/arquitetura.md` §7 (cancelamento e reconciliação chamando a
PandaDoc na requisição; SELECT em tabelas de outro módulo; `AdminController`; `HousekeepingJob`) foram reconfirmadas e
continuam pendentes de correção ou ADR, fora desta iniciativa.

## 8. Riscos e mitigação

| Risco                                                                                                                                                          | Mitigação                                                                                                                                                            |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Trecho sem teste quebra em silêncio (T02 a T08, T15, T19, T20 e T22 têm cobertura fraca ou nenhuma: só o `LocaSignE2EIT` ou um IT sem asserção sobre o trecho) | DS-03: o teste de caracterização entra primeiro e precisa passar no código atual.                                                                                    |
| O `LocaSignE2EIT` passa por um fluxo e não o verifica (notificações, payload do outbox)                                                                        | Não contar com ele como única rede: cada tarefa lista o teste direto que a protege.                                                                                  |
| Mudança de visibilidade ou de arquivo quebra o ArchUnit                                                                                                        | `HexagonalArchitectureTest` na verificação de cada tarefa que move ou cria tipos (T03, T11, T14, T15, T16).                                                          |
| Texto de exceção ou de log alterado sem querer                                                                                                                 | Critério de aceite explícito nas tarefas que tocam mensagens (T02, T04, T14, T15, T20); os testes de caracterização comparam mensagens.                              |
| Plano desatualizado quando a execução começar                                                                                                                  | A T00 confere se `src/main` mudou desde `abb0ed6` (`git diff --stat abb0ed6 -- src/main`); havendo mudança nos arquivos de uma tarefa, revalide-a antes de executar. |

## 9. Como retomar

1. Ler este arquivo e o [`todo.md`](todo.md).
2. Com Docker ligado, executar a **T00** (linha de base).
3. Seguir as tarefas na ordem do `todo.md`, marcando `[x]` em cada critério e verificação. Cada tarefa é independente
   para revisão, salvo as dependências indicadas.
4. Ao terminar cada tarefa, resumir os conceitos de Kotlin/Spring empregados (`AGENTS.md` §3, objetivo didático).

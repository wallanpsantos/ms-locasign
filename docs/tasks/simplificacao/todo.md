# Checklist: simplificação do código produtivo

Plano, decisões (DS-xx), inventário, descartes, pontos mantidos e achados fora de escopo em [`plan.md`](plan.md). Marque
`[x]` ao concluir cada item. Execute na ordem; as dependências estão em cada tarefa.

Convenções de verificação (PowerShell, na raiz do repositório, com Docker ligado quando houver `*IT`):

- Testes rápidos (sem Docker): `.\gradlew.bat test --tests "*Test" --console=plain`.
- Teste específico: `.\gradlew.bat test --tests "<FQN ou padrão>" --console=plain --rerun`.
- Compilação sem warnings novos: `.\gradlew.bat compileKotlin --console=plain`.
- Arquitetura: `.\gradlew.bat test --tests "*HexagonalArchitectureTest" --console=plain`.
- Confira na saída que os testes rodaram: um `--tests` sem correspondência também passa.

Regras que valem para todas as tarefas (DS-02 e DS-03):

- Comportamento idêntico: mesmas exceções e mensagens, ordem de efeitos, transações, SQL, JSON, logs e beans.
- Quando a tarefa pede teste de caracterização, ele é escrito **antes** da mudança, passa no código atual e continua
  passando depois, sem alteração (salvo o ajuste explícito indicado na tarefa).
- Testes existentes não são alterados, exceto o fake `FakeLeaseActivation` na T16 e o tipo de `contractId` no teste
  criado na T15. Acrescentar casos novos a uma classe de teste existente é permitido, inclusive um parâmetro injetado a
  mais no construtor.
- KDoc novo segue o ADR-015 e cita regras pela localização atual (`docs/agents/...`, DS-08).

As fases 0 a 4 (T00 a T22) estão concluídas e saíram deste checklist em 2026-10-14. O detalhamento de cada tarefa, com
critérios e verificação, está no histórico do Git (fases 0 a 3: commits `c28b581` a `d434510`; fase 4: os commits
seguintes a `9ba935d`). A T22 ficou só com o teste do limitador (`plan.md`, registro da Fase 4). A linha de base da
T00 continua no fim deste arquivo, porque a T27 a usa.

---

## Fase 5: KDoc (só comentários)

Mudança só em documentação: dispensa testes novos (`AGENTS.md` §3). Cada texto novo segue o ADR-015 (sem adjetivos
vazios, citando regra, ADR ou seção).

- O `compileKotlin` só detecta comentário mal fechado (por exemplo, um `/*` aninhado dentro do KDoc). O compilador não
  resolve links KDoc e o build não tem Dokka, então confira à mão cada link novo ou alterado: o alvo precisa estar
  importado, estar no mesmo pacote, ser membro do tipo documentado ou vir totalmente qualificado. Parâmetros de função
  citados no KDoc do tipo não resolvem e vão entre crases. Rode `compileKotlin` e os testes rápidos ao final.
- As linhas citadas nas T23 a T26 são do commit `672a2ad`. As fases 1 a 4 deslocam algumas delas. A T12 acrescenta um
  import, e o KDoc de `PandaDocWebhookController.kt:27` vai para a linha 28. A T10 e a T11 removem imports de
  `PandaDocSignatureProviderAdapter.kt` e `ContractQueryAdapter.kt`, e a T14 encurta o início de `ContractConsumers.kt`.
  Localize cada item pelo trecho citado, não só pelo número da linha.

### Tarefa T23: KDoc de `contract` (domínio e aplicação)

**Itens (todos conferidos no código):**

- [ ] Lembrete atribuído à R7 (o correto é R4, `plano-de-negocio.md` §8): `Contract.kt:34`, `ContractEvent.kt:181`
  (`ContractReminderSent`), `ContractSupport.kt:17`, `SendSignatureReminders.kt:10` e `ContractPorts.kt:17`.
- [ ] Reconciliação atribuída à R6 (o correto é o guia, §6.4): `ContractSupport.kt:17`, `ReconcileContracts.kt:15`,
  `ContractPorts.kt:17` e `ProviderModels.kt:76`.
- [ ] `ContractValueObjects.kt:7`: "Uuid v7" passa a "UUID v4 (`Uuid.random()`)".
- [ ] `ContractEvent.kt`:
    - `ContractSent` (:86-91) não dispara o agendamento de prazos nem de lembretes (os jobs consultam o banco);
    - `ContractSignerCompleted` (:112-116) não garante ordem nem exige `PARTIALLY_SIGNED`;
    - `ContractDocumentCreated` (:49) e `LeaseActivated` (:212) descrevem reações que nenhum consumidor executa;
    - em `ContractCompleted` (:130), só o arquivamento do PDF está errado: quem arquiva é o `ArchiveSignedDocument`,
      disparado pelo webhook `document_completed_pdf_ready`. A ativação da locação continua no texto, porque o
      `PostSignatureConsumer` reage ao evento com `RunPostSignatureActions`;
    - em `ContractCancelled` (:171), a anulação no provedor não é reação ao evento (o `CancelContract` a faz na
      requisição, `arquitetura.md` §7), e a notificação vai só para a imobiliária (`NotificationPolicy`, `AGENCY_ONLY`).
- [ ] Links que não resolvem passam a crase ou a link totalmente qualificado: `ContractStatus.kt:7`
  (`[ContractTransitionPolicy]`, do pacote `domain.services`, sem import) e `[Contract]` sem import em
  `SendSignatureReminders.kt:14`, `CancelContract.kt:24` e `ExpireOverdueContracts.kt:16`.
- [ ] `ContractStatus.kt:9`: quem garante as transições é a `ContractTransitionPolicy`, não o enum.
- [ ] `ContractPorts.kt:51` ("otimizadas", adjetivo vazio) e `:65` (`PostSignatureActionsPort`: ativação da locação,
  vistoria e primeira cobrança simuladas; o arquivamento não passa por ela).
- [ ] `ContractViews.kt:12` (`SignerView`): e-mail completo, sem máscara; não logar (`regras-de-codigo.md` §5).
- [ ] `ProviderErrors.kt:7`, `:18` e `:21`: o `DefaultErrorHandler` não distingue falhas transitórias de definitivas (os
  use cases capturam as definitivas, `regras-de-codigo.md` §2); `Forbidden` é 403 (permissão ou cota) e
  `Unavailable` inclui o 401 de credencial recusada.
- [ ] `IntegrationPorts.kt:31`: `ProviderWebhookGateway` é porta de saída, não "gateway de entrada".

**Verificação:** `compileKotlin` sem warnings novos e testes rápidos.

**Dependências:** T01 a T08 (mesmos arquivos). **Tamanho:** M.

### Tarefa T24: KDoc de `contract` (infra e interfaces)

**Itens:**

- [ ] `PandaDocSignatureProviderAdapter.kt:29`: "Ser o único ponto por onde os casos de uso chamam a API REST da
  PandaDoc; os endpoints ficam em [PandaDocClient], os DTOs em `dto/` e as conversões em `mappers/`."
- [ ] `PandaDocMappers.kt:116`: "Fornecer o estado consultado pela reconciliação (guia, seção 6.4; ADR-012)."
- [ ] `PandaDocWebhookGateway.kt:23`: "Adaptador de saída (driven) que implementa [ProviderWebhookGateway] para os
  webhooks da PandaDoc; o receptor HTTP é o `PandaDocWebhookController`."
- [ ] `PandaDocConfig.kt:27`: "timeouts resilientes" passa a "timeouts de conexão e leitura". Os links totalmente
  qualificados ficam como estão (`plan.md` §5).
- [ ] `ContractController.kt:35-37`: só a reconciliação exige o token de operador (ADR-013); `cancel` e `reconcile`
  chamam a PandaDoc na requisição (`arquitetura.md` §7).
- [ ] `ContractConsumers.kt`:
    - :36, :67 e :94 citam tópicos inexistentes (`contract-events`, `pandadoc-webhooks`): usar
      `[Topics.CONTRACT_EVENTS]`
      e `[Topics.PANDADOC_WEBHOOKS]`;
    - :67-69: `RunPostSignatureActions` ativa a locação e simula a vistoria e a cobrança; quem arquiva o PDF é o
      `ArchiveSignedDocument`;
    - trocar a frase do `OrchestratorConsumer` sobre "descarte de erros" por "Outros `eventType` são ignorados; falhas
      dos
      use cases sobem para o `DefaultErrorHandler`";
    - remover "tratamento resiliente" e "traces distribuídos";
    - escrever entre crases os links que não resolvem (`ContractRequested`, `ContractGenerated`, `ContractCompleted`,
      `Contract`).
- [ ] `PandaDocWebhookController.kt:27`: o controller só preserva os bytes; quem valida o HMAC é o
  `ReceiveProviderWebhook` (`arquitetura.md` §4).
- [ ] `ContractResponses.kt:38` (`SignerResponse`): e-mail completo, não mascarado.
- [ ] `ContractJobs.kt:63`: "(Regra R4)".
- [ ] `OutboxContractEventPublisher.kt:12`, `:15` e `:16`: a tabela é `outbox_events`. O "(Princípio 3)" vai para o
  item do outbox (guia, §1); a chave `contractId` cita o guia, §7.2.
- [ ] `ContractEntities.kt:11`: link qualificado `[br.com.locasign.contract.domain.models.Contract]`. Em `:14`,
  "coleção embutida" passa a "coleção filha (`@MappedCollection`, tabela `contract_signers`), apagada e regravada a
  cada `save`".
- [ ] `ContractRepositoryAdapter.kt:27-28`: as duas constraints viram 409 (`ux_contracts_one_active_per_lease` e
  `uq_contracts_lease_version`); remover "SQL otimizado".
- [ ] `ContractQueryAdapter.kt:18` ("projeções otimizadas") e `LeaseBridgeAdapters.kt:41` ("de forma limpa e
  desacoplada"): texto factual.
- [ ] `ContractEntityMappers.kt:84`: KDoc de uma linha para `ContractSignerEntity.toDomain()`.
- [ ] `ContractEntityMappers.kt:62` (KDoc de `ContractEntity.toDomain()`): `[CancelReason]` aparece entre os "Value
  Objects tipados", mas é uma interface selada sem validação (achado da revisão da Fase 1).

**Verificação:** `compileKotlin` sem warnings novos e testes rápidos.

**Dependências:** T03, T09 a T14 e T16 (mesmos arquivos). **Tamanho:** M.

### Tarefa T25: KDoc de `lease` e `notification`

**Itens:**

- [ ] `ActivateLease.kt:13` e `LeaseAdapters.kt:24`: links qualificados
  (`[br.com.locasign.lease.domain.models.LeaseStatus.ACTIVE]` e
  `[br.com.locasign.lease.infra.persistence.entities.LeaseEntity]`).
- [ ] `LeaseEntityMappers.kt:16-22` e `LeaseWebMappers.kt:12-18`: o KDoc de arquivo preso à primeira função vira um
  KDoc de uma linha por função (`toEntity`, `toDomain`, `toCommand`, `toResponse`).
- [ ] `LeasePorts.kt:21` ("otimizadas") e `LeaseAdapters.kt:39` e `:42` ("analíticas", "otimizadas"): texto factual,
  sinalizando a leitura de `contracts` como exceção de `arquitetura.md` §7.
- [ ] `RecordNotifications.kt:26`: "(R1-R10 / LGPD)" passa a "(R10, LGPD)".
- [ ] `NotificationPorts.kt:9`: quem lê o e-mail completo e o mascara é o adapter de `notification`; o caso de uso já o
  recebe mascarado.
- [ ] `JdbcNotificationAdapters.kt:16`: sinalizar a leitura de `contract_signers` como exceção de `arquitetura.md` §7.

**Verificação:** `compileKotlin` sem warnings novos e testes rápidos.

**Dependências:** T15, T16 e T17 (mesmos arquivos). **Tamanho:** S.

### Tarefa T26: KDoc de `shared`

**Itens:**

- [ ] `ObservabilityAdapters.kt:11`: não há Prometheus; contadores no `MeterRegistry`, expostos em
  `/actuator/metrics`.
- [ ] `ObservabilityAdapters.kt:29` (`MdcCorrelationContext`): o MDC é por thread; restaurar o mapa evita vazamento para
  a próxima tarefa na mesma thread ou para o contexto externo em chamadas aninhadas, e não "entre requisições
  concorrentes".
- [ ] `JdbcMessagingAdapters.kt:108` (`JdbcWebhookInboxAdapter`): o HMAC já foi validado antes da gravação (R7).
- [ ] `EventEnvelope.kt:33` (`EnvelopeReader`): componente de `interfaces` (driving), não "de infraestrutura".
- [ ] `LocaSignProperties.kt:57-58` (`retention`): o `HousekeepingJob` também apaga `processed_messages`.
- [ ] `OpenApiConfig.kt:13`: quem controla a exposição é `SWAGGER_ENABLED` (padrão `true`), não o ambiente.
- [ ] `Messaging.kt:23` (`ConsumerGroups`): compõe com o id do evento a chave única de `processed_messages` (R6); não é
  chave de partição.
- [ ] `KafkaConfig.kt:24`: entrega original mais 3 retentativas com backoff exponencial (1 s, 2 s, 4 s).
- [ ] `JdbcSupport.kt:8-18`: o KDoc de arquivo preso a `toDb()` vira um KDoc de uma linha para `toDb()` e outro para
  `getInstant()`.
- [ ] Links que não resolvem passam a crase ou a um alvo resolvível: `ObservabilityAdapters.kt:28`,
  `CorrelationContext.kt:21`, `WebhookInboxPort.kt:7` e `JdbcMessagingAdapters.kt:18`, `:77` e `:107`.
- [ ] O KDoc de `OutboxRow` já foi reescrito na T18: não repetir.

**Verificação:** `compileKotlin` sem warnings novos e testes rápidos.

**Dependências:** T14 e T18 (mesmos arquivos). **Tamanho:** S.

---

## Fase 6: verificação final

### Tarefa T27: fechamento

**Critérios de aceite:**

- [ ] `.\gradlew.bat build --console=plain` verde com Docker; número de testes igual à linha de base mais os testes
  novos, com `:test` executando.
- [ ] `.\gradlew.bat compileKotlin --console=plain` sem warnings novos em relação à T00.
- [ ] `HexagonalArchitectureTest` verde e checagem de fronteiras de `docs/agents/arquitetura.md` §6 sem ocorrências,
  salvo a última busca (`lease.(app|infra|interfaces)` em `contract`), que só pode casar em
  `contract/infra/persistence/adapters/LeaseBridgeAdapters.kt`.
- [ ] `git grep -n -E "storageValue|UUID\.fromString|const val UNAUTHORIZED|eventType = rs\.getString" -- src/main`
  vazio.
- [ ] `git status --short --untracked-files=all` e `git diff --stat` só com os arquivos das tarefas (os testes, os
  fakes e o `PostSignatureActionsAdapter.kt` novos aparecem como não rastreados, e o `git diff` sozinho não os mostra);
  nada em `src/main/resources/db/migration/` nem em `.env*`.
- [ ] Contagem de testes atualizada onde aparece: `AGENTS.md:43`, `AGENTS.md:239` e `README.md:182` (hoje "112").
- [ ] `docs/agents/*.md` conferidos para referências a símbolos alterados. A `LeaseActivationPort` continua existindo;
  na T16 só o tipo de retorno muda.
- [ ] Situação do `plan.md` atualizada e tarefas não executadas (T22, se for o caso) marcadas com o motivo.

**Dependências:** todas. **Tamanho:** S.

---

## Linha de base (registrada na T00)

- Testes aprovados: 112 (0 falhas, 0 erros, 0 ignorados), em `.\gradlew.bat clean build --console=plain` sobre
  `d013479` (2026-10-14), com Docker Engine 29.5.3 (Rancher Desktop).
- Warnings do `compileKotlin`: nenhum.

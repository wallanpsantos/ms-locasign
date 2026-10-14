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

---

## Fase 0: linha de base

### Tarefa T00: confirmar o ponto de partida

**Descrição:** garantir que o plano ainda corresponde ao código e registrar a linha de base da suíte.

**Critérios de aceite:**

- [x] `git status` sem mudanças fora de `docs/tasks/simplificacao/` (os arquivos do plano podem estar não rastreados
  ou modificados) e `git diff --stat abb0ed6 -- src/main` vazio (`plan.md`, commit base). Se houver diferença em
  `src/main`, revalidar as tarefas que tocam os arquivos alterados antes de executá-las.
- [x] Linha de base anotada no fim deste arquivo: número de testes aprovados (relatório em
  `build/reports/tests/test/index.html`) e warnings existentes do `compileKotlin`, se houver.

**Verificação:**

- [x] `.\gradlew.bat build --console=plain` verde, com `:test` executando (sem `NO-SOURCE`).

**Dependências:** nenhuma. **Arquivos:** nenhum. **Tamanho:** XS.

---

## Fase 1: domínio

### Tarefa T01: `Contract`, um único ponto de montagem do histórico

**Descrição:** quatro blocos de cerca de 11 linhas constroem `StatusHistoryEntry` (`applyTransition`, `recordRejected`,
`recordHistory` e `Contract.request`), e as seis chamadas de `recordHistory` passam sempre `status` e
`HistoryOutcome.INFO`. Unificar num helper privado e num atalho para os fatos INFO.

**Proposta:**

```kotlin
private fun appendHistory(
    from: ContractStatus?,
    to: ContractStatus,
    outcome: HistoryOutcome,
    source: ChangeSource,
    sourceEventId: String?,
    note: String?,
    now: Instant,
) {
    pendingHistory += StatusHistoryEntry(
        id = Uuid.random(),
        contractId = id,
        fromStatus = from,
        toStatus = to,
        outcome = outcome,
        source = source,
        sourceEventId = sourceEventId,
        note = note,
        occurredAt = now,
    )
}

/** Fato que não muda o status (assinatura, arquivamento, anomalia...): origem e destino são o status atual. */
private fun recordInfo(note: String, source: ChangeSource, sourceEventId: String?, now: Instant) =
    appendHistory(status, status, HistoryOutcome.INFO, source, sourceEventId, note, now)
```

- As seis chamadas viram `recordInfo("<nota>", source, sourceEventId, now)`; a de `markReminderSent` fica
  `recordInfo("Lembrete de assinatura registrado.", ChangeSource.SCHEDULER, null, now)`.
- `applyTransition`:
  `appendHistory(from, change.target, HistoryOutcome.APPLIED, change.source, change.sourceEventId, change.note, now)`.
- `recordRejected` mantém o nome e vira uma linha com `HistoryOutcome.IGNORED_TRANSITION` e `reason`.
- `Contract.request`: `contract.appendHistory(...)` com argumentos nomeados (`from = null`, `to = ContractStatus.DRAFT`,
  `outcome = HistoryOutcome.APPLIED`, `source = ChangeSource.API`, `sourceEventId = null`,
  `note = "Contrato v$versionNumber solicitado."`, `now = now`).

**Rede de segurança (antes da mudança):** em `ContractTest`, acrescentar casos que inspecionem `pullHistory()` depois de
`attachProviderDocument`, `recordSignerCompleted`, `markReminderSent`, `markSignedDocumentArchived`,
`recordLeaseActivated` e `recordAnomaly`. Conferir `fromStatus == toStatus ==` status atual, `outcome == INFO`,
`source`,
`sourceEventId` e o texto exato da nota. Montar os cenários com `restoreContract(...)` de `ContractFixtures`.

**Critérios de aceite:**

- [x] Os testes de caracterização passam antes e depois da mudança, sem alteração.
- [x] Mesmos campos e mesma ordem de inserção em `pendingHistory`; ordem touch, evento e histórico inalterada em cada
  método.
- [x] Nenhuma mudança na API pública do agregado; trailing comma nas listas multilinha reescritas.

**Verificação:**

- [x] `.\gradlew.bat test --tests "br.com.locasign.contract.domain.models.ContractTest" --console=plain --rerun`.
- [x] Testes rápidos e `.\gradlew.bat test --tests "*ContractRepositoryAdapterIT" --console=plain` (Docker).
- [x] `compileKotlin` sem warnings novos.

**Dependências:** T00. **Arquivos:** `contract/domain/models/Contract.kt`, `ContractTest.kt`. **Tamanho:** M.

### Tarefa T02: `Contract`, motivo efetivo do cancelamento e prazo nomeado

**Descrição:** (a) `applyTransition` grava
`cancelReason = change.cancelReason ?: CancelReason.Requested(change.note ?: "Cancelado.")`
e, logo depois, `eventFor` relê a propriedade com um ramo `null ->` inalcançável e repete o literal `"Cancelado."`. (b)
`cancel`, `failGeneration` e `expireIfOverdue` passam `Duration.ZERO` como prazo de assinatura, que parece "expira na
hora", mas é ignorado nos estados finais.

**Proposta:**

```kotlin
// membro privado de Contract (enxerga a constante do companion). Recebe a mudança por parâmetro, e não como
// receptor de extensão, para não confundir `change.cancelReason` com a propriedade `cancelReason` do agregado.
private fun effectiveCancelReason(change: StatusChange): CancelReason =
    change.cancelReason ?: CancelReason.Requested(change.note ?: DEFAULT_CANCEL_NOTE)

// applyTransition
ContractStatus.CANCELLED -> cancelReason = effectiveCancelReason(change)

// eventFor: sujeito não nulo, when exaustivo sem else e sem ramo null
ContractStatus.CANCELLED -> when (val reason = effectiveCancelReason(change)) {
    is CancelReason.GenerationFailed -> ContractGenerationFailed(id, reason.detail, now)
    is CancelReason.Requested -> ContractCancelled(id, reason.text, now)
}

// companion, ao lado de DEFAULT_GENERATION_FAILURE_NOTE
private const val DEFAULT_CANCEL_NOTE = "Cancelado."

/** Estados finais não abrem prazo de assinatura: o prazo (R4) só é calculado ao entrar em SENT, VIEWED ou PARTIALLY_SIGNED. */
private val NO_SIGNATURE_DEADLINE: Duration = Duration.ZERO
```

E `apply(..., now, NO_SIGNATURE_DEADLINE)` nos três pontos.

**Rede de segurança (antes da mudança):** em `ContractTest`: `cancel("motivo", now)` emite `ContractCancelled` com
`reason = "motivo"` e grava `CancelReason.Requested("motivo")`; `failGeneration(null, ...)` emite
`ContractGenerationFailed(detail = null)`, grava `CancelReason.GenerationFailed(null)` e a nota padrão;
`apply(StatusChange(CANCELLED, ...))` sem nota nem motivo grava `Requested("Cancelado.")` e emite
`ContractCancelled("Cancelado.")`; `expireIfOverdue` depois do prazo vai para `EXPIRED` sem alterar `expiresAt`.

**Critérios de aceite:**

- [x] Mesmos eventos (tipo e campos), mesmo `cancel_reason` e mesma nota de histórico em todos os casos.
- [x] Nenhum `else` nem ramo `null` no `when` sobre `CancelReason`.

**Verificação:**

- [x] `ContractTest`, testes rápidos e `*ContractRepositoryAdapterIT`.

**Dependências:** T01 (mesmo arquivo). **Arquivos:** `Contract.kt`, `ContractTest.kt`. **Tamanho:** S.

### Tarefa T03: formato de `cancel_reason` sai do domínio

**Descrição:** `CancelReason` (domínio) declara `storageValue` e `fromStorage`, que só `ContractEntityMappers` usa. Não
é
um value object que valida entrada (os casos têm construtor público), então a conversão de formato de coluna pertence a
`infra/persistence/mappers` (`regras-de-codigo.md` §2).

**Proposta:**

```kotlin
// ContractParts.kt (domínio)
sealed interface CancelReason {
    data class GenerationFailed(val detail: String?) : CancelReason
    data class Requested(val text: String) : CancelReason
}

// ContractEntityMappers.kt
private const val GENERATION_FAILED = "GENERATION_FAILED"
private const val REQUESTED = "REQUESTED"

/** Formato da coluna `cancel_reason`, também devolvido sem conversão pelo GET de contrato. */
private fun CancelReason.toStorage(): String = when (this) {
    is CancelReason.GenerationFailed -> if (detail.isNullOrBlank()) GENERATION_FAILED else "$GENERATION_FAILED: $detail"
    is CancelReason.Requested -> "$REQUESTED: $text"
}

private fun String.toCancelReason(): CancelReason = when {
    this == GENERATION_FAILED -> CancelReason.GenerationFailed(null)
    startsWith("$GENERATION_FAILED: ") -> CancelReason.GenerationFailed(removePrefix("$GENERATION_FAILED: "))
    startsWith("$REQUESTED: ") -> CancelReason.Requested(removePrefix("$REQUESTED: "))
    else -> CancelReason.Requested(this)
}
```

Usar `cancelReason?.toStorage()` em `toEntity()` e `cancelReason?.toCancelReason()` em `toDomain()`. No KDoc de
`CancelReason`, remover o item "Prover conversão e serialização consistentes para persistência...".

**Rede de segurança (antes da mudança):** novo `ContractEntityMappersTest` (sem Spring), em
`src/test/kotlin/br/com/locasign/contract/infra/persistence/mappers/`, passando pelos públicos `toEntity()`/`toDomain()`
com contratos montados por `restoreContract(...)`: `GenerationFailed(null)` grava `"GENERATION_FAILED"`;
`GenerationFailed("")` grava `"GENERATION_FAILED"` e volta como `GenerationFailed(null)`; `GenerationFailed("x")` grava
`"GENERATION_FAILED: x"`; `Requested("y")` grava `"REQUESTED: y"`; e a coluna legada `"texto livre"` volta como
`Requested("texto livre")`.

**Critérios de aceite:**

- [x] As mesmas strings nos dois sentidos; a resposta do GET de contrato não muda (`ContractQueryAdapter` lê a coluna
  crua, ver `plan.md` D3).
- [x] `git grep -n -E "storageValue|CancelReason(\.|::)fromStorage" -- src` vazio e
  `git grep -n "fromStorage" -- src/main/kotlin/br/com/locasign/contract/domain/models/ContractParts.kt` vazio.

**Verificação:**

- [x] `*ContractEntityMappersTest`, testes rápidos, `*HexagonalArchitectureTest` e `*ContractRepositoryAdapterIT`.

**Dependências:** T02. **Arquivos:** `ContractParts.kt`, `ContractEntityMappers.kt`, teste novo. **Tamanho:** S.

---

## Fase 2: aplicação (`contract/app`)

Fakes novos desta fase (DS-09), em `src/test/kotlin/br/com/locasign/contract/app/fakes/` ou `.../shared/app/fakes/`:
`RecordingSignatureProvider` (registra pedidos de criação, envios e anulações; resultado de `downloadSigned` e exceções
configuráveis), `RecordingMetricsPort` (registra nome e tags), `InMemoryOutboxPort`, `InMemoryWebhookInbox`,
`FixedWebhookGateway` e `InMemorySignedDocumentStorage`. Crie cada um na primeira tarefa que precisar dele.
A revisão da fase acrescentou o `RecordingTransactionRunner` (`shared/app/fakes`), que informa se há transação aberta,
e o gancho `beforeEachCall` do `RecordingSignatureProvider`: juntos, provam que as chamadas ao provedor ficam fora
da transação e antes do `markProcessed` (T04, T07 e T08).

### Tarefa T04: `CancelContract` usa `isAwaitingSignatures`

**Descrição:** `val wasSent = contract.status.progress >= ContractStatus.SENT.progress && !contract.status.isFinal`
recalcula o que `ContractStatus.isAwaitingSignatures` já nomeia (tabela-verdade idêntica nos 9 status). Só
`CancelContract`: em `SendContract.complete` a troca foi descartada (`plan.md` §5).

**Proposta:**

```kotlin
val documentToVoid = transactions.run {
    val contract = contracts.findById(command.contractId)
        ?: throw DomainException.NotFound("Contrato", command.contractId.toString())
    // Só documento já enviado pode ser assinado; rascunho no provedor não precisa ser anulado.
    val awaitingSignatures = contract.status.isAwaitingSignatures
    contract.cancel(reason, clock.now())
    persister.save(contract)
    contract.providerDocumentId.takeIf { awaitingSignatures }
}
```

Remover o import de `ContractStatus`, que fica sem uso.

**Rede de segurança (antes da mudança):** novo `CancelContractTest` (fakes, sem Spring). Em todos os cenários, o
contrato tem documento no provedor (`restoreContract(..., providerDocumentId = ProviderDocumentId.of("doc-1"))`), para
que a decisão venha do status, e não da falta de documento. Casos: anula o documento só a partir de `SENT`, `VIEWED` e
`PARTIALLY_SIGNED` (`@EnumSource`); não anula em `DRAFT` e `GENERATED`; estado final lança
`ContractFinal` sem anular; motivo vazio ou com mais de 500 caracteres lança `BusinessRuleViolation("reason", ...)` com
a
mensagem atual; contrato inexistente lança `NotFound`.

**Critérios de aceite:**

- [x] O predicado continua calculado antes de `cancel`, na mesma transação; a anulação continua depois do commit
  (exceção de `arquitetura.md` §7, preservada).

**Verificação:**

- [x] `*CancelContractTest`, testes rápidos, `compileKotlin`.

**Dependências:** T00. **Arquivos:** `CancelContract.kt`, teste e fake novos. **Tamanho:** S.

### Tarefa T05: `ApplyProviderUpdate`, helper `failGeneration`

**Descrição:** os ramos `CreationFailed` e `ProviderDocumentStatus.ERROR` repetem
`count(contract.failGeneration(...), ContractStatus.CANCELLED)`; o helper irmão `change(...)` já segue esse padrão.

**Proposta:**

```kotlin
/** Falha de geração cancela o contrato e conta a transição como CANCELLED (mesma métrica de `change`). */
private fun failGeneration(
    contract: Contract,
    detail: String?,
    command: ApplyProviderUpdateCommand,
    now: Instant,
    modifiedAt: Instant? = null,
) = count(
    contract.failGeneration(detail, command.source, command.sourceEventId, now, modifiedAt),
    ContractStatus.CANCELLED,
)
```

Usos: `is ProviderSignal.CreationFailed -> failGeneration(contract, signal.detail, command, now)` e, no ramo `ERROR` em
`DRAFT`, `failGeneration(contract, ERROR_DETAIL, command, now, signal.modifiedAt)`.

**Rede de segurança (antes da mudança):** novo `ApplyProviderUpdateTest` com `RecordingMetricsPort`: `CreationFailed`
cancela com `ContractGenerationFailed` e conta `TRANSITION_APPLIED` com `target=CANCELLED`; `ERROR` em `DRAFT` cancela
com `ERROR_DETAIL` e respeita `modifiedAt`; `ERROR` fora de `DRAFT` registra a anomalia
`"ANOMALIA: provedor reportou document.error sem pedido do LocaSign."`; `CreationFailed` em contrato `COMPLETED` conta
`TRANSITION_IGNORED` com `target=CANCELLED`, e em contrato já `CANCELLED` não conta métrica nenhuma (a policy devolve
`NoOp`).

**Critérios de aceite:**

- [x] Mesmos argumentos para `Contract.failGeneration`, mesmas métricas e tags, uma única transação.

**Verificação:**

- [x] `*ApplyProviderUpdateTest`, testes rápidos.

**Dependências:** T00. **Arquivos:** `ApplyProviderUpdate.kt`, teste e fake novos. **Tamanho:** S.

### Tarefa T06: `ReceiveProviderWebhook`, `outboxMessage(...)` e `HexFormat`

**Descrição:** (a) a montagem de `OutboxMessage` está cinco níveis abaixo de `execute` e repete
`item.documentId ?: deliveryId`; (b) `sha256Hex` usa `joinToString("") { "%02x".format(it) }`, enquanto o gateway usa
`HexFormat`.

**Proposta:**

```kotlin
val isNew = transactions.run {
    val inserted = inbox.store(deliveryId, rawBody)
    if (inserted) {
        items.forEachIndexed { index, item -> outbox.append(outboxMessage(deliveryId, index, item, now)) }
    }
    inserted
}

/** R7: cada item do array vira uma linha do outbox com id `deliveryId:índice` (ADR-006; docs/agents/regras-de-codigo.md §4). */
private fun outboxMessage(deliveryId: String, index: Int, item: WebhookItem, now: Instant): OutboxMessage {
    // Chave = id do documento: garante ordem por documento dentro da partição.
    val documentKey = item.documentId ?: deliveryId
    return OutboxMessage(
        id = "$deliveryId:$index",
        topic = Topics.PANDADOC_WEBHOOKS,
        key = documentKey,
        aggregateType = AGGREGATE_TYPE,
        aggregateId = documentKey,
        eventType = item.eventName ?: UNKNOWN_EVENT,
        occurredAt = now,
        payloadJson = item.json,
    )
}

private fun sha256Hex(bytes: ByteArray): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
```

Novos imports: `WebhookItem`, `java.time.Instant` e `java.util.HexFormat`.

**Rede de segurança (antes da mudança):** novo `ReceiveProviderWebhookTest` com `FixedWebhookGateway`,
`InMemoryWebhookInbox`, `InMemoryOutboxPort` e `RecordingMetricsPort`:

- sem `deliveryId`, o corpo `[]` gera o `deliveryId`
  `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` (SHA-256 em hex minúsculo; é a chave de
  deduplicação do `webhook_inbox`);
- dois itens geram duas mensagens com ids `<d>:0` e `<d>:1`, `key == aggregateId == documentId` (ou `deliveryId` sem
  documento) e `eventType` `unknown` quando o item não tem nome;
- entrega repetida devolve `Duplicate` sem novas mensagens; assinatura inválida devolve `InvalidSignature` sem gravar.

**Critérios de aceite:**

- [x] Mesma saída hexadecimal, byte a byte; mesmos campos, ordem e transação das mensagens do outbox.

**Verificação:**

- [x] `*ReceiveProviderWebhookTest`, testes rápidos, `*HexagonalArchitectureTest` (import novo em `app`).

**Dependências:** T00. **Arquivos:** `ReceiveProviderWebhook.kt`, teste e fakes novos. **Tamanho:** S.

### Tarefa T07: `CreateProviderDocument`, guardas separadas da montagem do pedido

**Descrição:** `prepare` mistura as guardas de idempotência com 16 linhas de montagem de `ProviderDocumentRequest`. O
irmão `SendContract.prepare` só decide se segue.

**Proposta:** `prepare` mantém, na mesma ordem e na primeira transação, `isProcessed`, `findById`, os descartes com
`markProcessed` e o `checkNotNull(leases.find(...))` com a mesma mensagem, e termina com
`return documentRequest(contract, lease)`:

```kotlin
/** Pedido de criação no provedor: signatários na ordem de assinatura (R3) e os campos do modelo (guia, §5). */
private fun documentRequest(contract: Contract, lease: LeaseSnapshot): ProviderDocumentRequest = ProviderDocumentRequest(
    contractId = contract.id,
    leaseId = contract.leaseId,
    documentName = "LocaSign contrato ${contract.id} v${contract.versionNumber}",
    recipients = contract.signers.sortedBy { it.order.value }.map {
        ProviderRecipient(it.role, it.name, it.email, it.order.value)
    },
    template = LeaseTemplateData(/* os mesmos seis campos de lease */),
)
```

O nome evita o prefixo `to...`, para não parecer um mapper fora de `mappers/`. Novos imports: `Contract` e
`LeaseSnapshot`.

**Rede de segurança (antes da mudança):** novo `CreateProviderDocumentTest` com `RecordingSignatureProvider`: o pedido
capturado tem o nome do documento, os destinatários na ordem de assinatura e os seis campos do modelo; evento duplicado
não chama o provedor; contrato fora de `DRAFT` marca o evento e não chama o provedor; `Forbidden` cancela o contrato com
a nota atual.

**Critérios de aceite:**

- [x] Mesma ordem de instruções e mesma transação; a chamada HTTP continua fora dela.

**Verificação:**

- [x] `*CreateProviderDocumentTest`, testes rápidos.

**Dependências:** T00 (reaproveita fakes da T04, se já existirem). **Arquivos:** `CreateProviderDocument.kt`, teste
novo. **Tamanho:** S.

### Tarefa T08: `ArchiveSignedDocument`, segunda transação em `complete(...)`

**Descrição:** a segunda transação está inline no `execute` (com um `if` quebrado em 7 linhas), enquanto
`CreateProviderDocument` e `SendContract` usam `complete(...)`. Só a segunda transação é extraída; o download continua
no `execute` (`plan.md` §5).

**Proposta:**

```kotlin
transactions.run { complete(command, contractId, storageKind, location) }
...
private fun complete(
    command: ArchiveSignedDocumentCommand,
    contractId: ContractId,
    storageKind: String,
    location: String,
) {
    if (!processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, command.eventId)) return
    val contract = contracts.findById(contractId) ?: return
    if (contract.markSignedDocumentArchived(storageKind, location, ChangeSource.WEBHOOK, command.eventId, clock.now())) {
        persister.save(contract)
    }
}
```

**Rede de segurança (antes da mudança):** novo `ArchiveSignedDocumentTest` com `RecordingSignatureProvider`,
`InMemorySignedDocumentStorage` e `InMemoryProcessedMessages`, cobrindo:

- evento já processado não baixa nada;
- contrato que terminou sem conclusão marca o evento e não baixa nada;
- contrato ainda não concluído lança `IllegalStateException` (retentativa);
- `Unavailable` grava `REFERENCE_ONLY` com `"provider:<id>"` e emite `SignedDocumentArchived`;
- `Available` grava `FILE` com a localização devolvida pelo storage;
- a segunda execução do mesmo evento não gera efeito novo (R6).

**Critérios de aceite:**

- [x] `markProcessed` continua sendo a primeira instrução da segunda transação e nunca é chamado antes do download
  (`regras-de-codigo.md` §4).

**Verificação:**

- [x] `*ArchiveSignedDocumentTest`, testes rápidos.

**Dependências:** T00. **Arquivos:** `ArchiveSignedDocument.kt`, teste e fake novos. **Tamanho:** S. **Risco:** médio.

---

## Fase 3: adapters e interfaces de `contract`

### Tarefa T09: DTOs de resposta da PandaDoc só com o que é lido

**Descrição:** `DocumentResponse.status`, `DocumentResponse.dateModified` e `RecipientResponse.email` nunca são lidos. O
e-mail completo também fica no `toString()` gerado (R10).

**Proposta:** `data class DocumentResponse(val id: String)` e `RecipientResponse(role, hasCompleted)`, mantendo
`@JsonIgnoreProperties(ignoreUnknown = true)` e o `@param:JsonProperty("has_completed")`. Ajustar os KDocs: o de
`DocumentResponse` passa a citar só o `id`; o de `DocumentDetailsResponse` troca "dados completos do documento" por
"status, data de modificação e papéis concluídos".

**Rede de segurança (antes da mudança):** em `PandaDocSignatureProviderAdapterTest`, um stub de detalhes com um
destinatário que traga `email`, `role` e `has_completed`, conferindo os papéis concluídos.

**Critérios de aceite:**

- [ ] Os stubs com campos extras continuam aceitos; `id` continua obrigatório.

**Verificação:**

- [ ] `*PandaDocSignatureProviderAdapterTest`, testes rápidos.

**Dependências:** T00. **Arquivos:** `PandaDocResponses.kt`, teste do adapter. **Tamanho:** XS.

### Tarefa T10: conversão do envio em `PandaDocMappers`

**Descrição:** `send` monta `SendDocumentRequest(request.subject, request.message)` dentro do adapter; as outras
operações convertem em `mappers/` (`regras-de-codigo.md` §2 e §4, passo 3).

**Proposta:**

```kotlin
// PandaDocMappers.kt
fun ProviderSendRequest.toPandaDoc(): SendDocumentRequest = SendDocumentRequest(subject, message)

// PandaDocSignatureProviderAdapter.kt
override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) {
    call("send") { client.send(documentId.value, request.toPandaDoc()) }
}
```

Remover do adapter o import de `SendDocumentRequest` e acrescentar em `PandaDocMappers.kt` os imports de
`br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest` e
`br.com.locasign.contract.infra.pandadoc.dto.request.SendDocumentRequest`.
`StatusChangeRequest(VOIDED_STATUS_CODE, VOID_NOTE)`
fica no adapter (comando fixo do ADR-012, não conversão).

**Rede de segurança (antes da mudança):** em `PandaDocSignatureProviderAdapterTest`, um caso **novo** (sem alterar
`envio de documento executa POST no endpoint correspondente`) que chame
`adapter.send(ProviderDocumentId.of("doc-created-999"), ProviderSendRequest("Assunto do Teste", "Corpo da Mensagem"))` e
confira
`wireMockServer.verify(postRequestedFor(urlEqualTo("/documents/doc-created-999/send")).withRequestBody(equalToJson("""{"subject":"Assunto do Teste","message":"Corpo da Mensagem","silent":false}""")))`.
Import novo no teste: `com.github.tomakehurst.wiremock.client.WireMock.equalToJson`.

**Critérios de aceite:**

- [ ] JSON enviado idêntico; construção do DTO continua dentro do lambda de `call`.

**Verificação:**

- [ ] `*PandaDocSignatureProviderAdapterTest`, testes rápidos.

**Dependências:** T09 (mesmo teste). **Arquivos:** adapter, mappers, teste do adapter. **Tamanho:** XS.

### Tarefa T11: `PostSignatureActionsAdapter` em arquivo próprio

**Descrição:** o adapter de escrita de `post_signature_actions` está no fim de `ContractQueryAdapter.kt`, arquivo de
nome
singular cujo KDoc promete só leitura.

**Proposta:** mover a classe, com KDoc e corpo inalterados, para
`contract/infra/persistence/adapters/PostSignatureActionsAdapter.kt` (mesmo pacote; imports `PostSignatureActionsPort`,
`ContractId`, `JdbcClient`, `Repository` e `toJavaUuid`). Em `ContractQueryAdapter.kt`, remover só o import de
`PostSignatureActionsPort`.

**Critérios de aceite:**

- [ ] Mesmo FQN, mesma anotação e mesmo nome de bean (`postSignatureActionsAdapter`); SQL idêntico.

**Verificação:**

- [ ] `*HexagonalArchitectureTest`, `*IntegrationTestSupportIT` e `*LocaSignE2EIT` (Docker).

**Dependências:** T00. **Arquivos:** os dois adapters. **Tamanho:** XS.

### Tarefa T12: `HttpStatus.UNAUTHORIZED` no receptor de webhook

**Descrição:** `PandaDocWebhookController` é o único ponto web com status numérico (`const val UNAUTHORIZED = 401`).

**Proposta:** `WebhookReceipt.InvalidSignature -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()`, com
`import org.springframework.http.HttpStatus`, e remover a constante. `DELIVERY_ID_HEADER` continua `const` (usado em
anotação).

**Critérios de aceite:**

- [ ] Resposta 401 sem corpo, idêntica; `when` exaustivo; nenhuma resposta 410.

**Verificação:**

- [ ] `*PandaDocWebhookControllerTest`, testes rápidos.

**Dependências:** T00. **Arquivos:** `PandaDocWebhookController.kt`. **Tamanho:** XS.

### Tarefa T13: argumentos nomeados nos mappers web de `contract`

**Descrição:** `SignerView.toResponse()` e `HistoryEntryView.toResponse()` chamam construtores com até sete `String`
posicionais seguidos (`fromStatus`, `toStatus`...); a função vizinha `ContractDetailView.toResponse()` usa argumentos
nomeados.

**Proposta:**

```kotlin
fun SignerView.toResponse(): SignerResponse = SignerResponse(
    role = role,
    name = name,
    email = email,
    signingOrder = signingOrder,
    completedAt = completedAt,
)

fun HistoryEntryView.toResponse(): HistoryEntryResponse = HistoryEntryResponse(
    id = id,
    fromStatus = fromStatus,
    toStatus = toStatus,
    outcome = outcome,
    source = source,
    sourceEventId = sourceEventId,
    note = note,
    occurredAt = occurredAt,
)
```

**Critérios de aceite:**

- [ ] Mesma ligação de argumentos; JSON de `GET /contracts/{id}` e `/history` idêntico.

**Verificação:**

- [ ] Testes rápidos e `*LocaSignE2EIT` (Docker).

**Dependências:** T00. **Arquivos:** `ContractWebMappers.kt`. **Tamanho:** XS.

---

## Fase 4: `lease`, `notification` e `shared`

### Tarefa T14: `EventEnvelope.aggregateUuid()`

**Descrição:** a regra "`aggregateId` inválido é mensagem ilegível e vai direto para a DLT", com a mesma mensagem, está
copiada em `ContractConsumers` e `NotificationConsumer`.

**Proposta:**

```kotlin
// shared/interfaces/messaging/EventEnvelope.kt (função de topo, não membro da data class)
/** `aggregateId` como [Uuid], para envelopes cujo agregado é identificado por UUID (eventos de contrato); valor inválido é mensagem ilegível e vai direto para a DLT ([UnreadableMessageException]). */
fun EventEnvelope.aggregateUuid(): Uuid =
    Uuid.parseOrNull(aggregateId)
        ?: throw UnreadableMessageException("Evento $eventId com aggregateId inválido: $aggregateId")
```

- `ContractConsumers.kt`: `private fun EventEnvelope.contractId(): ContractId = ContractId(aggregateUuid())`, mantendo o
  KDoc atual; remover o import de `UnreadableMessageException`.
- `NotificationConsumer.kt`: `val contractId = envelope.aggregateUuid()`; remover os imports de
  `UnreadableMessageException` e `Uuid`.
- O KDoc restringe o uso: nos itens de webhook, o `aggregateId` é o id do documento ou o `deliveryId`, não um UUID.
- Imports: `kotlin.uuid.Uuid` em `EventEnvelope.kt`; `br.com.locasign.shared.interfaces.messaging.aggregateUuid` em
  `ContractConsumers.kt` e `NotificationConsumer.kt` (função de topo de outro pacote).

**Teste (escrito junto, sem Spring):** novo `EventEnvelopeTest` em
`src/test/kotlin/br/com/locasign/shared/interfaces/messaging/`:
`aggregateId = "x"` lança `UnreadableMessageException` com a mensagem `"Evento <eventId> com aggregateId inválido: x"`;
um UUID válido devolve o mesmo `Uuid`.

**Critérios de aceite:**

- [ ] Tipo e texto da exceção idênticos; o parse continua antes de `reader.handle` nos dois consumidores.
- [ ] `shared` não importa nenhum módulo; `ContractId.parseOrNull` continua em uso (`PandaDocWebhookGateway`).

**Verificação:**

- [ ] `*EventEnvelopeTest`, `*HexagonalArchitectureTest`, testes rápidos e `*LocaSignE2EIT` (Docker).

**Dependências:** T00. **Arquivos:** `EventEnvelope.kt`, `ContractConsumers.kt`, `NotificationConsumer.kt`, teste novo.
**Tamanho:** S.

### Tarefa T15: `notification` com `contractId` tipado e nomes sem colisão

**Descrição:** (a) o consumidor valida o id como `Uuid` e o devolve como `String`; o command e as duas portas trafegam
texto, e os adapters fazem `UUID.fromString` de novo (os únicos de `src/main`). (b) Em `RecordNotifications`, a porta se
chama `log` e o logger SLF4J `logger`, ao contrário das outras 23 classes.

**Proposta:**

- `RecordNotificationsCommand(val eventId: String, val eventType: String, val contractId: Uuid)`.
- `recipientsOf(contractId: Uuid, ...)` e `record(contractId: Uuid, ...)` nas portas.
- No consumidor, `RecordNotificationsCommand(envelope.eventId, envelope.eventType, contractId)`.
- Nos adapters, `.param("contractId", contractId.toJavaUuid())`: entra o import `kotlin.uuid.toJavaUuid` e sai
  `java.util.*`. Acrescentar a trailing comma que falta em `NotificationRecipient(...)`.
- Importar `kotlin.uuid.Uuid` em `NotificationPorts.kt`, `RecordNotifications.kt` e `JdbcNotificationAdapters.kt`.
- Renomear a porta para `notificationLog` e o logger para `private val log = LoggerFactory.getLogger(javaClass)`; em
  `NotificationBeansConfig`, renomear o parâmetro `log` para `notificationLog`.
- Não usar `ContractId`: `notification` não importa `contract` (`arquitetura.md` §3).

**Rede de segurança (antes da mudança):** novo `RecordNotificationsTest` com fakes em
`src/test/kotlin/br/com/locasign/notification/app/fakes/` (`InMemoryNotificationRecipients` e
`InMemoryNotificationLog`), cobrindo:

- evento com público registra uma linha por destinatário, com `contractId`, `eventId`, `eventType` e e-mail mascarado;
- evento sem público não grava nada e não marca `processed_messages`;
- o mesmo `eventId` duas vezes gera um único efeito (R6).

Único ajuste permitido no teste depois da mudança: o tipo de `contractId` (`String` para `Uuid`) no command e nos fakes.

**Critérios de aceite:**

- [ ] Mesmo `java.util.UUID` no JDBC; o log `[simulado] Notificação ...` imprime o mesmo texto.
- [ ] `git grep -n "UUID.fromString" -- src/main` vazio.

**Verificação:**

- [ ] `*RecordNotificationsTest`, `*HexagonalArchitectureTest`, testes rápidos e `*LocaSignE2EIT` (Docker).

**Dependências:** T14. **Arquivos:** `NotificationConsumer.kt`, `RecordNotifications.kt`, `NotificationPorts.kt`,
`JdbcNotificationAdapters.kt`, `NotificationBeansConfig.kt`, testes e fakes novos. **Tamanho:** M.

### Tarefa T16: `ActivateLease` e `LeaseActivationPort` sem retorno `Boolean`

**Descrição:** `ActivateLease.execute` devolve `Boolean`, que sobe pela ponte e pela porta (cujo KDoc promete "retornar
se a ativação foi efetivamente aplicada"), mas o único chamador, `RunPostSignatureActions.kt:52`, descarta o valor: a
idempotência vem de `actions.registerIfAbsent(...)`. Conferido pelo coordenador; a segunda rodada de análise afirmou o
contrário sem evidência.

**Proposta:**

```kotlin
// lease/app/usecases/ActivateLease.kt
fun execute(leaseId: LeaseId) {
    transactions.run {
        val lease = leases.findById(leaseId) ?: throw DomainException.NotFound("Locação", leaseId.toString())
        if (lease.activate(clock.now())) leases.save(lease)
    }
}

// contract/app/ports/out/lease/LeasePorts.kt
interface LeaseActivationPort {
    fun activate(leaseId: LeaseId)
}

// contract/infra/persistence/adapters/LeaseBridgeAdapters.kt
override fun activate(leaseId: LeaseId) = activateLease.execute(leaseId)
```

- Tirar do KDoc da porta o item "Retornar se a ativação foi efetivamente aplicada nesta chamada (idempotência)".
- `Lease.activate(now): Boolean` (domínio) não muda, porque o use case usa o retorno para decidir se salva.
- Ajuste mecânico do fake (DS-03): `override fun activate(leaseId: LeaseId) { activated.add(leaseId) }`.

**Rede de segurança (antes da mudança):** novo `ActivateLeaseTest` (`lease/app/usecases`, com
`InMemoryLeaseRepository`):
locação registrada passa a `ACTIVE` com `activatedAt`; id inexistente lança `NotFound` com a mensagem atual. O teste
chama `execute` como instrução, sem ler o retorno, para compilar antes e depois. O `RunPostSignatureActionsTest` usa o
`FakeLeaseActivation` e não passa pela ponte: a ponte `LeaseActivationAdapter` → `ActivateLease` só é exercitada pelo
`LocaSignE2EIT` (a locação termina `ACTIVE`).

**Critérios de aceite:**

- [ ] Mesma transação (REQUIRED, unida à do chamador), mesmo `NotFound` e mesmo `save` condicional.
- [ ] `HexagonalArchitectureTest` verde: ele referencia `LeaseActivationAdapter` pelo nome, que não muda.

**Verificação:**

- [ ] `*ActivateLeaseTest`, `*RunPostSignatureActionsTest`, `*HexagonalArchitectureTest`, testes rápidos e
  `*LocaSignE2EIT` (Docker).

**Dependências:** T00. **Arquivos:** `ActivateLease.kt`, `LeasePorts.kt` (contract), `LeaseBridgeAdapters.kt`,
`ContractFakes.kt`, teste novo. **Tamanho:** S.

### Tarefa T17: leitura do contrato corrente nomeada e `CurrentContractView.toResponse()`

**Descrição:** (a) `LeaseQueryAdapter.findDetail` tem 50 linhas e duas consultas; a primeira lê `contracts` (exceção de
`arquitetura.md` §7) numa local genérica `contract`. (b) `LeaseDetailView.toResponse()` converte o contrato corrente num
`let` com quatro `it.` posicionais, enquanto `contract` usa uma extension própria.

**Proposta:**

```kotlin
// LeaseAdapters.kt
override fun findDetail(id: LeaseId): LeaseDetailView? {
    val currentContract = findCurrentContract(id)
    return jdbc.sql(""" ...SELECT de leases inalterado... """.trimIndent())
        .param("id", id.value.toJavaUuid())
        .query { rs, _ -> LeaseDetailView(/* ...inalterado... */ currentContract = currentContract) }
        .optional()
        .orElse(null)
}

/** Versão mais recente do contrato da locação. Lê `contracts`, tabela do módulo `contract`: exceção sem ADR (docs/agents/arquitetura.md §7), não use como precedente. */
private fun findCurrentContract(leaseId: LeaseId): CurrentContractView? =
    jdbc.sql(""" ...SELECT de contracts inalterado... """.trimIndent())
        .param("leaseId", leaseId.value.toJavaUuid())
        .query { rs, _ -> CurrentContractView(/* ...inalterado... */) }
        .optional()
        .orElse(null)

// LeaseWebMappers.kt
currentContract = currentContract?.toResponse(),
...
/** Converte a visão do contrato corrente ([CurrentContractView]) no DTO [CurrentContractResponse]. */
fun CurrentContractView.toResponse(): CurrentContractResponse =
    CurrentContractResponse(id, versionNumber, status, expiresAt)
```

Sem import novo no adapter; no mapper, importar `CurrentContractView`.

**Rede de segurança (antes da mudança):**

- No `LeaseControllerTest`, um caso com `currentContract` preenchido que confira `$.currentContract.id`,
  `versionNumber`, `status` e `expiresAt`.
- No `ContractRepositoryAdapterIT`, o único IT que grava contratos, acrescentar
  `@Autowired private val leaseQueryAdapter: LeaseQueryAdapter` ao construtor e um caso novo que grave a v1, cancele-a
  e grave a v2 (como em `após finalizar contrato anterior permite criação de nova versão`), conferindo que
  `leaseQueryAdapter.findDetail(lease.id)?.currentContract` traz o id da v2, `versionNumber = 2` e `status = "DRAFT"`.

**Critérios de aceite:**

- [ ] SQL, parâmetros e row mappers idênticos; a consulta a `contracts` continua antes da de `leases`.
- [ ] JSON de `GET /api/v1/leases/{id}` idêntico.

**Verificação:**

- [ ] `*LeaseControllerTest`, testes rápidos e `*ContractRepositoryAdapterIT` (Docker).

**Dependências:** T00. **Arquivos:** `LeaseAdapters.kt`, `LeaseWebMappers.kt`, testes. **Tamanho:** S.

### Tarefa T18: `OutboxRelay` sem campo morto e com a leitura do lote nomeada

**Descrição:** (a) o SELECT lê `event_type` para `OutboxRow.eventType`, que nada usa: o header `eventType` já vem da
coluna `headers`. (b) `relayBatch` tem cerca de 40 linhas que misturam SQL e mapeamento com o laço de publicação, é
público e só `relay()` o chama. (c) O par `row.topic to row.key` se chama `partition`, mas não é uma partição do Kafka.

**Proposta:**

- Tirar `event_type` da projeção:
  `SELECT id, topic, message_key, payload::text AS payload, headers::text AS headers`. `FROM`, `WHERE`,
  `ORDER BY seq`, `LIMIT` e `FOR UPDATE SKIP LOCKED` ficam inalterados.
- Tirar o mapeamento `eventType = rs.getString("event_type")` e o campo `val eventType` de `OutboxRow`. Reescrever o
  KDoc de `OutboxRow`: "Linha de `outbox_events` com os campos usados para montar o `ProducerRecord`; os headers já vêm
  serializados do outbox."
- Extrair `private fun lockNextBatch(): List<OutboxRow>`, com KDoc "Lê e trava (`FOR UPDATE SKIP LOCKED`) o próximo lote
  disponível, em ordem de `seq`; roda dentro da transação de [relayBatch].", chamado dentro de `transactions.run`.
- Tornar `relayBatch` `private`.
- Renomear `blocked`/`partition` para `blockedKeys`/`topicKey` (conferido pelo coordenador; mantém o comentário sobre a
  ordem por chave).

**Critérios de aceite:**

- [ ] Mesmas linhas travadas, na mesma ordem e na mesma transação que publica e marca; mesmos logs e retorno.
- [ ] Não aplicar a variante que mantém `event_type` na projeção.

**Verificação:**

- [ ] Testes rápidos, `*JdbcMessagingAdaptersIT` e `*LocaSignE2EIT` (Docker; exercita o relay de ponta a ponta).

**Dependências:** T00. **Arquivos:** `OutboxRelay.kt`. **Tamanho:** S.

### Tarefa T19: `DeadLetterReplayer` com a montagem do registro nomeada

**Descrição:** em `replayLocked`, a regra "voltar ao tópico original sem os headers `kafka_dlt-*`" fica cinco níveis
abaixo da função (`use`, `while`, `for`, `forEach`, `if`).

**Proposta:** o laço fica com
`kafka.send(replayRecord(record, sourceTopic)).get(properties.outbox.sendTimeout.toMillis(), TimeUnit.MILLISECONDS)` e
`replayed++`. A função é **membro privado da classe**, porque `DLT_HEADER_PREFIX` está no `private companion object` e
uma função de topo não o enxerga. O nome evita o prefixo `to...`, como na T07 (mapeamentos `to...` ficam em `mappers/`):

```kotlin
/** Volta ao tópico original (header `kafka_dlt-original-topic`) sem os headers `kafka_dlt-*` da DLT. */
private fun replayRecord(record: ConsumerRecord<String, String>, sourceTopic: String): ProducerRecord<String, String> {
    val target = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)?.value()?.toString(Charsets.UTF_8) ?: sourceTopic
    val outgoing = ProducerRecord(target, record.key(), record.value())
    record.headers().filterNot { it.key().startsWith(DLT_HEADER_PREFIX) }.forEach { outgoing.headers().add(it) }
    return outgoing
}
```

Import novo: `org.apache.kafka.clients.consumer.ConsumerRecord`.

**Rede de segurança (antes da mudança):** não há teste do replay. Recomendado: `DeadLetterReplayerIT` (herda
`IntegrationTestSupport`, em `src/test/kotlin/br/com/locasign/shared/infra/messaging/`):

- publicar em `locasign.contract.events.v1.dlt` (`KafkaTemplate`, `send(...).get()`) um registro com chave única,
  header `kafka_dlt-original-topic` = `locasign.contract.events.v1` e um header próprio;
- chamar `DeadLetterReplayer.replay(Topics.CONTRACT_EVENTS)` e conferir com `KafkaTestUtils` (grupo próprio,
  `earliest`) o registro no tópico original, localizado pela chave e não pelo total devolvido: header próprio presente e
  nenhum `kafka_dlt-*`;
- o valor **precisa** ser um envelope que os três consumidores do tópico aceitem sem erro, como
  `{"eventId":"<uuid>","eventType":"ReplayProbe","aggregateId":"<uuid>"}` (`eventType` sem roteamento e `aggregateId`
  UUID). Um valor ilegível vira `UnreadableMessageException`, volta à DLT na hora e é relido pela mesma chamada de
  `replay`, que nunca chega a duas leituras vazias (`plan.md`, B8).

Se o IT não for escrito, validar à mão com `POST /api/v1/admin/dead-letters/{topic}/replay` antes e depois, com o mesmo
tipo de mensagem, e anotar o resultado aqui.

**Critérios de aceite:**

- [ ] Mesmo destino, chave, valor e headers, na mesma ordem; envio síncrono, `commitSync`, lock e log inalterados.

**Verificação:**

- [ ] Testes rápidos e `*DeadLetterReplayerIT` (Docker), ou a validação manual registrada.

**Dependências:** T00. **Arquivos:** `DeadLetterReplayer.kt`, IT novo. **Tamanho:** S (M com o IT).

### Tarefa T20: `ApiExceptionHandler` com a montagem de Problem Details num lugar só

**Descrição:** `problem()` monta `forStatusAndDetail`, `type` e `title`; os dois overrides repetem isso à mão, com o par
`"validation"`/`"Requisição inválida"` duplicado. Faltam trailing commas nas linhas 64, 82 e 108.

**Proposta:**

```kotlin
private fun problemDetail(status: HttpStatus, slug: String, title: String, detail: String?): ProblemDetail {
    val body = ProblemDetail.forStatusAndDetail(status, detail ?: title)
    body.type = problemType(slug)
    body.title = title
    return body
}

private fun validationProblem(detail: String): ProblemDetail =
    problemDetail(HttpStatus.BAD_REQUEST, "validation", "Requisição inválida", detail)

private fun problem(status: HttpStatus, slug: String, title: String, detail: String?): ResponseEntity<ProblemDetail> =
    ResponseEntity.status(status).body(problemDetail(status, slug, title, detail))
```

- Os overrides continuam devolvendo `ResponseEntity<Any>?` e montam a resposta na própria expressão de retorno, sem
  passar por `problem(...)`: `ResponseEntity<ProblemDetail>` não é atribuível a `ResponseEntity<Any>?` (genérico Java
  invariante). Em `handleHttpMessageNotReadable`,
  `return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationProblem("Corpo da requisição ilegível: verifique o JSON, os tipos dos campos e os campos obrigatórios."))`.
  Em `handleMethodArgumentNotValid`, `val body = validationProblem("Um ou mais campos são inválidos.")`, depois
  `body.setProperty("errors", ...)` como hoje e `return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)`.
- No ramo `BusinessRuleViolation`, a resposta pode ser montada com
  `problemDetail(...).apply { setProperty("field", e.field) }`, eliminando o `body?`.
- O `when` exaustivo continua sem `else`.

**Rede de segurança (antes da mudança):** no `LeaseControllerTest`:

- JSON malformado (`{`) responde 400 `/problems/validation` com o título e o `detail` atuais de corpo ilegível;
- campo inválido devolve a propriedade `errors` com `field` e `message`;
- regra de negócio devolve 422 com a propriedade `field`.

**Critérios de aceite:**

- [ ] Status, `type`, `title`, `detail` e propriedades extras idênticos em todos os caminhos.

**Verificação:**

- [ ] `*LeaseControllerTest`, `*ContractControllerTest`, `*PandaDocWebhookControllerTest` e testes rápidos.

**Dependências:** T00. **Arquivos:** `ApiExceptionHandler.kt`, `LeaseControllerTest.kt`. **Tamanho:** S.

### Tarefa T21: `private companion object` em `CorrelationIdFilter` e `OperatorAccessGuard`

**Descrição:** `HEADER` e `TOKEN_HEADER` são públicos, mas nenhum código de `src/main` ou `src/test` os usa por fora (os
testes usam literais). O estilo vigente é `private companion object` (`regras-de-codigo.md` §2).

**Proposta:** `private companion object { const val HEADER = "X-Correlation-Id"; const val MAX_LENGTH = 100 }` e
`private companion object { const val TOKEN_HEADER = "X-Admin-Token" }`.

**Critérios de aceite:**

- [ ] Valores dos headers, validação de tamanho, respostas 403 e registro dos beans inalterados.

**Verificação:**

- [ ] Testes rápidos (as três fatias web carregam o filtro).

**Dependências:** T00. **Arquivos:** os dois arquivos. **Tamanho:** XS.

### Tarefa T22 (opcional): `SlidingWindowRateLimiter.tryReserve(...)`

**Descrição:** o `synchronized` de `acquire` tem duas saídas de natureza diferente: um `return` não local, que sai de
`acquire` porque `synchronized` é `inline`, e a última expressão, que vira `waitNanos`. As duas rodadas de análise
divergiram sobre o ganho (`plan.md` §4); por isso a tarefa é opcional.

**Proposta:**

```kotlin
fun acquire(operation: String) {
    val window = windows.computeIfAbsent(operation) { ArrayDeque() }
    val deadline = nanoTime() + maxWait.toNanos()
    while (true) {
        val waitNanos = tryReserve(window) ?: return
        if (nanoTime() + waitNanos > deadline) {
            throw ProviderException.RateLimited("Limite local de $permitsPerMinute requisições/min atingido para '$operation'")
        }
        sleeper(TimeUnit.NANOSECONDS.toMillis(waitNanos) + 1)
    }
}

/** Reserva uma permissão e devolve `null`; com a janela cheia, devolve quantos nanossegundos faltam para a mais antiga sair dela. */
private fun tryReserve(window: ArrayDeque<Long>): Long? = synchronized(window) {
    val now = nanoTime()
    while (window.isNotEmpty() && now - window.first() >= WINDOW_NANOS) window.removeFirst()
    if (window.size < permitsPerMinute) {
        window.addLast(now)
        null
    } else {
        window.first() + WINDOW_NANOS - now
    }
}
```

**Rede de segurança (antes da mudança):** novo `SlidingWindowRateLimiterTest` (sem Docker, com `nanoTime` e `sleeper`
falsos):

- permite até o limite dentro da janela;
- com a janela cheia, dorme o tempo esperado e então reserva;
- lança `RateLimited` com a mensagem atual quando a espera passa de `maxWait`;
- operações diferentes têm janelas independentes.

**Critérios de aceite:**

- [ ] Executar a extração só se, com o teste pronto, ela de fato ler melhor; caso contrário, marcar "não executada" e
  manter só o teste.
- [ ] Mesma sequência de leituras de `nanoTime()`; limpeza e reserva sob o mesmo monitor.

**Verificação:**

- [ ] `*SlidingWindowRateLimiterTest`, `*PandaDocSignatureProviderAdapterTest` e testes rápidos.

**Dependências:** T00. **Arquivos:** `SlidingWindowRateLimiter.kt`, teste novo. **Tamanho:** S.

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

## Linha de base (preencher na T00)

- Testes aprovados: 112 (0 falhas, 0 erros, 0 ignorados), em `.\gradlew.bat clean build --console=plain` sobre
  `d013479` (2026-10-14), com Docker Engine 29.5.3 (Rancher Desktop).
- Warnings do `compileKotlin`: nenhum.

# ADR-016: anulação assíncrona do documento na PandaDoc, com retentativa e DLT

- **Status:** proposta
- **Data:** 15/10/2026
- **Relação com o guia:** ajusta o ADR-012 (anulação "em melhor esforço") quanto ao onde e ao como da chamada;
  complementa a seção 5 (PandaDoc) e a tabela de tópicos da seção 7.2 (novo grupo de consumidores em
  `locasign.contract.events.v1`); resolve a exceção listada em `docs/agents/arquitetura.md` §7 (a regra inviolável 9 do
  `AGENTS.md`)
- **Origem:** achado AS1 de `docs/performance/auditoria-de-performance-2026-10-15.md`

## Contexto

O ADR-012 decide que o documento é anulado na PandaDoc (`PATCH /documents/{id}/status` com `{"status": 11}`) no
cancelamento e na expiração, "em melhor esforço". Ele não diz onde a chamada acontece. A implementação a fazia no mesmo
fluxo que grava a transição, por `cancelDocumentQuietly`:

- **Cancelamento:** `POST /api/v1/contracts/{id}/cancel` só devolvia 202 depois de `CancelContract` chamar a PandaDoc na
  thread HTTP. A chamada passa pelo `SlidingWindowRateLimiter` (espera de até 30 s por operação), pode ser retentada 3
  vezes em HTTP 429 (2 s, 4 s e 8 s) e tem leitura de 15 s por tentativa. No pior caso a resposta ao usuário demora na
  ordem de minutos (auditoria, ESTÁTICO).
- **Expiração:** `ExpireOverdueContracts` chamava a PandaDoc dentro do laço do lote. A operação `status` é limitada a 8
  por minuto e o `RateLimited` era capturado e só registrado em log. Do 9º contrato do lote em diante o documento não
  era anulado e a anulação nunca mais era tentada: o contrato já estava `EXPIRED` (estado final, R5) e o job só procura
  contratos em aberto. O signatário continuava podendo assinar um documento de contrato expirado.
- **Regra inviolável 9:** chamada à PandaDoc só em consumer Kafka ou job, nunca no fluxo HTTP. O cancelamento era a
  exceção pendente registrada em `docs/agents/arquitetura.md` §7.

A infraestrutura para fazer isso do jeito certo já existe. `ContractCancelled` e `ContractExpired` são gravados no
outbox, na mesma transação da transição, e chegam a `locasign.contract.events.v1` pelo `OutboxRelay`. O
`DefaultErrorHandler` faz a entrega original mais 3 retentativas (1 s, 2 s e 4 s) e envia para a DLT, e o
`DeadLetterReplayer` reenvia a DLT ao tópico original. A idempotência é por `processed_messages` (R6).

## Decisão

1. **Consumer próprio.** Novo `DocumentAnnulmentConsumer` (`contract/interfaces/messaging`), no grupo
   `locasign-document-annulment` (`ConsumerGroups.DOCUMENT_ANNULMENT`), escuta `locasign.contract.events.v1`, filtra os
   `eventType` `ContractCancelled` e `ContractExpired` e delega ao novo caso de uso `AnnulProviderDocument`. O payload
   dos eventos não muda: o caso de uso carrega o contrato pelo `aggregateId`.
2. **Caso de uso `AnnulProviderDocument`**, no padrão de `SendContract` (ADR-011): transação curta que consulta
   `isProcessed` e o contrato, chamada à PandaDoc fora de transação e transação curta com `markProcessed`. O evento só é
   marcado depois da chamada, para que uma falha não faça a retentativa pular uma anulação que não aconteceu.
3. **Quando anular.** Só se o contrato está `CANCELLED` ou `EXPIRED`, tem `providerDocumentId` e tem `sentAt`. A
   anulação só vale a partir de `sent`/`viewed` (ADR-012), e `sentAt` é o registro de que o documento chegou a ser
   enviado; um contrato cancelado em `DRAFT` ou `GENERATED` não tem o que anular. Em qualquer outro caso o evento é
   descartado e marcado.
4. **Classificação das falhas** (`ProviderException`):

   | Resultado do `PATCH` | Tratamento | Motivo |
                     |---|---|---|
   | sucesso | marca o evento | caminho normal |
   | `NotReady` (HTTP 404 ou 409) | resolvido: log `info`, marca o evento | documento inexistente, já anulado ou já concluído; o `PATCH` é idempotente |
   | `Forbidden` (403) ou `Rejected` (outros 4xx) | resolvido: log `warn`, marca o evento | falha definitiva; retentar só levaria a mensagem à DLT (`docs/agents/regras-de-codigo.md` §2) |
   | `RateLimited` ou `Unavailable` | propaga, não marca | o `DefaultErrorHandler` retenta e, esgotadas as tentativas, envia para a DLT, de onde o operador reenvia |

5. **Remoção do caminho síncrono.** `CancelContract` e `ExpireOverdueContracts` deixam de receber
   `SignatureProviderPort`, e `cancelDocumentQuietly` é removido. O `POST /cancel` continua respondendo 202, agora sem
   esperar a PandaDoc, e o lote de expiração não depende mais do limite de 8 chamadas por minuto.
6. **Sem novas peças de infraestrutura:** nenhum tópico, migration, dependência ou propriedade de configuração.

A garantia muda de "uma tentativa síncrona, falha engolida" para "tentativa durável a partir do outbox, retentada 3
vezes, com DLT replayável e idempotente".

## Consequências

- A requisição de cancelamento e o job de expiração não dependem mais da disponibilidade nem da cota da PandaDoc. O
  cancelamento responde sem esperar a PandaDoc.
- Toda anulação passa pelo `SlidingWindowRateLimiter` dentro do consumer, que tem 1 thread (`concurrency: 1`): um lote
  grande de expirações é drenado a 8 por minuto em vez de ser descartado. Se o limite local estourar o backoff do Kafka,
  a mensagem vai para a DLT (`locasign.kafka.dead_letter`) e pode ser reenviada.
- Falhas deixam de ser silenciosas: o que a PandaDoc não aceitar de forma transitória aparece na DLT e na métrica
  `locasign.kafka.dead_letter`. As falhas definitivas (`Forbidden`, `Rejected`) continuam só em log `warn`, como
  antes; um alerta específico fica fora do escopo.
- Há uma janela, de segundos no caso normal, entre o commit do cancelamento ou da expiração e a anulação, em que o
  signatário ainda pode abrir o documento. Se ele assinar nesse intervalo, o webhook tardio cai em R5: o contrato é
  final e a transição vira `IGNORED_TRANSITION`. Antes a janela existia (a chamada era depois do commit) e podia ser
  infinita para o 9º contrato do lote.
- **Rollout:** o grupo novo lê com `auto-offset-reset: earliest`. No primeiro deploy ele percorre todos os
  `ContractCancelled` e `ContractExpired` ainda retidos no tópico (a retenção não é configurada no projeto; vale a do
  broker). Para os documentos que já estavam anulados, a PandaDoc deve responder 4xx (a resposta exata não foi
  confirmada na documentação): 404 e 409 viram `NotReady` e os demais 4xx viram `Rejected`, ambos resolvidos sem erro.
  Os que nunca foram anulados (o 9º em diante de cada lote de expiração) passam a ser, efeito desejado. O custo são
  chamadas à PandaDoc limitadas a 8 por minuto, proporcionais ao backlog retido.
- Replay da DLT (`POST /api/v1/admin/dead-letters/{topic}/replay`) republica a mensagem no tópico original, e os quatro
  grupos a recebem de novo. Os outros três já marcaram o evento em `processed_messages` e não fazem nada; só o grupo de
  anulação, que não marcou, refaz a tentativa.
- O tópico de eventos passa a ter quatro grupos ligados (orquestrador, pós-assinatura, anulação e notificações).
  `docs/agents/testes.md` §6 (mensagens de teste nesse tópico) e a seção 7.2 do guia refletem isso por esta decisão.
- Uma linha por evento de anulação em `processed_messages`, removida pelo `HousekeepingJob` como as demais.

## Alternativas consideradas

- **Manter a chamada na requisição, só reduzindo o tempo** (por exemplo, menos retentativas de 429): continua
  contrariando a regra inviolável 9 e continua sem tratar o lote de expiração.
- **Executar a chamada em outra thread da própria requisição** (`@Async` ou executor): é proibido por
  `docs/agents/regras-de-codigo.md` §3 e não é durável. Um reinício da aplicação perde a anulação, sem retentativa nem
  DLT.
- **Job agendado que varre contratos finais com documento ainda aberto:** exigiria uma coluna nova (por exemplo,
  `document_annulled_at`), migration `V2` e índice, e duplicaria a retentativa que o Kafka já oferece. O consumer
  reaproveita o outbox. Um job de varredura pode ser somado depois se a DLT não bastar (YAGNI).
- **Evento novo `DocumentAnnulmentRequested` em tópico próprio:** acrescentaria tópico, DLT e beans `NewTopic` para um
  fato que `ContractCancelled` e `ContractExpired` já expressam. Renomear ou criar eventos exige cuidado com mensagens
  já gravadas (ADR-009).
- **Corrigir pela reconciliação (`ReconcileContracts`):** ela só consulta contratos em aberto, e o contrato já terminou
  quando a anulação falha; mudar isso altera a semântica da reconciliação e disputa a mesma cota de chamadas.

## Verificação

- `AnnulProviderDocumentTest` (anula com `CANCELLED`/`EXPIRED` enviados; ignora sem documento, sem `sentAt` ou em outro
  status; reentrega e evento já processado não chamam o provedor; chamada fora de transação e evento marcado depois;
  404/409 e falhas definitivas resolvidos; `RateLimited` e `Unavailable` sobem sem marcar o evento).
- `CancelContractTest` e `ExpireOverdueContractsTest`: a transição grava `ContractCancelled`/`ContractExpired` no
  outbox e os casos de uso nem recebem o provedor; 12 contratos vencidos no mesmo lote expiram todos.
- `ContractConsumersTest`: roteamento por `eventType`, `aggregateId` inválido na DLT e delegação ao caso de uso.
- `LocaSignE2EIT`: com o `PATCH` de anulação atrasado 4 s no WireMock, `POST /cancel` responde 202 em menos de 3 s e o
  `PATCH` chega depois, aguardado com Awaitility.

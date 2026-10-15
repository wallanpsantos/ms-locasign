# ADR-017: ajustes de resiliência e performance decorrentes da auditoria de 2026-10-15

- **Status:** proposta
- **Data:** 15/10/2026
- **Relação com o guia:** complementa R2 (limites de entrada em `Money` e DTOs de locação), R7 (proteção de memória no
  webhook com HTTP 413 pré-inbox), R5 (semântica de reconciliação de fotos atrasadas do provedor sem falsas regressões),
  migração V2 de banco de dados e ajustes de configuração de pool, mensageria e jobs
- **Origem:** achados L1, H1, G1, R1, RL1, RC1, RC2, RC4, HK1, A3, A4, A5 e A7 de
  `docs/performance/auditoria-de-performance-2026-10-15.md`

## Contexto

A auditoria de performance e resiliência de 15/10/2026 identificou gargalos operacionais e vulnerabilidades de
estabilidade em cenários de saturação ou falha de componentes externos:

1. **Entrada de locação (L1):** `BigDecimal(String)` no parse de `Money` apresentava custo computacional quadrático (O
   (N²)), permitindo que payloads maliciosos com centenas de milhares de dígitos congelassem threads HTTP por segundos
   ou minutos. Além disso, os campos de `CreateLeaseRequest` não possuíam anotações `@Size` alinhadas às restrições das
   colunas de banco.
2. **Webhook desprotegido contra estouro de memória (A3, R7):** `@RequestBody ByteArray` lia todo o payload em memória
   antes da validação HMAC. Payloads excessivamente grandes podiam esgotar a heap.
3. **Bloqueio no Transactional Outbox sob falha de mensageria (R1, G1):** `OutboxRelay` realizava `send().get()`
   individual para cada linha dentro de uma transação com `FOR UPDATE`. Com o broker indisponível, cada linha aguardava
   até 10 s ou 60 s, retendo a conexão do pool Hikari e locks por mais de 16 minutos em lotes cheios. Além disso, o
   gauge de atraso utilizava `min(created_at)`, disparando `Parallel Seq Scan` no PostgreSQL em backlogs volumosos.
4. **Housekeeping e saturação de I/O em limpezas diárias (H1, A7):** Os três `DELETE` diários do `HousekeepingJob`
   realizavam `Seq Scan` em tabelas de centenas de milhares de linhas (`outbox_events`, `webhook_inbox` e
   `processed_messages`). O job usava `initialDelay` de 24 horas (nunca rodando em deploys frequentes) e uma falha em
   uma tabela abortava as demais.
5. **Limitador da PandaDoc e tempestade de DLT (RL1, C2):** O `SlidingWindowRateLimiter` rejeitava chamadas que
   excedessem 30 s de espera. Os consumidores Kafka retentavam rapidamente (1 s, 2 s, 4 s) e encaminhavam as mensagens
   para a DLT mesmo durante congestionamentos normais. `max.poll.records` com padrão de 500 arriscava estourar o
   `max.poll.interval.ms`.
6. **Amplificação na reconciliação e poluição de histórico (RC1, RC2, RC4):** `ReconcileContracts` recarregava agregados
   redundantes apenas para obter `providerDocumentId`; ao aplicar o estado, mesmo sem alterações de negócio, regravava
   todo o agregado (deletando e reinserindo signatários); e tratava o atraso de status da PandaDoc (que permanece em
   `sent`/`viewed` enquanto o contrato está em `PARTIALLY_SIGNED`) como rejeição de regressão, inserindo centenas de
   linhas de `IGNORED_TRANSITION` inúteis no histórico.
7. **Pool Hikari e observabilidade (HK1, A4, A5):** O pool Hikari operava com defaults (timeout de 30 s), contrariando a
   premissa de devolução rápida do webhook (R7). O Kafka producer usava `linger.ms: 5` mesmo com envios síncronos, e o
   `KafkaHealthIndicator` criava clientes administrativos sem cache.

## Decisão

1. **Defesa em profundidade na entrada (L1):**
    - Limitação do `Money.PATTERN` para `^\d{1,10}(\.\d{1,2})?$`, rejeitando em O (1) cadeias fora do formato ou com
      mais de 10 dígitos inteiros (compatível com `numeric(12,2)` do PostgreSQL).
    - Inclusão de `@field:Size` em `CreateLeaseRequest` compatível com o banco (`rentAmount` 13, `cpf` 14, `email` 254).

2. **Teto de payload no Webhook com HTTP 413 (A3, R7):**
    - Introdução de limite de tamanho de corpo no webhook (`locasign.pandadoc.webhook-max-body-bytes`, padrão 1 MiB).
      Requisições maiores são rejeitadas imediatamente com `HTTP 413 Payload Too Large` sem chegar ao HMAC ou ao inbox.
    - **Trade-off com R7:** Esta é uma troca deliberada com a diretriz "grava tudo bruto antes de qualquer
      processamento": a proteção de memória da infraestrutura contra ataques de DoS prevalece sobre o arquivamento de
      payloads anômalos fora de especificação.
    - Conexões abortadas durante o streaming de leitura resultam em log de erro apropriado.

3. **Interrupção de lote no OutboxRelay (R1, G1):**
    - Na primeira ocorrência de falha de transporte (`TimeoutException` ou `RetriableException`), o `OutboxRelay`
      registra a falha apenas da linha corrente e encerra imediatamente o processamento do lote, liberando a transação,
      a conexão do pool e os locks de linha. As mensagens restantes permanecem pendentes para o ciclo posterior.
    - Consulta do gauge de atraso do outbox reescrita com `ORDER BY seq LIMIT 1`, executando em O (1) (<0,03 ms) via
      `ix_outbox_pending`.

4. **Migração V2 e resiliência no Housekeeping (H1, A7):**
    - Criação da migration `V2__create_housekeeping_indexes.sql` com:
        - `ix_outbox_published` (índice parcial B-Tree onde `published_at IS NOT NULL`);
        - `ix_inbox_received` em `webhook_inbox (received_at)`;
        - `ix_processed_at` em `processed_messages (processed_at)`.
    - `HousekeepingJob` ajustado para `initialDelay: PT5M` e blocos de expurgo isolados por tabela com tratamento
      independente de exceção.

5. **Diferenciação de espera no RateLimiter e ajuste de polling (RL1, C2):**
    - `SlidingWindowRateLimiter` passa a suportar duas políticas: `RateLimitWait.FULL_WINDOW` (espera até 70 s,
      permitindo que consumidores Kafka aguardem a rotação da cota sem envio desnecessário à DLT) e
      `RateLimitWait.SHORT` (máximo 30 s para rotas síncronas de operador e jobs de reconciliação que devem falhar
      rápido).
    - Instrumentação de telemetria local: métricas `locasign.pandadoc.local_rate_limited` e
      `locasign.pandadoc.local_rate_waited`.
    - Configuração de `spring.kafka.consumer.max-poll-records: ${KAFKA_MAX_POLL_RECORDS:10}` em `application.yaml`.

6. **Reconciliação otimizada e sem ruído no histórico (RC1, RC2, RC4, C5):**
    - `findReconciliationCandidates` passa a projetar diretamente `(id, provider_document_id)`, eliminando a recarga
      prévia do agregado.
    - A consulta de candidatos elimina a cláusula de função `GREATEST`, permitindo uso direto do índice `updated_at`.
    - O laço de `ReconcileContracts` é interrompido no primeiro `RateLimited`.
    - Criação da porta `ReconciliationMarkerPort`: quando a reconciliação constata ausência de novas transições
      funcionais, atualiza apenas `last_reconciled_at` via `UPDATE` direcionado, dispensando regravação do agregado.
    - **Semântica de conciliação e R5:** na origem `RECONCILIATION`, o recebimento de um retrato de estado remoto com
      progresso ordinal inferior ao status corrente de um contrato ainda aberto (ex.: PandaDoc reportando `sent` para
      contrato já em `PARTIALLY_SIGNED`) é classificado como `TransitionResult.NO_CHANGE`, sem emissão de eventos e sem
      inclusão de `IGNORED_TRANSITION` na auditoria. Contratos em estados finais (`COMPLETED`, etc.) continuam imutáveis
      e continuam rejeitando e registrando qualquer tentativa de alteração.
    - Exposição da propriedade `RECONCILIATION_STALE_AFTER` na documentação e tabela de variáveis.

7. **Configurações operacionais (HK1, A4, A5):**
    - Pool Hikari explicitamente configurado: `maximum-pool-size: 10` e `connection-timeout: 5000ms`.
    - `linger.ms: 0` no produtor Kafka para minimizar latência por mensagem no relay sequencial.
    - Cache de 10 s em `management.endpoint.health.cache.time-to-live`.

## Consequências

- **Positivas:**
    - Aplicação blindada contra payloads gigantescos na API e webhooks.
    - O `OutboxRelay` tolera quedas prolongadas do broker Kafka sem exaurir o pool Hikari nem prender locks no
      PostgreSQL.
    - Limpezas de banco passam a executar em milissegundos via índices dedicados.
    - Reconciliação reduz em mais de 85% o volume de operações no banco e não polui o histórico de auditoria com falsas
      regressões.
    - Consumidores Kafka aguardam janelas do provedor sem rejeição prematura para DLT.
- **Negativas / Limitações:**
    - Manutenção de 3 índices adicionais no PostgreSQL (~43 MB para 2 milhões de linhas).
    - Payloads de webhook com mais de 1 MiB são descartados antes do registro em banco.

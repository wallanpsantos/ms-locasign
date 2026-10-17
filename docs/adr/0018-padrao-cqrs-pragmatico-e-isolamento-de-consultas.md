# ADR-018: Padrão CQRS Pragmático, Isolamento de Consultas e Governança de Projeções

- **Status:** proposta
- **Data:** 17/10/2026
- **Relação com o guia:** complementa §3.5 (CQRS de Segregação Lógica), §4.5 (Tabela de Use Cases e Queries), §8
  (PostgreSQL), §9 (API REST), R1 (Unicidade), R5 (Imutabilidade), R7 (Inbox), R10 (Proteção de Dados LGPD), ADR-002,
  ADR-003, ADR-011, ADR-013 e ADR-015
- **Origem:** Consolidação e governança estática da arquitetura CQRS Nível 1 no monólito modular hexagonal

## Contexto

O LocaSign adota a arquitetura de Monólito Modular Hexagonal (`br.com.locasign`), dividindo o sistema em módulos de
negócio (`lease`, `contract`, `notification`) e transversal (`shared`). Desde a sua concepção, o sistema reconhece a
assimetria fundamental entre escrita e leitura:

1. **Caminho de Escrita (*Command Side*):** opera em `app/usecases/` sob controle transacional estrito
   (`TransactionRunner.run { }`, ADR-011). É responsável por garantir invariantes de negócio, concorrência com bloqueio
   otimista (`row_version`), máquina de estados determinística (`ContractTransitionPolicy`, R5), integridade relacional
   (R1) e publicação atômica via *Transactional Outbox* (`outbox_events`). O agregado raiz (`Contract`, `Lease`)
   encapsula suas mutações e eventos de domínio.
2. **Caminho de Leitura (*Query Side*):** opera em `app/queries/` projetando dados com baixa latência e zero efeito
   colateral via `JdbcClient`, devolvendo DTOs imutáveis de apresentação (`*View`). Não abre transações explícitas e não
   instancia agregados nem mappers de domínio.

Apesar dessa divisão conceitual, foram identificados pontos de melhoria na coesão do padrão e na sua governança
automatizada:

- **Mistura de responsabilidades no endpoint de reconciliação:** o endpoint
  `POST /api/v1/contracts/{contractId}/reconcile` executava o caso de uso de comando `ReconcileContracts.executeFor` e,
  em seguida, disparava o serviço de consulta `GetContract.execute(id)` apenas para preencher os metadados da resposta
  HTTP 202 (`leaseId` e `status`), gerando dois `SELECT`s redundantes no banco (inclusive na tabela `contract_signers`).
- **Comunicação cross-module de leitura e proteção de PII (R10):**
    - O adaptador `LeaseQueryAdapter` executa `LEFT JOIN LATERAL` na tabela `contracts` para compor
      `LeaseDetailView.currentContract` em consulta única otimizada.
    - O adaptador `JdbcNotificationRecipientsAdapter` consulta diretamente a tabela `contract_signers` para resolver
      e-mails mascarados de destinatários de notificação, preservando a regra R10 (que veda a inclusão de CPF e e-mail
      em tópicos Kafka).
    - Ambos os acessos eram classificados como "exceções sem ADR" em `docs/agents/arquitetura.md` §7.
- **Leitura cadastral entre módulos no Command Side:** na emissão do contrato, `LeaseLookupAdapter` provê a fotografia
  `LeaseSnapshot` para `RequestContract` e `CreateProviderDocument`. Essa leitura necessita de dados íntegros e não
  mascarados (`tenantCpf: Cpf`) para preenchimento dos campos do modelo junto à PandaDoc, diferenciando-se da projeção
  de apresentação `LeaseQueryPort`, cujo CPF é estritamente mascarado para a web.
- **Ausência de governança estática de CQRS via ArchUnit:** o `HexagonalArchitectureTest` protegia camadas concêntricas
  (`domain`, `app`, `infra`, `interfaces`) e módulos, mas não impedia dependências proibidas entre `usecases` e
  `queries`.

## Decisão

1. **Formalização do CQRS Nível 1 como Padrão Canônico:**
    - O LocaSign adota e consolida o **CQRS de Segregação Lógica sobre o Mesmo Banco Relacional (Nível 1)**.
    - Rejeita-se categoricamente o CQRS Nível 2 (banco de leitura separado atualizado assincronamente via Kafka), pois a
      consistência eventual quebraria a semântica *Read-Your-Own-Writes* imediata em requisições `POST` seguidas de
      `GET` (como na jornada `01-fluxo-feliz` do Bruno e no `LocaSignE2EIT`), além de introduzir complexidade
      operacional contrária aos princípios KISS e YAGNI.
    - Rejeitam-se barramentos genéricos de mediação (*MediatR*, `CommandBus`, `QueryBus` reflexivos): chamadores injetam
      diretamente a classe concreta do use case ou query e invocam `execute(...)`.

2. **Segregação Estrita de Portas e Serviços em `app/`:**
    - **`app/usecases/`:** orquestra comandos de mutação, transações e efeitos colaterais. Depende de agregados de
      domínio e portas de saída de persistência/mensageria/integração (`*RepositoryPort`, `ReconciliationMarkerPort`,
      `PostSignatureActionsPort`, `TransactionRunner`, `OutboxPort`, `ProcessedMessagesPort`, `WebhookInboxPort`,
      `ContractEventPublisherPort`, `SignatureProviderPort`, `LeaseLookupPort`, `LeaseActivationPort`). É
      terminantemente proibido importar `app/queries/` ou interfaces `*QueryPort`.
    - **`app/queries/`:** orquestra projeções imutáveis somente-leitura. Depende exclusivamente de `*QueryPort`. É
      terminantemente proibido importar `app/usecases/`, `*RepositoryPort`, `TransactionRunner` ou portas de mensageria
      e mutação. Consultas de leitura não abrem transações explícitas com `TransactionRunner`.

3. **Retorno Enxuto de Metadados em Comandos Operacionais (`ReconcileContracts`):**
    - O método `ReconcileContracts.executeFor(contractId: ContractId)` passa a devolver
      `ReconciledContractSummary(val leaseId: LeaseId, val status: ContractStatus)`, contendo os metadados resultantes
      da reconciliação.
    - O caso de uso `ApplyProviderUpdate.execute(...)` passa a retornar o status final do contrato após a aplicação dos
      sinais (`ContractStatus?`), permitindo que a reconciliação capture o estado convergido na segunda transação sem
      disparar novas leituras.
    - O controller `ContractController.reconcile` utiliza o retorno de `executeFor`, eliminando a chamada a
      `GetContract.execute(id)` no fluxo de comando HTTP 202.

4. **Regulamentação das Projeções Cruzadas no Banco (Read Model Compartilhado):**
    - É formalmente autorizada a leitura direta em SQL via `JdbcClient` de tabelas de outros módulos para fins
      exclusivos de projeção imutável de leitura quando houver justificativa de integridade ou proteção de PII:
        - `LeaseQueryAdapter` em `contracts`: autorizado via `LEFT JOIN LATERAL` para montagem de
          `LeaseDetailView.currentContract` em consulta atômica no PostgreSQL.
        - `JdbcNotificationRecipientsAdapter` em `contract_signers`: autorizado para resolução de destinatários
          mascarados em notificações, mantendo tópicos Kafka e payloads livres de PII conforme Regra R10.
    - Ambas as consultas são estritamente somente-leitura (proibido qualquer `INSERT`, `UPDATE` ou `DELETE` em tabelas
      de outro módulo).

5. **Leitura Cadastral de Domínio na Ponte `LeaseLookupAdapter`:**
    - A ponte `LeaseLookupAdapter` consome `LeaseRepositoryPort.findById` para reconstituição do agregado de locação e
      geração do `LeaseSnapshot`. Isso garante que o módulo `contract` receba value objects de domínio íntegros (`Cpf`,
      `Email`, `Money`) para envio ao provedor de assinatura, sem contaminar `LeaseQueryPort` (cuja finalidade é
      projeção pública web com CPF mascarado).

6. **Governança Automatizada de Arquitetura (ArchUnit):**
    - Adicionam-se regras estáticas ao `HexagonalArchitectureTest`:
        - Nenhuma classe em `..app.queries..` pode depender de `..app.usecases..` ou de portas de escrita
          (`*RepositoryPort`, `ReconciliationMarkerPort`, `PostSignatureActionsPort`, `TransactionRunner`, `OutboxPort`,
          etc.).
        - Nenhuma classe em `..app.usecases..` pode depender de `..app.queries..` ou de portas de leitura
          (`*QueryPort`).

## Consequências

- **Positivas:**
    - Eliminação de dois `SELECT`s redundantes no endpoint de reconciliação manual.
    - Separação clara e auditável entre o caminho de comando e o caminho de consulta em tempo de compilação e teste
      estático.
    - Projeções de leitura permanecem livres de overhead transacional e reconstituição de agregados desnecessários.
    - Formalização definitiva em ADR de todas as consultas cross-module no banco relacional, eliminando exceções não
      documentadas.
- **Negativas / Limitações:**
    - `LeaseQueryAdapter` e `JdbcNotificationRecipientsAdapter` mantêm acoplamento relacional ao schema das tabelas
      `contracts` e `contract_signers`, exigindo atenção em eventuais migrações Flyway dessas tabelas.

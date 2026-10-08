# LocaSign

Serviço backend que automatiza o ciclo de vida de contratos de **locação residencial**: gera o
contrato a partir de um modelo, envia para assinatura eletrônica pela **PandaDoc**, acompanha cada
passo por **webhooks** e dispara sozinho as ações pós-assinatura (ativar a locação, agendar a
vistoria, registrar a primeira cobrança e notificar os envolvidos).

> Projeto de estudo de Kotlin 2.4, Spring Boot 4.1, PostgreSQL 18, Kafka 4.3 e Docker.
> O negócio está em [`docs/plano-de-negocio.md`](docs/plano-de-negocio.md) e a arquitetura em
> [`docs/arquitetura-tecnica.md`](docs/arquitetura-tecnica.md). Decisões posteriores ao guia ficam
> formalizadas em [`docs/adr/`](docs/adr). O planejamento e backlog de tarefas estão em
> [`docs/tasks/plan.md`](docs/tasks/plan.md) e [`docs/tasks/todo.md`](docs/tasks/todo.md). Use somente **dados fictícios** (R10).

## Subindo o ambiente

Pré-requisitos: JDK 25 e Docker.

```bash
docker compose up -d                                        # PostgreSQL 18 + Kafka 4.3 (KRaft)
./gradlew bootRun --args='--spring.profiles.active=local'   # a aplicação, pela linha de comando ou IDE
```

Verifique em `http://localhost:8080/actuator/health` (banco e Kafka devem estar `UP`) e explore a API em
`http://localhost:8080/swagger-ui.html`. Requisições prontas para o IntelliJ estão em
[`http/locasign.http`](http/locasign.http).

Tudo em containers, com um único comando:

```bash
docker compose --profile app up -d --build
```

Outros profiles do Compose: `tools` (Kafka UI em `http://localhost:8081`) e `tunnel` (túnel HTTPS
público para os webhooks). As portas são publicadas apenas em `127.0.0.1`.

## Configuração

Variáveis de ambiente (defina no ambiente ou em um arquivo `.env` ao lado do `compose.yml`;
o `.env` nunca deve ser versionado). Sem as de PandaDoc a aplicação sobe, mas não gera nem envia contratos.

| Variável                                     | Padrão                                                                | Uso                                                                                                                                                  |
|----------------------------------------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`     | `jdbc:postgresql://localhost:5432/locasign` / `locasign` / `locasign` | Banco                                                                                                                                                |
| `KAFKA_BOOTSTRAP_SERVERS`                    | `localhost:9092`                                                      | Kafka (`kafka:29092` dentro do Compose)                                                                                                              |
| `PANDADOC_API_KEY`                           | vazio                                                                 | Chave **sandbox** no desenvolvimento; produção só no teste final                                                                                     |
| `PANDADOC_TEMPLATE_ID`                       | vazio                                                                 | UUID do modelo (aparece na URL do editor)                                                                                                            |
| `PANDADOC_WEBHOOK_SHARED_KEY`                | vazio                                                                 | Shared key do webhook. **Sem ela, todo webhook é recusado (401)**                                                                                    |
| `PANDADOC_BASE_URL`                          | `https://api.pandadoc.com/public/v1`                                  | API                                                                                                                                                  |
| `PANDADOC_DOWNLOAD_ENABLED`                  | `false`                                                               | `true` somente com chave de produção (download do PDF assinado)                                                                                      |
| `PANDADOC_RATE_LIMIT_PER_MINUTE`             | `8`                                                                   | Margem abaixo do limite de 10/min do sandbox                                                                                                         |
| `AGENCY_SIGNER_NAME` / `AGENCY_SIGNER_EMAIL` | vazio                                                                 | Signatário padrão da imobiliária                                                                                                                     |
| `CONTRACT_SIGNATURE_DEADLINE_DAYS`           | `7`                                                                   | Prazo de assinatura (R4)                                                                                                                             |
| `APP_TIMEZONE`                               | `America/Sao_Paulo`                                                   | Fuso das regras de data                                                                                                                              |
| `ADMIN_TOKEN`                                | vazio                                                                 | Habilita as operações de operador (reconciliação forçada e replay da DLT), que exigem o header `X-Admin-Token`. **Vazio: ficam desabilitadas (403)** |
| `HEALTH_SHOW_DETAILS`                        | `when-authorized`                                                     | `always` no perfil `local`                                                                                                                           |
| `SWAGGER_ENABLED`                            | `true`                                                                | Defina `false` fora do ambiente local                                                                                                                |
| `ARCHIVE_DIRECTORY`                          | `./data/signed-documents`                                             | Onde os PDFs assinados são arquivados                                                                                                                |

Os jobs podem ser ajustados com `JOB_RECONCILIATION_INTERVAL`, `JOB_EXPIRATION_INTERVAL`,
`JOB_REMINDER_INTERVAL` e `RECONCILIATION_STALE_AFTER` (veja `application.yaml`).

## Preparando a PandaDoc (checklist do dia 1)

1. Crie a conta (o plano gratuito inclui chave sandbox e de produção).
2. Em *Settings → API and Integrations*, habilite API e Webhooks e gere a chave **sandbox**.
3. Crie o modelo com as roles **`Locatario`** e **`Imobiliaria`** (nomes exatos) e as variáveis
   `Locatario.Nome`, `Locatario.CPF`, `Imovel.Endereco`, `Aluguel.Valor`, `Locacao.Inicio` e `Locacao.Prazo`.
4. Copie o UUID do modelo (`PANDADOC_TEMPLATE_ID`).
5. Suba o túnel (`docker compose --profile tunnel up -d`, depois `docker compose logs tunnel` para ver a URL;
   alternativa: `ngrok http 8080`) e cadastre `https://<tunel>/webhooks/pandadoc` com os eventos
   `document_state_changed`, `recipient_completed`, `document_creation_failed`,
   `document_completed_pdf_ready` e `document_deleted`. Copie a shared key (`PANDADOC_WEBHOOK_SHARED_KEY`).
   A URL de túneis gratuitos muda a cada execução: atualize o cadastro quando reiniciar. A PandaDoc anexa
   `?signature=` sozinha, sem placeholder.
6. No sandbox, remetente e destinatários precisam ser do **mesmo domínio**. Use endereços como
   `voce+locatario@seudominio.com`. *(Aliases não foram verificados: confirme no primeiro envio.)*

Se os webhooks não estiverem disponíveis na sua conta, o job de **reconciliação** consulta a PandaDoc
periodicamente e faz o papel de polling (plano B).

## Como o fluxo funciona

```text
POST /leases/{id}/contracts ──► Contract(DRAFT) + outbox(ContractRequested)        202
                                         │ relay
                                         ▼
                                  Kafka: locasign.contract.events.v1
        ┌────────────────────────────────┼────────────────────────┬──────────────────┐
        ▼ orchestrator                   ▼ post-signature         ▼ notifications    │
   cria o documento ──► PandaDoc    ContractCompleted ──►     registra a notificação │
   (e depois envia)                 ativa locação,            simulada (mascarada)   │
        │                           vistoria e cobrança                              │
        ▼ PandaDoc ──webhook HTTPS──► /webhooks/pandadoc (HMAC sobre o corpo bruto)  │
                                         │ inbox + outbox (200 rápido)               │
                                         ▼                                           │
                       Kafka: locasign.pandadoc.webhooks.v1 ──► provider-events ─────┘
                                         │ ApplyProviderUpdate
                                         ▼
                       agregado Contract decide a transição (R5) ──► novos eventos
```

Princípios: a PandaDoc é a fonte da verdade do **documento**; o LocaSign é a fonte da verdade do **contrato**. Nada
externo é chamado dentro da requisição do usuário. Todo evento sai pelo **outbox**
e todo webhook entra pelo **inbox**. Tudo é **idempotente**.

### Ciclo de vida

`DRAFT → GENERATED → SENT → VIEWED → PARTIALLY_SIGNED → COMPLETED`, com os finais `DECLINED`,
`EXPIRED` e `CANCELLED`. O status nunca regride e estados finais são imutáveis (R5), mas saltos para frente
são permitidos, porque a PandaDoc não reenvia webhooks perdidos (ADR-010 do guia). Transições recusadas
ficam na auditoria como `IGNORED_TRANSITION`.

### API

| Método | Caminho                                     | Descrição                              |
|--------|---------------------------------------------|----------------------------------------|
| POST   | `/api/v1/leases`                            | Cadastra a locação (201)               |
| GET    | `/api/v1/leases/{id}`                       | Locação e contrato atual               |
| POST   | `/api/v1/leases/{id}/contracts`             | Solicita o contrato, nova versão (202) |
| GET    | `/api/v1/contracts/{id}`                    | Status, signatários e prazos           |
| GET    | `/api/v1/contracts/{id}/history`            | Linha do tempo completa                |
| POST   | `/api/v1/contracts/{id}/cancel`             | Cancela, com motivo (202)              |
| POST   | `/api/v1/contracts/{id}/reconcile`          | Força a reconciliação (operador)       |
| POST   | `/api/v1/admin/dead-letters/{topic}/replay` | Reprocessa a DLT (operador)            |
| POST   | `/webhooks/pandadoc`                        | Receptor de webhooks                   |
| GET    | `/actuator/health`                          | Saúde: banco e Kafka                   |

Erros seguem Problem Details (RFC 9457): `400 /problems/validation`, `422 /problems/business-rule`,
`404 /problems/not-found`, `409 /problems/active-contract-exists` e `/problems/contract-final`. **A API não tem
autenticação no MVP** (decisão registrada no plano e no guia); só as operações
de operador são restritas.

### Testando webhooks sem a PandaDoc

```bash
PANDADOC_WEBHOOK_SHARED_KEY=segredo scripts/send-signed-webhook.sh http/samples/document-state-changed.json
```

Edite o `id` do documento no JSON. Reenviar com o mesmo `DELIVERY_ID` testa a deduplicação.

### Fila de erros (DLT)

Mensagens que falham 3 vezes (backoff exponencial) vão para `<tópico>.dlt`. Depois de corrigir a causa,
`POST /api/v1/admin/dead-letters/locasign.contract.events.v1/replay` as devolve ao tópico original; como os
consumidores são idempotentes, reprocessar é seguro. O Kafka UI (`--profile tools`) ajuda a investigar.

## Estrutura

```text
src/main/kotlin/br/com/locasign/
├── shared/        # transversal: erros, value objects (Cpf, Email, Money), outbox/relay, inbox, DLT
├── lease/         # locação (enxuto no MVP)
├── contract/      # o coração: agregado Contract, política de transições, PandaDoc, webhooks, jobs
└── notification/  # notificações simuladas
   (cada módulo: domain ← app ← interfaces / infra, verificado pela regra de dependência do guia)
```

### Decisões Arquiteturais (ADRs)

As decisões que detalham ou refinam o guia técnico estão registradas em [`docs/adr/`](docs/adr/):

- **ADR-011:** Adota a porta `TransactionRunner` desacoplada de Spring para gerenciar transações curtas em casos de uso e evitar conexões retidas durante chamadas HTTP lentas à PandaDoc.
- **ADR-012:** Consolidação dos comportamentos e validações com a API da PandaDoc (validação HMAC sobre bytes brutos do webhook, retentativas em HTTP 409/404, download protegido de PDF, etc.).
- **ADR-013:** Ajustes de implementação em relação ao guia (distribuição de Value Objects, colunas adicionais para auditoria/backoff, replay manual de DLT via endpoint de admin protegido por `X-Admin-Token`).

### Documentação de Classes (KDoc Estruturado)

Todas as classes, interfaces, objetos e enums da aplicação seguem o padrão estruturado de KDoc em português brasileiro:
- **O que a classe faz:** contextualização clara do comportamento técnico e papel no fluxo de negócio.
- **Responsabilidade:** explicitação da responsabilidade única (SRP), limites na arquitetura hexagonal e garantias/regras de negócio asseguradas.

## Problemas comuns

- **A app conecta no Kafka mas não produz/consome:** quase sempre são os *listeners anunciados*.
  O Compose anuncia `kafka:29092` (rede do Compose) e `localhost:9092` (host). Use o par certo para onde a app roda.
- **Todo webhook recebe 401:** `PANDADOC_WEBHOOK_SHARED_KEY` ausente ou diferente da shared key do painel.
- **`409`/`404` ao enviar logo após criar:** esperado; o documento só pode ser enviado em `document.draft`.
  O consumidor retenta com backoff.
- **Porta 5432 ou 9092 ocupada:** defina `POSTGRES_PORT` / `KAFKA_PORT`.

## Meu resumo do fluxo (exercício de estudo)

> Regra de estudo do plano: ao fim de cada dia, explique aqui o fluxo completo **sem olhar o código**.

*(escreva aqui)*

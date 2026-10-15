# LocaSign

[![Kotlin 2.4.21](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Java 25](https://img.shields.io/badge/Java-25%20LTS-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL 18.6](https://img.shields.io/badge/PostgreSQL-18.6-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Apache Kafka 4.3.1](https://img.shields.io/badge/Apache%20Kafka-4.3.1%20KRaft-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org)
[![Gradle 9.8.1](https://img.shields.io/badge/Gradle-9.8.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![Testes Automatizados](https://img.shields.io/badge/Testes-234%20passing-brightgreen?logo=junit5&logoColor=white)](docs/adr/0014-estrategia-e-ferramentas-de-testes.md)

Serviço backend de alto desempenho e resiliência que automatiza integralmente o ciclo de vida de contratos de **locação
residencial**: cadastra a locação, orquestra a geração do contrato, envia para assinatura eletrônica na **PandaDoc**,
acompanha cada etapa por **webhooks assíncronos com idempotência estrita** e dispara sozinho as ações
pós-assinatura (ativação da locação, agendamento de vistoria, emissão da primeira cobrança e notificações simuladas).

> **Propósito do repositório:** projeto de estudo de engenharia de software pragmática com Kotlin, Spring Boot,
> PostgreSQL, Apache Kafka e Docker.  
> As regras de negócio canônicas (**R1 a R10**) residem em [`docs/plano-de-negocio.md`](docs/plano-de-negocio.md), e o
> desenho
> de engenharia em [`docs/arquitetura-tecnica.md`](docs/arquitetura-tecnica.md). Decisões de design posteriores ao guia
> ficam
> registradas em [`docs/adr/`](docs/adr/). As diretrizes de desenvolvimento e governança estão em [
`AGENTS.md`](AGENTS.md) e
> [`docs/agents/`](docs/agents/). Utilize exclusivamente **dados fictícios** (R10).

---

## Sumário

1. [Regras de Negócio Fundamentais (R1 a R10)](#1-regras-de-negócio-fundamentais-r1-a-r10)
2. [Pilha Tecnológica Fixada](#2-pilha-tecnológica-fixada)
3. [Arquitetura e Fluxo Orientado a Eventos](#3-arquitetura-e-fluxo-orientado-a-eventos)
4. [Ciclo de Vida do Contrato (Máquina de Estados)](#4-ciclo-de-vida-do-contrato-máquina-de-estados)
5. [Coleção Bruno: Demonstração Interativa por Jornadas](#5-coleção-bruno-demonstração-interativa-por-jornadas)
6. [Catálogo de Endpoints e Problem Details (RFC 9457)](#6-catálogo-de-endpoints-e-problem-details-rfc-9457)
7. [Executando o Ambiente Local](#7-executando-o-ambiente-local)
8. [Variáveis de Ambiente e Configuração](#8-variáveis-de-ambiente-e-configuração)
9. [Preparando a PandaDoc (Checklist do Dia 1)](#9-preparando-a-pandadoc-checklist-do-dia-1)
10. [Estratégia e Pirâmide de Testes (234 Testes)](#10-estratégia-e-pirâmide-de-testes-234-testes)
11. [Estrutura do Projeto e Decisões Arquiteturais (ADRs)](#11-estrutura-do-projeto-e-decisões-arquiteturais-adrs)
12. [Resiliência, Operação e Troubleshooting](#12-resiliência-operação-e-troubleshooting)

---

## 1. Regras de Negócio Fundamentais (R1 a R10)

As regras abaixo constituem os invariantes absolutos do domínio ([`docs/plano-de-negocio.md`](docs/plano-de-negocio.md)
§8) e
prevalecem sobre qualquer outra decisão de engenharia:

|  Regra  | Nome / Tema                      | Descrição Sucinta                                                                                                     | Consequência Técnica no Sistema                                                                                      |
|:-------:|:---------------------------------|:----------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------|
| **R1**  | Unicidade de Contrato Ativo      | Uma locação só pode ter um contrato em estado não terminal por vez.                                                   | Rejeição com `409 Conflict` (`/problems/active-contract-exists`) e índice parcial único no PostgreSQL.               |
| **R2**  | Dados Obrigatórios da Locação    | Locatário com CPF válido, endereço do imóvel, aluguel positivo (> 0), data presente/futura e prazo (padrão 30 meses). | Validação de domínio defensiva devolvendo `422 Unprocessable` (`/problems/business-rule`) e Bean Validation (`400`). |
| **R3**  | Ordem de Assinatura              | Primeiro assina o locatário; apenas após a conclusão deste, o representante da imobiliária é acionado.                | Orquestrado via PandaDoc signing order (`recipient_completed`).                                                      |
| **R4**  | Prazos e Lembretes               | Prazo máximo de 7 dias para assinatura, com lembrete automático disparado no 3º dia.                                  | Jobs agendados `@Scheduled` (`ExpirationJob` e `ReminderJob`).                                                       |
| **R5**  | Imutabilidade e Não Regressão    | O status nunca regride. Estados finais (`COMPLETED`, `CANCELLED`, `DECLINED`, `EXPIRED`) são estritamente imutáveis.  | Rejeição de transição com `409 Conflict` (`/problems/contract-final`) e log na trilha de auditoria.                  |
| **R6**  | Idempotência Estrita             | A recepção repetida do mesmo evento ou webhook nunca gera efeito duplicado.                                           | Consumidores com controle de versão/deduplicação (`markProcessed`) e checagem de transições válidas.                 |
| **R7**  | Gravação Bruta no Inbox          | Todo webhook da PandaDoc é gravado bruto no banco antes de qualquer processamento síncrono.                           | Persistência atômica em `webhook_inbox` + `outbox_events` devolvendo `HTTP 200` rápido (nunca `410`).                |
| **R8**  | Gatilho das Ações Pós-Assinatura | Ações pós-assinatura (ativação, vistoria, primeira cobrança) só ocorrem quando o contrato está `COMPLETED`.           | Consumidor Kafka escutando `locasign.contract.events.v1` disparado exclusivamente pelo evento terminal.              |
| **R9**  | Correção de Contrato             | Correções exigem cancelamento formal com motivo registrado e emissão de nova versão (v2, v3).                         | O histórico anterior é integralmente preservado; transiciona o anterior para `CANCELLED` e gera novo `DRAFT`.        |
| **R10** | Proteção de Dados (LGPD)         | Uso estrito de dados fictícios. CPF e e-mail nunca são exibidos abertos em logs ou DTOs externos.                     | Value objects `Cpf.masked()` e `Email.masked()` aplicados em serializadores, logs e auditoria.                       |

---

## 2. Pilha Tecnológica Fixada

A stack técnica é estritamente fixada e controlada via Version Catalog (`gradle/libs.versions.toml`) e BOM gerenciado:

| Tecnologia ou Área           | Versão / Configuração           | Papel e Justificativa de Engenharia                                               |
|------------------------------|---------------------------------|-----------------------------------------------------------------------------------|
| **Linguagem**                | Kotlin `2.4.21`                 | Null-safety nativa, `sealed classes` exaustivas e imutabilidade de domínio.       |
| **Plataforma / JVM**         | Java `25 LTS` (Eclipse Temurin) | Toolchain fixada, compilação moderna e execução de alta performance.              |
| **Framework Base**           | Spring Boot `4.1.1`             | Spring Framework 7.0.7, Jackson 3 e inicialização otimizada sem `open` classes.   |
| **Persistência Relacional**  | PostgreSQL `18.6`               | Spring Data JDBC e `JdbcClient` (SQL explícito, sem sobrecarga de JPA/Hibernate). |
| **Mensageria Distribuída**   | Apache Kafka `4.3.1` (KRaft)    | Broker em modo KRaft (sem ZooKeeper) e biblioteca cliente `kafka-clients` 4.2.    |
| **Automação de Build**       | Gradle `9.8.1`                  | Build determinístico, checagem de warnings (`-Werror`) e geração de `app.jar`.    |
| **Testes de Integração**     | Testcontainers `1.20.6`         | Subida real de instâncias de PostgreSQL e Kafka para validação rigorosa.          |
| **Mocking de APIs Externas** | WireMock `3.13.2` (ADR-014)     | Isolamento completo da API da PandaDoc com testes de rate limit e resiliência.    |
| **Arquitetura Estática**     | ArchUnit `1.5.1` (ADR-014)      | Validação automatizada contínua das 13 regras do monólito modular hexagonal.      |
| **Containers**               | Docker / Compose v5+            | Ambientes padronizados para desenvolvimento local e pipelines de CI.              |

---

## 3. Arquitetura e Fluxo Orientado a Eventos

O sistema foi concebido como um **monólito modular hexagonal** (`shared`, `lease`, `contract`, `notification`). Chamadas
externas síncronas são terminantemente proibidas no ciclo de vida de requisições do usuário: todas as integrações com a
PandaDoc são executadas de maneira assíncrona orientada a eventos através dos padrões **Transactional Outbox** e
**Inbox**.

```mermaid
flowchart TD
    subgraph Cliente["Cliente / Web Browser"]
        Req["POST /api/v1/leases/{id}/contracts"]
    end

    subgraph Core["LocaSign (Monólito Hexagonal)"]
        UC["RequestContract (Use Case)"]
        Agg["Aggregate Contract (DRAFT)"]
        OutboxTable[("outbox_events")]
        InboxTable[("webhook_inbox")]
        Relay["OutboxRelay (@Scheduled)"]
        Consumer["ContractEventsConsumer"]
        WebHookRecv["POST /webhooks/pandadoc"]
        AppProvider["ApplyProviderUpdate"]
    end

    subgraph Broker["Apache Kafka (KRaft)"]
        TopicContract["locasign.contract.events.v1"]
        TopicWebhooks["locasign.pandadoc.webhooks.v1"]
    end

    subgraph Externo["Provedor de Assinatura"]
        PandaDocAPI["PandaDoc API"]
    end

    Req -->|1 . Requisição| UC
    UC -->|2 . Transação Atômica| Agg
    UC -->|2 . Grava Evento| OutboxTable
    UC -.->|3 . Resposta Rápida 202 Accepted| Cliente
    Relay -->|4 . Polling & Publicação| OutboxTable
    Relay -->|5 . Publica ContractRequested| TopicContract
    TopicContract -->|6 . Consome Evento| Consumer
    Consumer -->|7 . Chamada Assíncrona REST| PandaDocAPI
    PandaDocAPI -.->|8 . Webhook HTTPS com HMAC| WebHookRecv
    WebHookRecv -->|9 . Grava Bruto no Inbox| InboxTable
    WebHookRecv -->|9 . Grava no Outbox| OutboxTable
    WebHookRecv -.->|10 . Resposta Rápida 200 OK| PandaDocAPI
    Relay -->|11 . Publica WebhookReceived| TopicWebhooks
    TopicWebhooks -->|12 . Consome Webhook| AppProvider
    AppProvider -->|13 . Transiciona Contrato| Agg
```

---

## 4. Ciclo de Vida do Contrato (Máquina de Estados)

O ciclo de vida do contrato residencial é governado pelo agregado `Contract` e pela política centralizada
`ContractTransitionPolicy`. Conforme a regra **R5**, o status nunca regride e estados terminais são absolutamente
imutáveis:

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Solicitação de Contrato (202)
    DRAFT --> GENERATED: Documento criado na PandaDoc
    GENERATED --> SENT: Documento enviado aos signatários
    SENT --> VIEWED: Locatário abriu o documento
    VIEWED --> PARTIALLY_SIGNED: Locatário assinou (R3)
    PARTIALLY_SIGNED --> COMPLETED: Imobiliária assinou (R3)
    DRAFT --> CANCELLED: Cancelamento Formal (R9)
    GENERATED --> CANCELLED: Cancelamento Formal (R9)
    SENT --> CANCELLED: Cancelamento Formal (R9)
    VIEWED --> CANCELLED: Cancelamento Formal (R9)
    PARTIALLY_SIGNED --> CANCELLED: Cancelamento Formal (R9)
    SENT --> DECLINED: Signatário recusou assinar
    VIEWED --> DECLINED: Signatário recusou assinar
    PARTIALLY_SIGNED --> DECLINED: Imobiliária recusou assinar
    SENT --> EXPIRED: Prazo de 7 dias esgotado (R4)
    VIEWED --> EXPIRED: Prazo de 7 dias esgotado (R4)
    PARTIALLY_SIGNED --> EXPIRED: Prazo de 7 dias esgotado (R4)
    COMPLETED --> [*]: Dispara Ações Pós-Assinatura (R8)
    CANCELLED --> [*]: Permite Nova Versão v2 (R1, R9)
    DECLINED --> [*]: Permite Nova Versão v2 (R1, R9)
    EXPIRED --> [*]: Permite Nova Versão v2 (R1, R9)
```

> **Atenção (Saltos para Frente):** Como a PandaDoc não garante a retransmissão de webhooks intermediários perdidos,
> saltos
> diretamente para `COMPLETED` ou `VIEWED` são válidos para garantir a convergência do estado final (ADR-010).

---

## 5. Coleção Bruno: Demonstração Interativa por Jornadas

O repositório disponibiliza uma coleção completa e pronta para importação no cliente de APIs **Bruno** (padrão
OpenCollection
YAML) em [`http/demonstracao-do-plano-de-negocio`](http/demonstracao-do-plano-de-negocio):

```text
http/demonstracao-do-plano-de-negocio/
├── opencollection.yml                             # Metadados e variáveis globais da coleção
├── collection.bru                                 # Documentação unificada para o cliente Bruno
├── README.md                                      # Guia detalhado de uso e roteiro da coleção
├── environments/
│   └── Local.yml                                  # Ambiente Local (baseUrl: http://localhost:8080)
├── 01-fluxo-feliz/                                # 📁 01 - Fluxo Feliz (Ponta a Ponta) (8 requisições)
├── 02-cenarios-de-erro/                           # 📁 02 - Cenários de Erro e Regras de Negócio (13 requisições)
├── 03-operacao-e-resiliencia/                     # 📁 03 - Operação e Resiliência (6 requisições)
└── 04-infraestrutura-e-observabilidade/           # 📁 04 - Infraestrutura e Observabilidade (4 requisições)
```

### Principais Benefícios da Coleção

1. **Organização Visual em 4 Pastas:** Permite testar o fluxo feliz do início ao fim sem poluição de erros, ou auditar
   exclusivamente a robustez defensiva da aplicação.
2. **Automação Pós-Resposta de Variáveis:** As requisições de sucesso (`01-cadastra-locacao`, `03-solicita-contrato` e
   `08-solicita-nova-versao`) extraem e definem automaticamente as variáveis `leaseId` e `contractId`, permitindo
   execução
   contínua sem cópia e cola manual.
3. **Isolamento de Erros:** As requisições de teste de erro `404` utilizam identificadores nulos deliberados
   (`00000000-0000-0000-0000-000000000000`), garantindo que não sobrescrevam as variáveis do contrato em teste.
4. **Comandos cURL Embutidos:** Cada um dos 31 arquivos `.yml` possui um bloco `docs` com o comando `curl` equivalente
   pronto para execução direta no terminal.

Consulte o [**Guia Completo da Coleção**](http/demonstracao-do-plano-de-negocio/README.md) para o roteiro detalhado.

---

## 6. Catálogo de Endpoints e Problem Details (RFC 9457)

### 6.1 Endpoints REST da Aplicação

| Método | Caminho                                     |      Perfil de Acesso      | Descrição e Status Esperado                                            |
|--------|---------------------------------------------|:--------------------------:|------------------------------------------------------------------------|
| `POST` | `/api/v1/leases`                            |          Público           | Cadastra nova locação residencial (`201 Created`).                     |
| `GET`  | `/api/v1/leases/{id}`                       |          Público           | Consulta detalhes da locação e contrato ativo (`200 OK`).              |
| `POST` | `/api/v1/leases/{id}/contracts`             |          Público           | Solicita confecção do contrato ou nova versão (`202 Accepted`).        |
| `GET`  | `/api/v1/contracts/{id}`                    |          Público           | Consulta status atual, signatários e prazos (`200 OK`).                |
| `GET`  | `/api/v1/contracts/{id}/history`            |          Público           | Linha do tempo cronológica com trilha de auditoria (`200 OK`).         |
| `POST` | `/api/v1/contracts/{id}/cancel`             |          Público           | Cancelamento formal de contrato com justificativa (`202 Accepted`).    |
| `POST` | `/webhooks/pandadoc`                        |          PandaDoc          | Ingestão de webhooks assinado via HMAC-SHA256 (`200 OK`).              |
| `POST` | `/api/v1/contracts/{id}/reconcile`          | Operador (`X-Admin-Token`) | Força reconciliação manual com a API da PandaDoc (`202 Accepted`).     |
| `POST` | `/api/v1/admin/dead-letters/{topic}/replay` | Operador (`X-Admin-Token`) | Reenvia mensagens represadas na DLT para o tópico original (`200 OK`). |
| `GET`  | `/actuator/health`                          |          Público           | Health check consolidado de banco e broker Kafka (`200 OK`).           |
| `GET`  | `/actuator/metrics/locasign.*`              |          Público           | Métricas dimensionais Micrometer de transições e webhooks (`200 OK`).  |
| `GET`  | `/v3/api-docs` / `/swagger-ui.html`         |          Público           | Especificação OpenAPI 3.1 e documentação interativa Swagger.           |

### 6.2 Mapeamento de Erros Determinísticos (RFC 9457)

Em conformidade com a RFC 9457 (Problem Details for HTTP APIs), todas as falhas de validação e violações de regras de
negócio
retornam payloads JSON padronizados:

|     Código HTTP     | Tipo (`type`)                      | Descrição da Ocorrência                                                       |
|:-------------------:|------------------------------------|-------------------------------------------------------------------------------|
|  `400 Bad Request`  | `/problems/validation`             | Campos obrigatórios ausentes, parâmetros malformados ou justificativa vazia.  |
| `401 Unauthorized`  | —                                  | Assinatura HMAC-SHA256 ausente ou inválida no webhook (nunca devolve 410).    |
|   `403 Forbidden`   | —                                  | Tentativa de acesso a rotas operacionais sem o cabeçalho `X-Admin-Token`.     |
|   `404 Not Found`   | `/problems/not-found`              | Locação, contrato ou tópico de mensageria inexistente no sistema.             |
|   `409 Conflict`    | `/problems/active-contract-exists` | Tentativa de solicitar novo contrato para locação com contrato ativo (R1).    |
|   `409 Conflict`    | `/problems/contract-final`         | Tentativa de alterar contrato em estado terminal imutável (R5).               |
| `422 Unprocessable` | `/problems/business-rule`          | Violação de regra de domínio (CPF inválido, data passada, aluguel <= 0) (R2). |

---

## 7. Executando o Ambiente Local

### Pré-requisitos

- **Java 25 LTS** instalado no sistema operacional (Eclipse Temurin recomendado).
- **Docker** ou **Rancher Desktop** ativo (com suporte a Docker Compose).

### 7.1 Execução Híbrida (Infraestrutura em Containers + App no Host)

Este é o modo mais ágil para desenvolvimento diário:

#### No Windows (PowerShell):

```powershell
# 1. Sobe PostgreSQL 18.6 e Apache Kafka 4.3.1 (KRaft) em segundo plano
docker compose up -d

# 2. Executa a aplicação Spring Boot com perfil local
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

#### No Linux / macOS (Bash):

```bash
# 1. Sobe PostgreSQL 18.6 e Apache Kafka 4.3.1 (KRaft) em segundo plano
docker compose up -d

# 2. Executa a aplicação Spring Boot com perfil local
./gradlew bootRun --args='--spring.profiles.active=local'
```

Acesse no navegador:

- **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) (banco e Kafka devem
  estar `UP`)
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

### 7.2 Execução Totalmente Conteinerizada

Para subir todos os componentes (banco, broker e a aplicação compilada em container) em um único comando:

```bash
docker compose --profile app up -d --build
```

---

### 7.3 Perfis Opcionais do Compose

O arquivo [`compose.yml`](compose.yml) possui perfis modulares adicionais:

- **Kafka UI (Interface Web de Mensageria):**
  ```bash
  docker compose --profile tools up -d
  ```
  Acesse o painel em [http://localhost:8081](http://localhost:8081) para inspecionar tópicos, offsets e mensagens de
  DLT.

- **Túnel HTTPS Público para Webhooks (Cloudflare):**
  ```bash
  docker compose --profile tunnel up -d
  docker compose logs tunnel  # exibe a URL pública HTTPS gerada
  ```

- **Encerrando os Containers com Segurança:**
  ```bash
  # Encerra os serviços mantendo os volumes persistidos
  docker compose down

  # CUIDADO: o comando abaixo apaga banco de dados e mensagens do Kafka!
  # docker compose down -v
  ```

---

## 8. Variáveis de Ambiente e Configuração

Defina as variáveis no ambiente ou em um arquivo `.env` na raiz do projeto (nunca versione o `.env`). A aplicação inicia
mesmo sem as credenciais da PandaDoc, permitindo testar cadastros e o fluxo local:

| Variável                           | Padrão                                      | Descrição e Impacto                                                                 |
|------------------------------------|---------------------------------------------|-------------------------------------------------------------------------------------|
| `DB_URL`                           | `jdbc:postgresql://localhost:5432/locasign` | URL de conexão JDBC ao PostgreSQL.                                                  |
| `DB_USERNAME` / `DB_PASSWORD`      | `locasign` / `locasign`                     | Credenciais do banco de dados relacional.                                           |
| `KAFKA_BOOTSTRAP_SERVERS`          | `localhost:9092`                            | Endereço do broker Kafka (`kafka:29092` na rede interna do Compose).                |
| `PANDADOC_API_KEY`                 | *(vazio)*                                   | Chave de API da PandaDoc (utilize chave **sandbox** no desenvolvimento).            |
| `PANDADOC_TEMPLATE_ID`             | *(vazio)*                                   | UUID do modelo de contrato configurado no painel da PandaDoc.                       |
| `PANDADOC_WEBHOOK_SHARED_KEY`      | *(vazio)*                                   | Chave secreta compartilhada para cálculo do HMAC-SHA256 dos webhooks.               |
| `PANDADOC_BASE_URL`                | `https://api.pandadoc.com/public/v1`        | URL base da API REST da PandaDoc.                                                   |
| `PANDADOC_DOWNLOAD_ENABLED`        | `false`                                     | Se `true`, realiza o download do PDF assinado (exige chave de produção).            |
| `PANDADOC_RATE_LIMIT_PER_MINUTE`   | `8`                                         | Limite defensivo por minuto (abaixo do teto de 10/min do sandbox).                  |
| `AGENCY_SIGNER_NAME` / `_EMAIL`    | *(vazio)*                                   | Nome e e-mail padrão do representante da imobiliária (signatário 2).                |
| `CONTRACT_SIGNATURE_DEADLINE_DAYS` | `7`                                         | Prazo limite em dias para conclusão das assinaturas (R4).                           |
| `APP_TIMEZONE`                     | `America/Sao_Paulo`                         | Fuso horário canônico para cálculos de datas de vigência e prazos.                  |
| `ADMIN_TOKEN`                      | *(vazio)*                                   | Habilita operações sensíveis via cabeçalho `X-Admin-Token`. Vazio desabilita (403). |
| `ARCHIVE_DIRECTORY`                | `./data/signed-documents`                   | Diretório local de armazenamento dos documentos PDFs concluídos.                    |

---

## 9. Preparando a PandaDoc (Checklist do Dia 1)

1. Crie uma conta na **PandaDoc** (o plano de avaliação inclui sandbox e produção).
2. Em *Settings → API and Integrations*, ative o acesso à API e gere uma chave de **Sandbox**.
3. Crie um modelo de contrato com os papéis de signatário exatos: **`Locatario`** e **`Imobiliaria`**.
4. Configure no modelo os tokens de preenchimento automático:
    - `[Locatario.Nome]`, `[Locatario.CPF]`, `[Imovel.Endereco]`, `[Aluguel.Valor]`, `[Locacao.Inicio]`,
      `[Locacao.Prazo]`.
5. Copie o UUID do modelo e configure na variável `PANDADOC_TEMPLATE_ID`.
6. Suba o túnel HTTPS (`docker compose --profile tunnel up -d`, confira a URL gerada com `docker compose logs tunnel`)
   e registre o webhook para a URL `https://<url-do-tunel>/webhooks/pandadoc` com os eventos:
    - `document_state_changed`, `recipient_completed`, `document_creation_failed`, `document_completed_pdf_ready` e
      `document_deleted`.
7. Copie a chave secreta compartilhada e preencha em `PANDADOC_WEBHOOK_SHARED_KEY`.

---

## 10. Estratégia e Pirâmide de Testes (234 Testes)

A aplicação conta com uma esteira rigorosa de **234 testes automatizados com 100% de aprovação**, cobrindo todas as
camadas
da arquitetura hexagonal (formalizada no [ADR-014](docs/adr/0014-estrategia-e-ferramentas-de-testes.md)):

```text
              ▲
             / \
            /E2E\             LocaSignE2EIT (1 teste)
           /-----\            Fluxo assíncrono completo: REST ➔ Outbox ➔ Kafka ➔ WireMock ➔ Webhook ➔ Ativação
          /  IT   \
         /---------\          Testcontainers (PostgreSQL 18.6 + Kafka 4.3.1 KRaft) (19 testes)
        / WireMock  \         Persistência real, índices parciais (R1), outbox, inbox e idempotência (R6, R7, R8)
       /-------------\
      /   ArchUnit    \       PandaDocSignatureProviderAdapterTest e PandaDocErrorsTest (20 testes)
     /-----------------\      Contratos REST, resiliência HTTP/1.1, rate limit 429, backoff e tradução de erros
    /     Web Slices    \
   /---------------------\    HexagonalArchitectureTest (13 testes)
  /   Unitários de Domínio\   Fronteiras arquiteturais do AGENTS.md §5 (docs/agents/arquitetura.md §6)
 /_________________________\
                              @WebMvcTest com MockMvcTester (27 testes)
                              Validação Jakarta Bean Validation, RFC 9457 Problem Details e segurança do operador

                              Domínio e Aplicação Puros (154 testes)
                              Agregados Contract e Lease, políticas de transição (R5), value objects (Cpf, Email, Money)
```

### Comandos de Execução da Suíte

```bash
# Executa apenas os testes rápidos que não necessitam de Docker (unitários, fatias web, wiremock e archunit)
.\gradlew.bat test --tests "*Test" --console=plain        # Windows PowerShell
./gradlew test --tests "*Test" --console=plain          # Linux / macOS

# Executa todos os testes (incluindo testes de integração *IT via Testcontainers; exige Docker ativo)
.\gradlew.bat test --console=plain                      # Windows PowerShell
./gradlew test --console=plain                          # Linux / macOS

# Gate completo de qualidade da CI (compilação sem warnings, testes e geração do build/libs/app.jar)
.\gradlew.bat build --console=plain                     # Windows PowerShell
./gradlew build --console=plain                         # Linux / macOS
```

---

## 11. Estrutura do Projeto e Decisões Arquiteturais (ADRs)

### 11.1 Árvore Modular Hexagonal

```text
src/main/kotlin/br/com/locasign/
├── shared/         # Transversal: value objects (Cpf, Email, Money), inbox, outbox, DLT, Time
├── lease/          # Módulo de locação (cadastro, consulta e ciclo de vida de Lease)
├── contract/       # Núcleo de contratos: agregado Contract, políticas de transição, PandaDoc, jobs
└── notification/   # Módulo assíncrono de notificações simuladas aos envolvidos
```

Cada módulo organiza-se rigorosamente nas camadas:
`interfaces (web/kafka)` ➔ `app (casos de uso e portas de saída)` ➔ `domain (Kotlin puro)` ➔ `infra (adapters)`.

### 11.2 Registro de Decisões Arquiteturais (ADRs)

As decisões de engenharia que expandem ou refinam o guia inicial estão formalizadas em [`docs/adr/`](docs/adr/):

- [**ADR-011: Transações Curtas com TransactionRunner**](docs/adr/0011-transaction-runner-no-lugar-de-decorator.md) —
  Elimina conexões de banco presas durante chamadas lentas à PandaDoc, abandonando `@Transactional` e AOP em favor de
  blocos funcionais explícitos.
- [**ADR-012: Comportamentos Confirmados da PandaDoc**](docs/adr/0012-confirmacoes-da-pandadoc.md) — Validação HMAC
  sobre bytes brutos do corpo, download protegido de PDF assinado e retentativas defensivas em códigos HTTP 409/404.
- [**ADR-013: Ajustes de Implementação do LocaSign**](docs/adr/0013-ajustes-em-relacao-ao-guia.md) — Localização
  canônica de Value Objects, colunas para backoff de outbox e proteção de endpoints operacionais com `X-Admin-Token`.
- [**ADR-014: Estratégia e Ferramentas de Testes**](docs/adr/0014-estrategia-e-ferramentas-de-testes.md) — Padronização
  das convenções `*Test` e `*IT`, incorporação do WireMock para isolamento HTTP e ArchUnit para garantia estática da
  arquitetura.
- [**ADR-015: Convenção de KDoc Estruturado**](docs/adr/0015-convencao-de-kdoc.md) — Padrão unificado de documentação
  técnica em português brasileiro destacando comportamento técnico e responsabilidade única (SRP).

---

## 12. Resiliência, Operação e Troubleshooting

### 12.1 Armadilha Clássica dos Listeners do Kafka

O broker Kafka no Docker Compose anuncia dois endereços distintos:

- `PLAINTEXT://kafka:29092` — exclusivo para comunicação entre containers (como o container `app`).
- `EXTERNAL://localhost:9092` — exclusivo para clientes no host da máquina física (sua IDE ou `./gradlew bootRun`).  
  Se a aplicação inicia mas não consegue produzir ou consumir mensagens, certifique-se de que `KAFKA_BOOTSTRAP_SERVERS`
  está
  apontando para o endereço correto de acordo com onde a aplicação está rodando.

### 12.2 Validação de Webhook e Erro 401

Se o receptor `/webhooks/pandadoc` responder sistematicamente `401 Unauthorized`, verifique:

1. Se a variável `PANDADOC_WEBHOOK_SHARED_KEY` está preenchida exatamente com a mesma chave gerada no painel da
   PandaDoc.
2. A validação HMAC-SHA256 é calculada estritamente sobre os **bytes brutos do corpo HTTP** recebido; qualquer alteração
   ou
   formatação intermediária invalida o hash.
3. Utilize o script de teste local:
   ```bash
   PANDADOC_WEBHOOK_SHARED_KEY=segredo scripts/send-signed-webhook.sh scripts/samples/document-state-changed.json
   ```

### 12.3 Fila de Cartas Mortas (Dead Letter Topics - DLT)

Mensagens com falhas irrecuperáveis após 3 tentativas com backoff exponencial são enviadas automaticamente para
`<topico>.dlt`. Para reprocessá-las após a resolução do problema:

```bash
curl -X POST "http://localhost:8080/api/v1/admin/dead-letters/locasign.contract.events.v1/replay" \
     -H "X-Admin-Token: seu_token_configurado"
```

Como todos os consumidores da aplicação são rigorosamente idempotentes (R6), a reexecução de mensagens da DLT nunca gera
efeitos duplicados indesejados.

# Coleção Bruno: Demonstração do Plano de Negócio

Coleção de requisições HTTP para o cliente de APIs **Bruno** (padrão **OpenCollection YAML**), cobrindo integralmente o
ciclo de vida de contratos residenciais do **LocaSign**, as regras de negócio **R1 a R10**, o fluxo feliz ponta a ponta,
cenários de erro mapeados deterministicamente no padrão **RFC 9457 (Problem Details)**, operações de resiliência e
observabilidade.

A coleção está estruturada visualmente em 4 pastas numeradas para uma experiência fluida de navegação e demonstração.

---

## 1. Como Abrir no Bruno

1. Abra o **Bruno** (versão 3.0 ou superior).
2. No menu principal ou na tela inicial, clique em **Open Collection** (ou *Abrir Coleção*).
3. Selecione a pasta deste diretório:  
   `http/demonstracao-do-plano-de-negocio`
4. No canto superior direito da janela do Bruno, selecione o ambiente **Local** (ou utilize as variáveis padrão da
   coleção).
5. A coleção exibirá as seguintes 4 pastas estruturadas:
    - 📁 **01 - Fluxo Feliz (Ponta a Ponta)** (8 requisições)
    - 📁 **02 - Cenários de Erro e Regras de Negócio** (13 requisições)
    - 📁 **03 - Operação e Resiliência** (6 requisições)
    - 📁 **04 - Infraestrutura e Observabilidade** (4 requisições)

---

## 2. Automação de Variáveis entre Requisições

As requisições de sucesso contêm scripts pós-resposta (*post-response*) que capturam e armazenam automaticamente os
identificadores gerados para reutilização em cascata:

- **`01 - Cadastra a locação - Sucesso (201 Created)`**: extrai e define a variável `leaseId`.
- **`03 - Solicita o contrato - Sucesso (202 Accepted)`**: extrai e define a variável `contractId`.
- **`08 - Solicita nova versão após cancelamento - Sucesso (202 Accepted)`**: atualiza a variável `contractId` com o
  identificador da nova versão (v2).

As demais requisições do fluxo feliz e operacional consomem `{{leaseId}}` e `{{contractId}}` diretamente.  
Para garantir o isolamento estrito dos dados, as requisições da pasta `02 - Cenários de Erro` que testam recursos
inexistentes
utilizam identificadores nulos deliberados (`00000000-0000-0000-0000-000000000000`), nunca sobrescrevendo as variáveis
da locação e contrato ativos.

---

## 3. Estrutura Detalhada das Pastas

### 📁 Pasta 01: Fluxo Feliz (Ponta a Ponta)

Ciclo de vida sequencial e completo de um contrato residencial, desde o cadastro da locação até a assinatura,
cancelamento e nova versão:

|  Seq   | Nome da Requisição                       | Método | Caminho                                    | Status Esperado | Regra / Objetivo                                     |
|:------:|:-----------------------------------------|:------:|:-------------------------------------------|:---------------:|:-----------------------------------------------------|
| **01** | Cadastra a locação - Sucesso             | `POST` | `/api/v1/leases`                           |  `201 Created`  | Regra **R2**: dados válidos, captura `leaseId`       |
| **02** | Consulta a locação - Sucesso             | `GET`  | `/api/v1/leases/{{leaseId}}`               |    `200 OK`     | Consulta locação cadastrada (`REGISTERED`)           |
| **03** | Solicita o contrato - Sucesso            | `POST` | `/api/v1/leases/{{leaseId}}/contracts`     | `202 Accepted`  | Inicia versão 1 (`DRAFT`), captura `contractId`      |
| **04** | Acompanha o status do contrato - Sucesso | `GET`  | `/api/v1/contracts/{{contractId}}`         |    `200 OK`     | Consulta status atual, signatários e prazos (R3, R4) |
| **05** | Linha do tempo e histórico - Sucesso     | `GET`  | `/api/v1/contracts/{{contractId}}/history` |    `200 OK`     | Auditoria cronológica completa com todos os eventos  |
| **06** | Webhook PandaDoc - Sucesso               | `POST` | `/webhooks/pandadoc`                       |    `200 OK`     | Regra **R7**: gravação bruta no inbox e outbox Kafka |
| **07** | Cancela o contrato - Sucesso             | `POST` | `/api/v1/contracts/{{contractId}}/cancel`  | `202 Accepted`  | Regra **R9**: cancelamento formal para `CANCELLED`   |
| **08** | Solicita nova versão após cancelamento   | `POST` | `/api/v1/leases/{{leaseId}}/contracts`     | `202 Accepted`  | Regras **R1** e **R9**: gera versão 2 para a locação |

---

### 📁 Pasta 02: Cenários de Erro e Regras de Negócio

Bateria completa de testes de validação defensiva e invariantes de negócio mapeadas no padrão **RFC 9457**:

|  Seq   | Nome da Requisição                              | Método | Caminho                                        |   Status Esperado   | Regra / Violação                                                       |
|:------:|:------------------------------------------------|:------:|:-----------------------------------------------|:-------------------:|:-----------------------------------------------------------------------|
| **01** | Cadastra a locação - Erro 400                   | `POST` | `/api/v1/leases`                               |  `400 Bad Request`  | Bean Validation: payload vazio ou campos ausentes                      |
| **02** | Cadastra a locação - Erro 422 (CPF inválido)    | `POST` | `/api/v1/leases`                               | `422 Unprocessable` | Regra **R2**: CPF com dígitos verificadores matematicamente incorretos |
| **03** | Cadastra a locação - Erro 422 (Data no passado) | `POST` | `/api/v1/leases`                               | `422 Unprocessable` | Regra **R2**: `startDate` anterior ao dia corrente                     |
| **04** | Cadastra a locação - Erro 422 (Aluguel zerado)  | `POST` | `/api/v1/leases`                               | `422 Unprocessable` | Regra **R2**: `rentAmount` menor ou igual a zero                       |
| **05** | Consulta a locação - Erro 404                   | `GET`  | `/api/v1/leases/{uuid_inexistente}`            |   `404 Not Found`   | RFC 9457: locação não encontrada                                       |
| **06** | Solicita o contrato - Erro 404                  | `POST` | `/api/v1/leases/{uuid_inexistente}/contracts`  |   `404 Not Found`   | Tentativa de gerar contrato para locação inexistente                   |
| **07** | Solicita o contrato - Erro 409 (Contrato ativo) | `POST` | `/api/v1/leases/{{leaseId}}/contracts`         |   `409 Conflict`    | Regra **R1**: contrato ativo concorrente não permitido                 |
| **08** | Acompanha o status do contrato - Erro 404       | `GET`  | `/api/v1/contracts/{uuid_inexistente}`         |   `404 Not Found`   | Contrato inexistente                                                   |
| **09** | Linha do tempo e histórico - Erro 404           | `GET`  | `/api/v1/contracts/{uuid_inexistente}/history` |   `404 Not Found`   | Histórico de contrato inexistente                                      |
| **10** | Webhook PandaDoc - Erro 401                     | `POST` | `/webhooks/pandadoc?signature=invalida`        | `401 Unauthorized`  | Falha na validação HMAC-SHA256 (nunca devolve 410)                     |
| **11** | Cancela o contrato - Erro 400                   | `POST` | `/api/v1/contracts/{{contractId}}/cancel`      |  `400 Bad Request`  | Justificativa de cancelamento vazia ou em branco                       |
| **12** | Cancela o contrato - Erro 404                   | `POST` | `/api/v1/contracts/{uuid_inexistente}/cancel`  |   `404 Not Found`   | Cancelamento sobre contrato inexistente                                |
| **13** | Cancela o contrato - Erro 409 (Estado final)    | `POST` | `/api/v1/contracts/{{contractId}}/cancel`      |   `409 Conflict`    | Regra **R5**: estados terminais são estritamente imutáveis             |

---

### 📁 Pasta 03: Operação e Resiliência

Operações operacionais e administrativas protegidas pelo cabeçalho `X-Admin-Token`:

|  Seq   | Nome da Requisição                      | Método | Caminho                                          | Status Esperado | Objetivo Técnico                                           |
|:------:|:----------------------------------------|:------:|:-------------------------------------------------|:---------------:|:-----------------------------------------------------------|
| **01** | Força reconciliação PandaDoc - Erro 403 | `POST` | `/api/v1/contracts/{{contractId}}/reconcile`     | `403 Forbidden` | ADR-013: rejeição por ausência de token administrativo     |
| **02** | Força reconciliação PandaDoc - Erro 404 | `POST` | `/api/v1/contracts/{uuid_inexistente}/reconcile` | `404 Not Found` | Reconciliação sobre contrato inexistente                   |
| **03** | Força reconciliação PandaDoc - Sucesso  | `POST` | `/api/v1/contracts/{{contractId}}/reconcile`     | `202 Accepted`  | Sincronização manual com a API externa da PandaDoc         |
| **04** | Reprocessa DLT Kafka - Erro 403         | `POST` | `/api/v1/admin/dead-letters/{topic}/replay`      | `403 Forbidden` | Acesso não autorizado sem credencial de operador           |
| **05** | Reprocessa DLT Kafka - Erro 404         | `POST` | `/api/v1/admin/dead-letters/{invalido}/replay`   | `404 Not Found` | Tópico desconhecido (fora do catálogo canônico `Topics`)   |
| **06** | Reprocessa DLT Kafka - Sucesso          | `POST` | `/api/v1/admin/dead-letters/{topic}/replay`      |    `200 OK`     | Reenvia mensagens represadas na DLT para o tópico original |

---

### 📁 Pasta 04: Infraestrutura e Observabilidade

Validação da saúde da pilha técnica (PostgreSQL 18, Apache Kafka 4.3.1 KRaft) e métricas Micrometer:

|  Seq   | Nome da Requisição                         | Método | Caminho                                                  | Status Esperado | Métrica / Recurso                                       |
|:------:|:-------------------------------------------|:------:|:---------------------------------------------------------|:---------------:|:--------------------------------------------------------|
| **01** | Saúde da Aplicação (Actuator Health)       | `GET`  | `/actuator/health`                                       |    `200 OK`     | Health check consolidado de banco e broker Kafka        |
| **02** | Documentação da API (OpenAPI Spec v3)      | `GET`  | `/v3/api-docs`                                           |    `200 OK`     | Especificação OpenAPI 3.1 da aplicação                  |
| **03** | Métricas de negócio - Webhooks recebidos   | `GET`  | `/actuator/metrics/locasign.webhook.received`            |    `200 OK`     | Contador Micrometer de webhooks ingeridos               |
| **04** | Métricas de negócio - Transições aplicadas | `GET`  | `/actuator/metrics/locasign.contract.transition.applied` |    `200 OK`     | Contador Micrometer com tags por status alvo (`target`) |

---

## 4. Execução via Linha de Comando (cURL)

Todas as requisições `.yml` desta coleção possuem um bloco `docs` com o comando `curl` equivalente pronto para terminal
(PowerShell no Windows ou Bash no Linux). Basta copiar o comando da documentação da requisição no Bruno ou executar
diretamente a partir dos arquivos.

---

## 5. Roteiro Prático: Da Criação à Finalização com Assinatura Completa (Regra R8)

Por padrão, a pasta `01 - Fluxo Feliz` demonstra a alteração para `VIEWED` seguida de **cancelamento formal** e
**emissão de nova versão (v2)** (exercitando as regras **R5** e **R9**).

Se você deseja testar o ciclo de **finalização por assinatura completa** e observar as ações pós-assinatura da regra
**R8** (ativação da locação para `ACTIVE`, agendamento de vistoria e primeira cobrança), siga este roteiro:

### 5.1 Pré-requisitos

A aplicação precisa estar rodando com a chave compartilhada de webhook configurada:

```powershell
$env:PANDADOC_WEBHOOK_SHARED_KEY = "segredo"
$env:ADMIN_TOKEN = "abc123"
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

No Bruno, certifique-se de que o ambiente **Local** está selecionado no canto superior direito (para definir
`{{baseUrl}} = http://localhost:8080`).

### 5.2 Passos de Execução

1. **Passo 01 (`POST /api/v1/leases`):** cadastra a locação (`201 Created`). O Bruno salva o `leaseId`.
2. **Passo 02 (`GET /api/v1/leases/{{leaseId}}`):** confirma status inicial `REGISTERED`.
3. **Passo 03 (`POST /api/v1/leases/{{leaseId}}/contracts`):** solicita o contrato (`202 Accepted`). O Bruno salva o
   `contractId`.
4. **Passo 04 (`GET /api/v1/contracts/{{contractId}}`):** confirma contrato em `DRAFT`.
5. **Passo 06 (Webhook Assinado de Conclusão):**
    - **Pelo Bruno:** na requisição `06 - Webhook PandaDoc`, mude no JSON de entrada `"status": "document.viewed"` para
      `"status": "document.completed"` e nos signatários defina `"has_completed": true`. O script *Pre-Request*
      calculará o HMAC-SHA256 automaticamente com a chave `segredo` e enviará com sucesso (`200 OK`).
    - **Pelo PowerShell (Script Rápido):**
      ```powershell
      $contractId = "COLE_AQUI_O_CONTRACT_ID"
      $secret = "segredo"
      $payload = @"
      [{"event":"document_state_changed","data":{"id":"doc_123","date_modified":"$([System.DateTime]::UtcNow.ToString('o'))","status":"document.completed","metadata":{"contract_id":"$contractId"},"recipients":[{"email":"maria@example.com","role":"Locatario","has_completed":true},{"email":"alianca@example.com","role":"Imobiliaria","has_completed":true}]}}]
      "@
      $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
      $hmac = New-Object System.Security.Cryptography.HMACSHA256
      $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
      $sig = [System.BitConverter]::ToString($hmac.ComputeHash($bytes)).Replace('-', '').ToLower()
      Invoke-RestMethod -Uri "http://localhost:8080/webhooks/pandadoc?signature=$sig" -Method Post -Headers @{"Content-Type"="application/json";"X-PandaDoc-Webhook-Event-Id"=[System.Guid]::NewGuid().ToString()} -Body $bytes
      ```

### 5.3 Como Conferir os Efeitos Pós-Assinatura (Regra R8)

1. Execute novamente o **Passo 04 (`GET /api/v1/contracts/{{contractId}}`)**: o status estará **`COMPLETED`**.
2. Execute o **Passo 05 (`GET /api/v1/contracts/{{contractId}}/history`)**: a linha do tempo registrará a transição via
   webhook.
3. Execute o **Passo 02 (`GET /api/v1/leases/{{leaseId}}`)**: o status da locação mudou sozinho de `REGISTERED` para **
   `ACTIVE`**, com a data de ativação `activatedAt` devidamente preenchida!
4. No banco de dados ou nos logs, você verá a execução das ações registradas na tabela `post_signature_actions` e a
   notificação aos signatários em `notifications_log`.

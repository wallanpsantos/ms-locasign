# Coleção Bruno: Demonstração do Plano de Negócio

Coleção de requisições HTTP para o cliente de APIs **Bruno** (padrão **OpenCollection YAML**), cobrindo integralmente o
ciclo de vida de contratos residenciais do **LocaSign**, as regras de negócio **R1 a R10**, os fluxos felizes e todos os
cenários de erro mapeados deterministicamente no padrão **RFC 9457 (Problem Details)**.

---

## 1. Como Abrir no Bruno

1. Abra o **Bruno** (versão 3.0 ou superior).
2. No menu principal ou na tela inicial, clique em **Open Collection** (ou *Abrir Coleção*).
3. Selecione a pasta deste diretório:  
   `http/demonstracao-do-plano-de-negocio`
4. No canto superior direito da janela do Bruno, selecione o ambiente **Local** (ou utilize as variáveis padrão da
   coleção).
5. As requisições aparecerão organizadas e numeradas de **01 a 31**, na ordem recomendada de execução.

---

## 2. Automação de Variáveis entre Requisições

As requisições de sucesso contêm scripts pós-resposta (*post-response*) que armazenam automaticamente os identificadores
gerados:

- **`03 - Cadastra a locação - Sucesso (201 Created)`**: extrai e define a variável `leaseId`.
- **`10 - Solicita o contrato - Sucesso (202 Accepted)`**: extrai e define a variável `contractId`.
- **`23 - Solicita nova versão após cancelamento - Sucesso (202 Accepted)`**: atualiza a variável `contractId` com o
  identificador da versão 2.

As requisições subsequentes consomem `{{leaseId}}` e `{{contractId}}` diretamente, dispensando cópia manual.  
As requisições de teste de erro 404 utilizam identificadores nulos deliberados (`00000000-0000-0000-0000-000000000000`),
garantindo que a execução dos erros não altere as variáveis do fluxo feliz.

---

## 3. Roteiro Sequencial das Requisições

| Ordem  | Nome da Requisição                               | Método | Caminho                                                  |   Status Esperado   | Regra / Objetivo                                              |
|:------:|:-------------------------------------------------|:------:|:---------------------------------------------------------|:-------------------:|:--------------------------------------------------------------|
| **01** | Saúde da Aplicação (Actuator Health)             | `GET`  | `/actuator/health`                                       |      `200 OK`       | Validação de infraestrutura (Postgres 18 e Kafka 4.3.1 KRaft) |
| **02** | Documentação da API (OpenAPI Spec v3)            | `GET`  | `/v3/api-docs`                                           |      `200 OK`       | Contrato OpenAPI 3.1 JSON (`/swagger-ui.html`)                |
| **03** | Cadastra a locação - Sucesso                     | `POST` | `/api/v1/leases`                                         |    `201 Created`    | Regra **R2**: dados válidos, salva `leaseId`                  |
| **04** | Cadastra a locação - Erro 400                    | `POST` | `/api/v1/leases`                                         |  `400 Bad Request`  | Bean Validation: campos obrigatórios ausentes/inválidos       |
| **05** | Cadastra a locação - Erro 422 (CPF inválido)     | `POST` | `/api/v1/leases`                                         | `422 Unprocessable` | Regra **R2**: validação de dígitos verificadores do CPF       |
| **06** | Cadastra a locação - Erro 422 (Data no passado)  | `POST` | `/api/v1/leases`                                         | `422 Unprocessable` | Regra **R2**: `startDate` anterior à data atual               |
| **07** | Cadastra a locação - Erro 422 (Aluguel zerado)   | `POST` | `/api/v1/leases`                                         | `422 Unprocessable` | Regra **R2**: `rentAmount` menor ou igual a zero              |
| **08** | Consulta a locação - Sucesso                     | `GET`  | `/api/v1/leases/{{leaseId}}`                             |      `200 OK`       | Detalhes da locação cadastrada (`REGISTERED`)                 |
| **09** | Consulta a locação - Erro 404                    | `GET`  | `/api/v1/leases/{uuid_inexistente}`                      |   `404 Not Found`   | RFC 9457: recurso não encontrado                              |
| **10** | Solicita o contrato - Sucesso                    | `POST` | `/api/v1/leases/{{leaseId}}/contracts`                   |   `202 Accepted`    | Inicia versão 1 (`DRAFT`), salva `contractId`                 |
| **11** | Solicita o contrato - Erro 404                   | `POST` | `/api/v1/leases/{uuid_inexistente}/contracts`            |   `404 Not Found`   | Locação inexistente                                           |
| **12** | Solicita o contrato - Erro 409 (Contrato ativo)  | `POST` | `/api/v1/leases/{{leaseId}}/contracts`                   |   `409 Conflict`    | Regra **R1**: apenas um contrato ativo por locação            |
| **13** | Acompanha o status do contrato - Sucesso         | `GET`  | `/api/v1/contracts/{{contractId}}`                       |      `200 OK`       | Consulta status atual, signatários e prazos (R3, R4)          |
| **14** | Acompanha o status do contrato - Erro 404        | `GET`  | `/api/v1/contracts/{uuid_inexistente}`                   |   `404 Not Found`   | Contrato não encontrado                                       |
| **15** | Linha do tempo e histórico - Sucesso             | `GET`  | `/api/v1/contracts/{{contractId}}/history`               |      `200 OK`       | Trilha de auditoria cronológica completa                      |
| **16** | Linha do tempo e histórico - Erro 404            | `GET`  | `/api/v1/contracts/{uuid_inexistente}/history`           |   `404 Not Found`   | Histórico inexistente                                         |
| **17** | Webhook PandaDoc - Sucesso                       | `POST` | `/webhooks/pandadoc`                                     |      `200 OK`       | Regra **R7**: gravação bruta no inbox e outbox Kafka          |
| **18** | Webhook PandaDoc - Erro 401                      | `POST` | `/webhooks/pandadoc?signature=invalida`                  | `401 Unauthorized`  | ADR-012: falha na validação HMAC-SHA256 (nunca 410)           |
| **19** | Cancela o contrato - Erro 400                    | `POST` | `/api/v1/contracts/{{contractId}}/cancel`                |  `400 Bad Request`  | Justificativa de cancelamento em branco                       |
| **20** | Cancela o contrato - Erro 404                    | `POST` | `/api/v1/contracts/{uuid_inexistente}/cancel`            |   `404 Not Found`   | Contrato não encontrado                                       |
| **21** | Cancela o contrato - Sucesso                     | `POST` | `/api/v1/contracts/{{contractId}}/cancel`                |   `202 Accepted`    | Regra **R9**: transiciona para `CANCELLED`                    |
| **22** | Cancela o contrato - Erro 409 (Estado final)     | `POST` | `/api/v1/contracts/{{contractId}}/cancel`                |   `409 Conflict`    | Regra **R5**: estados finais são estritamente imutáveis       |
| **23** | Solicita nova versão após cancelamento - Sucesso | `POST` | `/api/v1/leases/{{leaseId}}/contracts`                   |   `202 Accepted`    | Regras **R1** e **R9**: gera versão 2 para a locação          |
| **24** | Força reconciliação PandaDoc - Erro 403          | `POST` | `/api/v1/contracts/{{contractId}}/reconcile`             |   `403 Forbidden`   | ADR-013: token `X-Admin-Token` ausente/inválido               |
| **25** | Força reconciliação PandaDoc - Erro 404          | `POST` | `/api/v1/contracts/{uuid_inexistente}/reconcile`         |   `404 Not Found`   | Contrato inexistente com token de operador válido             |
| **26** | Força reconciliação PandaDoc - Sucesso           | `POST` | `/api/v1/contracts/{{contractId}}/reconcile`             |   `202 Accepted`    | Sincronização manual com a API externa da PandaDoc            |
| **27** | Reprocessa DLT Kafka - Erro 403                  | `POST` | `/api/v1/admin/dead-letters/{topic}/replay`              |   `403 Forbidden`   | Operação restrita a operadores                                |
| **28** | Reprocessa DLT Kafka - Erro 404                  | `POST` | `/api/v1/admin/dead-letters/{invalido}/replay`           |   `404 Not Found`   | Tópico desconhecido (fora de `Topics.ALL`)                    |
| **29** | Reprocessa DLT Kafka - Sucesso                   | `POST` | `/api/v1/admin/dead-letters/{topic}/replay`              |      `200 OK`       | Reenvia mensagens da DLT para o tópico original               |
| **30** | Métricas de negócio - Webhooks recebidos         | `GET`  | `/actuator/metrics/locasign.webhook.received`            |      `200 OK`       | Métrica dimensional do Micrometer                             |
| **31** | Métricas de negócio - Transições aplicadas       | `GET`  | `/actuator/metrics/locasign.contract.transition.applied` |      `200 OK`       | Métrica dimensional do Micrometer por status alvo             |

---

## 4. Execução via Linha de Comando (cURL)

Cada arquivo `.yml` desta coleção possui uma seção `docs` detalhada contendo o comando `curl` equivalente pronto para
cópia e execução direta no terminal (PowerShell no Windows ou Bash no Linux).

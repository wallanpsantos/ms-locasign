# ADR-013: ajustes pequenos em relação ao guia técnico

- **Status:** aceita

Pequenas decisões tomadas na implementação, que o guia deixava em aberto ou descrevia de forma
diferente. Nenhuma altera uma regra de negócio (R1 a R10).

| Tema | Decisão | Motivo |
|---|---|---|
| Localização de value objects | `Cpf`, `Email` e `Money` ficam em `shared/domain/valueobjects`; `LeaseId` e `LeaseTerm` em `lease/domain`; `ContractId`, `ProviderDocumentId` e `SigningOrder` em `contract/domain`. | Locação e contrato usam os mesmos tipos; evita um módulo depender do outro só por um tipo. |
| Auditoria | `contract_status_history.outcome` ganhou o valor `INFO` (além de `APPLIED` e `IGNORED_TRANSITION`). | A linha do tempo registra fatos que não mudam o status: assinatura de um signatário, arquivamento, anomalias. |
| Colunas extras | `contracts`: `sent_at`, `reminder_sent_at`, `last_reconciled_at`, `signed_document_ref`. `leases`: `status`, `activated_at`, `row_version`. `outbox_events`: `seq`, `available_at`. | Lembrete do 3º dia, reconciliação sem repetir consulta a cada ciclo, arquivamento, ativação da locação e backoff do relay. |
| Ids de mensagem | `outbox_events.id` e `processed_messages.event_id` são `text`. | Itens de webhook usam `deliveryId:índice`, que não é UUID. |
| Contrato concluído | Uma locação com contrato `COMPLETED` não aceita nova versão (HTTP 409). | O plano só permite nova versão após Recusado, Expirado ou Cancelado. |
| Falha de envio | Erro 403/4xx definitivo no envio cancela o contrato com motivo `GENERATION_FAILED` e detalhe. | Reaproveita o caminho de falha de geração; o detalhe distingue as causas. |
| Ordem no relay | O relay lê por `seq`; se uma mensagem falha, as seguintes da mesma chave esperam no lote. | Preserva a ordem por chave sem bloquear as demais. Entre lotes a ordem é "melhor esforço"; o agregado tolera desordem (R5). |
| DLT | `POST /api/v1/admin/dead-letters/{topic}/replay` republica a DLT no tópico original. | Cumpre "investigar e reprocessar" do plano; seguro porque os consumidores são idempotentes. |
| Operações sensíveis | `POST .../reconcile` e o replay da DLT exigem o header `X-Admin-Token` igual ao `ADMIN_TOKEN`; **sem o token configurado ficam desabilitadas (403)**. O endereço de origem nunca é considerado confiável. O resto da API continua sem autenticação, como decidido para o MVP (guia, seções 9.1 e 14). | A reconciliação consome a cota de requisições da PandaDoc e o replay republica mensagens. Uma versão anterior aceitava loopback sem token, mas o túnel dos webhooks (ngrok, cloudflared) chega da própria máquina e faria qualquer cliente da internet parecer operador. |
| Health | `show-components: always` e `show-details: when-authorized` (o perfil `local` usa `always`). | Evita expor caminhos de disco e ids do cluster; o status de banco e Kafka continua visível. |
| Portas do Compose | Publicadas só em `127.0.0.1`; o alvo do túnel é configurável (`TUNNEL_TARGET`). | As credenciais padrão de desenvolvimento não devem ficar acessíveis na rede local. |
| Notificação de recusa | `ContractDeclined.reason` traz o que o sistema sabe (a PandaDoc não informa o motivo). | Ver ADR-012. |

# ADR-012: itens "(confirmar)" do guia, verificados na documentação da PandaDoc

- **Status:** aceita
- **Data da verificação:** 08/10/2026, em `developers.pandadoc.com` (páginas `.md` do `llms.txt`)

| Item do guia | Resultado verificado | Onde está no código |
|---|---|---|
| Assinatura do webhook | `?signature=` é anexado **automaticamente** à URL; não é preciso incluir placeholder no cadastro. HMAC-SHA256 em hexadecimal do corpo bruto, com a shared key. | `PandaDocWebhookGateway.isAuthentic` |
| Resposta a assinatura inválida | A documentação sugere 403; o guia manda 401. Mantido **401** (qualquer status que não seja 410 serve). | `PandaDocWebhookController` |
| Formato do `metadata` | Objeto de pares chave-valor de texto, devolvido em `data.metadata` nos webhooks e na consulta de detalhes. Enviamos `contract_id` e `lease_id`. | `ProviderDocumentRequest.toPandaDoc` |
| Status de recusa | Existe `document.declined` (código `12`). Não há campo documentado com o motivo da recusa. | `String.toProviderStatus`, `ContractDeclined.reason` |
| Anular documento | `PATCH /documents/{id}/status` com `{"status": 11}` (`document.voided`, rotulado "Expired" na UI). Vale a partir de `sent`/`viewed`, não de `draft`. Uso em melhor esforço no cancelamento e na expiração. | `SignatureProviderPort.cancelDocument` |
| Envio antes de ficar pronto | `POST /documents/{id}/send` responde **409** enquanto o documento está `document.uploaded`; `GET` responde **404**. Ambos viram `ProviderException.NotReady` (retentável). | `PandaDocErrors.kt` |
| Download protegido | `GET /documents/{id}/download-protected`: 200 com o PDF, 202 enquanto é gerado, **401 com chave sandbox** (só produção). | `PandaDocSignatureProviderAdapter.downloadSigned` |
| Estado dos destinatários | `GET /documents/{id}/details` traz `recipients[].has_completed` e `role`. Usado na reconciliação. | `DocumentDetailsResponse.toState` |
| Eventos de webhook | `document_state_changed`, `recipient_completed`, `document_creation_failed`, `document_completed_pdf_ready`, `document_deleted` são usados. `document_updated` e os demais são gravados no inbox e ignorados (descrevem a mesma mudança com outro id). | `PandaDocWebhookGateway.translate` |
| Aliases de e-mail no sandbox | **Não verificado** (depende da conta). O sandbox exige remetente e destinatários do mesmo domínio. Teste `voce+locatario@seudominio.com` no primeiro envio real. | `docs`/README |

## Observação sobre o cliente HTTP

O corpo JSON é enviado com `Content-Length` (buffer da requisição), e não em `Transfer-Encoding: chunked`,
que alguns proxies e WAFs rejeitam com 411.

#!/usr/bin/env bash
# Envia um webhook de teste assinado com HMAC-SHA256, como a PandaDoc faria.
#
# Uso:
#   PANDADOC_WEBHOOK_SHARED_KEY=segredo scripts/send-signed-webhook.sh http/samples/document-state-changed.json
#   ... [url]   (padrão: http://localhost:8080/webhooks/pandadoc)
#
# Reenviar o mesmo arquivo com o mesmo X-PandaDoc-Webhook-Event-Id testa a deduplicação:
#   DELIVERY_ID=teste-1 scripts/send-signed-webhook.sh arquivo.json
set -euo pipefail

payload="${1:?informe o arquivo JSON do webhook}"
url="${2:-http://localhost:8080/webhooks/pandadoc}"
key="${PANDADOC_WEBHOOK_SHARED_KEY:?defina PANDADOC_WEBHOOK_SHARED_KEY}"
delivery_id="${DELIVERY_ID:-$(cat /proc/sys/kernel/random/uuid 2>/dev/null || date +%s%N)}"

# A assinatura é calculada sobre os bytes exatos do arquivo, e o curl envia esses mesmos bytes.
signature="$(openssl dgst -sha256 -hmac "$key" -hex "$payload" | sed 's/^.* //')"

curl --silent --show-error --include \
  --request POST "${url}?signature=${signature}" \
  --header 'Content-Type: application/json' \
  --header "X-PandaDoc-Webhook-Event-Id: ${delivery_id}" \
  --data-binary "@${payload}"
echo

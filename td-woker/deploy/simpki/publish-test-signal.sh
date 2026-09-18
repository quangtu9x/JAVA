#!/usr/bin/env bash
# =====================================================================
# Publish 1 envelope "simpki.sign" thử nghiệm vào RabbitMQ (qua REST API của plugin management,
# KHÔNG cần cài amqp client) để kiểm tra end-to-end: Worker (profile simpki, xem run-simpki.sh) có
# nhận/consume/gọi MSSP/ghi log/publish phản hồi hay không — xem docs/18-trien-khai-test-simpki.md.
#
# Yêu cầu: RabbitMQ đã bật plugin management (cổng mặc định 15672) — docker-compose.yml của project
# đã bật sẵn plugin này. KHÔNG cần jq — tự escape JSON bằng sed (mọi giá trị do script tự sinh, không
# có ký tự đặc biệt cần escape phức tạp).
#
# Dùng: ./publish-test-signal.sh [msisdn] [noi-dung-can-ky]
#   ./publish-test-signal.sh 84972495193 "noi dung test"
# Biến môi trường tuỳ chỉnh: RABBITMQ_MGMT_URL, RABBITMQ_MGMT_USER, RABBITMQ_MGMT_PASS,
# RABBITMQ_VHOST (mặc định "/"), WORKER_RABBIT_EXCHANGE (mặc định worker.exchange).
# =====================================================================
set -euo pipefail

RABBITMQ_MGMT_URL="${RABBITMQ_MGMT_URL:-http://localhost:15672}"
RABBITMQ_MGMT_USER="${RABBITMQ_MGMT_USER:-guest}"
RABBITMQ_MGMT_PASS="${RABBITMQ_MGMT_PASS:-guest}"
RABBITMQ_VHOST="${RABBITMQ_VHOST:-/}"
EXCHANGE="${WORKER_RABBIT_EXCHANGE:-worker.exchange}"

MSISDN="${1:-84900000000}"
HASH_TEXT="${2:-noi dung test simpki $(date +%s)}"
HASH_B64="$(printf '%s' "$HASH_TEXT" | base64 | tr -d '\n')"

TS="$(date +%s)"
RAND="${RANDOM:-0}"
MESSAGE_ID="msg-simpki-test-${TS}-${RAND}"
UNIT_ID="u-simpki-test-${TS}-${RAND}"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

ENVELOPE=$(cat <<JSON
{"messageId":"${MESSAGE_ID}","taskType":"simpki.sign","correlationId":"${MESSAGE_ID}","source":"publish-test-signal.sh","createdAt":"${CREATED_AT}","headers":{},"units":[{"unitId":"${UNIT_ID}","tenantId":"test","payload":{"documentId":"test-doc-1","msisdn":"${MSISDN}","hashBase64":"${HASH_B64}"},"target":{}}]}
JSON
)

# Escape JSON để nhúng làm 1 STRING trong field "payload" của REST API management (\ trước, " sau).
ENVELOPE_ESCAPED=$(printf '%s' "$ENVELOPE" | sed 's/\\/\\\\/g; s/"/\\"/g')

# URL-encode vhost (thường "/" -> "%2F").
VHOST_ENC=$(printf '%s' "$RABBITMQ_VHOST" | sed 's#/#%2F#g')

BODY="{\"properties\":{\"delivery_mode\":2,\"content_type\":\"application/json\"},\"routing_key\":\"worker.simpki.sign\",\"payload\":\"${ENVELOPE_ESCAPED}\",\"payload_encoding\":\"string\"}"

echo "Publish messageId=${MESSAGE_ID}, unitId=${UNIT_ID}, msisdn=${MSISDN} -> exchange=${EXCHANGE}, routing_key=worker.simpki.sign"

RESPONSE=$(curl -sS -u "${RABBITMQ_MGMT_USER}:${RABBITMQ_MGMT_PASS}" \
    -H "Content-Type: application/json" \
    -X POST "${RABBITMQ_MGMT_URL}/api/exchanges/${VHOST_ENC}/${EXCHANGE}/publish" \
    -d "${BODY}")

echo "RabbitMQ response: ${RESPONSE}"
case "$RESPONSE" in
    *'"routed":true'*)
        echo "OK — message đã được route tới ít nhất 1 queue. Kiểm tra log Worker + bảng worker_simpki_sign_log."
        ;;
    *'"routed":false'*)
        echo "CẢNH BÁO: routed=false — KHÔNG queue nào bind đúng routing key 'worker.simpki.sign'." >&2
        echo "Kiểm tra: Worker (profile simpki) đã khởi động + khai báo topology chưa (log 'RabbitMQ topology: ... routing=worker.simpki.sign')." >&2
        exit 1
        ;;
    *)
        echo "CẢNH BÁO: không nhận diện được phản hồi (kiểm tra URL/credential quản trị RabbitMQ)." >&2
        exit 1
        ;;
esac

#!/usr/bin/env bash
# =====================================================================
# Trigger 1 envelope "simpki.sign" thử nghiệm qua HTTP TRỰC TIẾP (KHÔNG cần RabbitMQ) — dùng khi
# test độc lập, chưa có broker thật (docs/18-trien-khai-test-simpki.md mục "Test độc lập không cần
# RabbitMQ"). Yêu cầu Worker đang chạy với worker.debug.endpoint.enabled=true (`WORKER_DEBUG_ENDPOINT_ENABLED=true`
# trong .env — CHỈ dùng dev/test, xem cảnh báo ở DebugTriggerController).
#
# Khác publish-test-signal.sh (publish qua RabbitMQ Management API): script này POST thẳng envelope
# JSON tới Worker, không qua broker, không cần jq/base64-escape lồng nhau — đơn giản hơn nhiều.
#
# Dùng: ./publish-test-http.sh [msisdn] [noi-dung-can-ky]
# Biến môi trường tuỳ chỉnh: WORKER_HTTP_URL (mặc định http://localhost:8090).
# =====================================================================
set -euo pipefail

WORKER_HTTP_URL="${WORKER_HTTP_URL:-http://localhost:8090}"
MSISDN="${1:-84900000000}"
HASH_TEXT="${2:-noi dung test simpki $(date +%s)}"
HASH_B64="$(printf '%s' "$HASH_TEXT" | base64 | tr -d '\n')"

TS="$(date +%s)"
RAND="${RANDOM:-0}"
MESSAGE_ID="msg-simpki-http-${TS}-${RAND}"
UNIT_ID="u-simpki-http-${TS}-${RAND}"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

ENVELOPE=$(cat <<JSON
{"messageId":"${MESSAGE_ID}","taskType":"simpki.sign","correlationId":"${MESSAGE_ID}","source":"publish-test-http.sh","createdAt":"${CREATED_AT}","headers":{},"units":[{"unitId":"${UNIT_ID}","tenantId":"test","payload":{"documentId":"test-doc-1","msisdn":"${MSISDN}","hashBase64":"${HASH_B64}"},"target":{}}]}
JSON
)

echo "POST ${WORKER_HTTP_URL}/internal/debug/publish  messageId=${MESSAGE_ID} unitId=${UNIT_ID} msisdn=${MSISDN}"
echo "(Lưu ý: nếu ký thật, request này CHỜ tới worker.tasks.simpki.timeout-seconds — mặc định 300s —"
echo " vì đang chờ người dùng xác nhận trên điện thoại. Ctrl+C không huỷ giao dịch phía MSSP, chỉ"
echo " ngắt kết nối HTTP chờ kết quả.)"

curl -sS -X POST "${WORKER_HTTP_URL}/internal/debug/publish" \
    -H "Content-Type: application/json" \
    -d "${ENVELOPE}" \
    -w '\nHTTP %{http_code}\n'

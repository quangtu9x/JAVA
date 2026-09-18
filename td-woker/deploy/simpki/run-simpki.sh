#!/usr/bin/env bash
# =====================================================================
# Chạy td-worker CHỈ với module simpki.sign (profile "simpki", docs/18-trien-khai-test-simpki.md).
# Dùng cho Linux. Bản Windows tương ứng: run-simpki.ps1 (cùng thư mục).
#
# Yêu cầu TRƯỚC khi chạy (xem docs/18-trien-khai-test-simpki.md):
#   1. td-worker.jar (copy từ target/td-worker.jar sau khi build, hoặc dùng build.sh).
#   2. .env (copy từ .env.simpki.example, điền giá trị thật — KHÔNG commit).
#   3. certs/simpki/truststore-bcy.jks + certs/simpki/<keystore-that>.p12 (KHÔNG commit).
#   4. Database tdworker đã tạo (create-database.sql) — Worker tự migrate bảng lúc khởi động.
#
# Script LUÔN chạy TỪ thư mục chứa chính nó (cd) — mọi đường dẫn tương đối trong .env
# (certs/simpki/..., logs/...) đều tính từ đây.
#
# QUAN TRỌNG (đã tự phát hiện bằng thực nghiệm — không suy đoán): Spring Boot đọc ".env" qua
# "spring.config.import: optional:file:.env[.properties]" — cơ chế này KHÔNG áp dụng "relaxed
# binding" đầy đủ (không tự map WORKER_TASKS_SIMPKI_ENABLED -> worker.tasks.simpki.enabled) cho các
# property KHÔNG có placeholder ${...} tương ứng khai báo sẵn trong application.yml (vd
# worker.tasks.simpki.*, worker.tasks.guinhanquocgia.*, worker.debug.endpoint.* — các module đăng
# ký qua @ConfigurationProperties riêng, không đụng application.yml). Chỉ đọc file KHÔNG đủ — script
# này tự EXPORT từng dòng KEY=VALUE trong .env thành biến môi trường HỆ ĐIỀU HÀNH THẬT trước khi chạy
# java (biến môi trường thật luôn được Spring relaxed-bind đúng, đã xác nhận bằng thực nghiệm).
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_FILE="${SIMPKI_JAR:-td-worker.jar}"
if [[ ! -f "$JAR_FILE" ]]; then
    echo "Không tìm thấy $JAR_FILE trong $SCRIPT_DIR — chạy build.sh trước, hoặc copy target/td-worker.jar vào đây." >&2
    exit 1
fi
if [[ -f ".env" ]]; then
    echo "Nạp .env thành biến môi trường thật (xem ghi chú ở trên)..."
    set -a
    # shellcheck disable=SC1091
    source ".env"
    set +a
else
    echo "Cảnh báo: không thấy .env trong $SCRIPT_DIR — copy .env.simpki.example thành .env và điền giá trị thật trước khi chạy thật." >&2
fi

mkdir -p logs

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-simpki}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

echo "Khởi động td-worker (profile=$SPRING_PROFILES_ACTIVE) từ $SCRIPT_DIR ..."
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar "$JAR_FILE"

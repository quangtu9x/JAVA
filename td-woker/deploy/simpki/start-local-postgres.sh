#!/usr/bin/env bash
# =====================================================================
# Dựng NHANH 1 Postgres CỤC BỘ (Docker container) cho môi trường test simpki.sign khi máy chủ
# CHƯA có Postgres thật (docs/18-trien-khai-test-simpki.md). KHÔNG dùng cho production — chỉ để
# Worker Core có checkpoint DB (BẮT BUỘC, không thể bỏ qua — mọi taskType kể cả simpki.sign đều cần
# checkpoint/idempotency, xem docs/07-idempotency-checkpoint.md) khi chưa có hạ tầng DB thật.
#
# Yêu cầu: đã cài Docker trên máy chủ test. Nếu KHÔNG có Docker, xem hướng dẫn cài Postgres native
# trong docs/18-trien-khai-test-simpki.md mục "Chưa có Postgres — dựng DB local".
#
# Dùng:
#   SIMPKI_DB_PASSWORD=doi-mat-khau-nay ./start-local-postgres.sh
# Chạy lại (idempotent): nếu container đã tồn tại, script chỉ start lại (KHÔNG mất dữ liệu cũ).
# =====================================================================
set -euo pipefail

: "${SIMPKI_DB_PASSWORD:?Đặt biến môi trường SIMPKI_DB_PASSWORD (mật khẩu Postgres muốn dùng) trước khi chạy, vd: SIMPKI_DB_PASSWORD=doi-mat-khau-nay ./start-local-postgres.sh}"

CONTAINER_NAME="${SIMPKI_DB_CONTAINER_NAME:-td-worker-simpki-db}"
DB_PORT="${SIMPKI_DB_PORT:-5432}"

if ! command -v docker >/dev/null 2>&1; then
    echo "Không tìm thấy lệnh 'docker' — cài Docker trước, hoặc dùng Postgres native (xem docs/18-trien-khai-test-simpki.md)." >&2
    exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    echo "Container $CONTAINER_NAME đã tồn tại — start lại (giữ nguyên dữ liệu cũ nếu có)."
    docker start "$CONTAINER_NAME"
else
    echo "Tạo mới container $CONTAINER_NAME (postgres:15-alpine, port $DB_PORT, database/user 'tdworker')."
    docker run -d --name "$CONTAINER_NAME" \
        -e POSTGRES_DB=tdworker \
        -e POSTGRES_USER=tdworker \
        -e POSTGRES_PASSWORD="$SIMPKI_DB_PASSWORD" \
        -p "${DB_PORT}:5432" \
        postgres:15-alpine
fi

echo "Chờ Postgres sẵn sàng..."
for i in $(seq 1 30); do
    if docker exec "$CONTAINER_NAME" pg_isready -U tdworker >/dev/null 2>&1; then
        echo "Postgres local đã sẵn sàng tại localhost:${DB_PORT} (database=tdworker, user=tdworker)."
        break
    fi
    sleep 1
done

cat <<EOF

Cập nhật .env (cạnh td-worker.jar) với:
  WORKER_DB_URL=jdbc:postgresql://localhost:${DB_PORT}/tdworker
  WORKER_DB_USERNAME=tdworker
  WORKER_DB_PASSWORD=${SIMPKI_DB_PASSWORD}

Worker sẽ TỰ tạo bảng (Flyway) ngay lần khởi động đầu tiên — không cần chạy create-database.sql nữa
(Docker image Postgres đã tự tạo sẵn database/user 'tdworker' theo POSTGRES_DB/POSTGRES_USER ở trên).
EOF

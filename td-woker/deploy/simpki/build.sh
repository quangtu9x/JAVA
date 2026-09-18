#!/usr/bin/env bash
# =====================================================================
# Build td-worker.jar + đóng gói 1 thư mục "deploy bundle" sẵn sàng chép lên máy chủ test
# (docs/18-trien-khai-test-simpki.md). Dùng cho Linux (build TRÊN máy dev/CI, không cần build trên
# máy chủ test — chỉ cần JRE 17+ ở đó, xem docs/18 mục yêu cầu). Bản Windows: build.ps1.
#
# Mặc định KHÔNG copy secret thật (.env/*.p12) vào bundle — chỉ copy file .example + truststore CA
# công khai. Thêm cờ --with-secrets để copy CẢ .env thật + keystore .p12 thật (CHỈ dùng khi sắp
# chuyển ngay lên máy chủ test qua kênh an toàn — scp/rsync qua SSH; KHÔNG để bundle có secret nằm
# lâu trên máy build/upload lên nơi công khai).
#
# Kết quả: JAVA/td-woker/target/simpki-deploy/ (nằm trong target/ — đã .gitignore, không lọt git).
# =====================================================================
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/../.." && pwd)"
OUT_DIR="$PROJECT_DIR/target/simpki-deploy"

WITH_SECRETS=false
for arg in "$@"; do
    case "$arg" in
        --with-secrets) WITH_SECRETS=true ;;
        *) echo "Tham số không hợp lệ: $arg (chỉ hỗ trợ --with-secrets)" >&2; exit 1 ;;
    esac
done

echo "== Build td-worker.jar (mvn -DskipTests package) =="
(cd "$PROJECT_DIR" && mvn -q -DskipTests clean package)

echo "== Đóng gói deploy bundle: $OUT_DIR =="
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/certs/simpki" "$OUT_DIR/logs"

cp "$PROJECT_DIR/target/td-worker.jar" "$OUT_DIR/td-worker.jar"
cp "$DEPLOY_DIR/run-simpki.sh" "$OUT_DIR/run-simpki.sh"
cp "$DEPLOY_DIR/run-simpki.ps1" "$OUT_DIR/run-simpki.ps1"
cp "$DEPLOY_DIR/publish-test-signal.sh" "$OUT_DIR/publish-test-signal.sh"
cp "$DEPLOY_DIR/publish-test-signal.ps1" "$OUT_DIR/publish-test-signal.ps1"
cp "$DEPLOY_DIR/publish-test-http.sh" "$OUT_DIR/publish-test-http.sh"
cp "$DEPLOY_DIR/publish-test-http.ps1" "$OUT_DIR/publish-test-http.ps1"
cp "$DEPLOY_DIR/.env.simpki.example" "$OUT_DIR/.env.simpki.example"
chmod +x "$OUT_DIR/run-simpki.sh" "$OUT_DIR/publish-test-signal.sh" "$OUT_DIR/publish-test-http.sh"

if [[ -f "$PROJECT_DIR/certs/simpki/truststore-bcy.jks" ]]; then
    cp "$PROJECT_DIR/certs/simpki/truststore-bcy.jks" "$OUT_DIR/certs/simpki/truststore-bcy.jks"
    echo "Đã copy truststore-bcy.jks (CA công khai, không phải secret)."
fi

if [[ "$WITH_SECRETS" == "true" ]]; then
    echo "!! --with-secrets: lắp giá trị THẬT (AP_ID/AP_PASSWORD/mật khẩu keystore) vào .env dựa"
    echo "!! trên .env.simpki.example (KHÔNG copy nguyên .env dev — file đó cấu hình cho instance"
    echo "!! chạy NHIỀU module khác, không phải bản 'chỉ simpki' của bundle này)."
    cp "$OUT_DIR/.env.simpki.example" "$OUT_DIR/.env"

    SRC_ENV="$PROJECT_DIR/.env"
    if [[ -f "$SRC_ENV" ]]; then
        merge_env_value() {
            local key="$1" val
            val=$(grep -E "^${key}=" "$SRC_ENV" | tail -n1 | cut -d'=' -f2-)
            if [[ -n "$val" ]]; then
                if grep -qE "^${key}=" "$OUT_DIR/.env"; then
                    sed -i "s|^${key}=.*|${key}=${val}|" "$OUT_DIR/.env"
                else
                    echo "${key}=${val}" >> "$OUT_DIR/.env"
                fi
            fi
        }
        for key in WORKER_TASKS_SIMPKI_APID WORKER_TASKS_SIMPKI_APPASSWORD \
                   WORKER_TASKS_SIMPKI_KEYSTOREPASSWORD WORKER_TASKS_SIMPKI_TRUSTSTOREPASSWORD \
                   WORKER_TASKS_SIMPKI_SIGNATUREURL WORKER_TASKS_SIMPKI_MSSPID \
                   WORKER_TASKS_SIMPKI_SIGNATUREPROFILE WORKER_TASKS_SIMPKI_MSSFORMAT; do
            merge_env_value "$key"
        done
        echo "Đã lắp AP_ID/AP_PASSWORD/mật khẩu keystore thật từ $SRC_ENV vào $OUT_DIR/.env"
    else
        echo "Cảnh báo: không thấy $SRC_ENV — .env trong bundle vẫn chỉ là placeholder, phải tự điền tay." >&2
    fi

    shopt -s nullglob
    KEYSTORE_COPIED=""
    for p12 in "$PROJECT_DIR"/certs/simpki/*.p12; do
        cp "$p12" "$OUT_DIR/certs/simpki/"
        KEYSTORE_COPIED="$(basename "$p12")"
        echo "Đã copy $KEYSTORE_COPIED"
    done
    shopt -u nullglob
    if [[ -n "$KEYSTORE_COPIED" ]]; then
        sed -i "s|^WORKER_TASKS_SIMPKI_KEYSTOREPATH=.*|WORKER_TASKS_SIMPKI_KEYSTOREPATH=certs/simpki/${KEYSTORE_COPIED}|" "$OUT_DIR/.env"
    else
        echo "Cảnh báo: không tìm thấy file .p12 nào trong $PROJECT_DIR/certs/simpki/ để copy." >&2
    fi

    echo "!! CẢNH BÁO: $OUT_DIR giờ CHỨA SECRET THẬT (private key/mật khẩu)."
    echo "!! Chỉ chuyển qua kênh an toàn (scp/rsync qua SSH). KHÔNG upload lên nơi công khai."
    echo "!! Xoá khỏi máy build sau khi chuyển xong nếu không còn cần giữ lại."
else
    echo "Bỏ qua secret thật (.env/*.p12) — dùng --with-secrets khi sẵn sàng chuyển lên máy chủ test."
fi

echo "== Xong: $OUT_DIR =="
echo "Chuyển thư mục này lên máy chủ test (vd: scp -r \"$OUT_DIR\" user@host:/opt/td-worker-simpki),"
echo "sau đó trên máy chủ: cd vào đó, đảm bảo có .env + certs/simpki/*.p12 thật rồi chạy ./run-simpki.sh"

-- =====================================================================
-- Chạy 1 LẦN bằng superuser Postgres (vd `psql -U postgres -h <host> -f create-database.sql`) trên
-- máy chủ test CHƯA có database Worker. Chỉ tạo DATABASE + ROLE rỗng — Worker TỰ tạo bảng bên trong
-- qua Flyway lúc khởi động đầu tiên (WorkerSchemaMigrator, xem docs/10-deployment.md mục 3 +
-- docs/18-trien-khai-test-simpki.md). KHÔNG cần chạy tay bất kỳ file migration nào trong
-- src/main/resources/db/migration-worker/ — Worker tự áp dụng V1/V2/V3 theo thứ tự.
--
-- Đổi mật khẩu 'CHANGE_ME_STRONG_PASSWORD' trước khi chạy thật - KHÔNG dùng nguyên giá trị mẫu này.
-- =====================================================================

-- Role riêng cho Worker (đổi tên/mật khẩu theo quy ước máy chủ thật nếu cần).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tdworker') THEN
        CREATE ROLE tdworker LOGIN PASSWORD 'CHANGE_ME_STRONG_PASSWORD';
    END IF;
END
$$;

-- Database RIÊNG của Worker (checkpoint store + bảng dedup/audit simpki.sign) - KHÔNG phải database
-- tenant/control-plane của td-web (Worker không migrate tenant, xem docs/06-multi-tenant.md).
CREATE DATABASE tdworker OWNER tdworker ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C' TEMPLATE template0;

\c tdworker

GRANT ALL PRIVILEGES ON DATABASE tdworker TO tdworker;
GRANT ALL ON SCHEMA public TO tdworker;

-- Kiểm tra nhanh sau khi chạy:
--   psql -U tdworker -h <host> -d tdworker -c "SELECT current_database(), current_user;"
-- Sau đó khởi động Worker (profile simpki) - log sẽ báo:
--   "Flyway migrate database Worker (migration-worker): 3 migration được áp dụng"

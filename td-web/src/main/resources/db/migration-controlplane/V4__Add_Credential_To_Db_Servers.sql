-- BL-117 (bổ sung 19/08/2026): thêm credential ĐĂNG NHẬP TUỲ CHỌN (username/mật khẩu mã hoá) vào
-- registry "Máy chủ CSDL" — tiện autofill host+port+username+password lúc khởi tạo đơn vị (xem
-- docs/architecture/06-database-per-tenant-multi-tenant.md mục 7). NULLABLE — không bắt buộc mọi
-- máy chủ đều phải lưu credential.

ALTER TABLE db_servers ADD COLUMN username          VARCHAR(100);
ALTER TABLE db_servers ADD COLUMN encrypted_password TEXT;

COMMENT ON COLUMN db_servers.username           IS 'Tai khoan dang nhap mac dinh cua may chu nay (thuong la superuser dung de CREATE DATABASE) - tuy chon';
COMMENT ON COLUMN db_servers.encrypted_password IS 'Mat khau ma hoa 2 chieu (CredentialCipher, dung chung key/salt voi bang tenants) - tuy chon, NULL neu chua luu';

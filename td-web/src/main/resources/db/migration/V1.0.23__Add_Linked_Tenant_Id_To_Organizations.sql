-- Bổ sung 19/08/2026: liên kết MỀM (không FK thật) từ 1 "Cơ quan" (organizations, bảng NẰM TRONG
-- database riêng của TỪNG tenant) tới 1 "đơn vị" (tenants.tenant_id, bảng nằm ở database
-- "control-plane" KHÁC HẲN) — không thể có FK quan hệ thật giữa 2 database vật lý riêng biệt, chỉ
-- lưu chuỗi tenant_id để FE tự tra cứu qua API /api/v1/platform/tenants khi cần hiển thị.
-- Migration này chạy LẶP LẠI cho MỌI tenant hiện có (đúng cơ chế db/migration hiện tại).

ALTER TABLE organizations ADD COLUMN linked_tenant_id VARCHAR(100);

COMMENT ON COLUMN organizations.linked_tenant_id IS 'Tham chieu MEM (khong FK that) toi tenants.tenant_id o database control-plane khac - chi phuc vu hien thi/lien ket UI, khong rang buoc toan ven du lieu';

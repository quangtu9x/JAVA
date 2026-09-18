-- BL-116: "Danh mục riêng" (dungrieng/*) — ký hiệu văn bản, dữ liệu quá khứ... Khác bảng
-- `categories` (schema `shared`, dùng chung MỌI tenant — BL-115): bảng này sống trong schema RIÊNG
-- của từng tenant (bộ migration db/migration bình thường, không phải db/migration-shared) — mỗi đơn
-- vị được khởi tạo là 1 tenant riêng (app.multitenancy.tenants) sẽ tự động có dữ liệu HOÀN TOÀN
-- TÁCH BIỆT khỏi đơn vị khác, không cần thêm cột/logic tenant nào ở tầng Entity/Repository (đúng cơ
-- chế schema-per-tenant gốc của BL-303 — xem docs/architecture/05-shared-schema-multi-tenant.md để
-- hiểu vì sao categories cần schema riêng còn bảng này thì không).
--
-- Cấu trúc giống hệt categories (danh mục phẳng, hỗ trợ cây phân cấp qua parent_id — dùng cho các
-- danh mục đơn giản: ký hiệu văn bản, dữ liệu quá khứ; các danh mục phức tạp hơn của "Danh mục
-- riêng" như sổ văn bản, nhóm đơn vị/người nhận có entity/bảng riêng, không dùng bảng này).
CREATE TABLE private_categories (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    parent_id        UUID         REFERENCES private_categories(id),
    level            INT          NOT NULL DEFAULT 0,
    full_path        TEXT         NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    form             VARCHAR(100),
    -- BL-116 (bổ sung): "identifier" — mã định danh đơn vị/phòng ban (chuỗi, lấy nguyên từ
    -- currentUser.identifier bên FE, xem getIdentifier_by_level trong ThanhHoaDEV/src/utils/global.jsx).
    -- KHÔNG được xoá/bỏ qua như dự tính ban đầu: xác nhận lại rằng schema-per-tenant (BL-303) hiện
    -- chỉ có ĐÚNG 1 tenant thật (cả tỉnh Thanh Hoá, gồm mọi phòng/ban/đơn vị con dùng chung 1 schema)
    -- — "identifier" là cơ chế DUY NHẤT hiện có để tách dữ liệu GIỮA CÁC ĐƠN VỊ CON trong cùng 1
    -- tenant (vd: mỗi phòng/ban có "ký hiệu văn bản" riêng), khác hẳn việc tách theo tenant. Đăng
    -- nhập (currentUser.identifier/level/parentids) vẫn do Domino cấp (`qlvb/authen`/`qlvb/profile`,
    -- chưa migrate) nên field này giữ nguyên kiểu chuỗi tự do, không làm FK sang organizations.
    identifier       VARCHAR(200),
    dynamic_fields   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE UNIQUE INDEX UX_private_categories_code     ON private_categories(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_private_categories_parent_id       ON private_categories(parent_id);
CREATE INDEX IX_private_categories_level           ON private_categories(level);
CREATE INDEX IX_private_categories_is_active       ON private_categories(is_active);
CREATE INDEX IX_private_categories_sort_order      ON private_categories(sort_order);
CREATE INDEX IX_private_categories_deleted_on      ON private_categories(deleted_on);
CREATE INDEX IX_private_categories_created_on      ON private_categories(created_on);
CREATE INDEX IX_private_categories_form            ON private_categories(form);
CREATE INDEX IX_private_categories_identifier      ON private_categories(identifier);
CREATE INDEX idx_private_categories_dynamic_fields ON private_categories USING GIN (dynamic_fields);

COMMENT ON TABLE private_categories IS 'Danh muc RIENG theo tung don vi/tenant (BL-116) - khac categories (global, schema shared)';

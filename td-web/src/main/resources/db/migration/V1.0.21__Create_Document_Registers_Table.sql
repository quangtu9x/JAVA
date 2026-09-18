-- BL-116: "Sổ văn bản" (dungrieng/sovanban) — Danh mục riêng, NHƯNG dùng entity/bảng RIÊNG (không
-- tái dùng NumberingConfig — BL-104, đã đối chiếu: NumberingConfig là 1 định dạng số duy nhất/loại
-- văn bản, update-only, không có khái niệm "nhiều sổ/năm/đơn vị" hay kiểm tra trùng — không hợp với
-- nghiệp vụ Sổ văn bản; quyết định của người dùng khi thiết kế BL-116). Cũng KHÔNG dùng
-- private_categories (không phải danh mục cây phẳng — cấu trúc field khác hẳn).
--
-- `identifier`: xem comment trong V1.0.20 (private_categories) — hiện chỉ có ĐÚNG 1 tenant thật
-- (cả tỉnh), "identifier" là cơ chế tách Sổ văn bản GIỮA CÁC PHÒNG/BAN trong cùng tỉnh (mỗi phòng ban
-- có sổ riêng), y hệt cơ chế Domino cũ (view "sovanban_by_identifier").
--
-- Đơn giản hoá có chủ đích so với Domino gốc (ghi rõ để không hiểu nhầm là bỏ sót):
--   - "Cấp ban hành" (unit_publish) gốc lấy qua endpoint Domino `qlvb/danhmuc/publish?databasetype=
--     duthao` (gắn với module "dự thảo" chưa migrate) — giữ lại 3 cột nhưng để FE nhập tay (Input)
--     thay vì gọi lại Domino.
--   - "Lãnh đạo phân công" (assigner) gốc lấy qua view Domino `vwunitleaders` — chỉ giữ 1 cột text
--     tự do (assigner), bỏ hẳn user_assigner/assigner_position (chỉ phục vụ hiển thị nhãn ghép từ
--     view đó, không còn nguồn dữ liệu).
CREATE TABLE document_registers (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                    VARCHAR(300) NOT NULL,
    document_register_type  VARCHAR(50)  NOT NULL, -- "Văn bản đi" | "Văn bản đến"
    year                    INT          NOT NULL,
    identifier              VARCHAR(200),
    agency                  VARCHAR(300),
    agency_id               VARCHAR(100),
    document_type           VARCHAR(300), -- chỉ dùng khi document_register_type = "Văn bản đi"
    code_notation           VARCHAR(100),
    unit_publish            VARCHAR(300),
    unit_publish_identifier VARCHAR(200),
    unit_publish_id         VARCHAR(100),
    assigner                VARCHAR(300), -- chỉ dùng khi document_register_type = "Văn bản đến"
    code_start_number       INT          NOT NULL DEFAULT 1,
    auto_increment          BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default              BOOLEAN      NOT NULL DEFAULT FALSE,
    code_start_numbers      TEXT,
    is_locked               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by              UUID,
    created_on              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by        UUID,
    last_modified_on        TIMESTAMP WITH TIME ZONE,
    deleted_on              TIMESTAMP WITH TIME ZONE,
    deleted_by              UUID
);

CREATE INDEX IX_document_registers_identifier              ON document_registers(identifier);
CREATE INDEX IX_document_registers_type                    ON document_registers(document_register_type);
CREATE INDEX IX_document_registers_year                    ON document_registers(year);
CREATE INDEX IX_document_registers_is_locked                ON document_registers(is_locked);
CREATE INDEX IX_document_registers_deleted_on              ON document_registers(deleted_on);
-- Cùng 1 phòng/ban không được có 2 sổ trùng tên trong cùng loại sổ (kiemtrungdocument gốc) —
-- partial unique index, chỉ áp dụng cho bản ghi chưa xoá mềm.
CREATE UNIQUE INDEX UX_document_registers_identifier_type_name
    ON document_registers(identifier, document_register_type, name)
    WHERE deleted_on IS NULL;
-- Mỗi phòng/ban chỉ được có 1 sổ mặc định cho mỗi loại sổ (is_default = true) — ràng buộc DB thật,
-- chặt hơn bản Domino gốc (chỉ kiểm tra ở tầng app, có thể lọt race condition).
CREATE UNIQUE INDEX UX_document_registers_identifier_type_default
    ON document_registers(identifier, document_register_type)
    WHERE is_default = TRUE AND deleted_on IS NULL;

COMMENT ON TABLE document_registers IS 'So van ban (BL-116) - Danh muc rieng, entity rieng (khong dung chung NumberingConfig/BL-104)';

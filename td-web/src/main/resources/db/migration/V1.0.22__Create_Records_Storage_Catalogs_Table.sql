-- BL-116: "Danh mục hồ sơ lưu trữ" (dungrieng/phongtailieu, demuchoso, danhmuchosocoquan — 3 thư
-- mục FE cùng thao tác trên 1 loại bản ghi lá "hồ sơ lưu trữ", form Domino cũ = "records_storage_catalog").
-- Entity RIÊNG (không tái dùng PrivateCategory — cấu trúc field khác hẳn danh mục cây phẳng: không
-- có code/parent/level/fullPath, mà có category/storage_period/year_inception/kyhieu/
-- internal_unit_obj/individual_obj...).
--
-- Cây phân loại (Phông tài liệu/Đề mục lớn/Đề mục nhỏ) mà mỗi bản ghi ở đây thuộc về KHÔNG có bảng
-- riêng — TÁI SỬ DỤNG `private_categories` (BL-116, V1.0.20) với `form` = "phongtailieu"/"demuclon"/
-- "demucnho" (giống cách PrivateCategory đã tái dùng cho "nhóm đơn vị/người nhận"). Cây này KHÔNG có
-- `identifier` (dùng chung toàn tỉnh — xác nhận qua code gốc: API lấy cây
-- `qlvb/danhmuc/DanhSachPhongTaiLieu`/`DanhSachDeMucHoSo` không truyền identifier/keys nào, khác hẳn
-- API lấy danh sách "hồ sơ lưu trữ" `danhmuchoso_by_identifier` CÓ lọc identifier) — bảng NÀY (leaf)
-- thì CÓ identifier (tách theo phòng/ban, xem cột bên dưới).
--
-- Đơn giản hoá có chủ đích so với Domino gốc (không phải bỏ sót):
--   - Bỏ liên kết tăng số tự động với 1 catalog "ký hiệu hồ sơ" hoàn toàn tách biệt
--     (HOSOLUUTRU_Database, qua fetchDanhMucKyHieuHoSoOptions) — cột `kyhieu` giữ lại nhưng chỉ là
--     text tự do, không còn side-effect cập nhật số ở nơi khác (catalog đó không thuộc phạm vi
--     "Danh mục riêng"/BL-116, 8 mục đã thống nhất).
--   - `internal_unit_obj`/`individual_obj` ("Đơn vị lập hồ sơ"/"Người lập hồ sơ", chọn qua
--     ModalPhatHanhVanBan/ModalNguoiNhan — 2 picker giữ nguyên gọi Domino, ngoài phạm vi) lưu nguyên
--     dạng chuỗi JSON như bản gốc.
--   - "Chia sẻ thư mục" (qlvb/chiasetep/bochiasethumuc, gắn vào cây qua checkbox jstree) đã xác nhận
--     là code chết trong FE hiện tại (plugin "checkbox" không được bật, không có nút nào gọi tới) —
--     không ảnh hưởng gì khi cây chuyển sang PrivateCategory (UUID mới).
CREATE TABLE records_storage_catalogs (
    id                        UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    category                  VARCHAR(500) NOT NULL,
    code                      VARCHAR(100) NOT NULL,
    phong                     VARCHAR(300),
    phong_id                  UUID,
    first_topic               VARCHAR(300),
    first_topic_id            UUID,
    second_topic              VARCHAR(300),
    second_topic_id           UUID,
    storage_period            VARCHAR(100) NOT NULL,
    year_inception            INT          NOT NULL,
    sort_order                INT          NOT NULL DEFAULT 0,
    note                      TEXT,
    number_of_document_number INT,
    kyhieu                    VARCHAR(300),
    internal_unit_obj         TEXT,
    individual_obj            TEXT,
    identifier                VARCHAR(200),
    created_by                UUID,
    created_on                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by          UUID,
    last_modified_on          TIMESTAMP WITH TIME ZONE,
    deleted_on                TIMESTAMP WITH TIME ZONE,
    deleted_by                UUID
);

CREATE INDEX IX_records_storage_catalogs_identifier   ON records_storage_catalogs(identifier);
CREATE INDEX IX_records_storage_catalogs_phong_id     ON records_storage_catalogs(phong_id);
CREATE INDEX IX_records_storage_catalogs_first_topic  ON records_storage_catalogs(first_topic_id);
CREATE INDEX IX_records_storage_catalogs_second_topic ON records_storage_catalogs(second_topic_id);
CREATE INDEX IX_records_storage_catalogs_year         ON records_storage_catalogs(year_inception);
CREATE INDEX IX_records_storage_catalogs_deleted_on   ON records_storage_catalogs(deleted_on);

COMMENT ON TABLE records_storage_catalogs IS 'Danh muc ho so luu tru (BL-116) - entity rieng, cay phan loai tai su dung private_categories';

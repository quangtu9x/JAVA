-- Yêu cầu người dùng 22/10/2026: tệp đính kèm văn bản đến (nội tỉnh, trục quốc gia) và bản sao
-- "Chuyển xử lý" DÙNG CHUNG 1 object MinIO cho mọi đơn vị nhận (không nhân bản) - đơn vị xóa văn bản
-- KHÔNG được làm mất tệp của đơn vị khác, object chỉ bị xóa khi KHÔNG còn đơn vị nào tham chiếu.
-- Mỗi tenant có database riêng nên không đếm được bằng cách quét file_metadata -> sổ đăng ký dùng chung
-- đặt ở DATABASE RIÊNG (không phải control-plane, theo yêu cầu), td-web sở hữu migration (giống
-- migration-errortracking), td-woker ghi tham chiếu + chạy job dọn dẹp (SharedFileGcJob).

-- 1 dòng / 1 object MinIO vật lý đang được dùng chung.
CREATE TABLE shared_file_object (
    bucket_name     VARCHAR(255)  NOT NULL,
    object_key      VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Thời điểm tham chiếu cuối cùng được nhả (gợi ý cho job dọn dẹp, KHÔNG phải nguồn sự thật -
    -- job luôn kiểm tra lại shared_file_ref + file_metadata mọi tenant trước khi xóa).
    zero_ref_since  TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    PRIMARY KEY (bucket_name, object_key)
);

CREATE INDEX idx_shared_file_object_gc ON shared_file_object (zero_ref_since)
    WHERE zero_ref_since IS NOT NULL AND deleted_at IS NULL;

-- 1 dòng / 1 dòng file_metadata (ở 1 tenant) trỏ tới object dùng chung. released_at != NULL = văn bản
-- chứa tệp đã bị xóa vĩnh viễn (giữ lại để truy vết, không xóa dòng).
CREATE TABLE shared_file_ref (
    file_metadata_id UUID          PRIMARY KEY,
    tenant_id        VARCHAR(100)  NOT NULL,
    document_id      UUID,
    bucket_name      VARCHAR(255)  NOT NULL,
    object_key       VARCHAR(1024) NOT NULL,
    source           VARCHAR(30)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    released_at      TIMESTAMPTZ,
    FOREIGN KEY (bucket_name, object_key) REFERENCES shared_file_object (bucket_name, object_key)
);

CREATE INDEX idx_shared_file_ref_object ON shared_file_ref (bucket_name, object_key);
CREATE INDEX idx_shared_file_ref_tenant_document ON shared_file_ref (tenant_id, document_id);

COMMENT ON COLUMN shared_file_ref.source IS
    'vbdi_delivery (đến nội tỉnh) | national_incoming (đến trục quốc gia) | forward_unit (chuyển xử lý) | backfill | gc_repair';

-- Đánh dấu tenant đã quét dữ liệu cũ (trước khi có sổ đăng ký) - chạy 1 lần / tenant.
CREATE TABLE shared_file_backfill (
    tenant_id        VARCHAR(100) PRIMARY KEY,
    completed_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    marked_rows      INTEGER      NOT NULL DEFAULT 0,
    registered_rows  INTEGER      NOT NULL DEFAULT 0
);

-- Khóa lease để chỉ 1 instance td-woker chạy job dọn dẹp tại 1 thời điểm.
CREATE TABLE shared_file_gc_lease (
    id          INTEGER      PRIMARY KEY,
    owner       VARCHAR(200),
    lease_until TIMESTAMPTZ
);

INSERT INTO shared_file_gc_lease (id) VALUES (1);

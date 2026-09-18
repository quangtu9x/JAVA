-- BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026 (làm rõ lại sau V1.0.26): "ý tôi không phải
-- tạo bảng con, mà tạo bảng riêng, để độc lập dữ liệu" — partition (V1.0.26) vẫn là 1 phần LOGIC
-- của bảng "documents" (cùng $Formula/lifecycle/schema history), KHÔNG đủ "độc lập" theo đúng ý
-- người dùng. Dự thảo/draft_process chuyển hẳn sang bảng vật lý MỚI, TÁCH BIỆT HOÀN TOÀN
-- ({@code duthao_documents}, xem migration V1.0.28 + entity {@code DuThaoDocument}) — không còn lý
-- do gì để "documents" tiếp tục partition theo document_type nữa. Migration này ĐẢO NGƯỢC đúng
-- những gì V1.0.26 đã đổi, đưa "documents" về lại 1 bảng thường (KHÔNG partition) như trước đó.
--
-- Không cần giữ lại dữ liệu duthao/draft_process đang nằm trong 2 partition đó (đã hỏi người dùng ở
-- V1.0.26 - đang giai đoạn test, đồng ý bỏ qua; module đó chuyển hẳn sang bảng mới nên dữ liệu cũ
-- không còn "sống" ở đây được nữa dù có giữ). Dữ liệu của MỌI module KHÁC (vbden/vbdi/hosocv/... -
-- đang nằm ở partition "documents_default") được BẢO TOÀN đầy đủ.

-- Bước 1: đổi tên bảng cha đang partition (giữ tạm để copy dữ liệu ở bước 3).
ALTER TABLE documents RENAME TO documents_partitioned_before_revert;

-- Bước 2: tạo lại "documents" ĐÚNG NGUYÊN cấu trúc trước khi partition (V1.0.3 + V1.0.12) - PRIMARY
-- KEY 1 cột như cũ, document_type cho phép NULL như cũ (không còn là partition key nên không cần
-- NOT NULL nữa).
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    document_type VARCHAR(100),
    status VARCHAR(50),
    content TEXT,
    tags_json TEXT NOT NULL DEFAULT '[]',
    attributes_json TEXT NOT NULL DEFAULT '{}',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

-- Bước 3: copy dữ liệu của MỌI module khác (vbden/vbdi/hosocv/...) từ partition "documents_default"
-- (KHÔNG copy từ documents_partitioned_before_revert - bảng đó vẫn còn partition, SELECT * sẽ kéo
-- theo cả duthao/draft_process; documents_default giữ nguyên tên riêng, không bị rename ở bước 1,
-- nên vẫn truy vấn trực tiếp được dù bảng cha đã đổi tên).
INSERT INTO documents (
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
)
SELECT
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
FROM documents_default;

-- Bước 4: xoá bảng cha partition cũ - tự động xoá kèm TOÀN BỘ partition con của nó
-- (documents_duthao/documents_draft_process/documents_default), giải phóng tên index cũ.
DROP TABLE documents_partitioned_before_revert CASCADE;

-- Bước 5: index - đúng nguyên tập đã có ở V1.0.3/V1.0.12.
CREATE INDEX IX_documents_title ON documents(title);
CREATE INDEX IX_documents_document_type ON documents(document_type);
CREATE INDEX IX_documents_status ON documents(status);
CREATE INDEX IX_documents_created_on ON documents(created_on);
CREATE INDEX IX_documents_last_modified_on ON documents(last_modified_on);
CREATE INDEX IX_documents_deleted_on ON documents(deleted_on);
CREATE INDEX idx_documents_dynamic_fields ON documents USING GIN (dynamic_fields);

COMMENT ON TABLE documents IS 'Business documents with flexible unstructured fields';
COMMENT ON COLUMN documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';

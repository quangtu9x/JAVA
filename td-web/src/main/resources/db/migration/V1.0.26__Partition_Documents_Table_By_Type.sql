-- BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026: "chuyển dự thảo sang bảng mới, không lưu
-- chung documents" + làm rõ lại "chỉ dùng core chung, nhưng tạo bảng riêng" — nghĩa là KHÔNG viết
-- lại entity/repository/use case (BusinessDocument/DocumentRepository/PostgresDocumentRepository -
-- toàn bộ SQL trong đó đều hardcode "FROM documents d"/"documents" nên không thể trỏ 2 bảng khác
-- nhau bằng code mà không viết lại toàn bộ tầng đó), mà cần "documents" (tên bảng, mọi câu SQL hiện
-- có) tiếp tục đúng NGUYÊN VĂN, trong khi dữ liệu duthao/draft_process VẪN nằm ở 1 bảng vật lý TÁCH
-- BIỆT trên đĩa.
--
-- Giải pháp: PostgreSQL DECLARATIVE PARTITIONING (LIST) theo document_type. "documents" trở thành
-- 1 bảng cha KHÔNG chứa dữ liệu (chỉ định nghĩa cấu trúc) - Postgres tự route từng dòng INSERT tới
-- đúng bảng con (partition) vật lý theo document_type, HOÀN TOÀN TRONG SUỐT với tầng ứng dụng: mọi
-- "SELECT/INSERT/UPDATE/DELETE ... FROM documents ..." hiện có (PostgresDocumentRepository, JPA
-- entity BusinessDocument, mọi use case gọi qua DocumentRepository) tiếp tục chạy KHÔNG SỬA 1 DÒNG
-- CODE nào, trong khi dữ liệu duthao/draft_process vật lý nằm ở 2 bảng con riêng
-- (documents_duthao/documents_draft_process) - đúng yêu cầu "chỉ dùng core chung, nhưng tạo bảng
-- riêng".
--
-- Không cần script migrate riêng cho duthao (đã hỏi người dùng - đang giai đoạn test, đồng ý bỏ
-- qua) NHƯNG vẫn phải bảo toàn dữ liệu của MỌI module KHÁC (vbden/vbdi/hosocv/...) đang có sẵn
-- trong "documents" - dùng chung 1 bước copy dữ liệu tổng quát (INSERT ... SELECT), không phân biệt
-- document_type, nên duthao được copy theo "tiện thể" chứ không cần code riêng, và không module nào
-- khác bị mất dữ liệu.
--
-- Ràng buộc bắt buộc của Postgres partitioning: partition key (document_type) PHẢI nằm trong mọi
-- unique/primary key của bảng partition - đổi PRIMARY KEY từ (id) sang (id, document_type). Hệ quả
-- CHẤP NHẬN ĐƯỢC: mất ràng buộc DB-level "id là duy nhất tuyệt đối trên toàn bảng" (Postgres KHÔNG
-- cho phép UNIQUE(id) đứng riêng trên bảng đã partition) - dựa vào UUID.randomUUID() (đã dùng khắp
-- code) để đảm bảo duy nhất trong thực tế, đánh đổi tiêu chuẩn cho nhiều bảng partition khác cũng
-- chấp nhận. document_type đổi thành NOT NULL DEFAULT '' (bắt buộc với cột nằm trong PRIMARY KEY) -
-- đã rà code (BusinessDocument, mọi use case tạo document) xác nhận KHÔNG có nơi nào cố ý tạo
-- document với document_type=NULL ngoài vài unit test dùng mock repository (không chạm DB thật, nên
-- không bị ảnh hưởng).

-- Bước 1: đổi tên bảng cũ để giữ lại dữ liệu tạm thời trong lúc chuyển đổi cấu trúc.
ALTER TABLE documents RENAME TO documents_before_partition;

-- Bước 2: tạo bảng cha partition - CÙNG cấu trúc cột với bảng cũ (V1.0.3 + V1.0.12), chỉ khác
-- PRIMARY KEY (bắt buộc bao gồm document_type) và document_type NOT NULL DEFAULT ''.
CREATE TABLE documents (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    document_type VARCHAR(100) NOT NULL DEFAULT '',
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
    deleted_by UUID,
    PRIMARY KEY (id, document_type)
) PARTITION BY LIST (document_type);

-- Bước 3: bảng con RIÊNG cho duthao + draft_process (yêu cầu chính của lần đổi này) - vật lý tách
-- biệt khỏi mọi module khác, nhưng vẫn truy vấn qua tên "documents" như cũ.
CREATE TABLE documents_duthao PARTITION OF documents FOR VALUES IN ('duthao');
CREATE TABLE documents_draft_process PARTITION OF documents FOR VALUES IN ('draft_process');

-- Bước 4: bảng con DEFAULT - hứng TẤT CẢ document_type khác (vbden/vbdi/hosocv/categories nội bộ/...
-- và cả '' nếu có dòng cũ document_type NULL/rỗng) - giữ nguyên hành vi "1 bảng dùng chung" cho mọi
-- module CHƯA yêu cầu tách bảng riêng, không phá vỡ gì ở các module đó.
CREATE TABLE documents_default PARTITION OF documents DEFAULT;

-- Bước 5: copy toàn bộ dữ liệu cũ (mọi document_type, kể cả duthao - "tiện thể", không cần script
-- riêng) sang cấu trúc partition mới. COALESCE document_type NULL -> '' để không vi phạm NOT NULL
-- (rơi vào documents_default, đúng ngữ nghĩa "loại không phân biệt" như hành vi cũ NULL).
INSERT INTO documents (
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
)
SELECT
    id, title, COALESCE(document_type, ''), status, content, tags_json, attributes_json,
    metadata_json, dynamic_fields, version_no, created_by, created_on, last_modified_by,
    last_modified_on, deleted_on, deleted_by
FROM documents_before_partition;

-- Bước 6: dọn bảng tạm TRƯỚC khi tạo index mới - bảng cũ vẫn còn giữ NGUYÊN TÊN index cũ
-- (RENAME TABLE không rename kèm index), nếu tạo index mới cùng tên (bước 7) trước khi DROP bảng
-- này sẽ lỗi "relation already exists" (tên index là duy nhất theo SCHEMA, không phải theo bảng).
DROP TABLE documents_before_partition;

-- Bước 7: index - tạo trên bảng CHA, Postgres tự nhân bản xuống mọi bảng con hiện có VÀ bảng con
-- tạo sau này (từ PG11) - giữ nguyên đúng tập index đã có ở V1.0.3/V1.0.12.
CREATE INDEX IX_documents_title ON documents(title);
CREATE INDEX IX_documents_document_type ON documents(document_type);
CREATE INDEX IX_documents_status ON documents(status);
CREATE INDEX IX_documents_created_on ON documents(created_on);
CREATE INDEX IX_documents_last_modified_on ON documents(last_modified_on);
CREATE INDEX IX_documents_deleted_on ON documents(deleted_on);
CREATE INDEX idx_documents_dynamic_fields ON documents USING GIN (dynamic_fields);

COMMENT ON TABLE documents IS 'Business documents với field linh hoạt - bảng cha PARTITION BY LIST (document_type), KHÔNG tự chứa dữ liệu; xem documents_duthao/documents_draft_process/documents_default (bảng con vật lý thật)';
COMMENT ON TABLE documents_duthao IS 'Partition vật lý riêng cho document_type=duthao (BL-230, yêu cầu 24/08/2026) - cùng cấu trúc/entity/repository dùng chung (BusinessDocument/DocumentRepository), chỉ tách lưu trữ vật lý';
COMMENT ON TABLE documents_draft_process IS 'Partition vật lý riêng cho document_type=draft_process (DT-007, lịch sử xử lý dự thảo) - cùng lý do tách như documents_duthao';
COMMENT ON TABLE documents_default IS 'Partition mặc định - mọi document_type khác duthao/draft_process (vbden/vbdi/hosocv/...) - hành vi y hệt bảng "documents" dùng chung trước khi partition';

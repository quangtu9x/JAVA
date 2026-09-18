-- Yêu cầu người dùng 05/09/2026 ("trong văn bản đến các chức năng liên quan tới vbden_res (quá
-- trình xử lý...) tách thành bảng riêng... không lưu cùng bảng chính như hiện tại"): tách document
-- lịch sử xử lý (document_type = 'vbden_res', BL-210 DoD 4 — "quá trình xử lý" của vbden GỐC, KHÁC
-- vbden_xinykien_res của cụm "xin ý kiến" đã tách riêng ở V1.0.37) khỏi bảng {@code documents} dùng
-- CHUNG với văn bản đến chính ({@code document_type='vbden'}, VẪN Ở LẠI documents - use case này
-- KHÔNG đụng tới) sang bảng vật lý HOÀN TOÀN RIÊNG — đúng khuôn V1.0.30 (draft_process_documents,
-- tách khỏi duthao_documents) đã làm cho cụm dự thảo.
--
-- Cấu trúc CỐ Ý giống hệt "documents"/"draft_process_documents" (cùng kiến trúc BL-101 Dynamic Data
-- Engine) - xem entity VbdenResDocument (com.td.domain.vbden) + repository VbdenResDocumentRepository/
-- PostgresVbdenResDocumentRepository (com.td.application.vbden / com.td.infrastructure.persistence
-- .repository) + writer DUY NHẤT AddVbdenResDocumentUseCase.
--
-- DỮ LIỆU CŨ: KHÁC tiền lệ V1.0.30 (môi trường test, không cần tương thích ngược) - module vbden đã
-- chạy thật nhiều tuần (N06-N19) với dữ liệu quá trình xử lý thật + tệp đính kèm gắn theo ID bản ghi
-- vbden_res (document_files.document_id) + tham chiếu ID trong process_diagrams (DiagramNodeRequest
-- .resProcessId) - PHẢI di chuyển nguyên trạng, GIỮ NGUYÊN id (không dùng uuid_generate_v4() mới) để
-- không làm mồ côi 2 loại tham chiếu chéo trên. Sau migration này: "documents" hết còn dòng nào
-- document_type='vbden_res'; toàn bộ đã chuyển sang vbden_res_documents.
CREATE TABLE vbden_res_documents (
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

CREATE INDEX IX_vbden_res_documents_title ON vbden_res_documents(title);
CREATE INDEX IX_vbden_res_documents_document_type ON vbden_res_documents(document_type);
CREATE INDEX IX_vbden_res_documents_status ON vbden_res_documents(status);
CREATE INDEX IX_vbden_res_documents_created_on ON vbden_res_documents(created_on);
CREATE INDEX IX_vbden_res_documents_last_modified_on ON vbden_res_documents(last_modified_on);
CREATE INDEX IX_vbden_res_documents_deleted_on ON vbden_res_documents(deleted_on);
CREATE INDEX idx_vbden_res_documents_dynamic_fields ON vbden_res_documents USING GIN (dynamic_fields);

-- Di chuyen nguyen trang (giu nguyen id) toan bo ban ghi vbden_res dang nam trong "documents" -
-- xem ghi chu DU LIEU CU o dau file.
INSERT INTO vbden_res_documents (
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by)
SELECT
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
FROM documents
WHERE document_type = 'vbden_res';

DELETE FROM documents WHERE document_type = 'vbden_res';

COMMENT ON TABLE vbden_res_documents IS 'Bảng vật lý ĐỘC LẬP cho lịch sử "quá trình xử lý" của vbden GỐC (document_type=vbden_res, BL-210 DoD 4) - tách khỏi documents theo yêu cầu người dùng 05/09/2026 - mỗi lần chuyển xử lý/kết thúc xử lý/xử lý lại/thu hồi/lấy lại VB/tạo dự thảo/vanthu kết thúc xử lý ghi 1 dòng vết xử lý trỏ parentid về văn bản đến cha (vẫn ở bảng documents)';
COMMENT ON COLUMN vbden_res_documents.dynamic_fields IS 'Field động (key-value): parentid/action/opinion/creator/position/identifier/nguoi_thuc_hien/user_process_obj/user_coordinate_obj/user_to_read_obj/user_assigner_obj/res_obj... - đúng tên field Java thực dùng (xem ChuyenXuLyVbdenUseCase/LichSuXuLyUseCase)';

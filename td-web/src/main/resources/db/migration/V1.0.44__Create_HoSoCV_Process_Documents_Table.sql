-- Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới ... như các module đã thực
-- hiện"): bảng vật lý ĐỘC LẬP cho vết xử lý/lịch sử của hồ sơ công việc (trình duyệt/duyệt/chuyển
-- lưu trữ/phối hợp/góp ý — gộp 3 nhu cầu audit-trail thật của Domino "hosocv_res.nsf" vào 1 bảng,
-- phân biệt bằng field động "process_type"), đúng tiền lệ V1.0.30 (draft_process_documents).
--
-- Xem entity HoSoCVProcessDocument (com.td.domain.hosocv) + repository
-- HoSoCVProcessDocumentRepository/PostgresHoSoCVProcessDocumentRepository.
CREATE TABLE hosocv_process_documents (
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

CREATE INDEX IX_hosocv_process_documents_document_type ON hosocv_process_documents(document_type);
CREATE INDEX IX_hosocv_process_documents_created_on ON hosocv_process_documents(created_on);
CREATE INDEX idx_hosocv_process_documents_dynamic_fields ON hosocv_process_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosocv_process_documents IS 'Vet xu ly/lich su cua ho so cong viec (trinh duyet/duyet/chuyen luu tru/phoi hop/gop y) - append-only, phan biet bang dynamic_fields.process_type - mo rong BL-240';
COMMENT ON COLUMN hosocv_process_documents.dynamic_fields IS 'hosocv_id, process_type, action, old_value, new_value, note/opinion, creator';

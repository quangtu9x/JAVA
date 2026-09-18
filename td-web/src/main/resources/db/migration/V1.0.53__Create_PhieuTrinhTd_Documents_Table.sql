-- docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md (09/09/2026): bảng vật lý ĐỘC LẬP
-- cho module "Phiếu trình thẩm định" (phieutrinhtd) — form Domino "submission_dossier", database
-- Domino riêng "phieutrinhtd.nsf". Đúng tiền lệ vbcddh_documents (V1.0.51, BL-250): cấu trúc CỐ Ý
-- giống "documents"/"vbcddh_documents" (Dynamic Data Engine, BL-101) — field nghiệp vụ biến thiên
-- (status, lanhdao1/2, lanhdaovanphong, coordinator, nguoithamkhaohide...) nằm trong dynamic_fields
-- JSONB, KHÔNG tạo cột riêng cho từng field — xem entity PhieuTrinhTdDocument (com.td.domain.phieutrinhtd)
-- + repository PhieuTrinhTdDocumentRepository/PostgresPhieuTrinhTdDocumentRepository.
--
-- KHÔNG tạo bảng cho database Domino "phieutrinh" (không có hậu tố TD) — module đó không có menu/FE
-- nào kích hoạt và không có DXL/form-action nào đọc được (xem tài liệu 20 mục 1.1) — không có gì để
-- port.
CREATE TABLE phieutrinhtd_documents (
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

CREATE INDEX IX_phieutrinhtd_documents_title ON phieutrinhtd_documents(title);
CREATE INDEX IX_phieutrinhtd_documents_document_type ON phieutrinhtd_documents(document_type);
CREATE INDEX IX_phieutrinhtd_documents_status ON phieutrinhtd_documents(status);
CREATE INDEX IX_phieutrinhtd_documents_created_on ON phieutrinhtd_documents(created_on);
CREATE INDEX IX_phieutrinhtd_documents_last_modified_on ON phieutrinhtd_documents(last_modified_on);
CREATE INDEX IX_phieutrinhtd_documents_deleted_on ON phieutrinhtd_documents(deleted_on);
CREATE INDEX idx_phieutrinhtd_documents_dynamic_fields ON phieutrinhtd_documents USING GIN (dynamic_fields);

COMMENT ON TABLE phieutrinhtd_documents IS 'Phiếu trình thẩm định (form Domino submission_dossier, database phieutrinhtd.nsf) - docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md';
COMMENT ON COLUMN phieutrinhtd_documents.status IS 'Mã trạng thái Domino: 0 dang soan thao, 1 trinh tham khao, 2 trinh thong qua, 3 khong thong qua, 4 da thong qua, 5 trinh duyet cap1, 6 khong duyet cap1, 7 da duyet cap1, 25/26/27 = tuong duong cap2 (route/UI hien tai da tat o FE goc)';
COMMENT ON COLUMN phieutrinhtd_documents.dynamic_fields IS 'Field dong: code, title_rtext, description, creator/user_creator/unit_creator/agency_creator, lanhdao1/2(_obj), lanhdaovanphong(_obj), coordinator(_obj), nguoithamkhaohide, nguoidathamkhaohide, signerhide, documentin_id, document_profiles_id, unit_publish(_id), storaged, trinh<N>_date, ykientrinh<N>...';

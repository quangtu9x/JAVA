-- docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md (09/09/2026): nhật ký "quatrinh" của
-- phiếu trình (append-only) - mỗi lần trình/chuyển trạng thái (ChuyenTrangThaiPhieuTrinhTdUseCase,
-- TrinhThamKhaoPhieuTrinhTdUseCase) ghi 1 bản ghi mới vào đây, đúng cơ chế ChiTietPage.jsx#handleTrinh
-- (form=quatrinh, action_type=<status>, creator/receiver, parentid). Bảng riêng, KHÔNG dùng chung
-- documents/hosocv_process_documents - đúng tiền lệ tách bảng của vbcddh_documents/vbdi_res.
CREATE TABLE phieutrinhtd_process_documents (
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

CREATE INDEX IX_phieutrinhtd_process_documents_created_on ON phieutrinhtd_process_documents(created_on);
CREATE INDEX idx_phieutrinhtd_process_documents_dynamic_fields ON phieutrinhtd_process_documents USING GIN (dynamic_fields);

COMMENT ON TABLE phieutrinhtd_process_documents IS 'Nhat ky quatrinh cua phieu trinh tham dinh (append-only) - docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md muc 2.2/6.1';
COMMENT ON COLUMN phieutrinhtd_process_documents.dynamic_fields IS 'Field dong: parentid (id phieu trinh cha), action_type (= status moi), creator/user_creator, receiver/user_receiver/receiver_obj, create_date';

-- BL-103: Audit Log — append-only, ghi lai moi thao tac CRUD tren documents
CREATE TABLE document_logs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL,
    log_type    VARCHAR(30) NOT NULL,
    log_data    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_logs_document_id ON document_logs(document_id);
CREATE INDEX idx_document_logs_log_type ON document_logs(log_type);
CREATE INDEX idx_document_logs_created_at ON document_logs(created_at DESC);
CREATE INDEX idx_document_logs_log_data ON document_logs USING GIN(log_data);

COMMENT ON TABLE document_logs IS 'Audit log append-only cho moi thao tac CRUD tren documents (BL-103). Khong co FK toi documents(id) va khong dung ON DELETE CASCADE co chu dich: log phai song sot sau khi document bi hard-delete (BL-101 HardDeleteDocumentUseCase), neu khong se tu xoa chinh bang chung cua thao tac xoa vinh vien.';
COMMENT ON COLUMN document_logs.log_type IS 'create, update, delete, hard_delete (mo rong sau: read, process, sign, transfer)';
COMMENT ON COLUMN document_logs.log_data IS 'JSONB: {"newValue": {...}} cho create; {"oldValue": {...}, "newValue": {...}} cho update; {"oldValue": {...}} cho delete/hard_delete';

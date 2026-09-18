-- BL-101: Dynamic Data Engine — bổ sung cột JSONB lưu field động cho documents
ALTER TABLE documents ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_documents_dynamic_fields ON documents USING GIN (dynamic_fields);

COMMENT ON COLUMN documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';

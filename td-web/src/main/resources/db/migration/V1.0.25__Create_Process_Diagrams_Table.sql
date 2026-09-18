-- BL-211 (VBDen — Xử lý & Chuyển tiếp), bổ sung 22/08/2026 (R8, tài liệu phân tích 03): sơ đồ xử lý
-- (tương đương ClassTreeSoDo/"sodoxuly" phía Domino, lưu trong database vbden_res) — cây JSON ghi
-- lại chuỗi phân công/chuyển tiếp của 1 văn bản, dùng để FE vẽ sơ đồ xử lý trên tab "Quá trình xử
-- lý" (BL-210's QuaTrinhXuLy trả timeline dạng danh sách phẳng — KHÔNG đủ để vẽ cây, cần bảng riêng
-- này để lưu cấu trúc cha-con).
--
-- Thiết kế: 1 bảng riêng (không tái dùng "documents"/dynamic_fields — cây JSON lồng nhau không phù
-- hợp với model field-động phẳng đang dùng cho vbden/vbden_so/vbden_res). Khoá theo
-- (document_id, document_type) — 1 văn bản (vbden) có ĐÚNG 1 sơ đồ xử lý duy nhất, không nhân bản
-- theo tổ chức khác (khác vbden_so — 1 văn bản có thể có NHIỀU bản ghi "sổ" theo từng tổ chức vào
-- sổ độc lập).
CREATE TABLE process_diagrams (
    id                UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id       UUID         NOT NULL,
    document_type     VARCHAR(50)  NOT NULL DEFAULT 'vbden',
    diagram_json      TEXT         NOT NULL DEFAULT '{"username":"root","name":"root","children":[]}',
    created_by        UUID,
    created_on        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by  UUID,
    last_modified_on  TIMESTAMP WITH TIME ZONE,
    deleted_on        TIMESTAMP WITH TIME ZONE,
    deleted_by        UUID
);

CREATE INDEX IX_process_diagrams_document_id ON process_diagrams(document_id);
CREATE INDEX IX_process_diagrams_deleted_on   ON process_diagrams(deleted_on);
-- 1 van ban (theo document_type) chi co 1 so do xu ly con hoat dong (chua bi xoa mem).
CREATE UNIQUE INDEX UX_process_diagrams_document
    ON process_diagrams(document_id, document_type)
    WHERE deleted_on IS NULL;

COMMENT ON TABLE process_diagrams IS 'So do xu ly van ban (R8, tuong duong ClassTreeSoDo/"sodoxuly" phia Domino)';

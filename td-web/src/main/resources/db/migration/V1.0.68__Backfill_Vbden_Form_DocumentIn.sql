-- 29/09/2026 (bao loi "vbden/xuly/chophancong - lanh dao thay van ban nhung mo chi tiet khong co chuc nang
-- nao"): VbdenIncomingDocumentWriter (td-worker) tao van ban den nhan qua mang KHONG ghi "form", trong khi FE
-- (VBDenButtonActionXuLy.jsx) chi hien nhom nut xu ly khi form = "DocumentIn" (dung gia tri Domino gan cho
-- moi van ban den). Worker da sua ghi "form"; migration nay bo sung cho van ban den DA TAO truoc do.
-- Chi dong van ban den (document_type='vbden') CHUA co key "form" - khong dong vao ban ghi da co form
-- (vd DocumentIn_Deleted). Ban ghi "xin y kien" nam o bang rieng vbden_xinykien_documents, khong bi anh huong.
-- Idempotent: chay lai khong con dong nao khop WHERE.
UPDATE documents d
SET dynamic_fields = d.dynamic_fields || jsonb_build_object('form', 'DocumentIn')
WHERE d.document_type = 'vbden'
  AND d.dynamic_fields IS NOT NULL
  AND NOT (d.dynamic_fields ?| ARRAY['form', 'Form']);

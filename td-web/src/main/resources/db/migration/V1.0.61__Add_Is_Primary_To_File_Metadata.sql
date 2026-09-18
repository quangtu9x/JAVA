-- Yeu cau nguoi dung 15/09/2026 ("port ky so cho vbdi/vbden, giu file goc, tep ky luu moi, thay
-- the tep chinh thuc"): them cot "is_primary" that vao file_metadata - truoc day KySoVbdiUseCase da
-- goi UploadFileRequest.isPrimary(true) nhung khong co cot nao luu, hoan toan la no-op (xem
-- UploadFileUseCaseImpl - chua bao gio doc request.getIsPrimary()). Chi 1 tep duoc is_primary=true
-- tai 1 thoi diem cho cung 1 document_id (demote tep cu truoc khi set tep moi - xem
-- UploadFileUseCaseImpl#demoteExistingPrimary).
--
-- NOT NULL DEFAULT FALSE giong dung tien le cot is_shared (V1.0.50) - BAT BUOC entity Java phai co
-- @Builder.Default (xem javadoc FileMetadataEntity#isShared) vi Hibernate luon INSERT du moi cot,
-- DEFAULT cua DB khong bao gio duoc ap dung qua JPA.
ALTER TABLE file_metadata ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN file_metadata.is_primary IS 'Tep chinh thuc/hien hanh cua document_id nay (vd tep da ky so) - chi 1 tep is_primary=true tai 1 thoi diem cho cung document_id, tep cu bi demote (khong xoa) khi co tep moi duoc danh dau primary.';

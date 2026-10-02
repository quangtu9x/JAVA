-- 28/09/2026 (yeu cau nguoi dung "sua dong nhat co thu hoi van ban den, dam bao dung va hien thi, tranh
-- sai lech du lieu"): cung 1 co "da bi thu hoi" tung duoc ghi voi 3 ten khac nhau:
--   - "is_revoke" : VbdiRevokeTaskHandler / VbdenForwardUnitRevokeTaskHandler (worker) + ViewQueryService
--                   (sovanban_bixoa / sovanban_bithuhoi) doc -> CHUAN.
--   - "isRevoke"  : CapNhatVanBanThayTheThuHoiUseCase (truoc ban sua nay) ghi thang vao map, khong qua
--                   buoc lowercase cua DynamicDocumentFieldsService.
--   - "isrevoke"  : ten FE VBDenButtonAction.jsx tung doc (khuon Domino REST tra ten item viet thuong).
-- Code ghi/doc da thong nhat ve "is_revoke". Migration nay chuyen du lieu DA GHI ten cu sang ten chuan:
--   - Da co "is_revoke" -> GIU NGUYEN gia tri do (khong ghi de), chi bo key ten cu.
--   - Chua co -> lay gia tri tu "isRevoke" (uu tien) hoac "isrevoke".
-- Khong doi bat ky field nao khac. Idempotent: chay lai khong con dong nao khop dieu kien WHERE.
UPDATE documents d
SET dynamic_fields = (d.dynamic_fields - 'isRevoke' - 'isrevoke')
    || jsonb_build_object(
        'is_revoke',
        COALESCE(d.dynamic_fields->>'is_revoke', d.dynamic_fields->>'isRevoke', d.dynamic_fields->>'isrevoke')
    )
WHERE d.dynamic_fields ?| ARRAY['isRevoke', 'isrevoke'];

-- Yeu cau nguoi dung 25/09/2026 ("Loai ho so port sang JAVA neu chua co va cutoverFE luon"):
-- man quan tri "Loai ho so" (form type_document_profiles) DA dung Java tu BL-114 (18/08/2026), nhung
-- bang categories CHUA co ban ghi nao cua form nay (du lieu Domino chua tung duoc chuyen sang), trong
-- khi 2 noi DOC danh muc (o chon loai ho so o Ho so cong viec + Tim kiem ho so) van goi Domino - cutover
-- 2 noi doc sang Java ma khong co du lieu thi o chon se RONG.
--
-- Nap 4 loai ho so CHUAN, dung danh sach Domino goc dang hard-code o FE (utils/global.jsx#HoSo_LoaiHoSo,
-- key 1..4). short_name (dynamic_fields) = gia tri Ho so cong viec luu vao key_type - KHONG duoc doi
-- (du lieu that dang dung key_type="2"). Idempotent: bo qua neu da co ban ghi cung form + short_name
-- (vd nguoi dung da tu them qua man quan tri) hoac trung code.
INSERT INTO categories (code, name, level, full_path, sort_order, is_active, form, dynamic_fields)
SELECT v.code, v.name, 0, v.name, v.sort_order, TRUE, 'type_document_profiles',
       jsonb_build_object('short_name', v.short_name)
FROM (VALUES
    ('LOAI_HO_SO_1', 'Hồ sơ Xử lý văn bản',        '1', 1),
    ('LOAI_HO_SO_2', 'Hồ sơ Giải quyết công việc', '2', 2),
    ('LOAI_HO_SO_3', 'Hồ sơ Văn bản liên quan',    '3', 3),
    ('LOAI_HO_SO_4', 'Hồ sơ Soạn thảo văn bản',    '4', 4)
) AS v(code, name, short_name, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.deleted_on IS NULL
      AND (c.code = v.code
           OR (c.form = 'type_document_profiles' AND c.dynamic_fields ->> 'short_name' = v.short_name))
);

-- 22/10/2026: mã quyền màn "Trục quốc gia" (/tonghop/trucquocgia) — tdgateway/docs/24 §4.2.
-- Gán qua màn Nhóm vai trò như mọi mã quyền khác; authority = ROLE_<MÃ> (NationalExchangeAccessPolicy).
-- ADMIN/tài khoản tổng mặc định có đủ — các mã này để cấp quyền cho người KHÔNG phải ADMIN.
-- Phạm vi dữ liệu không nằm ở mã quyền: đăng nhập tenant cấp tỉnh (NATIONAL_EXCHANGE_PROVINCE_TENANT_IDS, mặc
-- định H05) thì xem toàn tỉnh, tenant khác chỉ thấy gói của mình.
INSERT INTO app_permissions (code, name, module_key, description, is_active)
SELECT v.code, v.name, 'Trục quốc gia', v.description, TRUE
FROM (VALUES
    ('NATIONAL_EXCHANGE_VIEW', 'Xem gửi nhận Trục quốc gia',
     'Xem danh sách, thống kê, chi tiết gói gửi/nhận qua Trục liên thông văn bản quốc gia'),
    ('NATIONAL_EXCHANGE_AUDIT', 'Xem nhật ký kỹ thuật Trục quốc gia',
     'Xem mô tả lỗi chi tiết, từng lần thao tác với Trục, người thực hiện thao tác quản trị'),
    ('NATIONAL_EXCHANGE_DOWNLOAD', 'Tải gói eDXML Trục quốc gia',
     'Tải tệp gói eDXML đã gửi/nhận (chưa mở chức năng)'),
    ('NATIONAL_EXCHANGE_REPLAY', 'Phát lại gói Trục quốc gia trong tỉnh',
     'Bàn giao lại gói đã nhận tới đơn vị trong tỉnh (chưa mở chức năng)'),
    ('NATIONAL_EXCHANGE_RESEND', 'Gửi lại gói ra Trục quốc gia',
     'Gửi lại văn bản/gói trạng thái ra Trục — nên giới hạn quản trị cấp tỉnh (chưa mở chức năng)')
) AS v(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM app_permissions p WHERE p.code = v.code AND p.deleted_on IS NULL
);

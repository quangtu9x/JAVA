-- Bugfix 03/09/2026 (báo lỗi người dùng: "level = 0, không có cấp 0, bắt đầu tính từ 1").
--
-- CreateOrganizationUseCase/UpdateOrganizationUseCase (từ khi port BL-105 sang Java) luôn gán node
-- gốc (agency_level không có cha) level = 0, rồi mọi con cháu tính level = cấp cha + 1 dựa trên đó
-- -> TOÀN BỘ cây tổ chức của MỌI tenant đang lưu level THẤP HƠN 1 so với độ sâu thật tính từ gốc
-- (vd cây "UBND Tỉnh(gốc) -> Sở ban ngành -> Sở KH&CN -> Phòng Công nghệ" đang lưu lần lượt
-- 0/1/2/3 thay vì đúng phải là 1/2/3/4). Đây chính là nguyên nhân màn "Đơn vị/Phòng ban" hiển thị
-- sai "Cấp theo Cây cơ cấu tổ chức" mà người dùng báo (vd "Phòng Công nghệ" ra cấp 3 thay vì cấp 4).
--
-- OrganizationHierarchyRules.resolveNodeType (fallback suy loại node khi thiếu cột "form") đã ngầm
-- định quy ước 1-indexed từ trước (level<=1 -> agency_level, level==2 -> agency, level==3 -> unit) —
-- xác nhận đây là quy ước ĐÚNG đã có sẵn, code gán level mới là chỗ sai lệch.
--
-- An toàn cộng dồn +1 cho MỌI dòng: bảng "organizations" không có bất kỳ migration nào từng INSERT
-- dữ liệu mẫu với level tính sẵn (grep toàn bộ db/migration/ xác nhận) — 100% dòng hiện có đều được
-- tạo qua chính 2 use case nói trên (đã cùng mắc lỗi 0-indexed), nên +1 đồng loạt khôi phục đúng độ
-- sâu thật cho MỌI tenant (Flyway tự áp dụng migration này cho từng tenant DB lúc khởi động).
UPDATE organizations
SET level = level + 1;

ALTER TABLE organizations
    ALTER COLUMN level SET DEFAULT 1;

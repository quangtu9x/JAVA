# Index tài liệu phát triển API

Đây là điểm vào chính cho nhóm tài liệu hỗ trợ nhân bản feature và sửa API trong dự án này.

## 1. Tài liệu nên mở đầu tiên

- Tổng quan chi tiết: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)
- Tách phân hệ độc lập: [HUONG_DAN_TACH_PHAN_HE_DOC_LAP.md](HUONG_DAN_TACH_PHAN_HE_DOC_LAP.md)
- Checklist tổng hợp: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)
- Checklist siêu ngắn khi sửa API: [CHECKLIST_SUA_API_NHANH.md](CHECKLIST_SUA_API_NHANH.md)
- Quy ước đặt tên: [QUY_UOC_DAT_TEN_FEATURE_API.md](QUY_UOC_DAT_TEN_FEATURE_API.md)

## 2. Template thực chiến

- Feature có hierarchy kiểu categories: [TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md](TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md)
- Feature CRUD + cache không hierarchy: [TEMPLATE_CRUD_CACHE_KHONG_HIERARCHY.md](TEMPLATE_CRUD_CACHE_KHONG_HIERARCHY.md)

## 3. Chọn tài liệu theo nhu cầu

Nếu anh muốn nhân một feature mới:

- Mở [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)
- Nếu là bài toán phân hệ độc lập, mở [HUONG_DAN_TACH_PHAN_HE_DOC_LAP.md](HUONG_DAN_TACH_PHAN_HE_DOC_LAP.md)
- Mở thêm [QUY_UOC_DAT_TEN_FEATURE_API.md](QUY_UOC_DAT_TEN_FEATURE_API.md)
- Sau đó chọn một trong hai template thực chiến phù hợp

Nếu anh muốn sửa nhanh một API đang có:

- Mở [CHECKLIST_SUA_API_NHANH.md](CHECKLIST_SUA_API_NHANH.md)
- Nếu sửa sâu hoặc thay schema, mở thêm [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

## 4. Gợi ý dùng thực tế

- Feature có `parentId`, `level`, `fullPath`: dùng template hierarchy
- Feature chỉ CRUD + cache đơn giản: dùng template không hierarchy
- Chỉ sửa endpoint, request, response, query hoặc auth: dùng checklist sửa nhanh

## 5. Ghi chú

- Bộ tài liệu này bám theo pattern `categories` đã implement và verify runtime trong repo.
- Khi sửa cross-module, ưu tiên build từ root bằng `mvn install -DskipTests` để tránh dùng jar cũ trong `.m2`.
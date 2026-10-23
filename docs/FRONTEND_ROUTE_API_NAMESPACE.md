# Phân tách namespace route FE và API `/qlvb`

Ngày cập nhật: 12/10/2026.

## Vấn đề

Prefix `/qlvb` trước đây được dùng đồng thời cho hai mục đích:

- Route SPA quản trị, ví dụ `/qlvb/cocau/nguoidung`.
- API tương thích legacy, gồm `/qlvb/authen`, `/qlvb/profile` và các API Domino chưa cutover.

Khi người dùng mở trực tiếp hoặc dán URL SPA `/qlvb/cocau/nguoidung` vào tab mới, reverse proxy
nhận diện `/qlvb/*` là API và chuyển request không có header token đến backend. Spring Security vì
thế trả `401` JSON trước khi request tới được `index.html` của SPA.

## Quyết định

- Route giao diện quản trị chuyển từ `/qlvb/*` sang `/quantri/*`.
- API `/qlvb/*` được giữ nguyên hoàn toàn.
- Không nới quyền Spring Security và không thêm ngoại lệ public dưới `/qlvb`; do đó không làm thay
  đổi bảo vệ của `authen`, `profile` hoặc API legacy.
- Tên thư mục/module mã nguồn `pages/qlvb` và các key cấu hình như `menu_qlvb` được giữ nguyên vì
  đây là tên nội bộ/dữ liệu phân quyền, không phải URL public.

Ví dụ mới: `/quantri/cocau/nguoidung`.

## Phạm vi cập nhật FE

- Router gốc và redirect sau đăng nhập.
- Redirect mặc định bên trong `SystemsPageQLVB`.
- Toàn bộ link sidebar quản trị.
- Hàm `getRedirectToQuantri` và link cấu hình người dùng mở từ trình ký PDF.

Các lời gọi API dạng `qlvb/...`, `/qlvb/authen` và `/qlvb/profile` không đổi.

## Lưu ý triển khai

Web server phục vụ FE phải áp dụng cùng SPA fallback đang dùng cho `/vbden`, `/vbdi`, ... đối với
`/quantri/*` (trả `index.html`). Không cấu hình `/quantri` như một upstream API.

Bookmark `/qlvb/...` cũ không thể redirect ở tầng React vì request đã bị reverse proxy chuyển sang
backend trước khi tải SPA. Nếu cần tương thích bookmark cũ, cấu hình proxy phải đặt redirect chính
xác cho các nhánh giao diện (`/qlvb/cocau`, `/qlvb/vaitro`, `/qlvb/dungchung`,
`/qlvb/dungrieng`, `/qlvb/hethong`) trước rule API tổng quát; tuyệt đối không redirect
`/qlvb/authen`, `/qlvb/profile` hoặc các nhánh API khác.

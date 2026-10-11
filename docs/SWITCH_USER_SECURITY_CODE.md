# Mã bảo mật chức năng chuyển tài khoản

Chức năng **Chuyển tài khoản** chỉ thực hiện khi đồng thời thỏa mãn hai điều kiện:

1. Người gọi có role `ADMIN` (giữ nguyên kiểm tra quyền hiện tại).
2. Trường `ma` trong request khớp với mã bảo mật riêng được cấu hình ở backend.

## Cấu hình

Backend đọc biến môi trường:

```dotenv
SWITCH_USER_SECURITY_CODE=123@!@#Aa
```

Giá trị mặc định `123@!@#Aa` được giữ để tương thích FE/ Domino cũ. Khi triển khai production nên đặt
một giá trị riêng đủ mạnh trong secret manager hoặc cấu hình môi trường, không đưa mã production vào Git.
Sau khi đổi biến môi trường cần khởi động lại `td-web`.

## API

`POST /api/v1/shared-core/users/switch`

```json
{
  "username": "tai-khoan-can-chuyen",
  "ma": "ma-bao-mat-rieng"
}
```

Nếu thiếu mã hoặc mã sai, backend trả HTTP `400` và không tra tài khoản đích, không cấp token chuyển tài
khoản. Mã chỉ được gửi trong body HTTPS, không được ghi vào log. Kiểm tra role `ADMIN` vẫn do Spring
Security thực hiện trước controller.

## FE

Popup **Chuyển tài khoản** yêu cầu nhập cả tên tài khoản và mã bảo mật. Nút **Xác nhận** chỉ bật khi cả
hai ô có dữ liệu. Backend luôn là nơi quyết định mã đúng hay sai; FE không lưu hoặc hard-code mã.

Sau khi chuyển thành công, FE điều hướng về `/dashboard` và lưu cờ phiên chuyển admin độc lập với
chuyển vai kiêm nhiệm. Trong phiên này FE không cho chuyển tiếp sang một tài khoản khác; người dùng phải
bấm biểu tượng **Về tài khoản gốc** để khôi phục tài khoản quản trị, sau đó mới có thể chuyển tiếp.
Cờ này được xóa khi quay về tài khoản quản trị hoặc đăng xuất.

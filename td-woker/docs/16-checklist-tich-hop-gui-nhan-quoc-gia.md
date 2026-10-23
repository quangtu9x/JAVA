# Cutover Gửi nhận quốc gia khỏi TD Worker

## Trạng thái

Từ ngày 14/10/2026, kết nối Trục liên thông văn bản quốc gia được chuyển sang project độc lập
`C:/code_projects/HSCV/tdgateway`. `td-woker` không còn đăng ký hoặc xử lý task `guinhanquocgia.*`.

Mục tiêu của cutover là bảo đảm chỉ một dịch vụ sở hữu kết nối VPCP/VXP, tránh gửi hoặc nhận trùng.

## Thành phần đã gỡ khỏi `td-woker`

- Package production `com.td.worker.tasks.guinhanquocgia`.
- Test riêng của package `guinhanquocgia`.
- SDK `EdXML102-0.0.3.jar`, `SDKVXP-2.0.0.jar` và Maven local repository tương ứng.
- Dependency `com.vnpt.xml:edxml102` và `com.vpcp.services:sdkvxp`.
- Repository Maven cục bộ chỉ phục vụ hai SDK trên.
- Các biến `WORKER_TASKS_GUINHANQUOCGIA_*` trong `.env.example`.

## Thành phần cố ý giữ lại

`db/migration-worker/V2__create_worker_guinhanquocgia_send_log.sql` được giữ nguyên như migration lịch sử.
Không xóa hoặc sửa checksum migration đã có thể được áp dụng ở production, vì Flyway sẽ từ chối khởi động
nếu migration trong `flyway_schema_history` không còn khớp. Bảng legacy không còn code nào đọc hoặc ghi.

Không tự động `DROP TABLE worker_guinhanquocgia_send_log`. Việc xóa dữ liệu lịch sử là thay đổi vận hành riêng,
chỉ thực hiện sau thời gian lưu trữ đã được phê duyệt.

## Checklist triển khai

- [x] Tạo và kiểm thử project độc lập `tdgateway`.
- [x] Gỡ handler, SDK, dependency, test và cấu hình khỏi `td-woker`.
- [x] Giữ migration Flyway lịch sử để tương thích database hiện hữu.
- [x] Xác nhận source `td-woker` không còn tham chiếu runtime đến `guinhanquocgia`.
- [x] Build/test sạch `td-woker` sau cutover.
- [ ] Deploy `tdgateway` nhưng giữ `GATEWAY_VPCP_ENABLED=false`.
- [ ] Dừng toàn bộ instance `td-woker` phiên bản cũ còn chứa handler quốc gia.
- [ ] Xác nhận không còn consumer cũ bind routing key `worker.guinhanquocgia.*`.
- [ ] Cấu hình endpoint/credential/queue riêng cho `tdgateway`.
- [ ] Bật một instance `tdgateway`, kiểm tra health và danh sách bốn handler.
- [ ] Chạy UAT một văn bản đi, một văn bản đến và hai chiều trạng thái.
- [ ] Theo dõi trùng `documentId`, DLQ, retry và audit tối thiểu một chu kỳ vận hành.
- [ ] Chỉ mở rộng số instance sau khi xác nhận idempotency với dữ liệu thật.

Checklist tích hợp Java BE và vận hành đầy đủ nằm tại
`C:/code_projects/HSCV/tdgateway/docs/18-checklist-cutover-va-tich-hop-java-be.md`.

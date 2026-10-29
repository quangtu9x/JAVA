# Thông báo người dùng: socket.io + hộp thư (port `TDInit.sendSocketNotSave`)

Tài liệu này mô tả cơ chế thông báo thay cho Domino `TDInit.sendSocketNotSave`. Có 2 đợt:

- **Đợt 1 (27/10/2026): đẩy realtime qua socket.io.** Backend Java gửi thông báo sau khi lưu. Trước đợt này,
  sau cutover không còn ai gửi các thông báo kiểu "văn bản chủ trì", "chờ ký duyệt".
- **Đợt 2 (27/10/2026): hộp thư thông báo lưu ở DB đơn vị.** Người chưa đăng nhập hoặc đang mất kết nối vẫn
  nhận lại được thông báo, và xem lại được lịch sử. FE có ngăn thông báo, có toast gộp và toast tổng hợp để
  không bị dồn dập.

## 1. Nguyên nhân mất thông báo (trước đợt 1)

1. **Java chưa từng gửi socket.** Mỗi use case port từ Domino đều bỏ `sendSocketNotSave`, ghi chú "hạ tầng realtime
   chưa tồn tại".
2. **FE tự gửi sai đường dẫn.** `SubFormChuyenXuLy.jsx` và `VaoSoNhieu.jsx` POST tới `api/socket/notification`.
   Server (`docs/socketio/index_socketio.js`) chỉ nhận đường dẫn chứa `user`/`coquan`/`donvi`/`phongban`, nên trả
   400. Ngoài ra `sendToDonvi` gửi `donvi_<mã>` trong khi FE chỉ join `coquan_<mã>`.
3. **Server chỉ giữ 1 socket cho mỗi user.** Mở nhiều tab hoặc nhiều máy thì chỉ tab mới nhất nhận được.
4. **Không lưu lại gì.** Danh sách thông báo nằm ở localStorage của từng trình duyệt; offline thì mất hẳn.
5. **Socket bỏ cuộc sau khoảng 25 giây** mất mạng (5 lần × 5 giây), tới khi người dùng bấm F5.

## 2. Kiến trúc

DB là nguồn sự thật; socket chỉ đóng vai "chuông cửa".

```
use case (sau save) ── RealtimeNotifier.notify(RealtimeNotification)
                         │  chụp username + tenant người gửi trên luồng request (luồng nền không có ngữ cảnh)
                         │  (đăng ký TransactionSynchronization.afterCommit; rollback thì không gửi gì)
                         ▼ sau COMMIT → RealtimeTaskDispatcher (luồng NỀN "realtime-notify-", 2–4 luồng,
                         │              hàng đợi 2000, quá tải thì luồng gọi tự chạy — không bỏ thông báo)
        ┌──────── 1. RecipientTenantResolver: người nhận → DB đơn vị của HỌ (mã đơn vị: tổ tiên có
        │              linked_tenant_id; username: như lúc người đó đăng nhập; không xác định → DB người gửi)
        │              lưu user_notifications theo từng tenant (TenantScopedExecutor.runInTenant +
        │              REQUIRES_NEW, persist theo lô), lỗi lưu → chỉ log, vẫn đẩy socket
        └──────── 2. RealtimeNotificationGateway (SocketIoRealtimeNotificationGateway)
                       POST {REALTIME_SOCKET_BASE_URL}/socket-batch?api_key=…  (1 request / thông báo)
                       body {items:[{channel:"user_<u>"|"coquan_<mã>", data:{…, notification_id, created_on}}]}
                       server chưa có /socket-batch (404) → 10 phút gửi /socket/{channel} từng người
                         ▼
                 server Node phát "user_notify" / "groups_notify" tới room
                         ▼
FE Notifications.tsx ── inboxStore.pushed() (chống trùng theo notification_id) → toast lẻ hoặc toast gộp
                    └─ khi connect / reconnect (rải 0–8 giây) / quay lại tab sau hơn 60 giây:
                       inboxStore.refresh() (GET /api/v1/notifications/summary) → toast tổng hợp nếu có thông báo MỚI
```

- **Vì sao luồng nền:** request nghiệp vụ không phải chờ INSERT hay HTTP. Đây cũng là điều kiện để ghi chéo tenant:
  trong `afterCommit`, Spring vẫn coi transaction là "active", nên `TenantScopedExecutor.runInTenant` từ chối
  chạy.
- **Không dùng bean `Executor`:** `RealtimeTaskDispatcher` là port riêng. Spring Boot 3.2 chỉ tự tạo
  `applicationTaskExecutor` khi chưa có bean `Executor` nào, nên thêm một bean như vậy sẽ âm thầm đổi hành vi
  `@Async` / MVC async của cả ứng dụng.

**Vì sao không cần outbox + worker retry:** dòng hộp thư được ghi trước khi đẩy socket. Nếu lần đẩy bị lỡ (server
socket sập, client mất mạng, người nhận offline), FE sẽ kéo lại khi kết nối lại hoặc đăng nhập. Không có thông
báo nào phụ thuộc vào việc lần đẩy socket thành công.

**Khoảng hở còn lại:** tiến trình chết đúng giữa lúc commit nghiệp vụ và lúc ghi hộp thư (cỡ mili giây). Lựa chọn
ghi sau commit, trong transaction riêng, là có chủ đích. Nếu ghi trong cùng transaction nghiệp vụ thì một lỗi ở
bảng thông báo (ví dụ chưa migrate) sẽ đánh dấu rollback-only và làm hỏng chính thao tác nghiệp vụ.

### 2.1 Bảng `user_notifications` (V1.0.78 + index dọn dữ liệu V1.0.79, DB đơn vị)

Một dòng là một thông báo cho một người nhận.

| Cột | Ý nghĩa |
|---|---|
| `recipient` / `recipient_type` | username (`user`), hoặc mã định danh đơn vị (`unit`, là hộp thư chung của văn thư) |
| `database_type`, `action`, `document_id` | Để lọc, đếm và mở văn bản. `action` khớp `ACTION_MAP` ở FE |
| `read_on`, `read_by` | Đã đọc. Với dòng `unit`, một văn thư đọc thì cả đơn vị coi là đã đọc; `read_by` ghi lại ai đọc |
| `dynamic_fields` | Nội dung hiển thị: `code`, `creator`, `position`, `opinion`, `deadline`, `message` |
| `deleted_on` | Thông báo đã bị ẩn (xoá mềm) |

- Có index theo `(lower(recipient), created_on DESC)`, index riêng cho thông báo chưa đọc, theo `document_id`, và
  theo `created_on` (V1.0.79, phục vụ job dọn). Mọi index đều partial `WHERE deleted_on IS NULL`.
- **Dọn hằng đêm** (`UserNotificationRetentionScheduler`, 02:30, lần lượt từng tenant):
  - Xoá mềm thông báo **đã đọc** quá 180 ngày, và **mọi** thông báo quá 365 ngày.
  - Chạy theo lô 5.000 dòng (mỗi lô khoảng 110ms trên 300k dòng).
  - Idempotent, nên chạy trên nhiều instance cũng an toàn.
- **Danh sách** truy vấn riêng từng khoá người nhận (`lower(recipient) = :key`, tối đa 2 khoá: cá nhân + đơn vị),
  rồi trộn theo thời gian trong Java. Lý do xem mục 8.
- Entity `com.td.domain.notifications.UserNotification` thuộc persistence unit chính (DB tenant).
- Repository: port `UserNotificationRepository`, implementation `PostgresUserNotificationRepository`.

### 2.2 API `UserNotificationsController` — `/api/v1/notifications`

Mọi API chỉ áp dụng cho **người đăng nhập** (username + hộp thư đơn vị nếu là văn thư).

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `?page&size&unreadOnly&databaseType` | `{items, total, unreadCount}`, mới nhất trước, `size` tối đa 200 |
| GET | `/summary` | `{unreadCount, groups:[{databasetype, action, count, latest_on}], latest:[5 chưa đọc]}` |
| POST | `/{id}/read` | Đánh dấu một thông báo đã đọc |
| POST | `/read-all` | Body tuỳ chọn `{databaseType}` |
| POST | `/read-document` | Body `{documentId}`: đánh dấu đã đọc mọi thông báo của văn bản (khi mở văn bản) |
| DELETE | `/{id}` | Ẩn thông báo (xoá mềm) |

- **Item** dùng đúng khoá của payload socket (`databasetype`, `action`, `docid`, `code`, `creator`…), cộng thêm
  `notification_id`, `created_on`, `read`, `recipient_type`.
- **Hộp thư đơn vị** chỉ hiển thị cho văn thư, tức người có một trong các quyền `QCapNhatVBDen` hoặc
  `QCapNhatVBDi` (kể cả các biến thể `DV`/`PB`), với mã định danh = `ResolveIdentifierByLevelUseCase`. FE join
  room `coquan_<mã>` theo đúng điều kiện này (`Notifications.tsx#hasUnitInboxRole`). Trước đây chỉ
  `QCapNhatVBDen` được join, nên văn thư VB đi không nhận được "Dự thảo chờ vào sổ phát hành".

### 2.3 Chọn kênh và cấu hình

- Người nhận khớp `RealtimeNotification.isUnitIdentifier` (`[A-Z][0-9]{2}(\.[0-9A-Za-z]+)*`) được coi là đơn vị:
  ghi `recipient_type=unit` và gửi vào room `coquan_<mã>`. Còn lại là người dùng, gửi vào `user_<username>`.
  Domino so tiền tố cứng `H05;K05;A05`, nên bỏ sót mã `H56.*`.
- Biến môi trường (`JAVA/.env`):
  - `REALTIME_SOCKET_ENABLED`
  - `REALTIME_SOCKET_BASE_URL`: phải gồm `/api`; để rỗng thì không đẩy socket (hộp thư vẫn được ghi)
  - `REALTIME_SOCKET_API_KEY`
  - `REALTIME_SOCKET_CONNECT_TIMEOUT_MILLIS`, `REALTIME_SOCKET_REQUEST_TIMEOUT_MILLIS`
  - `REALTIME_EXECUTOR_CORE_SIZE` (2), `REALTIME_EXECUTOR_MAX_SIZE` (4), `REALTIME_EXECUTOR_QUEUE_CAPACITY` (2000)
  - `REALTIME_INBOX_RETENTION_READ_DAYS` (180; đặt `<= 0` để tắt job dọn), `REALTIME_INBOX_RETENTION_ALL_DAYS` (365),
    `REALTIME_INBOX_CLEANUP_BATCH_SIZE` (5000), `REALTIME_INBOX_CLEANUP_CRON` (`0 30 2 * * *`)

## 3. Điểm gửi (Domino → Java)

### Dự thảo

| Use case | Action | Người nhận |
|---|---|---|
| `XuLyDuThaoUseCase` | `chuyenkyduyet` | `user_signer` |
| `XuLyDuThaoUseCase` | `tralai` | người nhận, nếu không có thì `user_creator` |
| `XuLyDuThaoUseCase` | `thuhoi` | người ký và người xử lý trước khi thu hồi |
| `XuLyDuThaoUseCase` | `chuyenduthao` (chuyển xử lý / chuyển người khác) | người nhận được chọn |
| `XuLyDuThaoUseCase` | `chuyenphoihop` | người nhận được chọn |
| `ChuyenPhoiHopDuThaoUseCase` | `chuyenphoihop` | người phối hợp mới |
| `LuuDuThaoUseCase` | `chuyenphoihop` | người phối hợp mới được thêm khi Lưu |
| `TaoDuThaoUseCase` | `chuyenphoihop` | người phối hợp có sẵn khi tạo mới dự thảo |
| `ChuyenThanhVanBanDiUseCase` | `chuyenphathanh` | `unit_publish_identifier` (văn thư) |
| `ThuHoiVanBanDiUseCase` | `thuhoi` | `unit_publish_identifier` |
| `TraLaiDuThaoSupport` (3 use case trả lại) | `tralai` | người nhận; nếu trả lãnh đạo thì thêm người soạn |
| `DuThaoVanThuKhongBanHanhUseCase` | `xoavanbandi` | người soạn, người ký, người xử lý khác |
| `DuThaoDaBanHanhUseCase` | `vaosovanbandi` (`databasetype` = `vbdi`) | người soạn, người ký |

### Văn bản đi

| Use case | Action | Người nhận |
|---|---|---|
| `SendVbdiDocumentUseCase` (chỉ các loại gửi, không gồm thu hồi) | `phathanhvanban` | coordinator (username), internal_unit / contact_unit (mã đơn vị). `creator` = đơn vị phát hành |

### Văn bản đến

| Use case | Action | Người nhận |
|---|---|---|
| `ChuyenXuLyVbdenUseCase` | `chuyenxuly` / `chuyenphoihop` (kèm hạn, ý kiến) | `userProcess` + `userCoordinate` (danh sách phẳng); đơn vị không có người đại diện → `coquan_<mã>` |
| `LuuVanBanDenUseCase` | `vanthuchuyenvanban` | `user_assigner` + `user_distributor`, chỉ khi giao lần đầu |
| `ThuHoiVanBanUseCase` | `thuhoi` | người **bị thu hồi** |
| `LayLaiVanBanUseCase` | `laylaivanban` | người **bị lấy lại** |
| `TraLaiVanBanUseCase` | `tralai` | người **nhận** văn bản trả lại |
| `CapNhatKetQuaXuLyUseCase` | `capnhatvanbantraloi` | `user_process` + `user_processing` |
| `CapNhatPhoiHopUseCase` | `capnhatphoihopxuly` | `user_process` |
| `PhucHoiVanBanDenUseCase` | `phuchoi` | `author` |
| `XoaVanBanDenUseCase` | `xoa` | `author` |

### Khác

| Use case | Action | Người nhận |
|---|---|---|
| `ChuyenTrangThaiPhieuTrinhTdUseCase` | `chuyenphieutrinh` (`databasetype` = `phieutrinh`) | người nhận bước, người tạo |
| `ManageMyDelegationUseCase` | `capnhatuyquyen` | người được ủy quyền mới và cũ |
| `UpdateAppUserUseCase` | `capnhatnguoidung` | chính tài khoản được sửa |

- **Khác Domino có chủ đích:**
  - Với thu hồi, lấy lại và trả lại, Domino báo cho `user_process` còn lại (kèm chính người thao tác). Java báo
    cho người bị ảnh hưởng.
  - Phối hợp dự thảo dùng `chuyenphoihop`. Domino dùng `chuyenduthao4`, nhưng FE không có nhãn cho action đó.
- **Không port** (Domino đã comment out hoặc không bao giờ gọi): vbden `ChuyenXuLy`, `XuLyLai`,
  `TraLoiVanBanXinYKien*`, `TheoDoiNhiemVu`, `CapNhatVanBanDen.vaosovanbandi()`.

**Thêm điểm gửi mới:** inject `RealtimeNotifier`, gọi
`notify(RealtimeNotification.of(databasetype, action, docId, code).to(...))` sau khi save. Sau đó thêm tiêu đề vào
`RECIPIENT_TITLE_MAP` và nhãn vào `ACTION_MAP` trong `ThanhHoaDEV/src/app/modules/notifs/core/notificationUtils.ts`.
Test dựng use case bằng constructor thì dùng `RealtimeNotifierTestSupport.noop()`, hoặc `recording(list)` khi cần
assert.

## 4. FE (`ThanhHoaDEV/src/app/modules/notifs/core`): thiết kế "có ích, không làm phiền"

| File | Vai trò |
|---|---|
| `notificationInboxApi.ts` | Gọi `/api/v1/notifications` |
| `inboxStore.ts` | Store nhỏ (`useSyncExternalStore`): số chưa đọc + `version`; chống trùng theo `notification_id`. Tách khỏi `ThongBaoProvider` để mỗi thông báo mới không re-render cả cây `useNoti()` |
| `notificationToasts.tsx` | Toast lẻ, toast gộp, toast tổng hợp; `NotificationBody` dùng chung |
| `NotificationCenter.tsx` | Ngăn thông báo (Drawer). Thay `SystemNotificationList.tsx` (đã xoá) |
| `notificationUtils.ts` | `getNotificationTitle()`: tiêu đề **theo góc nhìn người nhận**, ví dụ "Dự thảo chờ ký duyệt", "Văn bản đến cần xử lý", "Văn bản đến bị thu hồi" |
| `header-menus/SystemNotificationBadge.tsx` | Chuông luôn hiện, số = chưa đọc trên server (+ thông báo quản trị). Chỉ instance gắn sau cùng xử lý sự kiện mở từ toast, để không mở 2 ngăn chồng nhau |

### Hành vi

- **Thông báo lẻ:** một toast gọn gồm icon và màu theo phân hệ, tiêu đề theo người nhận, số/ký hiệu, người gửi,
  ý kiến (tối đa 2 dòng) và tag hạn xử lý (đỏ nếu quá hạn, cam nếu còn ≤ 2 ngày). Bấm vào toast mở ngăn thông báo
  kèm văn bản luôn.
- **Tới dồn dập:** mỗi 30 giây hiện tối đa 2 toast lẻ. Từ thông báo thứ 3 không bật thêm toast mà gộp vào **một**
  toast "N thông báo mới", cập nhật tại chỗ và đếm theo nhóm, có nút "Xem tất cả".
- **Đăng nhập / kết nối lại / quay lại tab sau hơn 60 giây:** kéo `summary`.
  - Nếu có thông báo chưa đọc **mới hơn** lần tổng hợp trước (lưu ở localStorage `noti-catchup-ack:<username>`),
    hiện **một** toast "Bạn có N thông báo chưa đọc" kèm danh sách theo nhóm, nút "Xem" và "Đánh dấu đã đọc".
  - F5 hoặc mở tab mới với cùng tồn đọng đó thì không nhắc lại.
  - Nếu chỉ có 1 thông báo thì hiện như toast lẻ.
- **Ngăn thông báo:**
  - Lọc Tất cả / Chưa đọc, và theo phân hệ (VB đến, VB đi, Dự thảo, Phiếu trình).
  - Danh sách chia theo ngày (Hôm nay / Hôm qua / dd/MM/yyyy); dòng chưa đọc có nền xanh nhạt và chấm.
  - Thông báo chung của đơn vị có tag "Đơn vị".
  - Mỗi dòng có nút "đánh dấu đã đọc" và "ẩn". Đầu ngăn có "Đánh dấu đã đọc tất cả" (hoặc chỉ của phân hệ đang
    lọc). "Xem thêm" tối đa 200 thông báo.
  - Bấm một dòng sẽ mở văn bản (modal chi tiết từng phân hệ) và đánh dấu đã đọc **mọi** thông báo của văn bản đó.
- **Socket:** kết nối lại vô hạn (giãn dần tới 60 giây), thay cho cách cũ chỉ thử 5 lần.
  - Đồng bộ hộp thư khi **kết nối lại** được rải ngẫu nhiên 0–8 giây, để khi server socket khởi động lại thì
    hàng nghìn trình duyệt không dồn lệnh `summary` vào backend cùng một lúc.
  - Ngăn thông báo đang mở: các thay đổi được gom 600ms trước khi tải lại.
- **Kiêm nhiệm:** thông báo của tài khoản kiêm nhiệm vẫn hiện toast nhưng không tính vào hộp thư của tài khoản
  chính, vì API chỉ trả hộp thư của người đăng nhập.
- **Thông báo quản trị** (`thongbao`, room `coquan_sys`): giữ cơ chế modal và localStorage cũ, hiện ở đầu ngăn.
  Các toast nghiệp vụ cũ còn trong localStorage (`type: "toast"`, trước 27/10) bị bỏ qua.

## 5. Server Node (`docs/socketio/index_socketio.js`): sửa 27/10, CẦN redeploy

- `register` dùng `socket.join(user_x)` (room), nên nhiều tab hoặc nhiều máy đều nhận. Trả `fail` (offline) khi
  room rỗng.
- `POST /api/socket/notification` có `body.channel` vẫn được nhận (tương thích FE build cũ).
- Thêm `GET /health` và `GET /api/health`. `api_key` đọc từ `SOCKET_API_KEY`, mặc định `TANDANJSC`.
- Thêm `POST /api/socket-batch`, body `{items:[{channel, data}]}`, tối đa 2.000 mục, giới hạn body JSON 5MB.
  Backend Java gửi một request cho mỗi thông báo thay vì một request cho mỗi người nhận.
- Sửa `/send-notification` gọi `errors.InvalidCredentialsError` (biến không tồn tại). Bỏ các `require` không dùng.
  File chuyển sang UTF-8.

## 6. Giới hạn đã biết

Ba việc còn mở của đợt 2 đã xong ngày 28/10: ghi chéo tenant, job dọn hằng đêm, và báo người phối hợp khi tạo
mới dự thảo. Những giới hạn còn lại:

- **Người nhận không xác định được tenant** thì vẫn ghi ở DB người gửi. Các trường hợp này gồm: đơn vị và tổ tiên
  đều chưa khai báo `linked_tenant_id`; tenant đã khoá; tài khoản chỉ có ở Keycloak.
- **Dòng đã xoá mềm vẫn nằm trong bảng.** Index đều partial nên không ảnh hưởng truy vấn. Nếu sau vài năm cần giảm
  dung lượng thì thêm bước xoá cứng dòng đã xoá mềm quá N ngày (cần người dùng đồng ý, vì trái luật
  "luôn xoá mềm").
- **Cache tenant của người nhận** giữ 10 phút. Nếu đổi `linked_tenant_id` hoặc chuyển đơn vị cho người dùng, tối đa
  10 phút sau mới có hiệu lực với thông báo.

## 7. Test

- `RecipientTenantResolverTest`: mã đơn vị lấy tenant từ tổ tiên; username lấy tenant của người dùng, có cache;
  tra lỗi trả về rỗng.
- `UserNotificationRetentionSchedulerTest`: dọn theo lô và dừng ở lô cuối.
- `RealtimeNotifierTest` (bổ sung 28/10): người nhận ở tenant khác được ghi vào hộp thư tenant đó; tenant không
  xác định hoặc không tồn tại thì ghi ở tenant người gửi; việc gửi chạy trên dispatcher (luồng nền) với
  username đã chụp sẵn.
- `SocketIoRealtimeNotificationGatewayTest` (bổ sung 28/10): một request batch cho mọi người nhận; payload không
  lặp danh sách `users`; server cũ trả 404 thì tự gửi từng người và nhớ 10 phút.
- `RealtimeNotifierTest`:
  - tách / loại trùng người nhận, nhận diện mã đơn vị;
  - ghi hộp thư (một dòng mỗi người nhận) và socket mang id;
  - lỗi ghi hộp thư vẫn đẩy socket;
  - chờ tới sau commit mới chạy;
  - lỗi gateway bị nuốt.
- `UserNotificationInboxUseCaseTest`: chỉ thấy thông báo của mình; hộp thư đơn vị chỉ cho văn thư; tóm tắt theo
  nhóm; không đánh dấu được thông báo của người khác; đọc theo văn bản / theo phân hệ; ẩn.
  Repository giả lập: `InMemoryUserNotificationRepository`.
- `UserNotificationsControllerTest`: envelope và tham số.
- `SocketIoRealtimeNotificationGatewayTest`: gọi HTTP thật tới server giả lập, kiểm tra kênh `user_`/`coquan_` và
  payload.
- Ca nghiệp vụ: `XuLyDuThaoUseCaseTest#execute_chuyenkyduyet_shouldNotifySignerRealtime`,
  `ChuyenXuLyVbdenUseCaseTest#execute_chuyenxuly_shouldNotifyChutriCoordinatorsAndUnitWithoutReceiver`.

## 8. Hiệu năng (rà soát 28/10/2026)

### 8.1 Đã tối ưu

| Điểm | Trước | Sau |
|---|---|---|
| Request nghiệp vụ | Chờ INSERT hộp thư + tra tên người gửi trong `afterCommit` | Chỉ chụp username/tenant rồi trả về; mọi việc chạy trên luồng nền |
| Ghi hộp thư | `saveAll` với UUID tự gán → `merge` → thêm 1 SELECT mỗi dòng | `persist` + flush, INSERT theo lô (`hibernate.jdbc.batch_size=20`) |
| Đẩy socket khi phát hành tới N đơn vị | N request HTTP song song, mỗi payload lặp cả danh sách `users` (≈1MB với 300 người nhận) | 1 request `/socket-batch`, bỏ `users` |
| Tra tenant người nhận | — | Cache 10 phút (tối đa 20.000 khoá) |
| Mã đơn vị của văn thư (mỗi API hộp thư) | Dựng hồ sơ tổ chức (duyệt cây) mỗi lần gọi | Cache 5 phút theo username |
| `summary` | Lấy 5 dòng mới nhất qua `Page`, kèm câu COUNT thừa | `findLatestUnread`, không COUNT |
| Danh sách hộp thư | `lower(recipient) IN (...) ORDER BY created_on LIMIT`: planner quét ngược index `created_on` rồi lọc | Truy vấn riêng từng khoá + trộn trong Java |
| FE | Mỗi thông báo tới lúc ngăn đang mở → tải lại; kết nối lại đồng loạt | Gom 600ms; rải 0–8 giây |

### 8.2 Số đo

Đo trên Postgres dev `h05_01_sokhcn` với 300.000 dòng giả lập (1.000 người dùng + 20 đơn vị, 70% đã đọc, trải
400 ngày), `EXPLAIN ANALYZE`, trong transaction đã ROLLBACK.

| Truy vấn | Kế hoạch | Thời gian |
|---|---|---|
| Danh sách 20 dòng, một khoá (cách mới) | Index Scan `ix_user_notifications_recipient` | 0,09ms |
| Danh sách, người không có thông báo nào | Như trên | 0,03ms |
| Hộp thư đơn vị 15.000 dòng, lấy 20 / 200 dòng | Như trên | 0,11ms / 0,53ms |
| Chưa đọc + lọc phân hệ | Như trên | 0,48ms |
| Danh sách với `IN` 2 khoá (cách cũ) | Quét ngược `ix_user_notifications_created_on` | **32ms**, tăng tuyến tính theo kích thước bảng |
| Đếm chưa đọc / tóm tắt theo nhóm | Bitmap `ix_user_notifications_unread` | 0,13ms / 0,20ms |
| Đếm tổng (phân trang) | Bitmap `ix_user_notifications_recipient` | 0,64ms |
| Đánh dấu đã đọc theo văn bản | Index Scan `ix_user_notifications_document` | 0,37ms |
| Dọn một lô 5.000 dòng | Bitmap `ix_user_notifications_created_on` | ≈110ms |

**Bài học:** với bảng hộp thư, đừng dùng `IN` nhiều khoá kèm `ORDER BY created_on LIMIT`. Khi còn có index trên
`created_on`, Postgres sẽ chọn quét ngược index thời gian. Hãy dùng `=` cho từng khoá.

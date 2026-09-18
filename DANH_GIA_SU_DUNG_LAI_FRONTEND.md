# Đánh giá khả năng tái sử dụng Frontend `ThanhHoaDEV` với Backend Java (`JAVA`)

> **Ngày phân tích**: 15/08/2026
> **Phạm vi**: Đối chiếu Frontend React (`C:\code_projects\HSCV\ThanhHoaDEV`) với Backend Spring Boot (`C:\code_projects\HSCV\JAVA`) để trả lời câu hỏi: **có tái sử dụng được frontend hiện tại khi chuyển sang Java không, và cần làm gì thêm?**
> **Mục đích**: Tài liệu phân tích — **chưa code**.
> **Nguồn tham khảo nội bộ**: `docs/frontend/*`, `docs/migration/*`, `docs/migration-backlog/*`, `docs/gap-analysis/*`, `JAVA/docs/*`, source `JAVA/td-{domain,application,infrastructure,web}`.

---

## Mục lục

1. [Bối cảnh & mục tiêu](#1-bối-cảnh--mục-tiêu)
2. [Tóm tắt hiện trạng hai phía](#2-tóm-tắt-hiện-trạng-hai-phía)
3. [Trả lời ngắn: Frontend có tái sử dụng được ngay không?](#3-trả-lời-ngắn-frontend-có-tái-sử-dụng-được-ngay-không)
4. [Phân tích chi tiết các rào cản](#4-phân-tích-chi-tiết-các-rào-cản)
5. [Điểm thuận lợi — hạ tầng Java đã chuẩn bị tốt cho FE cũ](#5-điểm-thuận-lợi--hạ-tầng-java-đã-chuẩn-bị-tốt-cho-fe-cũ)
6. [Ma trận đối chiếu FE module ↔ Backend Java](#6-ma-trận-đối-chiếu-fe-module--backend-java)
7. [Các phương án kết nối & khuyến nghị](#7-các-phương-án-kết-nối--khuyến-nghị)
8. [Checklist trước khi cắt chuyển (Big-bang cutover)](#8-checklist-trước-khi-cắt-chuyển-big-bang-cutover)
9. [Rủi ro cần quản lý](#9-rủi-ro-cần-quản-lý)
10. [Kết luận](#10-kết-luận)
11. [Phụ lục A — Phương án "đổi endpoint trực tiếp" (không dùng proxy)](#11-phụ-lục-a--phương-án-đổi-endpoint-trực-tiếp-không-dùng-proxy)
12. [Phụ lục B — Kịch bản MVP: đăng nhập được + tài khoản quản trị cao nhất](#12-phụ-lục-b--kịch-bản-mvp-đăng-nhập-được--tài-khoản-quản-trị-cao-nhất)
13. [Phụ lục C — Lộ trình và đánh giá mức việc cho MVP](#13-phụ-lục-c--lộ-trình-va-đánh-giá-mức-việc-cho-mvp)

---

## 1. Bối cảnh & mục tiêu

- Hệ thống hiện tại: Frontend **React 18 + Vite** (`ThanhHoaDEV`) đang gọi backend **HCL Domino/XPages** (`BE_THANHHOA`, route `/xsp/qlvb/*`).
- Kế hoạch: thay Domino bằng **Java Spring Boot** (`JAVA/` — Clean Architecture, 4 module) đã xây dựng một phần API.
- Người dùng hài lòng với các màn hình nghiệp vụ hiện tại → **không viết lại frontend**, chỉ chuyển hạ tầng phía sau.
- Đã có quyết định chiến lược (BL-001, `docs/migration-backlog/03-quyet-dinh-pham-vi-migration.md`):
  1. **Multi-tenant** (schema-per-tenant).
  2. **Big-bang cutover** (chuyển 1 lần, không dual-stack kéo dài).
  3. **Tương thích ngược API** với FE cũ (format response, header, endpoint) khi cắt chuyển.

Tài liệu này đánh giá mức độ khả thi của mục tiêu đó với hiện trạng thực tế mã nguồn.

---

## 2. Tóm tắt hiện trạng hai phía

### 2.1 Frontend (`ThanhHoaDEV`) — hợp đồng mà FE đang "kỳ vọng"

- **Base URL API**: từ biến môi trường `VITE_APP_API_URL` (`.env`) — hiện là `https://qlvbdh.thanhhoa.gov.vn/tdover2/tdoservices.nsf/xsp`, có thể đổi qua `config_public.js` (`window.APP_CONFIG.API_URL`).
- **Tầng gọi API** (`src/utils/baseAPI.js`):
  - Mọi request nối chuỗi: `` `${apiUrl}/${URL}` `` — VD `{API_URL}/qlvb/vbden/GetView`.
  - Hàm `updateURLWithDatabaseType(URL)` tự **suy luận `databasetype` từ đường dẫn trình duyệt** (`/vbden/`, `/vbdi/`, `/hosocv/`, `/duthao/`, `/vbcddh/`, `/chiasetep/`, `/phieutrinh/`, `/tailieucuochop/`, `/dungrieng/` → `tudien`, còn lại → `qlvb`) và append `?databasetype=...` nếu URL chưa có.
  - Cơ chế **fail-over**: FE tự thay `tdoservicesN.nsf` bằng host ngẫu nhiên khỏe mạnh (1–5) khi lỗi 401/404 (cooldown 60s) — chỉ chạy khi URL base có dạng `.nsf`.
  - Xử lý lỗi: đọc `status` là **mã HTTP** (không phải `code` trong body); nếu `message === "Sai token"` → tự refresh token; 400 → `Logout`; 401/404 → retry 3 lần rồi refresh.
  - Trả về `res.data` (toàn bộ body JSON).
- **Xác thực** (`src/app/modules/auth/`):
  1. OIDC/Keycloak (SSO tỉnh) → `access_token`.
  2. Gọi `POST {API_URL}/qlvb/authen` (header `Authorization: Bearer <access_token>`) để **đổi lấy JWT nội bộ `TDtoken`**.
  3. Gọi `GET {API_URL}/qlvb/profile?t=<ts>` để lấy hồ sơ `currentUser` (`access`, `roles` (chuỗi `;`), `menu_top`, `menu_qlvb`, `menu_vbden`, `coquan`, `donvi`, `type`...).
  4. Gọi `GET {API_URL}/qlvb/danhmuc/GetCauHinh?t=<ts>` để lấy cấu hình hệ thống (`system_config`).
  5. Mọi request sau đó gửi kèm header **`TDtoken`** (và/hoặc `Authorization: Bearer`) — thiết lập trong `setup/axios/SetupAxios.ts` + `baseAPI`.
- **LocalStorage keys**: `kt-auth-react-v`, `kt-user-react-v`, `kt-profile-react-v`, `kt-access-react-v`, `system_config`, `service-type`...
- **Realtime**: Socket.IO (`src/app/components/WebSocket/`) subscribe `user_<username>`, `coquan_<...>`, `donvi_<...>`.

### 2.2 Backend Java (`JAVA`) — hiện trạng

- **Spring Boot 3.2 + Java 17**, Maven đa module Clean Architecture: `td-domain`, `td-application`, `td-infrastructure`, `td-web`.
- Hạ tầng: **PostgreSQL + Flyway** (V1.0.1→V1.0.14, JSONB `dynamic_fields`), **Redis**, **Elasticsearch**, **MinIO**, **Keycloak** (OAuth2 Resource Server) + **Tdtoken bridge** (`TdTokenAuthenticationFilter`/`TdTokenService`), **Multi-tenant** schema-per-tenant (`TenantFilter`/`TenantResolver`), SpringDoc/OpenAPI, Actuator, CORS tùy biến.
- Cache nghiệp vụ Redis: `DocumentCacheService`, `CategoryCacheService`.
- ~23 controller sẵn: Documents, Categories, LegacyDanhMuc, ViewQuery, NumberingConfigs, Workflows, SharedCore (Users/Roles/Permissions/Organizations/DataScopes), Vbden, Vbdi, DuThao, HoSoCV, Vbcddh + Swagger helpers.
- Response chuẩn **`ApiResponse<T>`** `{status, code, message, data, properties, total}` (khớp TDMessage Domino).

## 3. Trả lời ngắn: Frontend có tái sử dụng được ngay không?

**Chưa tái sử dụng toàn bộ được ngay** với hiện trạng code, nhưng **hoàn toàn khả thi với một lượng công việc có giới hạn** và đúng hướng dự án đã chọn.

| Khía cạnh | Mức sẵn sàng hiện tại | Ghi chú |
|---|---|---|
| Giao diện/màn hình nghiệp vụ | ✅ **Giữ nguyên** | Không cần viết lại; FE không phụ thuộc kiến trúc backend bên dưới |
| Response envelope | ✅ Đã tương thích | `ApiResponse<T>` khớp `TDMessage` (BL-003) |
| Nhận xác thực `TDtoken` | ✅ Đã xử lý | `TdTokenAuthenticationFilter` (BL-004) |
| Endpoint login / profile / cấu hình | ❌ **Thiếu** | FE gọi `/qlvb/authen`, `/qlvb/profile`, `/qlvb/danhmuc/GetCauHinh` — chưa có controller tương ứng |
| Đường dẫn URL legacy → `/api/v1` | ❌ **Chưa có lớp dịch** | Không có gateway/rewrite/controller bắt prefix legacy |
| Phạm vi API nghiệp vụ | ⚠️ **Một phần** | Mới đủ core VBDen/VBDi/DuThao/HoSoCV/VBCDDH + hạ tầng; nhiều module FE chưa có backend |
| Realtime thông báo (Socket.IO) | ❌ **Thiếu** | Java chưa có server Socket.IO cho FE `useWebSocketNotification` |

=> **Kết luận sơ bộ**: không thể chỉ "đổi `VITE_APP_API_URL` là xong". Cần 3 nhóm công việc bổ sung:

1. **Chốt cơ chế ánh xạ đường dẫn** legacy ↔ `/api/v1` (proxy ở tầng web server là tối ưu).
2. **Bổ sung 3 endpoint lõi của FE**: `qlvb/authen`, `qlvb/profile`, `qlvb/danhmuc/GetCauHinh` (và kiểm tra thêm `qlvb/dsmenu`, `qlvb/getsysconfig`).
3. **Hoàn thiện các module FE chưa có backend** (không thể cắt chuyển nếu thiếu chúng).

Chi tiết từng rào cản và cách xử lý ở các mục dưới.

---

## 4. Phân tích chi tiết các rào cản

### 4.1 Rào cản #1 — Đường dẫn URL: legacy `/xsp/qlvb/*` vs `/api/v1/*`

**Hiện trạng FE**: mọi lời gọi nối vào `{API_URL}/<URL>` với URL dạng `qlvb/...`:

| Lời gọi FE (thường gặp) | Backend Domino | Backend Java (đã có) |
|---|---|---|
| `POST qlvb/vbden/GetView?databasetype=vbden` | `VBdenController.GetView` | `POST /api/v1/vbden/GetView` ✅ |
| `POST qlvb/vbden/ChuyenXuLy` | `ChuyenXuLy` | `POST /api/v1/vbden/ChuyenXuLy` ✅ |
| `POST qlvb/view/query` | `TDDocumentViewController` | `POST /api/v1/{dbType}/query` ✅ |
| `POST qlvb/add` | `DocumentController` | `POST /api/v1/documents/add` ✅ |
| `POST qlvb/danhmuc/CayCoCauToChuc` | `CayCoCauToChuc` | `POST/GET /api/v1/danhmuc/CayCoCauToChuc` ✅ |
| `POST qlvb/authen` | `TDAuthenController` | ❌ chưa có |
| `GET qlvb/profile` | `TDProfileController` | ❌ chưa có |
| `GET qlvb/danhmuc/GetCauHinh` | `GetCauHinh` | ❌ chưa có |

→ FE không biết prefix `/api/v1` và không tự dịch URL. Hiện **không có** cơ chế nào trong mã Java nhận request bắt đầu bằng `/xsp/qlvb/...` hay `/tdoservices.nsf/xsp/...`.

**Cách xử lý** (chi tiết mục 7):
- **(A) Reverse-proxy rewrite** (nginx/HAProxy): `^/xsp/qlvb/(.*)` → `/api/v1/$1`, kèm vài rewrite đặc biệt (`qlvb/add` → `/api/v1/documents/add`, `qlvb/view/query` → theo `dbType`...). FE giữ nguyên 100% code.
- **(B) Sửa tầng `baseAPI.js`** (1 file) để map prefix legacy → prefix mới + vài route đặc biệt. Tác động FE tối thiểu.
- **(C) Java mở thêm controller alias prefix legacy** — không nên: trùng lặp, tốn bảo trì.

> **Tác động**: gần như toàn bộ FE không chạy được nếu chưa chọn phương án — đây là điều kiện tiên quyết.

### 4.2. Thiếu các endpoint "sống" của luồng đăng nhập FE

Luồng khởi động FE (`AuthProvider`) gọi tuần tự 3 endpoint; nếu thiếu → FE không vào được màn hình nghiệp vụ:

1. **`POST {API_URL}/qlvb/authen`** — nhận `Authorization: Bearer <Keycloak access_token>`, phải **phát hành `TDtoken`** (JWT ký bằng secret khớp `app.security.tdtoken.secret`). Java hiện chỉ **verify** TDtoken (BL-004), chưa có nơi phát hành.
2. **`GET {API_URL}/qlvb/profile?t=<ts>`** — trả `UserModel` (phải có `access`, `roles` (chuỗi phân tách `;`), `menu_top`, `menu_qlvb`/`menu_vbden`..., `type`, `coquan`, `donvi`, `username`, `displayName`). Field `menu_*` dùng để gate route trong `PrivateRoutes`. Java chưa có endpoint này.
3. **`GET {API_URL}/qlvb/danhmuc/GetCauHinh?t=`** — trả `SystemConfigModel` (dashboard, giới hạn file...). Chưa có.

> Khi đổi `API_URL` sang Java, các endpoint này phải tồn tại theo đúng hợp đồng cũ — đây là nhóm việc có thể xử lý trước để FE "đăng nhập được" dù chưa có toàn bộ nghiệp vụ. Xem chi tiết hợp đồng tại `docs/frontend/02-auth-state-api.md` + `docs/architecture/03-quy-tac-migration-domino-sang-spring-boot.md`.

### 4.3. Phạm vi API nghiệp vụ: đã có core, còn thiếu nhiều màn FE

| Frontend module | Backend Java hiện có | Trạng thái |
|---|---|---|
| `qlvb` (43 mục, ~50 action danh mục) | `Categories`, `LegacyDanhMuc` (5 action), `SharedCore`, `ViewQuery` | ⚠️ Chỉ phủ phần; hàng chục action `danhmuc.*`, `cauhinhhethong` chưa có |
| `vbden` | 6 endpoint (BL-210/211) | ⚠️ Core có; nhiều action FE gọi chưa có (đã đọc/quan trọng, thu hồi, chuyển...) |
| `vbdi` | 5 endpoint (BL-220/221) | ⚠️ Tương tự |
| `duthao` | 4 endpoint (BL-230) | ⚠️ FE có 17 trang con |
| `hosocv` | 4 endpoint (BL-240) | ⚠️ Basic |
| `vbcddh` | 5 endpoint (BL-250) | ⚠️ Không CRUD đầy đủ ~59 route/view FE |
| `phieutrinhTD`, `lich`, `chiasetep`, `tailieucuochop`, `hosoluutru`, `nhatky*`, `tonghop`, `vanbanphapquy`, `Chat` | ❌ | Chưa có backend |
| `thongbaohethong` | ❌ (FE dùng data tĩnh) | Chi phí thấp |
| `search` | `SearchDocumentsUseCase` (Elasticsearch) | ⚠️ Có hạ tầng, chưa đúng đường FE |

**Kết luận**: để big-bang cutover thành công với FE giữ nguyên phạm vi hiện tại, cần migration đầy đủ cho **toàn bộ module**. Khối lượng còn lại lớn — ước tính trong gap-analysis còn mở cho các gap tồn đọng.

---
### 4.4. Realtime / thông báo (Socket.IO)

- FE kết nối tới `VITE_SOCKET_IO_URL` bằng **socket.io-client**, subscribe `user_<username>`/`coquan_<...>`/`donvi_<...>`, nhận sự kiện `notification`; `ThongBaoProvider` dùng kết quả để hiển thị badge.
- Java hiện **chưa có** server Socket.IO (mới chỉ có thiết kế outbox/Redis/WebSocket trong `PRODUCTION_CUTOVER_RUNBOOK.md`, chưa implement endpoint). Chỉ có `@microsoft/signalr` trong danh sách dependency FE nhưng không dùng.
- **Tác động**: không ảnh hưởng tới khả năng đăng nhập/xử lý cơ bản, nhưng badge thông báo real-time sẽ không cập nhật; nếu nghiệp vụ yêu cầu, cần build server Socket.IO phía Java hoặc giữ server notification hiện có cho tới khi có bản mới.

### 4.5. Xử lý tệp tin / file stream

- FE gọi các hàm `getPDFBlob`, `mofile`, `requestPOSTGetFile("qlvb/vbden/getFile", {id, filename, databasetype, ipserver, dbpath, ...})`, và các route `qlvb/getfile`, `qlvb/getallfilehosocv`, `qlvb/xoatepdinhkem`, `qlvb/removefile`, `qlvb/renamefile`, `qlvb/addfile`...
- Java có `DocumentsController` quản lý file qua MinIO (`/documents/{id}/files`...) nhưng **tên/đường dẫn/format giữ liệu khác nhau:** `getFile` legacy trả **PDF (stream)** được convert từ file office thật; Java cần bổ sung endpoint stream+convert đúng hợp đồng hoặc đi qua proxy rewrite.
- Lưu ý FE cách lưu: khi `databasetype = vbden|duthao|hosocv|...` thì FE tự thêm `_file` (`vbden_file`) và gửi `ipserver`/`dbpath` — Java (PostgreSQL+MinIO) không có khái niệm `ipserver/dbpath`; cần tham chiếu theo trường `database_type`/tenant thay vì reader cũ.
- Các định dạng cần hỗ trợ: PDF preview, convert sang PDF, Word online (`VITE_TDWORD_ONLINE_URL`), ký số (`VITE_SIM_SIGN_*`), OCR (`qlvb/dococr`) — hiện vẫn trỏ tới các dịch vụ ngoài không đổi.

### 4.6. Cơ chế `databasetype` tự động của FE

- `baseAPI.updateURLWithDatabaseType()` tự suy `databasetype` từ path: `/vbden/`→`vbden`, `/dungrieng/`→`tudien`, fallback→`qlvb`. Mọi request POST/GET của FE mang `?databasetype=...`.
- Java đã thiết kế `dbType` path variable trong `ViewQueryController` (`/api/v1/{dbType}/query`) và `DocumentsController` — tuy nhiên cần kiểm chứng các controller nghiệp vụ (Vbden/Vbdi...) có nhận `databasetype` **query param** đúng không (hiện VbdenController chỉ đọc `@RequestBody ViewQueryRequest`; `GetView` không đọc query param `databasetype`). → khi rewrite, phải map `databasetype` param hoặc bỏ qua có chọn lọc.
- Với các module FE có `databasetype` khác 8 module hiện nay (`phieutrinh`, `phieutrinhtd`, `hosoluutru`, `lichct`, `tlch`, `vbcddh`, `chiasetep`...) — phía Java phải hiểu hết để gọi đúng view/thao tác.

### 4.7. Multi-tenant & header

- FE gửi header `tenant: root` (SetupAxios) trong nhiều lời gọi; một số gọi không gửi.
- Java dùng `TenantFilter`/`TenantResolver`, schema-per-tenant (BL-303), default tenant `H05` (đường khác với header `tenant`). Cần xác nhận: tenant được lấy từ **header `tenant`** hay **JWT claim/query** để FE đăng nhập thật không bị 404/403 khi gọi `GetCauHinh`/`profile`.
- `VITE_MADINHDANH` (`H56` Thanh Hóa) / `G13` (dev) hiện chỉ là config FE; khi di Java, tenant mapping phải nhất quán (vd Thanh Hóa → tenant id `H05`/`H56`).

### 4.8. Ứng dụng ngoài & tích hợp

- **Keycloak**: FE dùng Keycloak realm `tdauth` (OIDC). Java cấu hình realm `td-webapi-realm`. Cần thống nhất realm/client khi cutover (FE không đổi được realm trừ khi sửa `.env`).
- Các dịch vụ ngoài khác vẫn dùng chung: ký số (`TDSIM*`, `VITE_SIGNATURE_PROVIDER`), Word online, PDF converter, OCR, email SMTP mới (BL-221), LGSP (`gettokenlgsp`) — cần rà cấu hình đứa một lần khi migration.

---

## 5. Điểm thuận lợi — hạ tầng Java đã chuẩn bị tốt cho FE cũ

Dự án Java đã có những lựa chọn kiến trúc rất đúng đắn để tái sử dụng FE:

1. **Response envelope `ApiResponse<T>` khớp `TDMessage`** (BL-003): `{status, code, message, data, properties, total}` — FE đọc `res.data` và `data.*` không đổi.
2. **Tdtoken bridge** (BL-004): `TdTokenAuthenticationFilter` kiểm tra JWT cũ ký HMAC đúng secret Domino — FE vẫn gửi `TDtoken` như cũ là được; bộ test bảo vệ cả case `TDtoken` (hoa) và ưu tiên `Authorization`.
3. **Bộ test `FeCompatibility*`** (BL-005): 12 test về URL mapping / auth / response format / method pattern — đúng lưới an toàn trước big-bang.
4. **Dynamic Data JSONB + `FieldTypeInferrer`** (BL-101) + **ViewQuery** (BL-102) + **Numbering** (BL-104): tái tạo cơ chế "view + field động kiểu NotesDocument" mà FE phụ thuộc nặng nhất (`GetView`, `getall`, `dynamic_fields`).
5. **Field mapping đã lập tài liệu** (`docs/field-mapping/` 01-VBDen 44 field, 02-VBDi 33 field, 03-User 49 field) — nền cho việc trả đúng key FE cần.
6. **Multi-tenant + Flyway** chuẩn bị sẵn cho nhiều đơn vị.
7. **Đối chiếu với Domino thật** khi xây module (BL-210/211/220/221/230/240/250 đều có section "Evidence") — giảm nguy cơ sai hợp đồng.

---
## 6. Ma trận đối chiếu FE module ↔ Backend Java

> Chi tiết từng module FE đã được phân tích trong `docs/frontend/03..11`; bảng dưới tổng hợp mức tương thích hiện tại.

| Nhóm FE | Đã migrate? | Điểm đấu nối | Mức sẵn sàng FE reuse |
|---|---|---|---|
| Auth & Profile (`authen`, `profile`, `GetCauHinh`) | ❌ | Cần controller mới | 🔴 Chặn login |
| Documents chung / `qlvb/add|get|update|delete` | ✅ (BL-101) | `/api/v1/documents/*` + rewrite | 🟡 Hoạt động khi có proxy |
| View query | ✅ (BL-102) | `/api/v1/{dbType}/query` | 🟡 Cần map `databasetype` |
| Danh mục (CayCoCauToChuc, GetView, LuuDanhMuc, XoaDanhMuc, CapNhatCache) | ✅ (BL-105) | `/api/v1/danhmuc/*` | 🟡 Dùng được sau proxy |
| Danh mục mở rộng (~40 action) | ❌ | — | 🔴 Chặn một phần QLVB |
| VBDen | ✅ (BL-210/211 core) | `/api/v1/vbden/*` | 🟡 Một phần |
| VBDi | ✅ (BL-220/221 core) | `/api/v1/vbdi/*` | 🟡 Một phần |
| DuThao | ✅ (BL-230 core) | `/api/v1/duthao/*` | 🟡 Một phần |
| HoSoCV | ✅ (BL-240 core) | `/api/v1/hosocv/*` | 🟡 Một phần |
| VBCDDH | ✅ (BL-250 limited) | `/api/v1/vbcddh/*` | 🟡 Hạn chế |
| PhieuTrinhTD | ❌ | chưa | 🔴 |
| Lich | ❌ | chưa | 🔴 |
| ChiaSeTep | ❌ | chưa | 🔴 |
| TaiLieuCuocHop | ❌ | chưa | 🔴 |
| HoSoLuuTru | ❌ | chưa (riêng module, có EDIT_ROLES) | 🔴 |
| NhatKyHeThong/TruyCap | ❌ | chưa | 🔴 |
| ThongBaoHeThong | ⚠️ | FE dùng data tĩnh — thấp | 🟢 (thấp) |
| TongHop/GS | ❌ | chưa | 🔴 |
| VanBanPhapQuy | ❌ | chưa | 🔴 |
| Search / Elasticsearch | ⚠️ | hạ tầng có, route chưa khớp | 🟡 |
| Chat | ❌ | chưa | 🔴 |
| Realtime notification | ❌ | cần Socket.IO server | 🟡 |

**Chú giải**: 🔴 chặn cutover nếu FE dùng chức năng đó; 🟡 hoạt động sau lớp rewrite + vài bổ sung; 🟢 sẵn sàng.

---

## 7. Các phương án kết nối & khuyến nghị

### Phương án A — Reverse-proxy rewrite (khuyên dùng cho giai đoạn chuyển đổi)
- Tại nginx/HAProxy đứng trước Java: rewrite `^/(tdover2/tdoservices\.nsf/xsp/)?qlvb/(.*)` → `/api/v1/$2` (kèm map các đường đặc biệt: `qlvb/add` → `/api/v1/documents/add`, `qlvb/view/query` → theo `dbType`...).
- **Ưu điểm**: FE 0 thay đổi; dễ test từng phần với FE thật; không can thiệp build.
- **Nhược điểm**: cần duy trì map rewrite; phải xử lý body lệch (FE gửi JSON/FormData/multipart — Java DTO `@Valid`).
- Kết hợp: bổ sung 3 endpoint lõi (`authen`, `profile`, `GetCauHinh`) để hoàn tất vòng đăng nhập.
- **Điều kiện**: tài liệu hoá bảng map backend ↔ FE trong repo `docs/`.

### Phương án B — Sửa nhẹ tầng kết nối FE (`baseAPI.js` + `config_public.js`)
- Sửa 1 file core: thay `updateURLWithDatabaseType` để map prefix cũ → prefix mới, thêm hàm ánh xạ route đặc biệt (`/qlvb/vbden/XemChiTiet` → `/vbden/XemChiTiet`...).
- **Ưu điểm**: không cần proxy phức tạp; rõ ràng trong code.
- **Nhược điểm**: rời khỏi "FE giữ nguyên 100%"; cần build lại FE; tăng trách nhiệm tầng FE.
- Đây là cải tiến dài hơi (bỏ fail-over `.nsf`, tắt `databasetype` tự suy nếu cần...).

### Phương án C — Java mở alias prefix legacy — *không khuyến khích*
- Controller thêm mapping thứ 2 `/xsp/qlvb/...` → trùng lặp code, tốn bảo trì, lệch OpenAPI.

### Khuyến nghị (kết hợp A ban đầu + B dần dần)
1. **Giai đoạn 1 (chuyển đổi)**: Làm A + bổ sung auth endpoints; mục tiêu: FE đăng nhập được, module đã migrate tương tác trực tiếp.
2. **Giai đoạn 2 (song song migrate module)**: Hoàn tất các module còn thiếu (PhieuTrinh, Lich, ChiaSeTep, HSLT, NhatKy, TongHop, VBPQ...) theo thứ tự đã chốt; mỗi module thêm map rewrite.
3. **Giai đoạn 3 (ổn định)**: Tối ưu FE (tuỳ chọn): bỏ `databasetype` tự suy, gọi thẳng `api/v1`, gỡ fail-over `.nsf` cũ, dọn file backup/`_bk` trước production.

---

## 8. Checklist trước khi cắt chuyển (Big-bang)

Tham khảo `JAVA/docs/PRODUCTION_CUTOVER_RUNBOOK.md`; tập trung:
1. **Chốt mapping URL** và triển khai proxy/rewrite — test toàn bộ đường FE.
2. **Bổ sung & test 3 endpoint lõi**: `authen`, `profile`, `GetCauHinh` (đúng hợp đồng `TDMessage`, field `menu_*`, `roles` chuỗi `;`, system config).
3. **Rà toàn bộ lời gọi FE** theo `docs/frontend/02..11` để sinh danh sách endpoint còn thiếu (mục 4.3) và lỗi lệch.
4. **File & PDF**: bổ sung endpoint stream/convert đúng `getFile` kèm `databasetype_file`; bỏ qua `ipserver/dbpath` cũ; test xem PDF/Word online/ký số.
5. **Realtime**: quyết định deploy Socket.IO tương đương hoặc chấp nhận delay; test `ThongBaoProvider`.
6. **Multi-tenant**: thống nhất tenant Thanh Hóa (H56/H05) với môi trường + `config_public.js`; test 2–3 tenant.
7. **Keycloak realm**: `tdauth` (FE) vs `td-webapi-realm` (Java) — chọn giữ bên, đồng bộ `.env`.
8. **SMTP (BL-221)**: cấu hình relay thật trước khi bật; set `MAIL_SENDING_ENABLED=false` nếu chưa có.
9. **Dữ liệu**: ETL schema map + import lịch sử (chuẩn bị mismatch <1%).
10. **Mở rộng FeCompatibility**: test cho 3 endpoint login + rewrite; integration qua Postgres/Redis thật (staging).

---
## 9. Rủi ro cần quản lý

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Thiếu endpoint `authen`/`profile`/`GetCauHinh` → FE không vào được | Cao | Tạo từ giai đoạn 1; test smoke login |
| URL mapping không đầy đủ → hàng loạt 404 | Cao | Danh mục map từ `docs/migration/03` + `docs/frontend/*`; review đủ trước cutover |
| Module FE chưa có backend (PhieuTrinh, HSLT, Lich...) | Cao | Big-bang phải đợi đủ module; hoặc giảm kích hoạt phạm vi |
| Body/Type lệch (form vs JSON, `dynamic_fields`, hậu tố `_date`/`_aut`) | Trung bình | Có `FieldTypeInferrer`/MapStruct; test với dữ liệu thật |
| Real-time thông báo không hoạt động | Trung bình | Build Socket.IO tương đương hoặc trì hoãn bật |
| Quyền module chưa chuẩn (SEC-01 chưa phủ hết controller) | Trung bình/Cao | Hoàn thiện `@PreAuthorize` thật theo bằng chứng trước go-live |
| `databasetype` tự suy của FE không khớp backend | Trung bình | Định nghĩa bảng map; rà theo module |
| Vòng đời token / refresh khớp chuỗi lỗi `"Sai token"` | Trung bình | Giữ đúng message; duy trì `FeCompatibilityAuthTest` |
| Keycloak client secret để lộ trong `.env` | Thấp | Xoay secret, tách biệt môi trường |

---

## 10. Kết luận

1. **Frontend có thể giữ nguyên và đưa vào production với backend Java**, nhưng không thể "chỉ đổi URL": cần **lớp ánh xạ đường dẫn legacy → `/api/v1`**, **bổ sung 3 endpoint lõi (authen/profile/GetCauHinh)**, **hoàn thiện các module chưa có backend** và đồng bộ hạ tầng (Socket.IO, tenant, keycloak realm, SMTP).
2. **Nền tảng Java đã đi đúng hướng** để tái sử dụng FE: envelope khớp `TDMessage`, Tdtoken bridge, Dynamic data/ViewQuery, test FeCompatibility, field mapping, đối chiếu Domino thật.
3. **Việc tiếp theo ưu tiên**: (1) bổ sung auth/authen/profile/GetCauHinh; (2) chốt chính sách URL (PA A hoặc B); (3) tiếp tục migrate module theo thứ tự backlog.
4. **Không cần viết lại FE ở giai đoạn này**; phạm vi can thiệp FE (nếu chọn PA B) chỉ ở `src/utils/baseAPI.js`/`.env`/`config_public.js` — không đổi màn hình nghiệp vụ.

---

*Tài liệu được sinh từ phân tích mã nguồn & tài liệu sẵn có tại thời điểm 15/08/2026. Số liệu trạng thái backlog lấy từ `docs/migration-backlog/04-migration-status.md` (cập nhật 06/08/2026) — cần đối chiếu lại khi có thay đổi.*
## 11. Phụ lục A — Phương án "đổi endpoint trực tiếp" (không dùng proxy)

### 11.1 Bản chất phương án

Thay vì đặt Reverse-proxy/nginx rewrite để "dịch URL" (PA A mục 7), phương án này thay đổi ngay trong **code/cấu hình hai phía**:

- **Phía FE (tối thiểu)**: đổi base URL API + thêm hàm ánh xạ endpoint legacy → endpoint Java trong tầng kết nối; không đổi màn hình nghiệp vụ.
- **Phía Java (tối thiểu)**: công bố thêm/điều chỉnh vài endpoint cho khớp đúng đường FE gọi (đặc biệt 3 endpoint đăng nhập), giữ nguyên các controller `/api/v1/*` hiện có.
- **Không có** rewrite ở hạ tầng.

Phương án này tương đương "Phương án B" đã giới thiệu ở mục 7 nhưng được nâng cấp thành phương án chủ đạo (không chỉ là tùy chọn dài hơi).

### 11.2 Nơi FE đang nối endpoint — bản đồ thay đổi

| Tầng FE | File | Cách ghép URL hiện tại | Cần sửa cho phương án này |
|---|---|---|---|
| Auth/login | `src/app/modules/auth/core/_requests.ts` | dùng thẳng `import.meta.env.VITE_APP_API_URL` + chuỗi `/qlvb/authen`, `/qlvb/profile`, `/qlvb/danhmuc/GetCauHinh` | Tối thiểu: để nguyên nếu Java mở đường `/qlvb/*`; hoặc đổi 3 hằng số nếu Java chỉ có `/api/v1/...` |
| API business | `src/utils/baseAPI.js` | `` `${apiUrl}/${URL}` `` với `apiUrl=getAPI_URL()` (đọc `VITE_APP_API_URL`) + `updateURLWithDatabaseType` append `?databasetype=` | Sửa 1 chỗ: thêm hàm ánh xạ legacy URL → URL Java; base URL đổi sang BE Java |
| Cấu hình runtime | `config_public.js` | `window.APP_CONFIG.API_URL`/`API_PUBLIC` | Đổi sang base Java (chạy không cần build lại nếu dùng `APP_CONFIG`) |
| Build-time | `.env` / `.env.development` | `VITE_APP_API_URL`, Keycloak… | Đổi khi build production |

### 11.3 Điểm tối quan trọng — 3 endpoint đăng nhập (auth) **không đi qua baseAPI**

- `AuthProvider` gọi trực tiếp bằng `axios`:
  - `POST {VITE_APP_API_URL}/qlvb/authen` (header `Authorization: Bearer <Keycloak access>`)
  - `GET {VITE_APP_API_URL}/qlvb/profile?t=<ts>`
  - `GET {VITE_APP_API_URL}/qlvb/danhmuc/GetCauHinh?t=<ts>`
- Hai ảnh hưởng:
  1. **Đổi base URL chung** sẽ làm 3 URL trên thành `.../api/v1/qlvb/authen` … — vì vậy nếu đặt base mới là `.../api/v1`, thì Java **phải có controller tại đúng đường có `qlvb` segment**: `/api/v1/qlvb/authen`, `/api/v1/qlvb/profile`, `/api/v1/qlvb/danhmuc/GetCauHinh`. (Kịch bản "ít đụng FE nhất": mở prefix `/qlvb/*` cho riêng khối auth chờ chơi.)
  2. **Response body của 3 endpoint này *không* bọc `ApiResponse`**: FE đọc `res.data.token` (flat ở `authen`) và `res.data` (trực tiếp `profile`, `GetCauHinh`) → Java phải trả **raw JSON**, không dùng `BaseController.ok(Result)` (vì `BaseController` gói thêm `{status, data…}`). Đây là ngoại lệ so với chuẩn envelope đang áp dụng — cần commit cho 3 endpoint "legacy" và ghi chú test.

### 11.4 Phương án được kỳ vọng khi chọn "đổi endpoint trực tiếp"

1. Đổi base URL (`.env`/`config_public.js`) → `https://<host-java>/api/v1`.
2. Java thêm controller **`CompatAuthController`** (đường `/api/v1/qlvb/authen`, `/api/v1/qlvb/profile`, `/api/v1/qlvb/danhmuc/GetCauHinh`) — dùng trực tiếp hạ tầng SharedCore/Keycloak; trả raw.
3. `baseAPI.js`: thêm hàm `translateLegacyUrl(url)`:
   - `qlvb/<module>/<action>` khi module ∈ {vbden, vbdi, duthao, hosocv, vbcddh, danhmuc, categories, ...} → `bỏ qlvb/` (VD `qlvb/vbden/GetView` → `vbden/GetView`, `qlvb/add` → `documents/add`, `qlvb/view/query` → `view/query`, `qlvb/profile` → `profile`...).
   - Còn lại tạm giữ nguyên (404 cho tới khi module tương ứng lên server).
4. Java mở rộng dần phần nghiệp vụ; khi mỗi module lên, map trong `translateLegacyUrl` được gỡ dần về sau.

### 11.5 Ưu / nhược so với proxy rewrite (PA A)

| Tiêu chí | PA A (proxy rewrite) | PA "đổi endpoint trực tiếp" |
|---|---|---|
| Sửa FE | 0 (giữ 100% code) | 1-2 file (baseAPI + config) + 3 hằng số auth hoặc giữ `qlvb/` namespace |
| Vận hành tại lớp hạ tầng | cần duy trì nginx/rewrite map | không cần (xóa bớt 1 thành phần) |
| Khả năng phát triển dần | rewrite dần từng route | tương đương (translate mỗi module) |
| Đau đầu `databasetype`/fail-over `.nsf` | vẫn giữ cơ chế cũ FE (có thể gây nhiều) | **dọn được**: đặt base Java không `.nsf` → fail-over tự tắt; có thể sửa `updateURLWithDatabaseType` để bỏ `databasetype` hoặc giao `databasetype` đúng vào request |
| Khả năng tái sử dụng code FE (màn hình) | 100% | 100% (chỉ đụng tầng nối, không đụng UI) |
| Rủi ro vỡ hợp đồng đối chiếu | thấp (giữ đường dùng cũ) | trung bình (phải đúng map mới) |
| Chi phí ban đầu cho mục tiêu "login + admin" | cần dựng proxy + map toàn bộ | chỉ vài file + 3 endpoint → **nhanh hơn để chứng minh MVP** |

=> **Với mục tiêu ban đầu "đăng nhập được + admin", phương án đổi endpoint trực tiếp rẻ và nhanh hơn proxy**; proxy vẫn là lựa chọn tốt nếu muốn không đụng code FE. Có thể kết hợp: dùng đổi-endpoint để làm MVP, giữ proxy cho một số đường legacy kế thừa về sau (không bắt buộc).

---
## 12. Phụ lục B — Kịch bản MVP: đăng nhập được + tài khoản quản trị cao nhất

> Mục tiêu giai đoạn đầu (không cần toàn bộ module): người dùng vào được hệ thống, đăng nhập bằng tài khoản **quản trị tối cao** và rơi vào màn hình quản trị (`/qlvb/cocau/nguoidung`), tương tác được với các màn hình đã có backend (danh mục, văn bản...). **Chỉ đánh giá, chưa code.**

### 12.1 Luồng đăng nhập end-to-end (theo đúng code FE hiện tại)

```
User mở FE (local hoặc prod)
 → oidc-client-ts redirect SSO (Keycloak) → nhận access_token
 → AuthProvider.getTDAuth()
    1. POST  {API_URL}/qlvb/authen      (Authorization: Bearer access_token)
       Java xác thực token Keycloak → phát hành TDtoken (JWT 3 phần) → body { token,... }
    2. GET   {API_URL}/qlvb/profile?t=<ts>   (header TDtoken) → body = UserModel
       userProfile.tdtoken = token; saveProfile / saveUser
    3. GET   {API_URL}/qlvb/danhmuc/GetCauHinh?t=<ts> → SystemConfigModel (lưu system_config)
 → AppRoutes: currentUser.access === "Manager" → redirect /qlvb/cocau/nguoidung
 → menu_top có "Quản trị" → route qlvb bật; màn hình QLVB hiển thị.
```

Điểm chặn nếu thiếu 1 trong 3 gọi: `getTDAuth` logout (toast lỗi) → **bắt buộc cả 3**.

### 12.2 Khối việc Backend Java (mới, tách khỏi module nghiệp vụ)

1. **`POST /qlvb/authen`** (CompatAuthController, đường này `permitAll`):
   - Nhận `Authorization: Bearer <access_token>`.
   - Validate qua Keycloak (như resource server đã có) → lấy `sub`/`username`.
   - Tìm/gắp user trong SharedCore/AppUser (fallback: tạo record mới cho username).
   - **Phát hành `TDtoken`**: JWT ký HMAC bằng `app.security.tdtoken.secret` (tái sử dụng `TdTokenService` — bổ sung hàm sign), claims tối thiểu: `username`, `roles` (chuỗi `;`), `name`, `exp`.
   - Trả body **flat**: `{ token, refreshToken, refreshTokenExpiryTime, isAuthSuccessful: true, isTfaEnabled: false }` (mục 11.3 — không dùng envelope).
   - Lưu ý `isPlainStringToken()` của FE: nếu `data.token` không phải JWT 3 phần → redirect `taikhoanchuakichhoat.html` → **phải trả JWT thật**.
2. **`GET /qlvb/profile?t=...`** (gate TDtoken): trả **raw `UserModel`** (contract mục 12.4); dữ liệu từ SharedCore + roles/menu.
3. **`GET /qlvb/danhmuc/GetCauHinh?t=...`** (gate TDtoken): trả `SystemConfigModel` tối thiểu (`appName`, `version`, `serverIp`...).
4. **Security**: permit `authen`; `profile`/`GetCauHinh` cho phép cả `Authorization` và `TDtoken` (phù hợp test FeCompat).

### 12.3 Khối việc Frontend (tối thiểu)

- **Bắt buộc**: đổi `VITE_APP_API_URL` / `config_public.js` sang base backend Java (VD `https://<host>/api/v1`), giữ segment `qlvb/` trong 3 đường auth (mục 11.3).
- **Nếu muốn gọn sau**: sửa 3 hằng số URL trong `src/app/modules/auth/core/_requests.ts` sang đường riêng (`/auth/login`, `/auth/profile`, `/sysconfig`) — chỉ đụng tầng nối, không đụng UI.
- **Không đổi**: màn hình, routing, các component nghiệp vụ.
- **Build**: đổi `.env` phải rebuild 1 lần cho MVP; `config_public.js` dùng cho phần business khi không muốn rebuild.

---
### 12.4 Hợp đồng dữ liệu "admin tối cao" mà FE cần từ `/profile`

FE dựa vào **UserModel** để:
- **Điều hướng** (`AppRoutes`, `PrivateRoutes`): `access === "Manager"`, `roles?.includes("CVQuanTri")`.
- **Gate route QLVB**: `menu_top` chứa chuỗi `"Quản trị"` (và các chuỗi khác để mở module).
- **Menu side-bar** (`SidebarMenuSystem*`, `ConfigMenu`, `CheckShowMenu`): trường `menu_*` phủ đủ nhãn.

Ví dụ payload tối giản cho "admin toàn quyền":

```json
{
  "id": 1,
  "username": "admin",
  "fullName": "Quản trị hệ thống",
  "coquan": "H56",
  "donvi": "",
  "access": "Manager",
  "roles": "CVQuanTri;QCapNhatVBDen;QCapNhatVBDi;QPhanCong;QPhanPhoi;",
  "menu_top": ["Quản trị", "Văn bản đến", "Văn bản đi", "Dự thảo", "Hồ sơ công việc", "Hồ sơ lưu trữ", "VBCDDH"],
  "menu_qlvb": ["Người dùng", "Sổ văn bản", "...toàn bộ mục cần xem..."],
  "menu_vbden": "...", "menu_vbdi": "...", "menu_duthao": "...",
  "menu_hosocv": "...", "menu_vbcddh": "...", "menu_lichct": "...",
  "menu_chiasetep": "...", "menu_tlch": "...",
  "caplanhdao": "Tỉnh",
  "type": 0,
  "tdtoken": "<jwt>",
  "language": "vi"
}
```

Lưu ý:
- `type` khác `0`/`null` → `ConfigMenu` giành rỗng → đặt `0`.
- `menu_top`/`menu_*` trong FE: `string | undefined`, check bằng `.includes(...)` → trả mảng (JSON array) tương đương.
- `roles` **phải là chuỗi** phân tách `;` (FE `CheckRole` split `;`).
- `id`, `username` bắt buộc (điều kiện `!currentUser?.id` để trigger fetch profile).

### 12.5 Cấu hình Keycloak — hai lựa chọn

| | (1) Giữ nguyên SSO tỉnh `tdauth` hiện có | (2) Keycloak local của dự án Java |
|---|---|---|
| Bối cảnh | realm `tdauth`, client `OIDC` (thật, đang chạy) | realm `td-webapi-realm` (hoặc import/recreate tên khác) |
| FE sửa | gần như 0 (chỉ API_URL) | đổi `VITE_KEYCLOAK_URL/REALM/CLIENTID` (`.env`) |
| Java | đổi `KEYCLOAK_REALM`/issuer trỏ realm tỉnh | điều chỉnh realm/client; tạo user `admin` |
| Rủi ro | đụng SSO đang chạy thật — rủi ro cao, không nên ở MVP | an toàn, chủ động; dễ tạo user |
| Khuyến nghị | — | **(2)**: import realm tên `tdauth` + client `OIDC` + user `admin` |

**Khuyến nghị MVP**: dùng Keycloak docker, import realm đặt tên **`tdauth`** (khớp `VITE_KEYCLOAK_REALM` của FE → không đổi code) hoặc realm khác và sửa `.env`; thêm client `OIDC` (standard flow, redirect `http://localhost:3355/*` và `https://<prod>/.../callback`).

### 12.6 Checklist kiểm chứng MVP (thủ công)

1. Chạy Java + Keycloak/Postgres/Redis/MinIO (docker-compose).
2. `.env` FE: `VITE_APP_API_URL=<backend>/api/v1`, Socket.IO, Keycloak (theo 12.5).
3. `yarn dev` → vào `/` → SSO `admin/admin123`.
4. Backend log thấy đủ `authen` → `profile` → `GetCauHinh`.
5. FE redirect `/qlvb/cocau/nguoidung` (Manager), sidebar đầy đủ.
6. Mở `/qlvb/sovanban`, `/qlvb/dashboard`... không 404/lỗi auth.
7. Logout/refresh: đúng flow, không kẹt `Sai token`.

---
## 13. Phụ lục C — Lộ trình và đánh giá mức việc cho MVP

### 13.1 Thứ tự thực hiện đề xuất (không cần chờ toàn bộ module)

| Bước | Việc | Chi phí ước tính | Ghi chú |
|---|---|---|---|
| 0 | Bật stack (Java + Keycloak/Postgres/Redis/MinIO) + realm `tdauth`/`admin` | S | Sẵn docker-compose + realm-export |
| 1 | Java: `CompatAuthController` (authen/profile/GetCauHinh) + ký TDtoken | M | Dựa hạ tầng có, seed 1-2 bảng auth/SharedCore nếu cần |
| 2 | FE: đổi `.env` (`API_URL`, nếu dùng Keycloak local — KEYCLOAK_*) + (nếu cần) map trong `baseAPI` cho `/dashboard`, `danhmuc`... | S–M | Không đụng màn hình nghiệp vụ |
| 3 | Vận kiểm tra MVP login (12.6) | S | |
| 4 | Từng bước bật module nghiệp vụ + map translate | M×n | Theo backlog module |

(Chi phí: S = vài giờ, M = 1–2 ngày, so với cả một module nghiệp vụ thường M–L.)

### 13.2 Đánh giá rủi ro riêng của phương án "đổi endpoint trực tiếp"

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| `_requests.ts` vẫn đọc env → nếu chỉ đổi `config_public.js` thì login fail | Cao | Thống nhất đổi `.env` + rebuild 1 lần cho MVP; hoặc sửa `_requests.ts` |
| 3 endpoint auth trả envelope thay vì raw → FE không đọc được `data.token` | Cao | Test `FeCompatibilityAuthTest`-style cho 3 endpoint; giữ raw contract |
| `databasetype` tự suy vẫn tiêm vào url không quen | Trung bình | Giữ nguyên (Java đọc/không quan tâm); hoặc bổ xung map chuyên biệt |
| Cơ chế fail-over `.nsf` tàn dư (không nổ khi base không `.nsf`) | Thấp | Để nguyên; cleanup sau |
| Realm Keycloak mới → FE phải đổi KEYCLOAK_* (`tdauth` vs `td-webapi-realm`) | Trung bình | Import realm đặt tên đúng (cứ `tdauth`) hoặc sửa `.env` |
| Tuổi thọ token & renew | Thấp | Giữ AMOUNT `exp`, cấu hình `TDTOKEN_CLOCK_SKEW_SECONDS` |

### 13.3 Tổng kết bổ sung

- Phương án "đổi endpoint trực tiếp" **hoàn toàn phù hợp** cho mục tiêu MVP "đăng nhập + admin": chi phí thấp, không đụng màn hình FE, cần thay đổi có kiểm soát ở tầng nối và thêm 3 endpoint auth.
- Nó **không loại bỏ** nhu cầu mở rộng backend cho các module FE còn thiếu (12.6 → phần A) — với mọi phương án kết nối, việc đó vẫn là khối lượng chính.
- Khi chọn phương án này, khuyến nghị **giữ namespace compatibility** (`/qlvb/*` cho 3 endpoint auth) và **từng bước** chuyển baseAPI sang gọi trực tiếp `/api/v1/*` bằng map trong 1 file — để có thể lùi lại mọi lúc (không phụ thuộc proxy).

---

*Phụ lục A–C được bổ sung ngày 15/08/2026 theo yêu cầu đánh giá "đổi endpoint trực tiếp, mục tiêu ban đầu đăng nhập + tài khoản quản trị cao nhất". Phân tích dựa trên mã hiện tại; tham số chính xác (realm, client, secret) cần đối chiếu với đội Keycloak/vận hành trước khi code.*
---
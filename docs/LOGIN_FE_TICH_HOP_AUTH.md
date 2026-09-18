# Đăng nhập FE (ThanhHoaDEV) ↔ Backend Java — Triển khai & Thay đổi

> **Ngày**: 15/08/2026 — **Trạng thái**: ✅ Đã code xong (đã compile + test) — **phạm vi**: chỉ phần ĐĂNG NHẬP (authen / profile / GetCauHinh), **không dùng reverse proxy, chuyển thẳng endpoint**.
> **Tham chiếu**: `DANH_GIA_SU_DUNG_LAI_FRONTEND.md` — Phụ lục A/B/C.

---

## 1. Mục tiêu & kết quả đạt được

| Mục tiêu (theo tài liệu) | Kết quả |
|---|---|
| FE đăng nhập được bằng luồng hiện tại (Keycloak SSO → `authen` → `profile` → `GetCauHinh`) | ✅ Có (3 endpoint đã có phía Java) |
| Không dùng reverse-proxy, chuyển thẳng FE sang backend Java | ✅ Chỉ thay đổi cấu hình/endpoint, không có proxy |
| Có tài khoản quản trị cao nhất (admin) | ✅ User Keycloak có role `ADMIN` (hoặc username `admin`) → profile trả `access=Manager`, đủ `menu_top`/`menu_*` |
| Không thay đổi kiến trúc Clean Architecture Java hiện có | ✅ Chỉ **thêm** controller compat + 1 method phát hành token; giữ nguyên các module cũ |
| Bổ sung tài liệu chi tiết thay đổi | ✅ Tài liệu này |

---

## 2. Kiến trúc triển khai (không proxy — chuyển thẳng)

```
Trình duyệt FE (localhost:3355)
   │  OIDC (Keycloak realm td-webapi-realm)  →  access_token
   ▼
AuthProvider (FE) — 3 gọi thẳng bằng axios (KHÔNG qua baseAPI)
   1) POST {VITE_APP_API_URL}/qlvb/authen            (Authorization: Bearer <access_token>)
      → Java: QLVBCompatAuthController.authen()  → phát hành Tdtoken (JWT) → body flat { token,... }
   2) GET  {VITE_APP_API_URL}/qlvb/profile                   (Authorization + TDtoken)
      → Java: profile() → UserModel (admin toàn quyền nếu role ADMIN/username admin)
   3) GET  {VITE_APP_API_URL}/qlvb/danhmuc/GetCauHinh        (Authorization + TDtoken)
      → Java: getCauHinh() → SystemConfigModel
   ▼
AppRoutes: access === "Manager" → /qlvb/cocau/nguoidung
```

Điểm mấu chốt:
- FE HIỆN TẠI đọc `import.meta.env.VITE_APP_API_URL` để ghép `/qlvb/authen`, `/qlvb/profile`, `/qlvb/danhmuc/GetCauHinh` → chỉ cần đổi base URL về backend Java là đủ, không đổi code logic login.
- 3 response này trả **JSON body phẳng** (không bọc `ApiResponse`) — đúng cách FE đọc `response.data.token` / `response.data`.

---

## 3. Danh sách file mới / thay đổi

### Backend Java (`C:\code_projects\HSCV\JAVA`)
| File | Loại thay đổi | Nội dung |
|---|---|---|
| `td-infrastructure/.../security/TdTokenService.java` | ✏️ Sửa | Thêm `public String issue(AuthenticatedUser)` và `issue(AuthenticatedUser, Duration)` — ký Tdtoken mới (HMAC HS256) + hằng số `DEFAULT_TOKEN_TTL` (8h) |
| `td-infrastructure/.../security/TdTokenServiceTest.java` | ✏️ Sửa | Thêm 2 test: `issue_thenVerify_shouldRoundTrip`, `issue_shouldThrow_whenDisabled` |
| `td-web/.../controllers/v1/QLVBCompatAuthController.java` | 🆕 Thêm mới | Controller `/qlvb/*` gồm `POST /authen`, `GET /profile`, `GET /danhmuc/GetCauHinh` (chỉ compat login) |
| `keycloak/realm-export.json` | ✏️ Sửa | Client `td-webapi-client`: thêm `redirectUris`/`webOrigins` cho `localhost:3355` và `127.0.0.1:3355` |

### Frontend (`C:\code_projects\HSCV\ThanhHoaDEV`)
| File | Loại thay đổi | Nội dung |
|---|---|---|
| `.env.development` | ✏️ Sửa (config) | `VITE_APP_API_URL=http://localhost:8080`, `VITE_APP_URL=http://localhost:3355`; Keycloak đổi sang Java: `VITE_KEYCLOAK_URL=http://localhost:8180`, `VITE_KEYCLOAK_REALM=td-webapi-realm`, `VITE_KEYCLOAK_CLIENTID=td-webapi-client`, `VITE_KEYCLOAK_CLIENT_SECRET=td-webapi-secret-2024` |
| `src/app/modules/auth/core/Auth.tsx` | ✏️ Sửa | Hàm `login()`: đóng tạm redirect SSO tỉnh (`dangnhap.thanhhoa.gov.vn`); mọi đường đăng nhập → `userManager.signinRedirect()` (Keycloak OIDC). **TẠM THỜI** — muốn quay lại SSO tỉnh thì revert hàm này |

> KHÔNG thay đổi file logic FE (không đụng baseAPI/auth/component). `.env` (production) **KHÔNG sửa** — giữ nguyên `Thanh Hóa` hiện tại.

---
## 4. Chi tiết thay đổi

### 4.1 `TdTokenService.issue(...)` (td-infrastructure)
- Bổ sung 2 overload:
  - `issue(AuthenticatedUser)` — TTL mặc định 8 giờ.
  - `issue(AuthenticatedUser, Duration)` — TTL tùy chỉnh.
- Cấu tạo token: claims `sub`/`username`, `name`, `roles` (chuỗi phân tách `;` — đúng định dạng FE `CheckRole`), `iat`, `exp`; ký HS256 bằng `app.security.tdtoken.secret` (env `TDTOKEN_JWT_SECRET`).
- Lỗi khi tắt tính năng/hết secret → `TdTokenAuthenticationException` (message `"Sai token"` như verify() hiện có).
- Token phát hành tại đây **verify được ngay** bằng `verify()` hiện có (round-trip) — đã có test.

### 4.2 `QLVBCompatAuthController` (controller mới)
Đường `/qlvb` — tách riêng (không phá vỡ controller `/api/v1/*` cũ):
- **`POST /qlvb/authen`** — body tùy chọn (`ipAddress` bỏ dùng); xác thực qua Security (resource server Keycloak, vì FE luôn gửi `Authorization`). Map Jwt → `AuthenticatedUser`; phát hành Tdtoken; trả flat `{ token, refreshToken, refreshTokenExpiryTime, isAuthSuccessful, isTfaEnabled }`.
- **`GET /qlvb/profile`** — đọc auth (Keycloak Jwt hoặc Tdtoken); build `UserModel`; nếu là admin tối cao (`ADMIN` role / username `admin`) → `access=Manager`, `roles=CVQuanTri;...`, `menu_top` đúng các chuỗi `PrivateRoutes` gate, đầy đủ `menu_*`; ngược lại profile thường.
- **`GET /qlvb/danhmuc/GetCauHinh`** — trả `{ appName, version, serverIp, features }`.

### 4.3 `realm-export.json`
- Client `td-webapi-client` mới allowed `redirectUris` & `webOrigins` gồm `http(s)://localhost:3355/*` và `127.0.0.1:3355/*` — để OIDC code flow từ FE dev (cổng 3355) hoạt động.
- Nhắc: Keycloak chỉ auto-import realm launch đầu tiên; nếu đã chạy, cần xóa volume Keycloak rồi up lại (hoặc import tay qua Admin UI).

### 4.4 FE `.env.development`
- Base API → backend Java (`http://localhost:8080`); nếu backend chạy cổng khác (vd `.env` Java đặt `SERVER_PORT=8111`) thì đổi đúng.
- Keycloak → realm/client của Java (`td-webapi-realm` / `td-webapi-client` / `td-webapi-secret-2024`).
- **KHÔNG đổi** `.env` (production).

### 4.5 Test
- `TdTokenServiceTest`: 2 test mới (issue→verify round-trip; disabled⇒throw). Cả 9 test pass.

---

## 5. Hợp đồng API — request/response mẫu

### `POST /qlvb/authen`
Request:
```
Authorization: Bearer <Keycloak access_token>
Content-Type: application/json
{ "ipAddress": "..." }      // có/thiếu đều được
```
Response `200` (body phẳng):
```json
{
  "token": "<Tdtoken JWT 3 phần>",
  "refreshToken": "",
  "refreshTokenExpiryTime": "",
  "isAuthSuccessful": true,
  "isTfaEnabled": false
}
```
> FE đọc `response.data.token`; nếu token không phải JWT 3 phần → FE redirect `taikhoanchuakichhoat.html`.

### `GET /qlvb/profile?t=<ts>`
Request headers: `Authorization` + `TDtoken` (FE gửi cả 2).
Response `200` (body phẳng = UserModel + `tdtoken`):
```json
{
  "id": 1,
  "userId": "admin",
  "username": "admin",
  "fullName": "Quản trị hệ thống",
  "coquan": "H56",
  "donvi": "",
  "access": "Manager",
  "roles": "CVQuanTri;QCapNhatVBDen;...;USER;",
  "menu_top": ["Quản trị", "Văn bản đến", "Văn bản đi", "Văn bản dự thảo", "Hồ sơ công việc", "VB chỉ đạo điều hành", "Hồ sơ lưu trữ", "TL cuộc họp"],
  "menu_qlvb": ["Người dùng", "Vai trò", "Cơ cấu tổ chức", "Danh mục", "Sổ văn bản", "Cấu hình hệ thống", "Báo cáo", "Dữ liệu quá khứ"],
  "menu_vbden": ["Văn bản đến"], "...": "...",
  "caplanhdao": "Tỉnh",
  "type": 0,
  "tdtoken": "<jwt>",
  "language": "vi"
}
```

### `GET /qlvb/danhmuc/GetCauHinh?t=<timestamp>`
Response `200`:
```json
{ "appName": "Hệ thống Quản lý Văn bản và Hồ sơ điện tử", "version": "1.0.0", "serverIp": "localhost", "features": {} }
```

---
## 6. Hướng dẫn chạy & kiểm chứng

### 6.1 Chuẩn bị hạ tầng (docker-compose của dự án)
```powershell
cd C:\code_projects\HSCV\JAVA
# Realm import đã có redirect 3355; nếu Keycloak đã chạy lần trước cần xóa volume để import lại:
docker compose down -v
docker compose up -d
```
- Keycloak: `http://localhost:8180` — realm `td-webapi-realm`, client `td-webapi-client`, script `admin/admin123` (role `ADMIN`+`USER`) từ realm-export.
- PostgreSQL `localhost:5432`, Redis `6379`, MinIO `9000`, Elasticsearch `9200`.

### 6.2 Chạy Backend Java
```powershell
# 1) Set secret ký/phát hành Tdtoken (bắt buộc — nếu không, /authen trả lỗi)
$env:TDTOKEN_JWT_SECRET = "dev-only-secret-0123456789-0123456789"

# 2) Đảm bảo DB tương ứng với môi trường (vd local docker):
#    DATABASE_URL=jdbc:postgresql://localhost:5432/tdwebapi ; username/password = postgres/postgres
#    KEYCLOAK_SERVER_URL=http://localhost:8180

# 3) Build & chạy (cổng mặc định 8080)
mvn -pl td-web -am install -DskipTests -q
mvn -pl td-web spring-boot:run
```
> Nếu chạy bằng `run-dev.ps1` (dùng `JAVA/.env`, cổng `8111`, server 10.10.10.116) → phải đổi `VITE_APP_API_URL` trong FE tương ứng (mục 6.3) và đảm bảo Keycloak điểm đúng realm đã import redirect.

### 6.3 Chạy Frontend (dev)
```powershell
cd C:\code_projects\HSCV\ThanhHoaDEV
yarn install   # nếu chưa có
yarn dev       # port 3355 (cấu hình vite.config.ts strictPort)
```
- `.env.development` đã trỏ: API `http://localhost:8080`, Keycloak `http://localhost:8180` realm `td-webapi-realm` client `td-webapi-client`.
- Mở `http://localhost:3355` → redirect đăng nhập Keycloak → user `admin/admin123`.

### 6.4 Checklist kiểm chứng (manual)
- [ ] Ở màn Keycloak, đăng nhập `admin` → redirect về `http://localhost:3355/callback` thành công (không lỗi `Invalid redirect_uri`).
- [ ] Log backend xuất hiện `Dang nhap thanh cong cho user 'admin'`.
- [ ] FE không toast lỗi "Token không hợp lệ"; chuyển tới `/qlvb/cocau/nguoidung`.
- [ ] DevTools → Network thấy 3 request `200`: `qlvb/authen`, `qlvb/profile`, `qlvb/danhmuc/GetCauHinh`.
- [ ] localStorage có `kt-auth-react-v`, `kt-user-react-v`, `kt-profile-react-v`, `system_config`.
- [ ] Sidebar QLVB hiển thị; các màn chưa có backend (vbden list...) sẽ báo lỗi API — BIẾT TRƯỚC (mục 7).

---

## 7. Giới hạn hiện tại & việc tiếp theo

| Giới hạn | Ghi chú |
|---|---|
| Chỉ có 3 endpoint login ↔ KHÔNG có route compat cho module nghiệp vụ | Sau login, các màn hình gọi `/qlvb/vbden/*`, `/qlvb/view/query`... sẽ 404/500 cho TỚI KHI làm bước "ánh xạ endpoint nghiệp vụ" (Phụ lục A, `translateLegacyUrl` hoặc controller compat từng module) |
| Keycloak realm tên `td-webapi-realm` khác `tdauth` cũ | FE dev giờ dùng realm java; khi quay lại SSO tỉnh chỉ cần revert `.env.development`. Production `.env` chưa đổi |
| Logout local: FE code hard-code URL logout tỉnh | Chỉ áp dụng logout; không nằm trong mục tiêu đăng nhập. Khi chạy local có thể lỗi khi bấm đăng xuất (không ảnh hưởng login) |
| `TDTOKEN_JWT_SECRET` phải cấu hình đúng | Nếu thiếu → `authen` lỗi. Xem 6.2 |
| `profile`/`GetCauHinh` chưa đọc từ DB | MVP: hằng số + lấy roles từ token Keycloak; khi có bảng `app_users`/phân quyền sẽ thay bằng dữ liệu thật |
| FeCompatibility tests chưa covered 3 endpoint này | Nên thêm test slice (next: `QLVBCompatAuthControllerTest`) khi hoàn tất |

Việc tiếp theo đề xuất (ngoài phạm vi lần này): 1) map `/qlvb/*` nghiệp vụ → `/api/v1/*` (từng module), 2) đưa profile DB + phân quyền, 3) mở rộng test FeCompatibility cho `/qlvb` endpoints, 4) thay thế hard-code logout URL.

---

## Cập nhật 19/08/2026 (BL-118, xem `docs/migration-backlog/04-migration-status.md`)

Dòng "`profile`/`GetCauHinh` chưa đọc từ DB" ở mục 7 đã một phần lỗi thời: từ BL-111, `profile()`
(user thường) đã hợp nhất thêm quyền/menu từ Nhóm vai trò (RoleGroup) đọc trực tiếp DB. Tuy nhiên
phát hiện thêm 1 lỗi thật: role Nhóm vai trò đó **chỉ ảnh hưởng phía FE** (`currentUser.roles` cho
`CheckRole()`) — hoàn toàn không liên quan tới role Keycloak thật mà Spring Security dùng cho
`@PreAuthorize("hasAnyRole(...)")` ở tầng backend. Vì `AppUser`/`CreateAppUserUseCase` không
provision tài khoản Keycloak, mọi tài khoản staff chưa được gán TAY role Keycloak `USER` đều bị
`403` trên toàn bộ 14 controller "danh mục quản trị". Đã sửa bằng cách cấp `ROLE_USER` tự động cho
mọi request xác thực thành công (`KeycloakJwtConverter`/`TdTokenAuthenticationFilter`) — chi tiết ở
`docs/migration-backlog/04-migration-status.md` mục BL-118.

---

## Cập nhật 19/08/2026 (2) — dữ liệu tổ chức thật + "Kiêm nhiệm" trong `profile()`

Dòng "`profile`/`GetCauHinh` chưa đọc từ DB" ở mục 7 nay đã HOÀN TOÀN lỗi thời cho phần dữ liệu tổ
chức: `fullName`/`position`/`caplanhdao`/`agency`/`unit`/`department`/`agency_level` (và alias
`coquan`/`donvi`/`phongban`) trước đây lấy từ claim Keycloak hoặc hardcode rỗng/hằng số cố định
(`"H56"`/`"Tỉnh"`) cho MỌI tài khoản — nay tra cứu THẬT qua `AppUser` + đi lên `Organization.parentId`
(`GetUserOrganizationProfileUseCase`, đối chiếu đúng cơ chế BE_THANHHOA thật). Hằng số cũ chỉ còn là
fallback cho ĐÚNG 1 trường hợp: tài khoản `admin` bootstrap không có bản ghi `AppUser` đi kèm.

Đồng thời `profile()` giờ nhúng thêm **`user_other_obj`** — danh sách "Kiêm nhiệm" (mỗi mục là 1 hồ
sơ đầy đủ + `tdtoken` riêng để FE chuyển vai trò ngay, đúng cơ chế Domino thật) khi
`AppUser.parentUserId` có tài khoản con trỏ về tài khoản đang đăng nhập. Chi tiết đầy đủ (đối chiếu
BE_THANHHOA, thiết kế, rủi ro còn lại): `docs/migration-backlog/04-migration-status.md` mục "BL-118
(tiếp)" và `docs/field-mapping/03-user-field-mapping.md`.

---

## 8. Đối chiếu với tài liệu `DANH_GIA_SU_DUNG_LAI_FRONTEND.md`

| Mục trong tài liệu | Trạng thái sau lần code này |
|---|---|
| Phụ lục B — 3 endpoint login | ✅ Đã có: `authen`/`profile`/`GetCauHinh` |
| Phụ lục B — phát hành TDtoken bằng secret tương thích | ✅ `TdTokenService.issue()` (test round-trip) |
| Phụ lục B — admin tối cao (`access=Manager`, menu đủ) | ✅ Theo role ADMIN / username `admin` |
| Phụ lục A — sau khi login, module nghiệp vụ cần lớp translate | ⏳ Chưa thực hiện (ngoài phạm vi) |
| Phụ lục B — Keycloak dùng theo Java đang cấu hình | ✅ Realm `td-webapi-realm` / client `td-webapi-client` |
| Kiểm thử FeCompatibility cho các endpoint login | ⏳ Test mới cho issue có; test HTTP slice để lại (mục 7) |

---

*Tài liệu bổ sung theo yêu cầu: "thực hiện code phần đăng nhập... bổ sung tài liệu chi tiết các mục đã hoàn thành, có thay đổi".*
# Phân quyền người dùng Domino BE — Đối chiếu với tài liệu hiện có & Checklist

> **Ngày**: 19/08/2026 — **Tính chất**: Tài liệu phân tích + đối chiếu, **KHÔNG** thay đổi tài liệu có sẵn, **KHÔNG** code.
>
> **Phạm vi**: Tập trung vào **code phân quyền người dùng** phía backend Domino (`BE_THANHHOA`), có nhấn mạnh cơ chế **tạo Group trong `names.nsf` và gán người dùng vào Group** — vốn chưa được mô tả ở bất kỳ tài liệu nào hiện có. Sau đó đối chiếu với bộ tài liệu Java (`JAVA/docs/*`) + `docs/**` để chỉ ra: tài liệu đã đủ chưa, thiếu gì, cần làm thêm gì (chỉ rõ file code), kèm checklist.

---

## 1. Tóm tắt nhanh (TL;DR)

Hệ thống QLVB cũ (Domino) có **Hai MÔ HÌNH phân quyền SONG SONG**, dễ bị nhầm lẫn:

| # | Mô hình | Nơi lưu | Mục đích | Tình trạng trong tài liệu hiện có |
|---|---|---|---|---|
| A | **Quyền nghiệp vụ** dạng chuỗi `roles` (`QCapNhatVBDen`, `CVQuanTri`...) | Document *Người dùng* (trong `db_catalog.nsf`, field `roles` / `group_roles_id`) → đưa vào claim `roles` của JWT `Tdtoken` | FE `CheckRole()`, logic server `TDRoles.checkRole()`, `roles.contains()` | **Đã có tài liệu** (SEC-01, field-mapping, frontend 02) — nhưng còn thiếu nhiều chi tiết |
| B | **Group ACL trong `names.nsf`** (Form `Group`, field `Members`, `ListName`) → dùng làm entry trong **ACL của từng NSF** | `names.nsf` (Directory) | Cấp **quyền truy nhập database** (đọc/ghi/xoá) theo đơn vị hành chính (Cơ quan/Đơn vị/Phòng ban) + quyền "*xem*" theo module | **KHÔNG có tài liệu nào mô tả** — chính là phần cần làm mới |

> ⚠️ Trọng tâm của tài liệu này là **mô hình B** (Group `names.nsf`) — phần đang thiếu nhất.
> Mô hình A đã được SEC-01 + `docs/field-mapping/03-user-field-mapping.md` + `docs/frontend/02-auth-state-api.md` đề cập.

---

## 2. Đọc code phân quyền người dùng Domino BE (bản đồ file)

Toàn bộ nằm trong `BE_THANHHOA/Code/Java/`:

| File | Vai trò |
|---|---|
| `vn/com/tandan/xsp/views/TDAuthentication.java` | Xác thực + phát hành JWT `Tdtoken` (claim `roles`, `access`...); tạo tài khoản Person (`CreateUserLoginAccount`) |
| `vn/com/tandan/xsp/common/TDRoles.java` | **Trung tâm phân quyền**: tạo Group `names.nsf`, gán/xoá user khỏi Group, gán ACL database, kiểm tra role |
| `vn/com/tandan/xsp/common/TDDatabaseConnection.java` | Tra cứu hồ sơ/quyền người dùng (`getUserInformationAndPermissions`, `getJackSonUserInfo...`, `getGroups`) |
| `vn/com/tandan/authentication/TDTokenObject.java` | POJO claim JWT (định nghĩa field `roles`, `access`, `identifiers`...) |
| `vn/com/tandan/xsp/servlet/AbstractXSPServlet.java` | Chặn request chưa có/ sai `Tdtoken`; gọi `TDAuthentication.VerifyAuthenToken/ReadAuthenToken` |
| `vn/com/tandan/xsp/services/danhmuc/CapNhatNhomACL.java` | Quét toàn bộ cơ cấu tổ chức + người dùng → đồng bộ **Group ACL `names.nsf`** + **ACL database** theo từng đơn vị |
| `vn/com/tandan/xsp/services/danhmuc/CapNhatACLDB.java`, `LuuDanhMuc.java`, `XoaDanhMuc.java` | Gọi `TDRoles.addGroupACL/insertUserToGroup/removeUserFromGroup/addGroup` khi tạo/sửa/xoá cơ quan, đơn vị, người dùng |
| `vn/com/tandan/xsp/services/danhmuc/ImportNguoiDung.java` | Import người dùng hàng loạt; gán `group_roles`/`group_roles_id` |
| `vn/com/tandan/xsp/common/AddRolesToDocument.java`, `vn/com/tandan/xsp/services/duthao/AddRolesToDocument.java` | Row-level Author/Reader trên document (đã được SEC-01 ghi nhận là **gần như không active**) |
| `vn/com/tandan/xsp/common/TDInitializeDatabase.java` | Tạo database + `SetDefaultACL`; cũng dùng `names.nsf` (replicate/updall) |

---

## 3. Mô hình A — Quyền nghiệp vụ dạng chuỗi `roles` (đã có tài liệu một phần)

### 3.1 Nguồn gốc role
- Document *Người dùng* (`db_catalog.nsf`) lưu field `roles` (chuỗi `;QCapNhatVBDen;CVQuanTri;...`) và `group_roles_id`.
- Lúc **đăng nhập / refresh profile**:
  - `TDAuthentication.buildUserToken()` (dòng ~51-85) đọc **trực tiếp** `doc.getItemValueString("roles")` → claim `roles` của JWT.
  - `TDDatabaseConnection.getUserInformationAndPermissions()` (dòng ~628-682) **ưu tiên** `group_roles_id`: nếu có, tải document "Nhóm quyền/vai trò" rồi lấy `roles` của doc đó; ngược lại dùng `roles` gốc. → dùng cho endpoint refresh profile.

### 3.2 Nơi kiểm tra role
- Server Domino: `TDRoles.checkRole(String roles, String roleToCheck)` (dòng 434-444, tách theo `;`); nhiều service gọi `roles.contains("QCapNhatVBDen")` (vd `xsp/services/vbden/ChuyenXuLy.java`, `danhmuc/ChuyenXuLy.java`).
- Server Domino (session): `TDRoles.GetRolesUser(rolename)` / `GetRolesUserinDB(db, rolename)` dựa `database.queryAccessRoles(username)` (dòng 25-54).
- FE: `CheckRole()`, `roles.includes()` — xem `docs/frontend/02-auth-state-api.md` mục 4.

### 3.3 Đã được tài liệu hoá
- `docs/gap-analysis/01-gap-analysis-tong-hop.md` — **SEC-01** (mô hình role QLVB thật, bỏ hướng ACL row-level).
- `docs/migration-backlog/01-migration-backlog-tong-hop.md` — mục SEC-01.
- `docs/field-mapping/03-user-field-mapping.md` — field `roles` (#27), `group_roles` (#28), `group_roles_id` (#29).
- `docs/frontend/02-auth-state-api.md` — phân quyền tầng UI.
- `JAVA/td-infrastructure/.../security/QlvbRoles.java` — hằng số quyền thật (phía Java mới).
- Migration `JAVA/td-web/src/main/resources/db/migration/V1.0.18__Create_Role_Groups_And_Extend_Roles.sql` — mô hình `AppPermission/AppRole/RoleGroup` phía Java.

---

## 4. Mô hình B — Tạo Group trong `names.nsf` & gán người dùng vào Group (MỚI — cần tài liệu)

> Đây là phần **chưa có tài liệu nào** mô tả. Toàn bộ logic nằm trong `TDRoles.java`.

### 4.1 Các hàm cốt lõi (`TDRoles.java`)

| Hàm | Dòng | Mô tả |
|---|---|---|
| `createGroupACL_InNames(dbNames, session, GroupName, GroupDescription)` | 274-300 | **Tạo Document Group trong `names.nsf`**: Form=`"Group"`, Type=`"Group"`, GroupType=`"0"`, `ListName`(canonical), `ListDescription`; `computeWithForm` + `save`; sau đó gọi `addGroupToACLDB` |
| `addGroupToACLDB(session, nameGroup)` | 303-325 | Quét `cocautochuc_by_identifier`/`_other` rồi gọi `addGroupACL` lên **16 database** `/vbden, /vbden_file, /vbden_res, /vbden_so, /vbdi, ... /phieutrinh_res` |
| `addGroup(dbNames, session, GroupName, GroupDescription)` | 56-81 | Kiểm tra Group đã tồn tại qua view `GroupsByListName`; nếu chưa có thì tạo mới |
| `insertUserToGroup(session, dbNames, user, GroupName, GroupDescription, createGroupACL)` | 228-273 | **Gán người dùng vào Group**: thêm username (canonical) vào field `Members`; nếu Group chưa tồn tại (`createGroupACL=true`) thì tạo + set `Members` |
| `removeUserFromGroup(session, dbNames, user, GroupName)` | 395-433 | Xoá username khỏi field `Members` |
| `addGroupACL(db, GroupName, Roles, AccessLevel, CanDeleteDocument)` | 83-121 | Tạo/ex-remove entry trong **ACL của 1 NSF** với GroupName + bật các role (vd `_xemvbden`) |
| `removeGroup / removeGroupACL` | 182-227 | Xoá Group / entry ACL |
| `renameGroup / renameGroupACL` | 122-178 | Đổi tên Group / entry ACL |

### 4.2 Cấu trúc Document Group trong `names.nsf`
- Form: `Group`; Type: `Group`; GroupType: `"0"`.
- `ListName`: tên canonical (vd `CN=H56/O=TANDAN`... hay thường là **mã identifier đơn vị** như `H56/vanthu`, `H56_xemvbden`...).
- `Members`: danh sách canonical username — **chính là "gán người dùng vào group"**.
- `ListDescription`: mô tả (tuỳ chọn).
- View dùng để tra: `GroupsByListName` (tra theo tên — `TDRoles`, `LuuDanhMuc`), `GroupsByMembers` (tra theo member — `TDDatabaseConnection.getGroups`).

### 4.3 Ý nghĩa nghiệp vụ (quy tắc đặt tên Group / ACL)
- **Theo đơn vị hành chính** (Cơ quan / Đơn vị / Phòng ban), dùng **mã identifier** làm tên Group:
  - `CapNhatNhomACL.java` (dòng ~155-247): với mỗi bản ghi cơ cấu tổ chức + người dùng, tính:
    - Group **identifier cơ quan** → `processUserRole(identifierCQ, nameCQ, "", username, true)` (full quyền cập nhật/ghi).
    - Group `identifier + "_vanthu"` → khi user có `QCapNhatVBDen`/`QCapNhatVBDi` (quyền "văn thư").
    - Group `identifier + "_xemvbden"` / `"_xemvbdi"` → khi user có `QXemVBDen`/`QXemVBDi`.
  - Tương tự cấp Đơn vị (`_DV`) và Phòng ban (`_PB`).
- Cùng lúc đó `TDRoles.addGroupACL` thêm **entry ACL** cho từng NSF module (vbden, vbdi, duthao, hosocv, phieutrinh) với:
  - AccessLevel `4` (Editor) cho Group gốc `identifier`.
  - AccessLevel `2` (Reader) cho Group `identifier_xem*`.
- `getRoleSuffix()` (dòng 382-389) ánh xạ tên database → hậu tố `_xemvbden/_xemvbdi/_xemduthao/_xemhosocv/_xemphieutrinh`.

### 4.4 Điểm nối giữa 2 mô hình
- Khi tạo/sửa/xoá **cơ quan / đơn vị / người dùng** (`LuuDanhMuc.java`, `XoaDanhMuc.java`), Domino **đồng thời**:
  1. Cập nhật field `roles`/`group_roles_id` trên document người dùng (mô hình A), **và**
  2. Gọi `TDRoles.*` để tạo/cập nhật Group `names.nsf` + ACL database (mô hình B).

→ **Mô hình A quyết định "user được phép làm gì" ở tầng logic; mô hình B quyết định "user được mở/đọc/ghi database nào" ở tầng ACL Domino.** Khi migrate lên Spring/Keycloak (`JAVA/`), cả hai đều phải được ánh xạ lại riêng.

### 4.5 Quan sát khi đọc code (rủi ro/điểm cần xác nhận)
- `group_roles_id` trong thực tế có thể là **danh sách** (code `TDDatabaseConnection` dòng 652-664 / 778-792 **split theo `;`** và lặp), trong khi migration `V1.0.18__Create_Role_Groups_And_Extend_Roles.sql` (dòng 92-94) lúc đầu thiết kế `AppUser.role_group_id` là **FK số ít** theo giả định "user chỉ thuộc ĐÚNG 1 nhóm" — sau đó (19/08/2026) đã bổ sung bảng N:N `app_user_role_groups` để khớp thực tế N:N. Cần đối chiếu kỹ dữ liệu thật.
- `buildUserToken` (JWT) đọc thẳng `roles` từ doc trong khi `getUserInformationAndPermissions` (profile) ưu tiên `group_roles_id` — 2 nguồn có thể lệch nhau; cần xác nhận luồng FE nào dùng claim JWT, luồng nào dùng profile.

---

## 5. Đánh giá: Tài liệu hiện có đã đủ chưa? Thiếu gì?

### 5.1 Những gì **ĐÃ có** trong tài liệu
- [x] Luồng đăng nhập + JWT `Tdtoken`: `JAVA/docs/LOGIN_FE_TICH_HOP_AUTH.md`, `docs/frontend/02-auth-state-api.md`, `docs/migration/02-phan-tich-abstractxspservlet.md`.
- [x] Cấu trúc document người dùng & claim JWT (`roles`, `access`, ...): `docs/migration/04-du-lieu-notesdocument.md`, `docs/field-mapping/03-user-field-mapping.md`.
- [x] Quyết định bỏ ACL row-level → dùng role-string cho luồng vbden/vbcddh/vbdi (SEC-01): `docs/gap-analysis/01-gap-analysis-tong-hop.md`, `docs/migration-backlog/01-.../05-review...`.
- [x] Mô hình role mới phía Java (`AppPermission`/`AppRole`/`RoleGroup`, seed 34 quyền): migration `V1.0.18`, `docs/architecture/04-quy-tac-crud-api-shared-core.md`.

### 5.2 Những gì **THIẾU** — cần bổ sung

| STT | Chủ đề thiếu | Liên quan code | Mức độ |
|---|---|---|---|
| 1 | **Cơ chế Group trong `names.nsf` + gán user vào Group** (mô hình B): cấu trúc Doc Group (Form/ListName/Members), view `GroupsByListName`/`GroupsByMembers`, các hàm `createGroupACL_InNames/addGroup/insertUserToGroup/removeUserFromGroup` | `TDRoles.java` | **CAO** (chính là yêu cầu của tài liệu này) |
| 2 | **Quy tắc đặt tên Group/ACL** theo đơn vị hành chính + hậu tố `_vanthu`/`_xem*`, và AccessLevel (4/2) trên từng NSF | `CapNhatNhomACL.java`, `CapNhatACLDB.java`, `TDRoles.getRoleSuffix` | CAO |
| 3 | **Đồng bộ 2 mô hình** khi tạo/sửa/xoá đơn vị, người dùng, import hàng loạt | `LuuDanhMuc.java`, `XoaDanhMuc.java`, `ImportNguoiDung.java` | CAO |
| 4 | **Ánh xạ mô hình B (ACL Domino) sang mô hình mới** (Keycloak groups / Spring Security / tenant quota) — hiện chỉ mới ánh xạ mô hình A (role string) | `JAVA/td-infrastructure/security/*`, `keycloak/realm-export.json` | CAO (phục vụ BL-305/big-bang) |
| 5 | **So sánh `group_roles_id` đơn/nhiều + độ lệch nguồn roles (JWT vs profile)** | `TDAuthentication.buildUserToken`, `TDDatabaseConnection.getUserInformationAndPermissions` | TRUNG BÌNH |
| 6 | **Role Keycloak (USER/ADMIN/PRODUCT_MANAGER/BRAND_MANAGER/PLATFORM_ADMIN) vs role QLVB Domino** — không trùng nhau, cần bảng mapping rõ | `keycloak/realm-export.json`, `QlvbRoles.java`, `V1.0.18` | TRUNG BÌNH |
| 7 | **`GetNhomVaiTro` / màn "Nhóm vai trò"** — cách Domino đọc danh sách nhóm vai trò (`limit`, `full_access`) và nút bị ẩn `CapNhatNhomACL` | `services/danhmuc/GetNhomVaiTro.java`, `CapNhatNhomACL.java` | TRUNG BÌNH |
| 8 | **Chi tiết row-level Author/Reader** — 2 bản `AddRolesToDocument` khác nhau (bản `common` có READER, bản `duthao` chỉ AUTHOR & comment Reader) | `AddRolesToDocument.java` (2 bản) | THẤP (SEC-01 đã ghi nhận phần nào) |

---

## 6. Cần làm thêm gì (đề xuất, chưa code) — chỉ rõ file

> Ghi chú: tuỳ theo phạm vi, các việc dưới có thể chỉ là *bổ sung tài liệu* hoặc *bổ sung catalog dữ liệu mẫu cho việc đối chiếu* — tài liệu này **không** chủ trương tự ý sửa code Java/Domino.

1. **Viết tài liệu riêng cho mô hình B** (Group `names.nsf`): mô tả đầy đủ cấu trúc + luồng + đặt tên/ACL — nội dung chính của tài liệu này, đề xuất lưu thành 1 file riêng (vd `docs/architecture/07-names-nsf-group-acl.md`) hoặc gộp vào `docs/migration/`.
2. **Lập bảng ánh xạ 3 mô hình**: Domino Group ACL → Domino role-string (`roles`) → Spring `AppPermission/AppRole/RoleGroup` → Keycloak realm role / group. Nguồn đối chiếu: `TDRoles.java`, `QlvbRoles.java`, `V1.0.18`, `keycloak/realm-export.json`.
3. **Xác nhận dữ liệu thật** cho `group_roles_id` (1 vs nhiều) trước khi chốt mô hình `app_user_role_groups` — nguồn: `TDDatabaseConnection.java` (dòng 652-664, 778-792), `legacyAdminProfile.json`.
4. **Thống nhất nguồn roles**: quyết định claim JWT `roles` lấy từ field `roles` gốc hay từ `group_roles_id` — đối chiếu `TDAuthentication.buildUserToken` vs `getUserInformationAndPermissions`, và xem FE (`docs/frontend/02-auth-state-api.md`) dùng claim hay profile.
5. **Tài liệu hoá `GetNhomVaiTro` + 34 quyền seed** — nguồn: `GetNhomVaiTro.java`, `SQL V1.0.18`, FE `vaitro/nhomvaitro`.
6. **Đối chiếu Keycloak role → QLVB role** cho big-bang (BL-305): ai gán role nào, có tự động provision như `TdTokenAuthenticationFilter` cấp `ROLE_USER` sàn (BL-118) hay không.

---
### 6.1 Đánh giá: các "bổ sung" đề xuất → thay đối code hiện ở hay not? (theo mức dộ)

> **Mục đích**: đánh giá chi caixd bố sung đề xuất ở mục 5.2 + 6 **yêu cầu thay đối code hiện ở** (Domino `BE_THANHHOA` và/abo Java Spring `JAVA/`) hay chỉ *bổ sung tài liệu*. Kết io: **phần lớn são tài li-u-only (KHÔNG thay đối code)**; chỉ 2-3 đề relevant thay đối code khi xây thật.

| Mức | Chi đề bổ sung (đatre 5.2) | Thay đối code **Domino** hiện ở? | Thay đối code **Java/Spring** hiện ở? | Đánh giá |
|---|---|---|---|---|
| **CAO** | 1. Tài liệu Group `names.nsf` + gán user i Group (mô B): `createGroupACL_InNames`, `insertUserToGroup`, `addGroup`, `removeUserFromGroup` | ❌ Không | ❌ Không | Tài liệu-only; logic Domino înt ź không đổi. KHÔNG code. |
| **CAO** | 2. Quy tắc đặt tên Group/ACL (CQ/DV/PB + hậu tố `_vanthu`/`_xem*` + AccessLevel 4/2) | ❌ Không | ❌ Không | Tài liệu-only; quy tắc đặt tên đ/jà miscura thật trong Domino. |
| **CAO** | 3. Đ/g sync 2 mô when CRUD đơn vị/user/import | ❌ Không | ❌ Không | Tài liệu-only; logic Domino đang có (LuuDanhMuc/CapNhatACLDB) jamx vo đối. |
| **CAO** | 4. Ánh xạ mô B (ACL Domino) sang new (Keycloak group/tenant quota) | ❌ Không (tài liệu phase 1) | ⚠️ **CÓ** study khi triuée auć: **code Java/Spring** (Keycloak group, tenant quota/mobility) | Hiện 1 d - annoté tài liệu; code SEULE → BL-এই/big-bang |
| **TRUNG** | 5. So’i `group_roles_id` đơn/nhiều + nguồn roles (JWT vs profile) | ⚠️ CÓ nếu data audit phisine bug (`buildUserToken` vs `getUserInformationAndPermissions`) | ⚠️ CÓ nếu mapping new | Tài liệu now → code dan data-audit (BL-305) |
| **TRUNG** | 6. Role Keycloak ↔ role QLVB (mappingしている) | ❌ Không | ⚠️ CÓ nếu mapping đưa thật trong Java | Mapping ở tài liệu now; code dan khi quyết đ tim ánh xạ |
| **TRUNG** | 7. `GetNhomVaiTro` + 34 seed + màn "Nhóm vai trò" | ❌ Không | ❌ Không (34 đ ถải ở SQL V1.0.18) | Tài liệu + xác nhận seed; KHÔNG code mới & zi |
| **THẤP** | 8. Row-level Author/Reader (2 bản `AddRolesToDocument`) | ❌ Không | ❌ Không | SEC-01 confirm không active; tài liệu only |

### 6.1.1 Concret — bổ sung → code thay đối, theo nơi bổ

| Nơi bổ sung (m a) | Code thay đối? | Nơi dạ khi |
|---|---|---|
| 4.1 Group `names.nsf` (mô B) | Tài liệu-only; KHÔNG code | Now (tài liệu này) |
| 4.3 Quy tắc ACL | Tài liệu-only | Now |
| 4.4 Sync 2 mô | Tài liệu-only | Now |
| 5.2-#4 ánh xạ → Keycloak | ⚠️ Code Java/Spring (security/tenant) | Only dan BL-305/big-bang |
| 5.2-#5 group_id/high + JWT vs profile | ⚠️ Code Domino (bug) HOẶC Java (mapping) | Sau data audit |
| 5.2-#6 Keycloak ↔ QLVB | ⚠️ Code Java nếu mapping ENE | khi quye hutit |
| 5.2-#7 seed 34 | Không (deja in SQL) | Xác nhân |
| 5.2-#8 Author/Reader | Không | Tài liệu |

### 6.1.2 Giới dọc
- **Bổ sung = h0 surly KHÔNG thay đối code đůnow:** #1, #2, #3, #7, #8 → tài liệu-only.
- **Có thay đối code (đan khi trieke x../fiát)** : #4 (Java/Spring tenant/quyota), #5 (Domino hourity mapping), #6 (Java mapping).
- 🚨 Tran émig bỗ Số (tài liệuOnly) → KHÔNG đổi tá THỈ. Lă vi chí → gjá c sỉ m code Domino/Java when secuting phổ (BL-305/big-bang) — cần quyết tá bench весе.

---

## 7. CheckList

### 7.1 Đọc/rà soát code
- [ ] Đọc `TDRoles.java` toàn bộ (56-544): `addGroup`, `createGroupACL_InNames`, `addGroupToACLDB`, `insertUserToGroup`, `removeUserFromGroup`, `addGroupACL`, `addUser`, `checkRole`.
- [ ] Đọc `CapNhatNhomACL.java` (toàn bộ) — quy tắc group theo CQ/DV/PB + `_vanthu`/`_xem*`.
- [ ] Đọc `CapNhatACLDB.java`, `LuuDanhMuc.java` (đoạn gọi `TDRoles.*` ~dòng 700-1100 & 1400-1440), `XoaDanhMuc.java`.
- [ ] Đọc `TDDatabaseConnection.getGroups` (347-375), `getUserInformationAndPermissions` (628-682), phần `group_roles_id` (652-664, 778-866).
- [ ] Đọc 2 bản `AddRolesToDocument.java` (common: có Reader; `duthao`: chỉ Author + comment Reader).
- [ ] Đọc `TDAuthentication.buildUserToken` / `CreateUserLoginAccount` / `TDRoles.addUser` (tạo Person `names.nsf`).
- [ ] Đọc phía Java mới: `QlvbRoles.java`, `TdTokenService.java`, `TdTokenAuthenticationFilter.java`, `KeycloakJwtConverter`, `GetUserRoleGroupConfigUseCase.java`, `SQL V1.0.18`, `keycloak/realm-export.json`.

### 7.2 Tài liệu hiện có (đã đối chiếu lần này)
- [ ] `JAVA/docs/LOGIN_FE_TICH_HOP_AUTH.md` — đã đối chiếu luồng login.
- [ ] `docs/gap-analysis/01-gap-analysis-tong-hop.md` — SEC-01.
- [ ] `docs/migration-backlog/01-migration-backlog-tong-hop.md`, `04-migration-status.md` — SEC-01/BL-111/BL-118.
- [ ] `docs/field-mapping/03-user-field-mapping.md` — field user/roles.
- [ ] `docs/frontend/01-kien-truc-routing-phan-quyen.md`, `02-auth-state-api.md` — route/UI permission.
- [ ] `docs/migration/02-phan-tich-abstractxspservlet.md`, `04-du-lieu-notesdocument.md` — luồng auth/notes.
- [ ] `docs/architecture/04-quy-tac-crud-api-shared-core.md`, `05-shared-schema-multi-tenant.md`, `06-database-per-tenant-multi-tenant.md` — mô hình role mới.

### 7.3 Khoảng trống cần bổ sung (mới)
- [ ] File tài liệu Group `names.nsf` + gán user vào group (đây = file này, mục 4).
- [ ] Bảng ánh xạ Domino Group ACL ↔ role-string ↔ Spring `App*` ↔ Keycloak.
- [ ] Xác nhận `group_roles_id` đơn/nhiều + nguồn roles (JWT vs profile).
- [ ] Đối chiếu Keycloak realm roles ↔ QLVB roles cho big-bang (BL-305).
- [ ] Tài liệu hoá `GetNhomVaiTro` + 34 quyền seed + màn "Nhóm vai trò".

### 7.4 Không làm (giữ nguyên)
- [ ] KHÔNG sửa các tài liệu `.md` đang có ngoài việc thêm file mới này.
- [ ] KHÔNG sửa code `BE_THANHHOA` (Domino) hay `JAVA` (Spring) trong phạm vi nhiệm vụ này.

---

## 8. Phụ lục — Các file code tham chiếu chính (đường dẫn tuyệt đối)

- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\common\TDRoles.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\services\danhmuc\CapNhatNhomACL.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\services\danhmuc\CapNhatACLDB.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\services\danhmuc\LuuDanhMuc.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\services\danhmuc\GetNhomVaiTro.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\services\danhmuc\ImportNguoiDung.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\common\TDDatabaseConnection.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\views\TDAuthentication.java`
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\xsp\common\AddRolesToDocument.java` (và bản `services/duthao/AddRolesToDocument.java`)
- `C:\code_projects\HSCV\BE_THANHHOA\Code\Java\vn\com\tandan\authentication\TDTokenObject.java`
- `C:\code_projects\HSCV\JAVA\td-infrastructure\src\main\java\com\td\infrastructure\security\QlvbRoles.java`
- `C:\code_projects\HSCV\JAVA\td-application\src\main\java\com\td\application\sharedcore\GetUserRoleGroupConfigUseCase.java`
- `C:\code_projects\HSCV\JAVA\td-web\src\main\resources\db\migration\V1.0.18__Create_Role_Groups_And_Extend_Roles.sql`
- `C:\code_projects\HSCV\JAVA\keycloak\realm-export.json`
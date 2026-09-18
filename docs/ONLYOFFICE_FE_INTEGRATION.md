# OnlyOffice — Component soạn thảo tài liệu Frontend (ThanhHoaDEV)

> **Ngày**: 13/09/2026 — **Trạng thái**: ✅ Component FE đã code xong, ✅ đã trỏ tới Document Server dev thật (`10.10.10.107:8081`) — **phạm vi**: CHỈ 1 isolated React component nhúng OnlyOffice Document Server ở Frontend, theo đúng yêu cầu tại `JAVA/docs/promt/onlyoffice.txt`.
> **Cập nhật 15/09/2026**: Backend (build/ký JWT config, endpoint `callbackUrl`) + wiring vào màn
> **dự thảo** (icon riêng "Sửa trực tuyến (OnlyOffice - thử nghiệm)", song song "Sửa tệp" cũ qua
> BTEAdapter) đã hoàn thành — xem `JAVA/docs/ONLYOFFICE_BE_INTEGRATION.md`. Mục 4 dưới đây giữ
> nguyên làm lịch sử, KHÔNG còn phản ánh đúng phạm vi hiện tại.

---

## 1. Yêu cầu gốc

Trích `JAVA/docs/promt/onlyoffice.txt`: thiết kế 1 **isolated React component** cho trình chỉnh sửa tài liệu, **zero side-effects**, đáp ứng:

- **Architecture & Security**: externalize server endpoint qua `env`; nhận signed JWT payload qua props.
- **Fault Tolerance**: bọc `ErrorBoundary`, graceful degradation khi lỗi kết nối.
- **Resource Management**: teardown instance/event listeners lúc unmount, tránh memory leak.
- **UX & Performance**: 60fps mượt, không CLS/giật; skeleton lúc initialization; layout fluid 100% cho cả Full-page và Modal.

Toàn bộ các tiêu chí trên được hiện thực trong 1 thư mục component duy nhất — không động vào routing, store, hay bất kỳ trang nghiệp vụ nào đang chạy.

## 2. Kiến trúc

```
Trang nghiệp vụ (chưa có — sẽ tự truyền props khi làm)
   │  config: OnlyOfficeConfig  (document.url, token đã ký JWT, callbackUrl, ...)
   ▼
<OnlyOfficeEditor config={config} mode="full|modal" onReady={...} onError={...} />
   │  (bọc sẵn OnlyOfficeEditorBoundary)
   ▼
OnlyOfficeEditorCore
   │  loadOnlyOfficeScript(documentServerUrl)  →  <script src="{docserver}/web-apps/apps/api/documents/api.js">
   ▼
window.DocsAPI.DocEditor(placeholderId, config)  →  iframe Document Server
```

Điểm mấu chốt:
- Component **không tự tạo/ký JWT** — nhận nguyên `config.token` đã ký sẵn qua props, đúng yêu cầu "nhận signed JWT payload qua props".
- Địa chỉ Document Server externalize qua `import.meta.env.VITE_ONLYOFFICE_DOCSERVER_URL`, có thể override qua prop `documentServerUrl`.
- Không thêm dependency npm mới — dùng đúng cách tích hợp chính thức của OnlyOffice (script `api.js` do chính Document Server phục vụ).

## 3. Danh sách file mới

### Frontend (`C:\code_projects\HSCV\ThanhHoaDEV`)

| File | Loại | Nội dung |
|---|---|---|
| `src/app/components/OnlyOfficeEditor/index.tsx` | 🆕 | Entry point — export mặc định, đã bọc `OnlyOfficeEditorBoundary` |
| `src/app/components/OnlyOfficeEditor/OnlyOfficeEditor.tsx` | 🆕 | Component lõi: load script (cache), khởi tạo/destroy `DocsAPI.DocEditor`, skeleton + fallback lỗi + timeout + retry |
| `src/app/components/OnlyOfficeEditor/OnlyOfficeEditorBoundary.tsx` | 🆕 | ErrorBoundary class component (React lifecycle `getDerivedStateFromError`/`componentDidCatch`) |
| `src/app/components/OnlyOfficeEditor/loadOnlyOfficeScript.ts` | 🆕 | Loader script `api.js`, cache theo URL ở module-level, dedup khi nhiều editor mount |
| `src/app/components/OnlyOfficeEditor/types.ts` | 🆕 | Kiểu TypeScript cho `OnlyOfficeConfig`/props (theo config gốc OnlyOffice) |
| `src/app/components/OnlyOfficeEditor/OnlyOfficeEditor.scss` | 🆕 | Skeleton shimmer + layout fluid, scope riêng `td-onlyoffice-*` |
| `src/app/components/OnlyOfficeEditor/README.md` | 🆕 | Hướng dẫn dùng chi tiết (props, ví dụ Full-page/Modal, bảo mật JWT, troubleshooting) |
| `.env` | ✏️ Sửa (chỉ thêm dòng) | Thêm `VITE_ONLYOFFICE_DOCSERVER_URL=http://10.10.10.107:8081` |
| `.env.development` | ✏️ Sửa (chỉ thêm dòng) | Thêm `VITE_ONLYOFFICE_DOCSERVER_URL=http://10.10.10.107:8081` |

**Không sửa** bất kỳ file nào khác — không đổi routing, không đổi component/module nghiệp vụ đang hoạt động (vbden/vbdi/duthao/hosocv/vbcddh...), không thêm package vào `package.json`.

### Backend Java (`C:\code_projects\HSCV\JAVA`)

| File | Loại | Nội dung |
|---|---|---|
| `.env` | ✏️ Sửa (chỉ thêm dòng, file đã `.gitignore` — không lên git) | Thêm section `ONLYOFFICE_DOCSERVER_URL` / `ONLYOFFICE_JWT_ENABLED` / `ONLYOFFICE_JWT_SECRET=ThayDoiMatKhauBaoMatNay123` / `ONLYOFFICE_JWT_HEADER` / `ONLYOFFICE_JWT_IN_BODY` — **chỉ lưu tạm, chưa có code nào đọc** |
| `.env.example` | ✏️ Sửa (file có lên git) | Thêm section tương ứng nhưng dùng placeholder (`change-this-secret`), không chứa secret thật |

Không có thay đổi nào ở code Java (controller/service/pom...) — nằm ngoài phạm vi yêu cầu lần này (xem mục 4).

## 4. Phạm vi CHƯA làm (rõ ràng để tránh hiểu nhầm)

Theo đúng "tránh làm ngoài phạm vi", các phần sau **cố ý chưa triển khai**:

1. **OnlyOffice Document Server** — đã có server dev thật chạy độc lập tại `10.10.10.107:8081` (JWT bật, secret `ONLYOFFICE_JWT_SECRET` lưu ở `JAVA/.env`), KHÔNG nằm trong `docker-compose.yml` của repo (hạ tầng dev quản lý riêng, cùng host đang chạy MinIO/RabbitMQ). Còn thiếu: cấu hình tương đương cho production.
2. **Backend build + ký `OnlyOfficeConfig`** — chưa có controller/service nào ở `td-web`/`td-application` build payload (`document.url`, `document.key`, `token` ký HS256, `editorConfig.callbackUrl`...).
3. **Endpoint `callbackUrl`** — nơi Document Server gọi ngược lại để lưu file khi người dùng Save/đóng tài liệu — chưa có.
4. **Wiring vào màn hình nghiệp vụ** — chưa có trang nào (dự thảo, văn bản đến/đi...) thực sự render `<OnlyOfficeEditor />`. Component hiện là "component dùng chung" độc lập, sẵn sàng để 1 trang nghiệp vụ cụ thể import và truyền `config` khi có yêu cầu tiếp theo.

Khi làm tiếp các phần trên, thứ tự khuyến nghị: (1) dựng Document Server + JWT secret → (2) Backend endpoint build/ký config + `callbackUrl` → (3) 1 trang nghiệp vụ cụ thể import `OnlyOfficeEditor`, truyền `config` lấy từ (2).

## 5. Kiểm tra không phá vỡ chức năng hiện có

- Chỉ thêm file mới trong 1 thư mục component riêng (`OnlyOfficeEditor/`) — không sửa file nào của module nghiệp vụ khác.
- Các dòng thêm vào `.env`/`.env.development` (FE) và `.env`/`.env.example` (JAVA) đều là biến MỚI hoàn toàn (không đổi giá trị biến cũ nào) — không ảnh hưởng cấu hình hiện tại vì chưa nơi nào trong code Java đọc các biến `ONLYOFFICE_*`, và chưa trang FE nào gọi `OnlyOfficeEditor`.
- `ONLYOFFICE_JWT_SECRET` thật chỉ nằm trong `JAVA/.env` (đã `.gitignore`, không lên git) — `.env.example` chỉ có placeholder, và **không** biến `VITE_*` nào của FE chứa secret này (tránh lộ ra bundle JS công khai).
- Không thêm dependency mới vào `package.json`/`yarn.lock` — không có rủi ro xung đột version khi build.
- Style scope riêng bằng prefix class `td-onlyoffice-*` — không đè CSS toàn cục.

Xem chi tiết cách dùng, props, ví dụ và troubleshooting tại
`ThanhHoaDEV/src/app/components/OnlyOfficeEditor/README.md`.

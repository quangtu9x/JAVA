# Template thực chiến: tạo feature Department theo pattern Categories

Tài liệu này là mẫu thao tác nhanh khi muốn nhân một feature mới từ `categories`.

Checklist ngắn: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Hướng dẫn chi tiết: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

## 1. Mục tiêu ví dụ

Ví dụ này giả sử cần tạo feature `departments` với các yêu cầu:

- Quản lý phòng ban
- Có mã `code`
- Có tên `name`
- Có mô tả `description`
- Có cha/con để tạo cây phòng ban
- Có `sortOrder`
- Có `isActive`
- Có soft delete
- Có cache detail và list

Nếu feature thực tế của anh không có hierarchy hoặc không có cache thì bỏ các phần đó đi.

## 2. Mapping tên từ Categories sang Department

- `Category` -> `Department`
- `categories` -> `departments`
- `/api/v1/categories` -> `/api/v1/departments`
- `CategoryDto` -> `DepartmentDto`
- `CreateCategoryUseCase` -> `CreateDepartmentUseCase`
- `SearchCategoriesRequest` -> `SearchDepartmentsRequest`
- `CategoriesController` -> `DepartmentsController`
- `CategoryCacheService` -> `DepartmentCacheService`
- `categories:by-id` -> `departments:by-id`
- `categories:list` -> `departments:list`

## 3. Danh sách file cần tạo hoặc sửa

### A. Database

Tạo mới:

- `td-web/src/main/resources/db/migration/V1.0.X__Create_Departments_Table.sql`

### B. Domain

Tạo mới:

- `td-domain/src/main/java/com/td/domain/departments/Department.java`

### C. Application

Tạo mới:

- `td-application/src/main/java/com/td/application/departments/DepartmentDto.java`
- `td-application/src/main/java/com/td/application/departments/DepartmentDtoMapper.java`
- `td-application/src/main/java/com/td/application/departments/DepartmentRepository.java`
- `td-application/src/main/java/com/td/application/departments/CreateDepartmentRequest.java`
- `td-application/src/main/java/com/td/application/departments/UpdateDepartmentRequest.java`
- `td-application/src/main/java/com/td/application/departments/SearchDepartmentsRequest.java`
- `td-application/src/main/java/com/td/application/departments/DepartmentCacheStatsDto.java`
- `td-application/src/main/java/com/td/application/departments/DepartmentListCacheEntry.java`
- `td-application/src/main/java/com/td/application/departments/DepartmentCacheService.java`
- `td-application/src/main/java/com/td/application/departments/CreateDepartmentUseCase.java`
- `td-application/src/main/java/com/td/application/departments/GetDepartmentUseCase.java`
- `td-application/src/main/java/com/td/application/departments/UpdateDepartmentUseCase.java`
- `td-application/src/main/java/com/td/application/departments/DeleteDepartmentUseCase.java`
- `td-application/src/main/java/com/td/application/departments/SearchDepartmentsUseCase.java`

### D. Infrastructure

Tạo mới:

- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/DepartmentJpaRepository.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresDepartmentRepository.java`

### E. Web

Tạo mới:

- `td-web/src/main/java/com/td/web/controllers/v1/DepartmentsController.java`

Sửa:

- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`

## 4. Khung schema mẫu

```sql
CREATE TABLE departments (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    parent_id        UUID         REFERENCES departments(id),
    level            INT          NOT NULL DEFAULT 0,
    full_path        TEXT         NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE UNIQUE INDEX UX_departments_code ON departments(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_departments_parent_id ON departments(parent_id);
CREATE INDEX IX_departments_level ON departments(level);
CREATE INDEX IX_departments_is_active ON departments(is_active);
CREATE INDEX IX_departments_sort_order ON departments(sort_order);
```

## 5. Khung endpoint mẫu

- `GET /api/v1/departments`
- `POST /api/v1/departments/search`
- `GET /api/v1/departments/{id}`
- `POST /api/v1/departments`
- `PUT /api/v1/departments/{id}`
- `DELETE /api/v1/departments/{id}`
- `GET /api/v1/departments/cache/stats`

## 6. Logic nên giữ nguyên từ Categories

Nếu business tương tự categories, có thể giữ nguyên các rule sau và đổi tên feature:

- Chuẩn hóa `code`: trim -> NFC normalize -> uppercase -> thay khoảng trắng bằng `_`
- Chuẩn hóa text input qua `TextNormalizer.normalizeAndSanitize(...)`
- Nếu có `parentId`, tự tính `level` và `fullPath`
- Khi `create`, xóa list cache
- Khi `update`, xóa by-id cache và xóa list cache
- Khi `delete`, xóa by-id cache và xóa list cache
- Trả `X-Cache: MISS/HIT` cho endpoint detail và list/search

## 7. Chỗ thường phải sửa khác đi so với Categories

Các phần không được copy nguyên xi mà phải soát lại:

- Tên bảng
- Tên package
- Tên route
- Message lỗi tiếng Việt
- Role `@PreAuthorize`
- Field nghiệp vụ thật sự của phòng ban
- Rule cha/con nếu feature không hỗ trợ hierarchy
- TTL cache nếu dữ liệu thay đổi thường xuyên hơn

## 8. Checklist copy nhanh

- [ ] Copy package `categories` sang `departments`
- [ ] Rename class từ `Category` sang `Department`
- [ ] Rename route từ `/api/v1/categories` sang `/api/v1/departments`
- [ ] Rename cache region từ `categories:*` sang `departments:*`
- [ ] Soát lại field và message lỗi
- [ ] Soát lại role
- [ ] Thêm migration đúng tên bảng `departments`
- [ ] Cập nhật `RedisCacheConfig`
- [ ] `mvn install -DskipTests`
- [ ] Chạy app và verify runtime

## 9. Bộ lệnh tìm và thay nhanh

```bash
rg --files td-domain td-application td-infrastructure td-web | rg "Category|categories"
rg -n "Category|categories|/api/v1/categories|categories:" td-domain td-application td-infrastructure td-web
```

Sau khi copy, tìm lại toàn bộ `Category` hoặc `categories` còn sót rồi thay đúng ngữ cảnh.
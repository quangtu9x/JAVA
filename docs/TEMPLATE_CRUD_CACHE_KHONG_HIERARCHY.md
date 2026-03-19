# Template thực chiến: feature CRUD + cache không có hierarchy

Tài liệu này dùng khi muốn tạo một feature mới có CRUD + cache nhưng không có cây cha/con như `categories`.

Index tài liệu: [INDEX_TAI_LIEU_API.md](INDEX_TAI_LIEU_API.md)

Checklist tổng: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Checklist sửa nhanh: [CHECKLIST_SUA_API_NHANH.md](CHECKLIST_SUA_API_NHANH.md)

Template có hierarchy: [TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md](TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md)

## 1. Khi nào dùng template này

Dùng cho các feature kiểu:

- document type
- customer group
- partner type
- expense category đơn cấp
- source code master data

Đặc điểm:

- Có bảng riêng
- Có `code`, `name`, `description` hoặc field tương tự
- Có `isActive`
- Có soft delete
- Có list/search/detail/create/update/delete
- Có cache detail và list
- Không có `parentId`, `level`, `fullPath`

## 2. Mapping mẫu từ Categories sang feature không hierarchy

Ví dụ tạo feature `DocumentType`:

- `Category` -> `DocumentType`
- `categories` -> `document-types` hoặc `documentTypes` tùy route convention được chọn
- `/api/v1/categories` -> `/api/v1/document-types`
- `CategoryDto` -> `DocumentTypeDto`
- `CreateCategoryUseCase` -> `CreateDocumentTypeUseCase`
- `SearchCategoriesRequest` -> `SearchDocumentTypesRequest`
- `CategoriesController` -> `DocumentTypesController`
- `CategoryCacheService` -> `DocumentTypeCacheService`
- `categories:by-id` -> `document-types:by-id`
- `categories:list` -> `document-types:list`

## 3. Danh sách file cần tạo hoặc sửa

### A. Database

Tạo mới:

- `td-web/src/main/resources/db/migration/V1.0.X__Create_[TABLE_NAME]_Table.sql`

### B. Domain

Tạo mới:

- `td-domain/src/main/java/com/td/domain/[feature]/[FeatureName].java`

### C. Application

Tạo mới:

- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]Dto.java`
- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]DtoMapper.java`
- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]Repository.java`
- `td-application/src/main/java/com/td/application/[feature]/Create[FeatureName]Request.java`
- `td-application/src/main/java/com/td/application/[feature]/Update[FeatureName]Request.java`
- `td-application/src/main/java/com/td/application/[feature]/Search[FeaturePlural]Request.java`
- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]CacheStatsDto.java`
- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]ListCacheEntry.java`
- `td-application/src/main/java/com/td/application/[feature]/[FeatureName]CacheService.java`
- `td-application/src/main/java/com/td/application/[feature]/Create[FeatureName]UseCase.java`
- `td-application/src/main/java/com/td/application/[feature]/Get[FeatureName]UseCase.java`
- `td-application/src/main/java/com/td/application/[feature]/Update[FeatureName]UseCase.java`
- `td-application/src/main/java/com/td/application/[feature]/Delete[FeatureName]UseCase.java`
- `td-application/src/main/java/com/td/application/[feature]/Search[FeaturePlural]UseCase.java`

### D. Infrastructure

Tạo mới:

- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/[FeatureName]JpaRepository.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/Postgres[FeatureName]Repository.java`

### E. Web

Tạo mới:

- `td-web/src/main/java/com/td/web/controllers/v1/[FeaturePlural]Controller.java`

Sửa:

- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`

## 4. Khung schema mẫu

```sql
CREATE TABLE [table_name] (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    sort_order       INT          NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE UNIQUE INDEX UX_[table_name]_code ON [table_name](code) WHERE deleted_on IS NULL;
CREATE INDEX IX_[table_name]_is_active ON [table_name](is_active);
CREATE INDEX IX_[table_name]_sort_order ON [table_name](sort_order);
CREATE INDEX IX_[table_name]_deleted_on ON [table_name](deleted_on);
```

## 5. Khung endpoint mẫu

- `GET /api/v1/[feature]`
- `POST /api/v1/[feature]/search`
- `GET /api/v1/[feature]/{id}`
- `POST /api/v1/[feature]`
- `PUT /api/v1/[feature]/{id}`
- `DELETE /api/v1/[feature]/{id}`
- Nếu có cache stats: `GET /api/v1/[feature]/cache/stats`

## 6. Những gì copy được từ Categories

Giữ lại pattern:

- request model
- DTO + mapper
- repository interface
- repository implementation dùng `Specification`
- cache service với by-id cache và list cache
- controller với `useCache`, `X-Cache`, `CachedResult`, `CachedPaginationResponse`
- `TextNormalizer.normalizeAndSanitize(...)` cho text input
- normalize `code` nếu feature cũng dùng rule uppercase + underscore

## 7. Những gì phải bỏ khi copy từ Categories

Xóa các phần sau nếu feature không có hierarchy:

- `parentId`
- `level`
- `fullPath`
- logic kiểm tra parent tồn tại
- logic chặn self-reference
- index `parent_id`, `level`
- mô tả Swagger liên quan đến cây cha/con

## 8. Verify tối thiểu

- [ ] Create thành công
- [ ] Detail thành công
- [ ] List/search đúng filter
- [ ] Update đúng dữ liệu
- [ ] Delete không còn xuất hiện trong list nếu dùng soft delete
- [ ] Code được normalize đúng nếu feature có rule này
- [ ] Cache `MISS -> HIT`

## 9. Mẹo thực tế

- Nếu chỉ là master data đơn giản, nên bắt đầu từ template này thay vì template categories để tránh copy thừa logic hierarchy.
- Nếu route dự án đang dùng plural path có dấu `-`, giữ thống nhất toàn feature.
- Nếu feature có ít field, giữ request/DTO gọn, không bê nguyên field của categories.
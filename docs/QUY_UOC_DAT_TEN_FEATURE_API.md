# Quy ước đặt tên feature API

Index tài liệu: [INDEX_TAI_LIEU_API.md](INDEX_TAI_LIEU_API.md)

Checklist tổng: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Hướng dẫn chi tiết: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

## 1. Mục đích

File này quy định cách đặt tên thống nhất khi tạo feature mới hoặc sửa feature cũ, để tránh lệch giữa:

- tên bảng
- tên package
- tên class
- tên route
- tên request/DTO/use case
- tên cache region
- tên file migration

Nếu đặt tên không thống nhất, việc tìm code và bảo trì sẽ chậm hơn rất nhiều.

## 2. Nguyên tắc chung

### A. Một feature có 3 dạng tên chính

1. Tên domain/class dạng PascalCase
   - Ví dụ: `Category`, `Department`, `DocumentType`

2. Tên package và thư mục dạng lowercase plural hoặc lowercase feature name
   - Ví dụ: `categories`, `departments`, `documenttypes` hoặc `document-types` chỉ dùng cho route, không dùng cho package Java

3. Tên route dạng lowercase plural
   - Ví dụ: `/api/v1/categories`, `/api/v1/departments`, `/api/v1/document-types`

### B. Luôn thống nhất singular và plural

- Class entity, DTO, repository interface: dùng singular
- Controller và route: thường dùng plural
- Search request/use case: dùng plural nếu trả danh sách

Ví dụ:

- `Category`
- `CategoryDto`
- `CategoryRepository`
- `SearchCategoriesRequest`
- `SearchCategoriesUseCase`
- `CategoriesController`
- `/api/v1/categories`

## 3. Quy ước theo từng layer

### A. Database

#### 1. Tên bảng

- Dùng `snake_case`
- Thường dùng dạng plural

Ví dụ:

- `categories`
- `departments`
- `document_types`

#### 2. Tên cột

- Dùng `snake_case`
- Giữ thống nhất với schema hiện có của dự án

Ví dụ:

- `parent_id`
- `sort_order`
- `is_active`
- `created_on`
- `last_modified_on`
- `deleted_on`

#### 3. Tên index

- Unique index: `UX_<table>_<field>`
- Normal index: `IX_<table>_<field>`

Ví dụ:

- `UX_categories_code`
- `IX_categories_parent_id`
- `IX_document_types_sort_order`

#### 4. Tên migration

- Dạng: `Vx.x.x__<Action>_<Table_or_Feature>.sql`

Ví dụ:

- `V1.0.7__Create_Categories_Table.sql`
- `V1.0.8__Create_Departments_Table.sql`
- `V1.0.9__Add_Is_Active_To_Document_Types.sql`

## 4. Domain layer

### A. Package

- Dùng lowercase
- Thường dùng plural nếu feature đang đại diện cho một nhóm nghiệp vụ

Ví dụ:

- `com.td.domain.categories`
- `com.td.domain.departments`

### B. Entity

- Dùng PascalCase singular

Ví dụ:

- `Category`
- `Department`
- `DocumentType`

### C. Entity file

- Tên file trùng tên class

Ví dụ:

- `Category.java`
- `Department.java`

## 5. Application layer

### A. Package

- Dùng lowercase theo feature

Ví dụ:

- `com.td.application.categories`
- `com.td.application.departments`

### B. DTO

- Dạng: `[FeatureName]Dto`

Ví dụ:

- `CategoryDto`
- `DepartmentDto`

### C. Mapper

- Dạng: `[FeatureName]DtoMapper`

Ví dụ:

- `CategoryDtoMapper`
- `DepartmentDtoMapper`

### D. Repository interface

- Dạng: `[FeatureName]Repository`

Ví dụ:

- `CategoryRepository`
- `DepartmentRepository`

### E. Request model

- Tạo mới: `Create[FeatureName]Request`
- Cập nhật: `Update[FeatureName]Request`
- Tìm kiếm danh sách: `Search[FeaturePlural]Request`

Ví dụ:

- `CreateCategoryRequest`
- `UpdateCategoryRequest`
- `SearchCategoriesRequest`

### F. Use case

- Tạo mới: `Create[FeatureName]UseCase`
- Chi tiết: `Get[FeatureName]UseCase`
- Cập nhật: `Update[FeatureName]UseCase`
- Xóa: `Delete[FeatureName]UseCase`
- Tìm kiếm danh sách: `Search[FeaturePlural]UseCase`

Ví dụ:

- `CreateDepartmentUseCase`
- `GetDepartmentUseCase`
- `SearchDepartmentsUseCase`

### G. Cache classes

- Cache service: `[FeatureName]CacheService`
- Cache stats DTO: `[FeatureName]CacheStatsDto`
- Cache list entry: `[FeatureName]ListCacheEntry`

Ví dụ:

- `CategoryCacheService`
- `CategoryCacheStatsDto`
- `CategoryListCacheEntry`

## 6. Infrastructure layer

### A. JPA repository

- Dạng: `[FeatureName]JpaRepository`

Ví dụ:

- `CategoryJpaRepository`
- `DepartmentJpaRepository`

### B. Repository implementation

- Dạng: `Postgres[FeatureName]Repository`

Ví dụ:

- `PostgresCategoryRepository`
- `PostgresDepartmentRepository`

Nếu sau này dùng database khác, class implementation nên phản ánh đúng storage backend.

## 7. Web layer

### A. Controller

- Dạng plural: `[FeaturePlural]Controller`

Ví dụ:

- `CategoriesController`
- `DepartmentsController`
- `DocumentTypesController`

### B. Route

- Dạng: `/api/v1/<feature-plural>`
- Ưu tiên lowercase plural
- Nếu là tên nhiều từ, route nên dùng kebab-case

Ví dụ:

- `/api/v1/categories`
- `/api/v1/departments`
- `/api/v1/document-types`

### C. Method controller

Ưu tiên đặt tên rõ theo action:

- `list<FeaturePlural>`
- `search<FeaturePlural>`
- `get<FeatureName>`
- `create<FeatureName>`
- `update<FeatureName>`
- `delete<FeatureName>`
- `getCacheStats`

Ví dụ:

- `listCategories`
- `createCategory`
- `updateDepartment`

## 8. Quy ước cache

### A. Tên cache region

- Detail cache: `<feature-route>:by-id`
- List cache: `<feature-route>:list`

Ví dụ:

- `categories:by-id`
- `categories:list`
- `document-types:by-id`
- `document-types:list`

### B. Tên helper method trong cache service

Ưu tiên giữ pattern giống categories:

- `get(...)`
- `put(...)`
- `evict(...)`
- `isCachedById(...)`
- `get<FeatureName>ByIdCacheKey(...)`
- `getList(...)`
- `putList(...)`
- `evictAllListCaches()`
- `isListCached(...)`
- `get<FeatureName>ListCacheKey(...)`
- `getStats()`

## 9. Quy ước đặt tên field chung

Nếu feature dùng audit + soft delete, ưu tiên thống nhất các field sau:

- `id`
- `code`
- `name`
- `description`
- `sortOrder`
- `isActive`
- `createdBy`
- `createdOn`
- `lastModifiedBy`
- `lastModifiedOn`
- `deletedOn`
- `deletedBy`

Nếu có hierarchy:

- `parentId`
- `level`
- `fullPath`

## 10. Quy ước route và bảng cho feature nhiều từ

Ví dụ feature `DocumentType`:

- Entity class: `DocumentType`
- Package Java: `documenttypes` hoặc package domain-specific rõ nghĩa hơn nếu team đã có convention
- Controller: `DocumentTypesController`
- Route: `/api/v1/document-types`
- Table: `document_types`
- Cache: `document-types:by-id`, `document-types:list`

Khuyến nghị thực tế:

- Java class: PascalCase
- Java package: lowercase, không dùng dấu `-`
- Route: kebab-case nếu nhiều từ
- Table: snake_case
- Cache region: bám theo route để dễ nhìn

## 11. Những lệch tên thường gây lỗi

- Route là `document-types` nhưng cache lại đặt `documentTypes:list`
- Entity là `Department` nhưng controller lại đặt `DepartmentController` thay vì plural pattern
- Search request dùng singular, ví dụ `SearchDepartmentRequest`, trong khi use case và controller đang dùng plural
- Tên bảng là `department` nhưng index lại viết theo `departments`
- Migration file ghi `Create_Department_Table` nhưng entity và route đều đang dùng plural `departments`

## 12. Quy tắc chốt trước khi merge

Trước khi hoàn tất feature mới, kiểm tra nhanh:

- [ ] Tên entity, DTO, request, use case, controller có thống nhất singular/plural
- [ ] Tên route, cache region, table name có thống nhất
- [ ] Tên migration phản ánh đúng hành động và bảng
- [ ] Không còn tên feature cũ bị sót sau khi copy từ template khác
- [ ] Message lỗi và Swagger description đúng ngữ cảnh feature mới
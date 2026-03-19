# Checklist ngắn: nhân bản feature và sửa API

Tài liệu này là bản checklist 1 trang để thao tác nhanh.

Index tài liệu: [INDEX_TAI_LIEU_API.md](INDEX_TAI_LIEU_API.md)

Tài liệu chi tiết: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

Quy ước đặt tên: [QUY_UOC_DAT_TEN_FEATURE_API.md](QUY_UOC_DAT_TEN_FEATURE_API.md)

Checklist chỉ cho case sửa API: [CHECKLIST_SUA_API_NHANH.md](CHECKLIST_SUA_API_NHANH.md)

Mẫu thực chiến copy feature mới: [TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md](TEMPLATE_DEPARTMENT_THEO_CATEGORIES.md)

Mẫu thực chiến CRUD + cache không hierarchy: [TEMPLATE_CRUD_CACHE_KHONG_HIERARCHY.md](TEMPLATE_CRUD_CACHE_KHONG_HIERARCHY.md)

Nếu làm theo pattern CRUD + cache giống `categories`, hãy dùng luôn checklist này như template thao tác chuẩn.

## 1. Thông tin đầu vào

- Tên feature: `[FeatureName]`
- Tên bảng: `[table_name]`
- Route chính: `/api/v1/[feature]`
- Có soft delete: `[Có/Không]`
- Có cache: `[Có/Không]`
- Có hierarchy cha/con: `[Có/Không]`
- Role truy cập: `[USER / ADMIN / ...]`
- Các field tính tự động: `[vd: code, level, fullPath]`

## 1.1. Template tên file và class theo pattern dự án

Đổi từ mẫu `Category` sang feature mới:

- Entity: `[FeatureName].java`
- DTO: `[FeatureName]Dto.java`
- Mapper: `[FeatureName]DtoMapper.java`
- Repository interface: `[FeatureName]Repository.java`
- Request tạo mới: `Create[FeatureName]Request.java`
- Request cập nhật: `Update[FeatureName]Request.java`
- Request tìm kiếm: `Search[FeaturePlural]Request.java`
- Use case tạo mới: `Create[FeatureName]UseCase.java`
- Use case chi tiết: `Get[FeatureName]UseCase.java`
- Use case cập nhật: `Update[FeatureName]UseCase.java`
- Use case xóa: `Delete[FeatureName]UseCase.java`
- Use case tìm kiếm: `Search[FeaturePlural]UseCase.java`
- JPA repository: `[FeatureName]JpaRepository.java`
- Repository implementation: `Postgres[FeatureName]Repository.java`
- Controller: `[FeaturePlural]Controller.java`
- Cache service: `[FeatureName]CacheService.java`
- Cache list entry: `[FeatureName]ListCacheEntry.java`
- Cache stats DTO: `[FeatureName]CacheStatsDto.java`

Ví dụ nếu làm `Department`:

- `Department.java`
- `DepartmentDto.java`
- `CreateDepartmentUseCase.java`
- `SearchDepartmentsRequest.java`
- `DepartmentsController.java`
- `DepartmentCacheService.java`

## 1.2. Template endpoint theo pattern CRUD + cache

- [ ] `GET /api/v1/[feature]`
- [ ] `POST /api/v1/[feature]/search`
- [ ] `GET /api/v1/[feature]/{id}`
- [ ] `POST /api/v1/[feature]`
- [ ] `PUT /api/v1/[feature]/{id}`
- [ ] `DELETE /api/v1/[feature]/{id}`
- [ ] Nếu có cache stats: `GET /api/v1/[feature]/cache/stats`

## 1.3. Template cache theo pattern categories

- [ ] Cache by-id: `[feature]:by-id`
- [ ] Cache list: `[feature]:list`
- [ ] TTL detail cache: `60 phút`
- [ ] TTL list cache: `10 phút`
- [ ] Header kiểm tra cache: `X-Cache: MISS/HIT`
- [ ] Response cache list: `CachedPaginationResponse`
- [ ] Response cache detail: `CachedResult`

## 2. Checklist nhân bản một feature mới

### A. Database

- [ ] Tạo migration mới trong `td-web/src/main/resources/db/migration/`
- [ ] Tạo bảng với đúng cột nghiệp vụ
- [ ] Thêm `deleted_on`, `deleted_by` nếu dùng soft delete
- [ ] Thêm index cho field search/filter/sort
- [ ] Nếu có unique + soft delete, dùng partial unique index `WHERE deleted_on IS NULL`

### B. Domain

- [ ] Tạo entity trong `td-domain/src/main/java/com/td/domain/[feature]/`
- [ ] Kế thừa `AuditableEntity<UUID>` nếu phù hợp
- [ ] Implement `IAggregateRoot` nếu là aggregate chính
- [ ] Tạo constructor chính
- [ ] Tạo method `update(...)` cho mutation

### C. Application

- [ ] Tạo package `td-application/.../[feature]/`
- [ ] Tạo `Dto`
- [ ] Tạo `DtoMapper`
- [ ] Tạo `Repository` interface
- [ ] Tạo `CreateRequest`
- [ ] Tạo `UpdateRequest`
- [ ] Tạo `SearchRequest`
- [ ] Tạo `CreateUseCase`
- [ ] Tạo `GetUseCase`
- [ ] Tạo `UpdateUseCase`
- [ ] Tạo `DeleteUseCase`
- [ ] Tạo `SearchUseCase`
- [ ] Nếu có cache: tạo `CacheService`, `CacheStatsDto`, `ListCacheEntry`
- [ ] Đặt business rule ở use case, không đặt ở controller

### D. Infrastructure

- [ ] Tạo `JpaRepository` trong `td-infrastructure`
- [ ] Tạo repository implementation, ví dụ `Postgres[Feature]Repository`
- [ ] Thêm query search/filter/sort
- [ ] Nếu query derived method lỗi với `UUID id`, đổi sang `@Query` tường minh

### E. Web

- [ ] Tạo controller trong `td-web/src/main/java/com/td/web/controllers/v1/`
- [ ] Khai báo route `/api/v1/[feature]`
- [ ] Gắn `@PreAuthorize`
- [ ] Gắn `@Tag`, `@Operation`
- [ ] Dùng `@Valid` cho request body
- [ ] Giữ response wrapper đúng pattern chung: `Result`, `PaginationResponse`, `CachedResult`, `CachedPaginationResponse`

### F. Cache và config

- [ ] Nếu có cache: thêm cache region vào `RedisCacheConfig`
- [ ] Khớp tên cache constant giữa `CacheService` và config
- [ ] Chọn TTL hợp lý cho detail/list cache
- [ ] Với `create/update/delete`, nhớ evict cache đúng chỗ

### G. Verify

- [ ] Chạy `mvn install -DskipTests` từ root
- [ ] Chạy app lại từ `td-web`
- [ ] Kiểm tra `actuator/health`
- [ ] Test create
- [ ] Test get detail
- [ ] Test list/search
- [ ] Test update
- [ ] Test delete soft/hard theo thiết kế
- [ ] Nếu có cache: verify `X-Cache: MISS -> HIT`

### H. Verify nghiệp vụ đặc thù nếu copy từ categories

- [ ] Nếu có `code`, verify rule chuẩn hóa, ví dụ space -> `_`, chữ thường -> chữ hoa
- [ ] Nếu có hierarchy, verify `parentId`, `level`, `fullPath`
- [ ] Verify update không cho self-reference nếu có cây cha/con
- [ ] Verify parent không hợp lệ hoặc parent bị disable sẽ bị chặn nếu business yêu cầu

## 3. Checklist sửa một API đang có

### A. Xác định điểm vào

- [ ] Tìm controller từ URL bằng `rg -n "/api/v1/[feature]" td-web/src/main/java`
- [ ] Xác định method controller đang xử lý endpoint
- [ ] Xác định request model đang nhận input
- [ ] Xác định use case đang xử lý nghiệp vụ
- [ ] Xác định DTO đang trả response
- [ ] Nếu có search/filter: xác định repository implementation tương ứng
- [ ] Nếu có cache: xác định `CacheService` và `RedisCacheConfig`

### B. Sửa theo loại thay đổi

#### 1. Sửa request hoặc validation

- [ ] Sửa controller nếu đổi param/body
- [ ] Sửa `CreateRequest` / `UpdateRequest` / `SearchRequest`
- [ ] Kiểm tra lại annotation validation

#### 2. Sửa response

- [ ] Sửa `Dto`
- [ ] Sửa `DtoMapper`
- [ ] Kiểm tra controller có cần đổi wrapper/header không

#### 3. Sửa business rule

- [ ] Sửa use case tương ứng
- [ ] Kiểm tra create/update/delete/get/search có bị ảnh hưởng dây chuyền không

#### 4. Sửa search/filter/sort

- [ ] Sửa `SearchRequest`
- [ ] Sửa repository implementation
- [ ] Nếu cần, thêm migration để bổ sung index
- [ ] Kiểm tra cache key của list/search có còn đúng không

#### 5. Sửa schema DB

- [ ] Tạo migration mới, không sửa migration cũ đã chạy production
- [ ] Sửa entity
- [ ] Sửa request/dto/mapper/usecase/repository liên quan

#### 6. Sửa auth/quyền

- [ ] Sửa `@PreAuthorize` ở controller

#### 7. Sửa cache

- [ ] Sửa `CacheService`
- [ ] Sửa `RedisCacheConfig` nếu đổi region/TTL
- [ ] Kiểm tra evict sau mutation

## 4. Các lưu ý ngắn bắt buộc nhớ

- [ ] Không đặt business rule trong controller
- [ ] Không tin FE cho các field tính toán như `level`, `fullPath`, `code chuẩn hóa`
- [ ] Có soft delete thì query list/detail/search phải lọc bản ghi chưa xóa
- [ ] `update` phải phân biệt rõ: không truyền field và truyền `null`
- [ ] Normalize/sanitize global đã có, nhưng rule business đặc thù vẫn phải xử lý trong use case
- [ ] Sau khi sửa module ngoài `td-web`, nên chạy lại `mvn install -DskipTests` từ root để tránh dùng jar cũ trong `.m2`

## 5. Bộ lệnh tìm nhanh

```bash
rg -n "/api/v1/" td-web/src/main/java/com/td/web/controllers
rg --files td-domain td-application td-infrastructure td-web | rg "[FeatureName]|[feature]"
rg -n "CacheService|RedisCacheConfig|X-Cache" td-application td-web
rg -n "deletedOn|deleted_on|findByIdAndDeletedOnIsNull" td-application td-infrastructure td-web
```

## 6. Mẫu copy nhanh khi bắt đầu một feature mới

- [ ] Copy cấu trúc package của `categories`
- [ ] Đổi toàn bộ tên lớp từ `Category` sang `[FeatureName]`
- [ ] Đổi route từ `/api/v1/categories` sang `/api/v1/[feature]`
- [ ] Đổi cache constant từ `categories:*` sang `[feature]:*`
- [ ] Soát lại toàn bộ field nghiệp vụ, không giữ lại field thừa của categories
- [ ] Soát lại role `@PreAuthorize`
- [ ] Soát lại message lỗi tiếng Việt cho đúng ngữ cảnh feature mới
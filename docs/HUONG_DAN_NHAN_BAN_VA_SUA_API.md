# Hướng dẫn nhân bản và sửa API theo pattern Categories

Index tài liệu: [INDEX_TAI_LIEU_API.md](INDEX_TAI_LIEU_API.md)

Checklist thao tác nhanh: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Quy ước đặt tên: [QUY_UOC_DAT_TEN_FEATURE_API.md](QUY_UOC_DAT_TEN_FEATURE_API.md)

## Mục đích

Tài liệu này dùng để:

- Nhân thêm một module quản lý mới theo đúng pattern của phần quản lý danh mục vừa làm.
- Biết cần thêm ở đâu trong từng layer.
- Khi sửa một API đang có, biết phải tìm ở đâu và cập nhật những phần nào.
- Tránh các lỗi dễ gặp về cache, soft delete, query JPA, normalize dữ liệu đầu vào.

Feature `categories` là mẫu tham chiếu chuẩn cho tài liệu này.

## 1. Nhìn nhanh cấu trúc của một feature

Repo này đang đi theo Clean Architecture, nên một feature đầy đủ thường đi qua 4 layer:

1. `td-domain`
   - Entity, aggregate root, trạng thái lõi.
2. `td-application`
   - Use case, DTO, request model, interface repository, cache service.
3. `td-infrastructure`
   - JPA repository, query/filter/search, implementation repository.
4. `td-web`
   - Controller, config, migration SQL, Swagger/OpenAPI annotations.

Với feature `categories`, các file mẫu quan trọng là:

- `td-domain/src/main/java/com/td/domain/categories/Category.java`
- `td-application/src/main/java/com/td/application/categories/CreateCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/UpdateCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/SearchCategoriesRequest.java`
- `td-application/src/main/java/com/td/application/categories/CategoryDto.java`
- `td-application/src/main/java/com/td/application/categories/CategoryCacheService.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/CategoryJpaRepository.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresCategoryRepository.java`
- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`
- `td-web/src/main/resources/db/migration/V1.0.7__Create_Categories_Table.sql`
- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`

## 2. Nếu muốn nhân thêm một module quản lý mới giống Categories

Ví dụ: muốn làm thêm `departments`, `regions`, `document-types`, `customer-groups` hoặc một module CRUD có cache.

### Bước 1. Chốt yêu cầu trước khi code

Làm rõ trước các điểm này:

- Bảng cần những cột gì.
- Có soft delete hay không.
- Có cây cha/con hay không.
- Có field tính toán tự động hay không.
- Có cache hay không.
- Có endpoint list/search/detail/create/update/delete hay không.
- Quyền truy cập endpoint là gì.
- Có normalize dữ liệu riêng như `code -> UPPERCASE_UNDERSCORE` hay không.

Nếu không chốt trước, rất dễ sửa dây chuyền ở nhiều layer.

### Bước 2. Thêm migration SQL

Vị trí:

- `td-web/src/main/resources/db/migration/`

Việc cần làm:

- Tạo file migration mới theo version tiếp theo.
- Khai báo bảng, index, unique index, comment nếu cần.
- Nếu dùng soft delete thì nên có `deleted_on`, `deleted_by`.
- Nếu có unique nhưng dùng soft delete, nên dùng partial unique index kiểu `WHERE deleted_on IS NULL`.

Mẫu tham chiếu:

- `td-web/src/main/resources/db/migration/V1.0.7__Create_Categories_Table.sql`

Checklist:

- Có index cho các field lọc/search thường dùng.
- Có index cho `deleted_on` nếu feature dùng soft delete.
- Nếu có hierarchy, có index cho `parent_id`, `level`.

### Bước 3. Tạo Domain entity

Vị trí:

- `td-domain/src/main/java/com/td/domain/<feature>/`

Việc cần làm:

- Tạo entity mới.
- Thường kế thừa `AuditableEntity<UUID>`.
- Implement `IAggregateRoot` nếu đây là aggregate chính.
- Khai báo `@Entity`, `@Table`.
- Tạo constructor chính.
- Tạo method `update(...)` cho mutation.

Mẫu tham chiếu:

- `td-domain/src/main/java/com/td/domain/categories/Category.java`

Nguyên tắc:

- Entity giữ trạng thái và business rule lõi.
- Không nhét logic web, cache, request parsing vào domain.

### Bước 4. Tạo Application layer

Vị trí:

- `td-application/src/main/java/com/td/application/<feature>/`

Một module CRUD gần như luôn cần các file sau:

- `<Feature>Dto.java`
- `<Feature>DtoMapper.java`
- `<Feature>Repository.java`
- `Create<Feature>Request.java`
- `Update<Feature>Request.java`
- `Search<Feature>Request.java`
- `Create<Feature>UseCase.java`
- `Get<Feature>UseCase.java`
- `Update<Feature>UseCase.java`
- `Delete<Feature>UseCase.java`
- `Search<Feature>UseCase.java`

Nếu có cache, thêm:

- `<Feature>CacheService.java`
- `<Feature>CacheStatsDto.java`
- `<Feature>ListCacheEntry.java`

Mẫu tham chiếu:

- `td-application/src/main/java/com/td/application/categories/CategoryDto.java`
- `td-application/src/main/java/com/td/application/categories/CategoryDtoMapper.java`
- `td-application/src/main/java/com/td/application/categories/CategoryRepository.java`
- `td-application/src/main/java/com/td/application/categories/CreateCategoryRequest.java`
- `td-application/src/main/java/com/td/application/categories/UpdateCategoryRequest.java`
- `td-application/src/main/java/com/td/application/categories/SearchCategoriesRequest.java`
- `td-application/src/main/java/com/td/application/categories/CreateCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/GetCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/UpdateCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/DeleteCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/SearchCategoriesUseCase.java`
- `td-application/src/main/java/com/td/application/categories/CategoryCacheService.java`

Nguyên tắc đặt logic:

- Validate request bằng annotation ở request model khi phù hợp.
- Business rule chính đặt trong use case.
- Mapping entity -> DTO đặt ở mapper.
- Logic normalize đặc thù nghiệp vụ đặt ở use case.
- Không để controller tự tính dữ liệu dẫn xuất như `level`, `fullPath`, `code chuẩn hóa`.

### Bước 5. Tạo Infrastructure layer

Vị trí:

- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/`

Việc cần làm:

- Tạo `<Feature>JpaRepository.java` kế thừa `BaseRepository<Entity>`.
- Tạo `Postgres<Feature>Repository.java` implement interface repository ở application layer.
- Viết query filter/search trong repository implementation.
- Nếu cần list/search linh hoạt, dùng `Specification`.

Mẫu tham chiếu:

- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/CategoryJpaRepository.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresCategoryRepository.java`

Lưu ý rất quan trọng:

- Nếu dùng derived query kiểu `existsByCodeAndIdNot...` trên repository generic, Spring Data có thể suy luận sai kiểu `id` và nổ lỗi kiểu `Cannot compare left expression of type 'java.lang.Object' with right expression of type 'java.util.UUID'`.
- Trường hợp đó nên chuyển sang `@Query` viết tường minh như feature `categories` đang làm.

### Bước 6. Tạo Web controller

Vị trí:

- `td-web/src/main/java/com/td/web/controllers/v1/`

Việc cần làm:

- Tạo controller mới.
- Gắn route `/api/v1/<feature>`.
- Inject các use case cần dùng.
- Dùng `@PreAuthorize` cho role.
- Dùng `@Operation`, `@Tag` để Swagger rõ ràng.
- Dùng `@Valid` cho request body.

Mẫu tham chiếu:

- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`

Pattern endpoint đang dùng ở `categories`:

- `GET /api/v1/categories`
- `POST /api/v1/categories/search`
- `GET /api/v1/categories/{id}`
- `POST /api/v1/categories`
- `PUT /api/v1/categories/{id}`
- `DELETE /api/v1/categories/{id}`
- `GET /api/v1/categories/cache/stats`

Nên giữ response wrapper theo pattern chung hiện tại:

- `Result<T>`
- `PaginationResponse<T>`
- `CachedResult<T>`
- `CachedPaginationResponse<T>`

### Bước 7. Nếu có cache thì cập nhật config

Vị trí:

- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`

Việc cần làm:

- Thêm cache region mới.
- Đặt TTL phù hợp cho detail cache và list cache.
- Đảm bảo tên cache khớp với constant trong `<Feature>CacheService`.

Mẫu tham chiếu:

- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`
- `td-application/src/main/java/com/td/application/categories/CategoryCacheService.java`

### Bước 8. Build và verify

Quan trọng:

- Nếu sửa ở `td-domain`, `td-application`, `td-infrastructure` rồi chạy `mvn spring-boot:run` riêng trong `td-web`, app có thể dùng jar cũ trong `.m2`.
- Vì vậy sau thay đổi cross-module, nên chạy lại từ root:

```bash
mvn install -DskipTests
```

Sau đó mới chạy app:

```bash
cd td-web
mvn spring-boot:run
```

Checklist verify tối thiểu:

- App lên `actuator/health` bình thường.
- Tạo mới thành công.
- Lấy chi tiết thành công.
- List/search đúng dữ liệu.
- Update đúng business rule.
- Delete đúng soft delete.
- Nếu có cache, verify `X-Cache: MISS -> HIT`.

## 3. Nếu muốn sửa một API đang có thì tìm ở đâu

### Cách lần ra nhanh nhất từ URL

Ví dụ muốn sửa `/api/v1/categories`:

```bash
rg -n "/api/v1/categories" td-web/src/main/java
```

Nếu biết tên controller hoặc method:

```bash
rg -n "CategoriesController|createCategory|updateCategory|getCategory|searchCategories" td-web td-application td-infrastructure
```

Nếu muốn tìm toàn bộ feature theo package:

```bash
rg --files td-application | rg categories
rg --files td-infrastructure | rg Category
rg --files td-domain | rg Category
```

### Cách xác định cần sửa file nào theo loại thay đổi

#### 1. Sửa URL, query param, request body, validation

Tìm và sửa ở:

- Controller trong `td-web`
- Request model trong `td-application`

Ví dụ với categories:

- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`
- `td-application/src/main/java/com/td/application/categories/CreateCategoryRequest.java`
- `td-application/src/main/java/com/td/application/categories/UpdateCategoryRequest.java`
- `td-application/src/main/java/com/td/application/categories/SearchCategoriesRequest.java`

#### 2. Sửa response trả về

Tìm và sửa ở:

- DTO
- Mapper
- Có thể cả controller nếu đổi wrapper hoặc header

Ví dụ với categories:

- `td-application/src/main/java/com/td/application/categories/CategoryDto.java`
- `td-application/src/main/java/com/td/application/categories/CategoryDtoMapper.java`
- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`

#### 3. Sửa business rule

Tìm và sửa ở:

- Use case trong `td-application`

Ví dụ:

- Tạo mới: `td-application/src/main/java/com/td/application/categories/CreateCategoryUseCase.java`
- Cập nhật: `td-application/src/main/java/com/td/application/categories/UpdateCategoryUseCase.java`
- Xóa mềm: `td-application/src/main/java/com/td/application/categories/DeleteCategoryUseCase.java`
- Lấy chi tiết: `td-application/src/main/java/com/td/application/categories/GetCategoryUseCase.java`

#### 4. Sửa logic search, filter, sort, phân trang

Tìm và sửa ở:

- `Search<Feature>Request`
- Repository implementation ở `td-infrastructure`
- Có thể cần thêm index DB nếu filter mới dùng nhiều

Ví dụ:

- `td-application/src/main/java/com/td/application/categories/SearchCategoriesRequest.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresCategoryRepository.java`
- `td-web/src/main/resources/db/migration/` nếu phải thêm index mới

#### 5. Sửa cột dữ liệu hoặc schema DB

Tìm và sửa ở tất cả các điểm sau:

- Migration SQL
- Domain entity
- DTO
- Mapper
- Request model nếu input thay đổi
- Repository query nếu có liên quan
- Use case nếu có business rule mới

Ví dụ cho categories:

- `td-web/src/main/resources/db/migration/V1.0.7__Create_Categories_Table.sql`
- `td-domain/src/main/java/com/td/domain/categories/Category.java`
- `td-application/src/main/java/com/td/application/categories/CategoryDto.java`
- `td-application/src/main/java/com/td/application/categories/CategoryDtoMapper.java`
- `td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresCategoryRepository.java`

#### 6. Sửa quyền truy cập API

Tìm và sửa ở:

- `@PreAuthorize` trong controller

Ví dụ:

- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`

#### 7. Sửa cache hoặc TTL cache

Tìm và sửa ở:

- `<Feature>CacheService`
- `RedisCacheConfig`
- Controller nếu muốn đổi header `X-Cache` hoặc cơ chế bypass cache

Ví dụ:

- `td-application/src/main/java/com/td/application/categories/CategoryCacheService.java`
- `td-web/src/main/java/com/td/web/config/RedisCacheConfig.java`
- `td-web/src/main/java/com/td/web/controllers/v1/CategoriesController.java`

#### 8. Sửa normalize/sanitize đầu vào

Có 2 lớp cần phân biệt:

1. Normalize/sanitize dùng chung cho toàn bộ HTTP input:

- `td-web/src/main/java/com/td/web/config/StringNormalizationConfig.java`
- `td-application/src/main/java/com/td/application/common/TextNormalizer.java`

2. Normalize đặc thù business:

- Ví dụ `code -> viết HOA + thay space bằng _`
- Phần này nên sửa trong use case, không nên nhét vào cấu hình global

Ví dụ:

- `td-application/src/main/java/com/td/application/categories/CreateCategoryUseCase.java`
- `td-application/src/main/java/com/td/application/categories/UpdateCategoryUseCase.java`

## 4. Checklist nhanh khi sửa một API

Khi sửa một API bất kỳ, đi theo thứ tự này sẽ ít sót nhất:

1. Tìm controller từ URL.
2. Xác định request model nào đang nhận dữ liệu.
3. Xác định use case nào xử lý nghiệp vụ.
4. Xác định DTO nào trả response.
5. Nếu có query/search, tìm repository implementation.
6. Nếu có cache, tìm cache service và `RedisCacheConfig`.
7. Nếu đổi schema, thêm migration và sửa entity.
8. Compile lại toàn bộ module liên quan.
9. Chạy app và verify runtime.

## 5. Các lưu ý quan trọng

### 1. Controller chỉ nên orchestration, không chứa business rule

Controller nên làm các việc sau:

- Nhận request.
- Gọi use case.
- Trả response.
- Gắn auth, validation, Swagger.

Không nên để controller tự:

- Tính `level`, `fullPath`.
- Chuẩn hóa `code`.
- Ghép logic DB.
- Tự thao tác cache phức tạp ngoài pattern đã có.

### 2. Dữ liệu dẫn xuất phải tính ở server

Với `categories`:

- `level`
- `fullPath`
- `code` chuẩn hóa

Các giá trị này phải được tính trong use case, không tin dữ liệu từ FE.

### 3. Mutation phải xóa cache đúng chỗ

Nếu feature có cache:

- `create`: tối thiểu phải xóa list cache.
- `update`: xóa by-id cache của bản ghi đó và xóa list cache.
- `delete`: xóa by-id cache của bản ghi đó và xóa list cache.

Nếu quên bước này, API list/detail sẽ trả dữ liệu cũ.

### 4. Soft delete phải đi xuyên suốt

Nếu feature dùng soft delete:

- Bảng có `deleted_on`, `deleted_by`.
- Query search/list/detail phải lọc `deletedOn IS NULL` khi cần.
- Unique index nên bỏ qua bản ghi đã soft delete.

### 5. Phân biệt "không truyền field" và "truyền field = null"

Đây là điểm rất dễ sai với API update.

Ví dụ `UpdateCategoryRequest` đang dùng pattern:

- Có cờ `updateParent`
- Có `@JsonSetter("parentId")`

Ý nghĩa:

- Không truyền `parentId`: giữ nguyên cha hiện tại.
- Truyền `parentId: null`: chuyển node lên gốc.

Nếu sau này có field nullable khác cũng cần phân biệt hai trường hợp này, nên áp dụng lại pattern tương tự.

### 6. Normalize global đã có, nhưng normalize nghiệp vụ vẫn phải tự làm

Hiện tại repo đã có normalize/sanitize global cho toàn bộ HTTP input:

- JSON body qua Jackson module
- Query param / form field qua `WebDataBinder`

Nhưng các rule kiểu:

- viết HOA
- thay space bằng `_`
- ghép `fullPath`
- quy đổi mã theo business convention

vẫn phải làm trong use case.

### 7. Khi chạy app từ `td-web`, nhớ nguy cơ dùng jar cũ

Đây là lỗi đã gặp thực tế.

Sau khi sửa module khác ngoài `td-web`, trước khi chạy:

```bash
mvn install -DskipTests
```

Nếu không, `td-web` có thể load dependency cũ từ `.m2`, khiến log và code chạy không khớp với source hiện tại.

### 8. Nếu thêm filter/search mới, kiểm tra cả hiệu năng

Khi thêm điều kiện search mới:

- Sửa request model.
- Sửa specification/query trong repository.
- Cân nhắc thêm index DB.
- Verify cache key có phân biệt đúng request mới.

Với `CategoryCacheService`, key list đang được hash từ toàn bộ `SearchCategoriesRequest`, nên khi request model đổi field thì cache key cũng đổi theo.

## 6. Mẫu suy nghĩ khi nhân bản một feature mới

Ví dụ muốn làm `departments`, có thể đi theo checklist này:

1. Tạo migration `Create_Departments_Table`.
2. Tạo entity `Department` ở `td-domain`.
3. Tạo package `com.td.application.departments`.
4. Tạo DTO, request, repository interface, use cases.
5. Tạo `DepartmentJpaRepository` và `PostgresDepartmentRepository`.
6. Tạo `DepartmentsController` với route `/api/v1/departments`.
7. Nếu có cache, tạo `DepartmentCacheService` và cập nhật `RedisCacheConfig`.
8. `mvn install -DskipTests` từ root.
9. Chạy app và test create/detail/list/update/delete.

## 7. Bộ lệnh tìm nhanh nên dùng

Tìm controller theo route:

```bash
rg -n "/api/v1/" td-web/src/main/java/com/td/web/controllers
```

Tìm toàn bộ file của một feature:

```bash
rg --files td-domain td-application td-infrastructure td-web | rg "categories|Category"
```

Tìm use case đang xử lý một action:

```bash
rg -n "CreateCategoryUseCase|UpdateCategoryUseCase|DeleteCategoryUseCase|SearchCategoriesUseCase" td-application td-web
```

Tìm config cache:

```bash
rg -n "CacheService|RedisCacheConfig|X-Cache" td-application td-web
```

Tìm migration liên quan đến một bảng:

```bash
rg -n "categories|parent_id|full_path" td-web/src/main/resources/db/migration
```

## 8. Kết luận ngắn

Muốn nhân thêm một module giống `categories`, gần như luôn phải đi qua đủ các điểm sau:

- Migration SQL
- Domain entity
- Application request/dto/usecase/repository interface
- Infrastructure repository
- Web controller
- Cache config nếu có cache
- Compile, install, run, verify runtime

Muốn sửa một API đang có, hãy bắt đầu từ controller theo URL, sau đó lần xuống request, use case, DTO, repository, cache, migration tùy loại thay đổi. Đi theo đúng luồng này sẽ ít bỏ sót nhất.
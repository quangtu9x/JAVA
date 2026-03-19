# Checklist siêu ngắn: sửa một API đang có

Checklist tổng quát: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Hướng dẫn chi tiết: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

## 1. Tìm đúng chỗ trước khi sửa

- [ ] Tìm controller từ URL
- [ ] Xác định method controller xử lý endpoint
- [ ] Xác định request model nhận input
- [ ] Xác định use case xử lý nghiệp vụ
- [ ] Xác định DTO trả response
- [ ] Nếu có query/search: xác định repository implementation
- [ ] Nếu có cache: xác định `CacheService` và `RedisCacheConfig`

## 2. Sửa theo đúng loại thay đổi

### A. Đổi request hoặc validation

- [ ] Sửa controller
- [ ] Sửa request model
- [ ] Kiểm tra validation

### B. Đổi response

- [ ] Sửa DTO
- [ ] Sửa mapper
- [ ] Kiểm tra wrapper response

### C. Đổi business rule

- [ ] Sửa use case
- [ ] Kiểm tra ảnh hưởng create/get/update/delete/search

### D. Đổi search/filter/sort

- [ ] Sửa search request
- [ ] Sửa repository implementation
- [ ] Kiểm tra index DB nếu cần
- [ ] Kiểm tra cache key list/search

### E. Đổi schema DB

- [ ] Tạo migration mới
- [ ] Sửa entity
- [ ] Sửa request, DTO, mapper, use case, repository liên quan

### F. Đổi quyền hoặc cache

- [ ] Sửa `@PreAuthorize`
- [ ] Sửa `CacheService` hoặc `RedisCacheConfig` nếu cần
- [ ] Kiểm tra evict cache sau mutation

## 3. Trước khi kết thúc

- [ ] `mvn install -DskipTests`
- [ ] Chạy app lại
- [ ] Test endpoint thực tế
- [ ] Nếu có cache: check `X-Cache`
- [ ] Nếu có soft delete: check query không lấy bản ghi đã xóa

## 4. Lệnh tìm nhanh

```bash
rg -n "/api/v1/[feature]" td-web/src/main/java
rg -n "[UseCaseName]|[ControllerName]|[DtoName]" td-web td-application td-infrastructure
rg -n "CacheService|RedisCacheConfig|X-Cache" td-application td-web
```
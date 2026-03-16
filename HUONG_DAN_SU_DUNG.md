# Hướng dẫn sử dụng TD WebAPI - Java Spring Boot

## Mục lục
1. [Giới thiệu](#giới-thiệu)
2. [Cài đặt](#cài-đặt)
3. [Cấu trúc dự án](#cấu-trúc-dự-án)
4. [Các tính năng chính](#các-tính-năng-chính)
5. [API Endpoints](#api-endpoints)
6. [Ví dụ sử dụng](#ví-dụ-sử-dụng)
7. [Troubleshooting](#troubleshooting)

---

## Giới thiệu

TD WebAPI là hệ thống quản lý sản phẩm và thương hiệu được xây dựng bằng Java Spring Boot với:
- **Clean Architecture**: Domain → Application → Infrastructure → Web
- **Hybrid Database**: PostgreSQL (data) + MongoDB (logs) + Elasticsearch (search) + MinIO (files)
- **Keycloak OAuth2**: Authentication & Authorization
- **RESTful API**: OpenAPI/Swagger documentation

---

## Cài đặt

### Yêu cầu
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (khuyến nghị)

### Quick Start với Docker

```powershell
# 1. Clone repository
git clone <repo-url>
cd td-webapi-java

# 2. Start tất cả services (PostgreSQL, MongoDB, MinIO, Keycloak, Elasticsearch)
docker-compose up -d

# 3. Chờ services khởi động (30-60 giây)
docker-compose logs -f

# 4. Build application
mvn clean install

# 5. Run application
cd td-web
mvn spring-boot:run

# 6. Truy cập Swagger UI
# http://localhost:8080/swagger-ui.html
```

### Setup thủ công (Không dùng Docker)

Chi tiết xem file: `docs/QUICK_START.md`

---

## Cấu trúc dự án

```
td-webapi-java/
├── td-domain/              # Domain Layer - Entities & Business Logic
│   ├── catalog/            # Product, Brand entities
│   ├── logs/               # AuditLog, ApiLog (MongoDB)
│   ├── search/             # ProductDocument, BrandDocument (Elasticsearch)
│   ├── storage/            # FileMetadata
│   └── common/             # Base entities, interfaces
│
├── td-application/         # Application Layer - Use Cases & DTOs
│   ├── catalog/            # Product/Brand use cases
│   │   ├── products/       # CreateProduct, UpdateProduct, SearchProducts, etc.
│   │   └── brands/         # CreateBrand, UpdateBrand, SearchBrands, etc.
│   ├── logs/               # GetAuditLogs
│   ├── search/             # AdvancedSearch, SearchSuggestions (Elasticsearch)
│   ├── storage/            # UploadFile, DownloadFile, DeleteFile
│   └── common/             # Result, PaginationResponse, Interfaces
│
├── td-infrastructure/      # Infrastructure Layer - Technical Implementation
│   ├── config/             # Database, MongoDB, Elasticsearch, MinIO configs
│   ├── persistence/        # JPA Repositories, MongoDB Repositories
│   ├── search/             # Elasticsearch repositories & services
│   ├── storage/            # MinIO service implementation
│   └── security/           # Keycloak JWT, SecurityConfig
│
└── td-web/                 # Web Layer - REST API Controllers
    ├── controllers/v1/     # ProductsController, BrandsController, FileController, etc.
    ├── config/             # OpenAPI config
    └── resources/
        ├── application.yml # Main configuration
        └── db/migration/   # Flyway SQL scripts
```

---

## Các tính năng chính

### 1. Quản lý Sản phẩm (Products)
- ✅ Tạo, sửa, xóa (soft delete) sản phẩm
- ✅ Tìm kiếm với filters (name, brand, price range)
- ✅ Phân trang & sắp xếp
- ✅ Export Excel
- ✅ Business logic (giảm giá, kiểm tra giá cao)

### 2. Quản lý Thương hiệu (Brands)
- ✅ CRUD operations
- ✅ Tìm kiếm theo tên, mô tả
- ✅ Đếm số sản phẩm thuộc brand
- ✅ Export Excel

### 3. Tìm kiếm nâng cao (Elasticsearch)
- ✅ Full-text search
- ✅ Fuzzy matching (tìm kiếm gần đúng)
- ✅ Search suggestions (gợi ý tìm kiếm)
- ✅ Aggregations (thống kê)

### 4. Quản lý File (MinIO)
- ✅ Upload file (images, documents)
- ✅ Download file
- ✅ Xóa file
- ✅ Metadata tracking (FileMetadata trong PostgreSQL)

### 5. Audit Logging (MongoDB)
- ✅ Tự động log mọi thay đổi data (created, updated, deleted)
- ✅ API request/response logging
- ✅ Truy vấn audit logs
- ✅ Filter theo user, entity, date range

### 6. Authentication & Authorization (Keycloak)
- ✅ OAuth2 JWT authentication
- ✅ Role-based access control (USER, ADMIN)
- ✅ Token refresh
- ✅ Single Sign-On (SSO)

---

## API Endpoints

### Base URL: `http://localhost:8080/api/v1`

### Authentication
Tất cả endpoint (trừ public) yêu cầu JWT token trong header:
```http
Authorization: Bearer {access_token}
```

### Products API

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/products/search` | Tìm kiếm sản phẩm với filters | USER |
| `GET` | `/products/{id}` | Lấy chi tiết sản phẩm | USER |
| `POST` | `/products` | Tạo sản phẩm mới | USER |
| `PUT` | `/products/{id}` | Cập nhật sản phẩm | USER |
| `DELETE` | `/products/{id}` | Xóa sản phẩm (soft delete) | USER |
| `POST` | `/products/export` | Export Excel | USER |

### Brands API

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/brands/search` | Tìm kiếm thương hiệu | USER |
| `GET` | `/brands/{id}` | Lấy chi tiết thương hiệu | USER |
| `POST` | `/brands` | Tạo thương hiệu mới | USER |
| `PUT` | `/brands/{id}` | Cập nhật thương hiệu | USER |
| `DELETE` | `/brands/{id}` | Xóa thương hiệu | USER |
| `POST` | `/brands/export` | Export Excel | USER |

### Search API (Elasticsearch)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/search/products/advanced` | Tìm kiếm nâng cao sản phẩm | USER |
| `GET` | `/search/suggestions?q={query}` | Gợi ý tìm kiếm | USER |
| `POST` | `/search/admin/reindex` | Reindex Elasticsearch | ADMIN |
| `POST` | `/search/admin/sync` | Sync PostgreSQL → Elasticsearch | ADMIN |

### Files API (MinIO)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/files/upload` | Upload file | USER |
| `GET` | `/files/{fileId}/download` | Download file | USER |
| `DELETE` | `/files/{fileId}` | Xóa file | USER |
| `GET` | `/files/{fileId}/metadata` | Lấy metadata file | USER |

### Audit Logs API (MongoDB)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/audit-logs/search` | Tìm kiếm audit logs | ADMIN |
| `GET` | `/audit-logs/{id}` | Lấy chi tiết audit log | ADMIN |

---

## Ví dụ sử dụng

### 1. Đăng nhập với Keycloak

```bash
# Get access token
curl -X POST "http://localhost:8180/realms/td-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=td-client" \
  -d "username=admin" \
  -d "password=admin"
```

Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer"
}
```

### 2. Tạo Brand mới

```bash
curl -X POST "http://localhost:8080/api/v1/brands" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Apple",
    "description": "Technology company from Cupertino"
  }'
```

Response:
```json
{
  "succeeded": true,
  "data": "123e4567-e89b-12d3-a456-426614174000",
  "messages": []
}
```

### 3. Tạo Product mới

```bash
curl -X POST "http://localhost:8080/api/v1/products" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 15 Pro",
    "description": "Latest flagship phone",
    "rate": 29990000,
    "brandId": "123e4567-e89b-12d3-a456-426614174000",
    "imagePath": "/images/iphone15pro.jpg"
  }'
```

Response:
```json
{
  "succeeded": true,
  "data": "987e6543-e21b-98d7-a654-987321654321",
  "messages": []
}
```

### 4. Tìm kiếm Products với filters

```bash
curl -X POST "http://localhost:8080/api/v1/products/search" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone",
    "minRate": 10000000,
    "maxRate": 50000000,
    "brandId": "123e4567-e89b-12d3-a456-426614174000",
    "pageNumber": 0,
    "pageSize": 10,
    "sortBy": "rate",
    "sortDirection": "desc"
  }'
```

Response:
```json
{
  "data": [
    {
      "id": "987e6543-e21b-98d7-a654-987321654321",
      "name": "iPhone 15 Pro",
      "description": "Latest flagship phone",
      "rate": 29990000,
      "brandName": "Apple",
      "imagePath": "/images/iphone15pro.jpg",
      "createdOn": "2024-01-15T10:30:00"
    }
  ],
  "currentPage": 0,
  "totalPages": 1,
  "totalCount": 1,
  "pageSize": 10,
  "hasPreviousPage": false,
  "hasNextPage": false
}
```

### 5. Upload File

```bash
curl -X POST "http://localhost:8080/api/v1/files/upload" \
  -H "Authorization: Bearer {access_token}" \
  -F "file=@/path/to/image.jpg" \
  -F "category=PRODUCT_IMAGE" \
  -F "description=Product image for iPhone 15"
```

Response:
```json
{
  "succeeded": true,
  "data": {
    "fileId": "abc123-def456-ghi789",
    "fileName": "image.jpg",
    "fileSize": 1024000,
    "contentType": "image/jpeg",
    "downloadUrl": "http://localhost:8080/api/v1/files/abc123-def456-ghi789/download"
  }
}
```

### 6. Tìm kiếm nâng cao với Elasticsearch

```bash
curl -X POST "http://localhost:8080/api/v1/search/products/advanced" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "iphone pro",
    "fuzzy": true,
    "brandName": "Apple",
    "minRate": 20000000,
    "pageNumber": 0,
    "pageSize": 10
  }'
```

### 7. Lấy Audit Logs

```bash
curl -X POST "http://localhost:8080/api/v1/audit-logs/search" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "entityType": "Product",
    "action": "CREATE",
    "fromDate": "2024-01-01T00:00:00",
    "toDate": "2024-12-31T23:59:59",
    "pageNumber": 0,
    "pageSize": 20
  }'
```

---

## Code Examples - Tích hợp vào ứng dụng

### Java/Spring Boot Client

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final RestTemplate restTemplate;
    private final String apiBaseUrl = "http://localhost:8080/api/v1";
    
    public ProductDto createProduct(CreateProductRequest request, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CreateProductRequest> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<Result<UUID>> response = restTemplate.exchange(
            apiBaseUrl + "/products",
            HttpMethod.POST,
            entity,
            new ParameterizedTypeReference<Result<UUID>>() {}
        );
        
        UUID productId = response.getBody().getData();
        
        // Get product details
        return getProduct(productId, accessToken);
    }
    
    public ProductDto getProduct(UUID id, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Result<ProductDto>> response = restTemplate.exchange(
            apiBaseUrl + "/products/" + id,
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<Result<ProductDto>>() {}
        );
        
        return response.getBody().getData();
    }
}
```

### JavaScript/TypeScript Client

```typescript
// api-client.ts
import axios, { AxiosInstance } from 'axios';

class TdApiClient {
  private client: AxiosInstance;
  private accessToken: string;

  constructor(baseURL: string = 'http://localhost:8080/api/v1') {
    this.client = axios.create({ baseURL });
  }

  setAccessToken(token: string) {
    this.accessToken = token;
    this.client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  }

  async createProduct(request: CreateProductRequest): Promise<string> {
    const response = await this.client.post<Result<string>>('/products', request);
    return response.data.data; // Product ID
  }

  async searchProducts(request: SearchProductsRequest): Promise<PaginationResponse<ProductDto>> {
    const response = await this.client.post<PaginationResponse<ProductDto>>(
      '/products/search',
      request
    );
    return response.data;
  }

  async uploadFile(file: File, category: string): Promise<UploadFileResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', category);

    const response = await this.client.post<Result<UploadFileResponse>>(
      '/files/upload',
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' }
      }
    );
    return response.data.data;
  }
}

// Usage
const apiClient = new TdApiClient();

// 1. Login with Keycloak
const tokenResponse = await fetch('http://localhost:8180/realms/td-realm/protocol/openid-connect/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'password',
    client_id: 'td-client',
    username: 'admin',
    password: 'admin'
  })
});
const { access_token } = await tokenResponse.json();
apiClient.setAccessToken(access_token);

// 2. Create product
const productId = await apiClient.createProduct({
  name: 'iPhone 15 Pro',
  description: 'Latest flagship',
  rate: 29990000,
  brandId: brandId,
  imagePath: '/images/iphone.jpg'
});

// 3. Search products
const results = await apiClient.searchProducts({
  name: 'iPhone',
  minRate: 10000000,
  pageNumber: 0,
  pageSize: 10
});
console.log(`Found ${results.totalCount} products`);
```

---

## Configuration - Cấu hình

### application.yml

File: `td-web/src/main/resources/application.yml`

```yaml
# Server Configuration
server:
  port: 8080

spring:
  application:
    name: TD WebAPI
    
  # PostgreSQL - Primary Database
  datasource:
    url: jdbc:postgresql://localhost:5432/td_webapi
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
    
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        
  # MongoDB - Logging Database
  data:
    mongodb:
      uri: mongodb://localhost:27017/td_webapi_logs
      
  # Flyway Migration
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    
  # Security - Keycloak OAuth2
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/td-realm
          jwk-set-uri: http://localhost:8180/realms/td-realm/protocol/openid-connect/certs

# MinIO Configuration
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: td-files

# Elasticsearch Configuration
elasticsearch:
  uris: http://localhost:9200
  username: elastic
  password: changeme

# Logging
logging:
  level:
    com.td: DEBUG
    org.springframework.security: DEBUG
    org.springframework.data.mongodb: DEBUG
```

### Environment Variables (Production)

Thay vì hardcode trong `application.yml`, sử dụng environment variables:

```bash
# PostgreSQL
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/td_webapi
export SPRING_DATASOURCE_USERNAME=prod_user
export SPRING_DATASOURCE_PASSWORD=prod_password

# MongoDB
export SPRING_DATA_MONGODB_URI=mongodb://prod-mongo:27017/td_logs

# Keycloak
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://keycloak.prod.com/realms/td-realm

# MinIO
export MINIO_ENDPOINT=https://minio.prod.com
export MINIO_ACCESS_KEY=prod_access_key
export MINIO_SECRET_KEY=prod_secret_key

# Elasticsearch
export ELASTICSEARCH_URIS=https://elasticsearch.prod.com:9200
```

---

## Troubleshooting

### 1. Lỗi kết nối PostgreSQL

**Triệu chứng:**
```
org.postgresql.util.PSQLException: Connection refused
```

**Giải pháp:**
```bash
# Kiểm tra PostgreSQL đang chạy
docker ps | grep postgres

# Xem logs
docker logs td-postgres

# Restart PostgreSQL
docker-compose restart postgres

# Test connection
psql -h localhost -p 5432 -U postgres -d td_webapi
```

### 2. JWT Token validation failed

**Triệu chứng:**
```
401 Unauthorized: Invalid JWT signature
```

**Giải pháp:**
- Kiểm tra Keycloak đang chạy: `docker ps | grep keycloak`
- Verify `issuer-uri` trong `application.yml` đúng
- Check token chưa expired (thường 5-15 phút)
- Lấy token mới:
```bash
curl -X POST "http://localhost:8180/realms/td-realm/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=td-client&username=admin&password=admin"
```

### 3. Flyway migration failed

**Triệu chứng:**
```
FlywayException: Validate failed: Checksum mismatch
```

**Giải pháp:**
```bash
# Option 1: Repair migration
cd td-web
mvn flyway:repair

# Option 2: Clean & migrate (CHÚ Ý: Mất dữ liệu!)
mvn flyway:clean
mvn flyway:migrate

# Option 3: Baseline existing database
mvn flyway:baseline
```

### 4. MongoDB connection timeout

**Triệu chứng:**
```
MongoTimeoutException: Timed out after 30000 ms
```

**Giải pháp:**
```bash
# Kiểm tra MongoDB
docker logs td-mongodb

# Test connection
mongosh mongodb://localhost:27017/td_webapi_logs

# Tăng timeout trong application.yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/td_webapi_logs?connectTimeoutMS=10000&socketTimeoutMS=10000
```

### 5. MinIO file upload failed

**Triệu chứng:**
```
ErrorResponse: Access Denied
```

**Giải pháp:**
```bash
# 1. Kiểm tra MinIO
docker logs td-minio

# 2. Truy cập MinIO Console: http://localhost:9001
# Login: minioadmin / minioadmin

# 3. Kiểm tra bucket 'td-files' exists
# Nếu chưa có: Create Bucket → Name: td-files → Access Policy: Public

# 4. Verify credentials trong application.yml
minio:
  access-key: minioadmin
  secret-key: minioadmin
```

### 6. Elasticsearch not available

**Triệu chứng:**
```
NoNodeAvailableException: None of the configured nodes are available
```

**Giải pháp:**
```bash
# Kiểm tra Elasticsearch
docker logs td-elasticsearch

# Restart
docker-compose restart elasticsearch

# Test connection
curl http://localhost:9200

# Disable Elasticsearch tạm thời (comment trong application.yml)
#elasticsearch:
#  uris: http://localhost:9200
```

### 7. Port already in use

**Triệu chứng:**
```
Web server failed to start. Port 8080 was already in use.
```

**Giải pháp:**
```bash
# Option 1: Tìm process đang dùng port 8080
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Option 2: Đổi port trong application.yml
server:
  port: 8081
```

---

## Tài liệu tham khảo

- [ARCHITECTURE.md](./ARCHITECTURE.md) - Kiến trúc chi tiết hệ thống
- [QUICK_START.md](./docs/QUICK_START.md) - Hướng dẫn cài đặt nhanh
- [EXTERNAL_SERVICES.md](./docs/EXTERNAL_SERVICES.md) - Cấu hình external services
- [Swagger UI](http://localhost:8080/swagger-ui.html) - API Documentation interactive

---

## Support

Nếu gặp vấn đề, hãy:
1. Check logs: `docker-compose logs -f <service-name>`
2. Kiểm tra [Troubleshooting](#troubleshooting)
3. Xem [ARCHITECTURE.md](./ARCHITECTURE.md) để hiểu luồng hoạt động
4. Test từng service riêng lẻ (PostgreSQL, MongoDB, Keycloak, MinIO, Elasticsearch)

---

**Version:** 1.0.0  
**Last Updated:** 2024-01-15

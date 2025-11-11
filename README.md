# TD WebAPI - Java Spring Boot

Đây là phiên bản Java Spring Boot của TD WebAPI, được phát triển dựa trên phiên bản .NET gốc với kiến trúc Clean Architecture.

## Kiến trúc dự án

Dự án được tổ chức theo Clean Architecture với các module sau:

- **td-domain**: Chứa các entity, aggregate root, domain events và business logic
- **td-application**: Chứa use cases, DTOs, interfaces và application services  
- **td-infrastructure**: Chứa implementations cho database, security, caching, file storage
- **td-web**: Chứa controllers, main application và configuration

## Công nghệ sử dụng

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Security 6.2.0** - OAuth2 Resource Server với Keycloak
- **Spring Data JPA** - ORM và Repository pattern cho PostgreSQL
- **Spring Data MongoDB** - NoSQL database cho logging
- **PostgreSQL** - Primary relational database
- **MongoDB** - Document database cho audit logs và API logs
- **MinIO** - Object storage cho file management
- **Keycloak** - Identity Provider và Authentication/Authorization
- **Flyway** - Database migration
- **MapStruct** - Object mapping
- **Lombok** - Code generation
- **SpringDoc OpenAPI** - API documentation
- **Maven** - Build tool
- **Docker Compose** - Infrastructure orchestration

## Cài đặt và chạy

### Yêu cầu hệ thống

- Java 17 hoặc cao hơn
- Maven 3.8+
- Docker & Docker Compose (khuyến nghị)

**Hoặc cài đặt thủ công:**
- PostgreSQL 12+ 
- MongoDB 5.0+
- MinIO Server
- Keycloak 22+

### Setup với Docker Compose (Khuyến nghị)

#### Quick Start
```powershell
# 1. Start tất cả services
docker-compose up -d

# 2. Chạy setup script
.\setup-dev.ps1

# 3. Build và chạy application
mvn clean install
mvn -pl td-web -am spring-boot:run
```

#### Services được khởi động:
- **PostgreSQL** - Database chính (port 5432)
- **MongoDB** - Logging database (port 27017)
- **MinIO** - File storage (API: 9000, Console: 9001)
- **Keycloak** - Identity provider (port 8180)
- **Redis** - Caching (port 6379)

### Setup thủ công (không dùng Docker)

#### PostgreSQL (Primary Database)
1. Cài đặt PostgreSQL 12+
2. Tạo database tên `tdwebapi`
3. Set environment variables:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/tdwebapi"
$env:DATABASE_USERNAME = "postgres"
$env:DATABASE_PASSWORD = "postgres"
```

#### MongoDB (Logging Database)
1. Cài đặt MongoDB 5.0+
2. Set environment variables:

```powershell
$env:MONGODB_HOST = "localhost"
$env:MONGODB_PORT = "27017"
$env:MONGODB_DATABASE = "tdwebapi_logs"
```

#### Keycloak (Identity Provider)
1. Cài đặt Keycloak 22+
2. Import realm từ `keycloak/realm-export.json`
3. Set environment variables:

```powershell
$env:KEYCLOAK_SERVER_URL = "http://localhost:8180"
$env:KEYCLOAK_REALM = "td-webapi-realm"
$env:KEYCLOAK_CLIENT_ID = "td-webapi-client"
$env:KEYCLOAK_CLIENT_SECRET = "td-webapi-secret-2024"
```

#### MinIO (File Storage)
1. Cài đặt MinIO Server
2. Set environment variables:

```powershell
$env:MINIO_URL = "http://localhost:9000"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_BUCKET_NAME = "td-webapi-files"
```

### Build và chạy ứng dụng

#### Option 1: Automatic Setup (Khuyến nghị)
```powershell
# Setup toàn bộ môi trường development
.\setup-dev.ps1

# Build và chạy
mvn -pl td-web -am spring-boot:run
```

#### Option 2: Manual Build
```powershell
# Build tất cả modules
mvn clean install

# Chạy ứng dụng với environment variables
mvn -pl td-web -am spring-boot:run
```

#### Option 3: Chạy từ IDE
1. Import project vào IDE (IntelliJ IDEA, Eclipse, VS Code)
2. Set environment variables trong IDE run configuration
3. Chạy class `TdWebApiApplication` trong module `td-web`

## Service URLs

Sau khi setup hoàn tất:

- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Keycloak Admin**: http://localhost:8180 (admin/admin)
- **MinIO Console**: http://localhost:9001 (minioadmin/minioadmin)
- **Health Check**: http://localhost:8080/api/health

## API Endpoints

### Products (Requires Authentication)

- `POST /api/v1/products/search` - Tìm kiếm products (USER, ADMIN, PRODUCT_MANAGER)
- `GET /api/v1/products/{id}` - Lấy thông tin product (USER, ADMIN, PRODUCT_MANAGER)
- `POST /api/v1/products` - Tạo product mới (ADMIN, PRODUCT_MANAGER)
- `PUT /api/v1/products/{id}` - Cập nhật product (ADMIN, PRODUCT_MANAGER)
- `DELETE /api/v1/products/{id}` - Xóa product (ADMIN)
- `POST /api/v1/products/export` - Export products (ADMIN, PRODUCT_MANAGER)

### Brands (Requires Authentication)

- `POST /api/v1/brands/search` - Tìm kiếm brands (USER, ADMIN, BRAND_MANAGER)
- `GET /api/v1/brands/{id}` - Lấy thông tin brand (USER, ADMIN, BRAND_MANAGER)
- `POST /api/v1/brands` - Tạo brand mới (ADMIN, BRAND_MANAGER)
- `PUT /api/v1/brands/{id}` - Cập nhật brand (ADMIN, BRAND_MANAGER)
- `DELETE /api/v1/brands/{id}` - Xóa brand (ADMIN)

### File Management (Requires Authentication)

- `POST /api/v1/files/upload` - Upload file (USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER)
- `GET /api/v1/files/download/{id}` - Download file (USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER)
- `DELETE /api/v1/files/{id}` - Delete file (ADMIN or file owner)
- `GET /api/v1/files/info/{id}` - Get file information (USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER)

### Audit Logs (Admin Only)

- `POST /api/v1/audit-logs/search` - Tìm kiếm audit logs (ADMIN)

### Health Check (Public)

- `GET /api/health` - Health check endpoint

## Authentication & Authorization

Ứng dụng sử dụng **Keycloak** cho identity management và **OAuth2/OIDC** cho authentication.

### Test Users (có sẵn trong Keycloak)

| Username | Password | Roles |
|----------|----------|-------|
| admin | admin123 | ADMIN, USER |
| user | user123 | USER |
| product_manager | pm123 | PRODUCT_MANAGER, USER |
| brand_manager | bm123 | BRAND_MANAGER, USER |

### Cách test API

#### 1. Lấy Access Token
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"

$token = $response.access_token
```

#### 2. Call API với Bearer Token
```powershell
$headers = @{ "Authorization" = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/search" `
  -Method POST `
  -Headers $headers `
  -ContentType "application/json" `
  -Body '{"pageIndex": 0, "pageSize": 10}'
```

#### 3. Upload File
```powershell
$form = @{
    file = Get-Item "C:\path\to\file.pdf"
    category = "DOCUMENT"
    description = "Sample document"
}
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/files/upload" `
  -Method POST `
  -Headers $headers `
  -Form $form
```

## Database Schema

### PostgreSQL (Primary Database)
Database được tạo tự động bằng Flyway migrations:

#### Tables
- **brands**: Quản lý thương hiệu
- **products**: Quản lý sản phẩm  
- **file_metadata**: Metadata cho files trong MinIO

#### Sample Data
Migration sẽ tự động tạo sample data cho:
- 3 brands: Samsung, Apple, Sony
- 5 products: Galaxy S24, iPhone 15 Pro, PlayStation 5, Galaxy Tab S9, MacBook Pro

### MongoDB (Logging Database)
MongoDB collections được tạo tự động:

#### Collections
- **audit_logs**: Lưu trữ audit trail cho các thao tác CRUD
- **api_logs**: Lưu trữ API request/response logs

### MinIO (File Storage)
Object storage được tổ chức theo buckets và categories:

#### Default Bucket: `td-webapi-files`

#### File Categories
- **products/** - Product images, specifications
- **brands/** - Brand logos, marketing materials
- **users/** - User avatars, profile pictures
- **documents/** - General documents, contracts
- **temp/** - Temporary uploads
- **system/** - System backups, exports
- **marketing/** - Marketing campaigns, banners
- **support/** - Manuals, help documents

### Keycloak (Identity Provider)
Realm: `td-webapi-realm` với pre-configured users và roles.

## Development

### Code Structure

```
td-webapi-java/
├── td-domain/           # Domain layer
│   └── src/main/java/com/td/domain/
│       ├── catalog/     # Product & Brand entities
│       ├── storage/     # File storage entities
│       ├── logs/        # Audit log entities
│       └── common/      # Base entities & contracts
├── td-application/      # Application layer  
│   └── src/main/java/com/td/application/
│       ├── catalog/     # Catalog use cases & DTOs
│       ├── storage/     # File management use cases
│       ├── logs/        # Audit log use cases
│       └── common/      # Shared interfaces & CQRS
├── td-infrastructure/   # Infrastructure layer
│   └── src/main/java/com/td/infrastructure/
│       ├── config/      # Configurations (Keycloak, MinIO, MongoDB)
│       ├── persistence/ # JPA & MongoDB repositories
│       ├── security/    # OAuth2 security implementations
│       └── storage/     # MinIO service implementations
└── td-web/             # Web layer
    └── src/main/java/com/td/web/
        ├── controllers/ # REST controllers
        └── config/      # Web configurations
├── docker-compose.yml   # Infrastructure setup
├── setup-dev.ps1       # Development setup script
├── keycloak/           # Keycloak realm configuration
└── .env.example        # Environment variables template
```

### Testing

```powershell
# Chạy tất cả tests
mvn test

# Chạy tests cho một module cụ thể
mvn -pl td-domain test

# Build without tests (faster)
mvn clean install -DskipTests

# Integration tests với Testcontainers (future)
mvn verify
```

### Code Quality & Architecture

- **Clean Architecture** - Domain-driven design với clear separation of concerns
- **CQRS Pattern** - Command Query Responsibility Segregation
- **Repository Pattern** - Data access abstraction
- **Dependency Injection** - Spring IoC container
- **Lombok** - Reduce boilerplate code
- **MapStruct** - Type-safe object mapping
- **Bean Validation** - Input validation với annotations
- **OAuth2 Security** - Industry standard authentication/authorization

## Environment Profiles

- **dev** (default): Development environment với Docker services
- **staging**: Staging environment với external services  
- **prod**: Production environment với secure configurations

Thay đổi profile:
```powershell
# PowerShell
$env:SPRING_PROFILES_ACTIVE = "prod"

# Hoặc trong application startup
mvn -pl td-web -am spring-boot:run -Dspring-boot.run.profiles=staging
```

## Troubleshooting

### Common Issues

1. **Port conflicts**: Các services chạy trên ports: 8080, 8180, 9000, 9001, 5432, 27017
2. **Docker issues**: `docker-compose down -v` để reset tất cả volumes
3. **Keycloak not ready**: Đợi 1-2 phút để Keycloak khởi động hoàn toàn
4. **MinIO bucket**: Bucket được tạo tự động lần đầu upload file
5. **Database migrations**: Flyway chạy tự động khi app start

### Logs

```powershell
# Application logs
tail -f logs/td-webapi.log

# Docker logs
docker-compose logs -f keycloak
docker-compose logs -f minio
```

## Additional Documentation

- **PROJECT_USAGE_NOTES.md** - Chi tiết cấu hình và usage notes
- **QUICK_START.md** - Hướng dẫn setup nhanh
- **keycloak/realm-export.json** - Keycloak realm configuration
- **.env.example** - Environment variables template

## Contributing

1. Fork repository
2. Tạo feature branch từ `main`
3. Follow Clean Architecture patterns
4. Write tests cho new features
5. Update documentation
6. Create pull request

## License

MIT License

## Support

- **GitHub Issues** - Bug reports và feature requests
- **Documentation** - Chi tiết trong PROJECT_USAGE_NOTES.md
- **Examples** - Test scripts trong setup-dev.ps1 

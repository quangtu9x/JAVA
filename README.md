# TD WebAPI - Java Spring Boot

> Migration từ .NET TD WebAPI sang Java Spring Boot với Clean Architecture

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📚 Tài liệu

- **[HUONG_DAN_SU_DUNG.md](./HUONG_DAN_SU_DUNG.md)** - Hướng dẫn sử dụng chi tiết bằng tiếng Việt (ƯU TIÊN ĐỌC FILE NÀY TRƯỚC)
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - Kiến trúc hệ thống chi tiết
- **[docs/QUICK_START.md](./docs/QUICK_START.md)** - Cài đặt nhanh
- **[docs/EXTERNAL_SERVICES.md](./docs/EXTERNAL_SERVICES.md)** - Cấu hình services bên ngoài

## 🎯 Tổng quan

TD WebAPI là hệ thống quản lý sản phẩm và thương hiệu được xây dựng bằng **Java Spring Boot** với:

### ✨ Tính năng chính
- 🛒 **Quản lý Sản phẩm & Thương hiệu**: CRUD, tìm kiếm, export Excel
- 🔍 **Tìm kiếm nâng cao**: Elasticsearch full-text search với fuzzy matching
- 📁 **Quản lý File**: Upload/download với MinIO object storage
- 📊 **Audit Logging**: Tự động tracking mọi thay đổi data vào MongoDB
- 🔐 **Authentication**: Keycloak OAuth2/OIDC với JWT
- 📄 **API Documentation**: Swagger/OpenAPI interactive docs

### 🏗️ Kiến trúc

```
┌─────────────────────────────────────────────┐
│           Web Layer (td-web)                │  ← REST API Controllers
├─────────────────────────────────────────────┤
│    Infrastructure (td-infrastructure)       │  ← Repositories, Security, External Services
├─────────────────────────────────────────────┤
│    Application (td-application)             │  ← Use Cases, DTOs, Business Workflows
├─────────────────────────────────────────────┤
│       Domain (td-domain)                    │  ← Entities, Business Logic (Pure Java)
└─────────────────────────────────────────────┘
```

**Clean Architecture Principles:**
- Domain không phụ thuộc vào framework nào
- Application chỉ biết về Domain
- Infrastructure implement interfaces từ Application
- Web layer điều phối tất cả

### 🗄️ Hybrid Database Strategy

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  PostgreSQL  │  │   MongoDB    │  │Elasticsearch │  │    MinIO     │
│  (Primary)   │  │  (Logging)   │  │   (Search)   │  │   (Files)    │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
     Products          AuditLogs        Full-text        Images/Docs
     Brands            ApiLogs          Suggestions      Metadata
```

## 🚀 Quick Start

### 📦 Với Docker Compose (Khuyến nghị)

```bash
# 1. Clone repository
git clone <repo-url>
cd td-webapi-java

# 2. Start tất cả infrastructure services
docker-compose up -d

# 3. Đợi services khởi động (~30-60 giây)
docker-compose logs -f

# 4. Build application
mvn clean install

# 5. Run application
cd td-web
mvn spring-boot:run

# 6. Truy cập Swagger UI
# http://localhost:8080/swagger-ui.html
```

### 🛠️ Setup thủ công (Không Docker)

Chi tiết xem: **[docs/QUICK_START.md](./docs/QUICK_START.md)**

## 🔗 Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| 🌐 Application | http://localhost:8080 | - |
| 📖 Swagger UI | http://localhost:8080/swagger-ui.html | - |
| 🔐 Keycloak Admin | http://localhost:8180 | admin / admin |
| 📦 MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| 🔍 Elasticsearch | http://localhost:9200 | elastic / changeme |

## 📡 API Endpoints

### Authentication - Lấy Access Token

```bash
curl -X POST "http://localhost:8180/realms/td-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=td-client" \
  -d "username=admin" \
  -d "password=admin"
```

### Products API

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/api/v1/products/search` | Tìm kiếm sản phẩm | USER |
| `GET` | `/api/v1/products/{id}` | Chi tiết sản phẩm | USER |
| `POST` | `/api/v1/products` | Tạo sản phẩm | USER |
| `PUT` | `/api/v1/products/{id}` | Cập nhật sản phẩm | USER |
| `DELETE` | `/api/v1/products/{id}` | Xóa sản phẩm | USER |
| `POST` | `/api/v1/products/export` | Export Excel | USER |

### Brands API

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/api/v1/brands/search` | Tìm kiếm thương hiệu | USER |
| `GET` | `/api/v1/brands/{id}` | Chi tiết thương hiệu | USER |
| `POST` | `/api/v1/brands` | Tạo thương hiệu | USER |
| `PUT` | `/api/v1/brands/{id}` | Cập nhật thương hiệu | USER |
| `DELETE` | `/api/v1/brands/{id}` | Xóa thương hiệu | USER |

### Search API (Elasticsearch)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/api/v1/search/products/advanced` | Tìm kiếm nâng cao | USER |
| `GET` | `/api/v1/search/suggestions?q={query}` | Gợi ý tìm kiếm | USER |
| `POST` | `/api/v1/search/admin/reindex` | Reindex Elasticsearch | ADMIN |

### Files API (MinIO)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/api/v1/files/upload` | Upload file | USER |
| `GET` | `/api/v1/files/{id}/download` | Download file | USER |
| `DELETE` | `/api/v1/files/{id}` | Xóa file | USER |

### Audit Logs API

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| `POST` | `/api/v1/audit-logs/search` | Tìm audit logs | ADMIN |

**Chi tiết sử dụng API:** Xem [HUONG_DAN_SU_DUNG.md](./HUONG_DAN_SU_DUNG.md)

## 🔐 Authentication & Authorization

### Keycloak OAuth2/OIDC Flow

1. **Login** → Lấy access token từ Keycloak
2. **API Request** → Gửi token trong header `Authorization: Bearer {token}`
3. **Validation** → Spring Security verify token với Keycloak
4. **Authorization** → Check roles với `@PreAuthorize("hasRole('USER')")`

### Test Users (trong Keycloak)

| Username | Password | Roles | Quyền |
|----------|----------|-------|-------|
| admin | admin | ADMIN, USER | Tất cả API |
| user | user | USER | Read Products/Brands |

## 🏗️ Cấu trúc Project

```
td-webapi-java/
├── td-domain/                  # Domain Layer (Pure Java)
│   ├── catalog/                # Product, Brand entities
│   ├── logs/                   # AuditLog, ApiLog (MongoDB documents)
│   ├── search/                 # ProductDocument (Elasticsearch)
│   ├── storage/                # FileMetadata
│   └── common/                 # Base entities, interfaces
│
├── td-application/             # Application Layer (Use Cases)
│   ├── catalog/products/       # CreateProduct, SearchProducts, etc.
│   ├── catalog/brands/         # CreateBrand, SearchBrands, etc.
│   ├── search/                 # AdvancedSearch, Suggestions
│   ├── storage/                # UploadFile, DownloadFile
│   ├── logs/                   # GetAuditLogs
│   └── common/                 # Result, PaginationResponse
│
├── td-infrastructure/          # Infrastructure Layer (Technical)
│   ├── config/                 # Database, Security, Elasticsearch configs
│   ├── persistence/            # JPA & MongoDB repositories
│   ├── search/                 # Elasticsearch repositories
│   ├── storage/                # MinIO service
│   └── security/               # Keycloak JWT converter
│
├── td-web/                     # Web Layer (REST API)
│   ├── controllers/v1/         # ProductsController, BrandsController, etc.
│   ├── config/                 # OpenAPI/Swagger config
│   └── resources/
│       ├── application.yml     # Main configuration
│       └── db/migration/       # Flyway SQL scripts
│
├── docs/                       # Documentation
│   ├── QUICK_START.md
│   ├── EXTERNAL_SERVICES.md
│   └── ...
│
├── ARCHITECTURE.md             # Kiến trúc chi tiết (tiếng Việt)
├── HUONG_DAN_SU_DUNG.md       # Hướng dẫn sử dụng (tiếng Việt) ⭐
├── docker-compose.yml          # Docker services
└── pom.xml                     # Maven parent POM
```

## 🛠️ Tech Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| **Language** | Java 17 | Primary language |
| **Framework** | Spring Boot 3.2.0 | Application framework |
| **Security** | Spring Security 6.2.0 + Keycloak | OAuth2/OIDC authentication |
| **Database (SQL)** | PostgreSQL 12+ | Primary transactional data |
| **Database (NoSQL)** | MongoDB 5.0+ | Audit logs, API logs |
| **Search** | Elasticsearch 8+ | Full-text search |
| **Storage** | MinIO | S3-compatible object storage |
| **Migration** | Flyway | Database version control |
| **ORM** | Spring Data JPA | PostgreSQL ORM |
| **Mapping** | MapStruct | DTO ↔ Entity mapping |
| **Validation** | Hibernate Validator | Bean validation |
| **Documentation** | SpringDoc OpenAPI | Swagger UI |
| **Build** | Maven 3.8+ | Build automation |

## 📊 Database Schema

### PostgreSQL Tables

- `products` - Sản phẩm (id, name, description, rate, brand_id, image_path, audit fields)
- `brands` - Thương hiệu (id, name, description, audit fields)
- `file_metadata` - Metadata files (id, file_name, content_type, file_size, storage_path)

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

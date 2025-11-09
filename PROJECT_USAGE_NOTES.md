# TD WebAPI Java - Lưu Ý Sử Dụng Dự Án

*Tạo ngày: 9 tháng 11, 2025*

## 📋 Tổng Quan Dự Án

### Kiến Trúc
- **Multi-module Maven** theo Clean Architecture
- **Modules**: `td-domain` → `td-application` → `td-infrastructure` → `td-web`
- **Main Application**: `com.td.web.TdWebApiApplication`

### Công Nghệ Chính
- **Java 17**, **Spring Boot 3.2.0**, **Spring Security 6.2.0**
- **Database**: PostgreSQL (chính) + MongoDB (logging)
- **Security**: JWT Authentication với Auth0 java-jwt
- **Migration**: Flyway
- **Tools**: MapStruct, Lombok, SpringDoc OpenAPI

---

## 🚀 Hướng Dẫn Build & Run (Windows PowerShell)

### Yêu Cầu Hệ Thống
```powershell
# Kiểm tra Java
java -version  # Cần Java 17+
mvn -version   # Cần Maven 3.8+
```

### Build Toàn Bộ Dự Án
```powershell
# Từ thư mục gốc dự án
cd d:\TD.WebAPI\td-webapi-java

# Build tất cả modules
mvn clean install

# Build nhanh (skip tests)
mvn clean package -DskipTests
```

### Chạy Ứng Dụng

#### Cách 1: Maven Spring Boot Plugin
```powershell
# Chạy từ module td-web
mvn -pl td-web -am spring-boot:run

# Hoặc chạy với profile cụ thể
mvn -pl td-web -am spring-boot:run -Dspring-boot.run.profiles=dev
```

#### Cách 2: Chạy JAR
```powershell
# Sau khi mvn package
java -jar td-web/target/td-web-1.0.0-SNAPSHOT.jar

# Với profile
java -jar td-web/target/td-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

#### Cách 3: IDE
- Import project vào IntelliJ IDEA/Eclipse/VS Code
- Chạy class `TdWebApiApplication` trong module `td-web`

---

## ⚙️ Cấu Hình Biến Môi Trường

### Database (PostgreSQL)
```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/tdwebapi"
$env:DATABASE_USERNAME = "postgres"
$env:DATABASE_PASSWORD = "postgres"
```

### MongoDB (Logging)
```powershell
$env:MONGODB_HOST = "localhost"
$env:MONGODB_PORT = "27017"
$env:MONGODB_DATABASE = "tdwebapi_logs"
$env:MONGODB_USERNAME = ""
$env:MONGODB_PASSWORD = ""
$env:MONGODB_AUTH_DB = "admin"
```

### JWT Security
```powershell
# ⚠️ QUAN TRỌNG: Thay đổi secret trên production
$env:JWT_SECRET = "your-very-secure-secret-key-minimum-256-bits"
```

### Spring Profiles
```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"  # dev, staging, prod
```

### Chạy với Environment Variables
```powershell
# Thiết lập tất cả biến môi trường rồi chạy
mvn -pl td-web -am spring-boot:run
```

---

## 🗃️ Cấu Hình Database

### PostgreSQL Setup
1. **Cài đặt PostgreSQL 12+**
2. **Tạo database**:
   ```sql
   CREATE DATABASE tdwebapi;
   ```
3. **Flyway sẽ tự động tạo schema** khi app khởi động

### MongoDB Setup
1. **Cài đặt MongoDB 5.0+**
2. **Database sẽ được tạo tự động**: `tdwebapi_logs`
3. **Collections**: `audit_logs`, `api_logs`

### Flyway Migrations
- **Location**: `td-web/src/main/resources/db/migration/`
- **File hiện tại**: `V1.0.1__Create_Initial_Tables.sql`
- **Auto-run**: Khi app start (enabled = true)

---

## 🔐 Cấu Hình Security

### JWT Configuration
- **Access Token**: 1 giờ (mặc định)
- **Refresh Token**: 24 giờ (mặc định)
- **Algorithm**: HMAC256
- **Issuer**: TD.WebAPI

### API Endpoints Security
```yaml
Public Endpoints:
  - /api/v1/auth/**
  - /api/health/**
  - /swagger-ui/**, /v3/api-docs/**

Protected Endpoints (USER/ADMIN):
  - /api/v1/products/**
  - /api/v1/brands/**

Admin Only:
  - /api/v1/admin/**
```

### CORS Configuration
- **Development**: Cho phép tất cả origins
- **Production**: ⚠️ Cần tighten origins

---

## 📁 Cấu Trúc File Quan Trọng

### Configuration Files
```
td-web/src/main/resources/
├── application.yml           # Main configuration
└── db/migration/            # Flyway migrations
    └── V1.0.1__Create_Initial_Tables.sql
```

### Key Classes
```
td-infrastructure/src/main/java/com/td/infrastructure/
├── config/
│   ├── MongoConfig.java           # MongoDB config (app.mongodb.*)
│   ├── MongoClientConfig.java     # MongoDB client setup
│   └── DatabaseConfig.java       # PostgreSQL config (app.database.*)
└── security/
    ├── SecurityConfig.java        # Security rules & CORS
    ├── JwtProperties.java         # JWT settings (app.security.jwt.*)
    ├── JwtService.java            # JWT token operations
    └── JwtAuthenticationFilter.java # JWT request filter
```

---

## ⚠️ Lưu Ý Quan Trọng & Gotchas

### 1. Cấu Hình Không Nhất Quán
**Vấn đề**: 
- `application.yml` sử dụng `spring.datasource.*` và `spring.data.mongodb.*`
- Nhưng `DatabaseConfig`/`MongoConfig` sử dụng `@ConfigurationProperties` với prefix `app.database.*`/`app.mongodb.*`

**Giải pháp**:
- **Option A**: Sử dụng Spring Boot auto-config (recommend)
- **Option B**: Thêm `app.database.*` và `app.mongodb.*` vào `application.yml`

### 2. JWT Secret Security
**⚠️ CRITICAL**: 
- Mặc định có secret trong `application.yml`
- **KHÔNG BAO GIỜ** sử dụng secret mặc định trên production
- Sử dụng Azure Key Vault, AWS Secrets Manager, hoặc secure env vars

### 3. JWT Roles Mapping
**Vấn đề**: Spring Security `hasAnyRole("USER")` tìm `ROLE_USER`, nhưng filter map roles as-is.
**Giải pháp**: Đảm bảo roles trong JWT token có format đúng hoặc adjust mapping logic.

### 4. Database Migration
- **Flyway enabled**: Migrations chạy tự động
- **JPA ddl-auto**: `validate` - schema phải khớp với entities
- **Đảm bảo**: Database `tdwebapi` tồn tại trước khi chạy app

### 5. IDE Setup
- **Bật Annotation Processing** cho Lombok và MapStruct
- **IntelliJ**: Settings → Build → Compiler → Annotation Processors → Enable
- **Eclipse**: Project Properties → Java Build Path → Annotation Processing

---

## 🔍 API Documentation

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON
```
http://localhost:8080/v3/api-docs
```

### Main Endpoints
```
Products:
  POST /api/v1/products/search
  GET  /api/v1/products/{id}
  POST /api/v1/products
  PUT  /api/v1/products/{id}
  DELETE /api/v1/products/{id}
  POST /api/v1/products/export

Brands:
  POST /api/v1/brands/search
  GET  /api/v1/brands/{id}
  POST /api/v1/brands
  PUT  /api/v1/brands/{id}
  DELETE /api/v1/brands/{id}

Audit Logs:
  POST /api/v1/audit-logs/search (Admin only)

Health:
  GET /api/health
```

---

## 🧪 Testing

### Unit Tests
```powershell
# Chạy tất cả tests
mvn test

# Chạy tests cho module cụ thể
mvn -pl td-domain test
```

### Integration Tests
```powershell
# Chạy integration tests (nếu có)
mvn verify
```

---

## 🛠️ Troubleshooting

### Common Issues

#### 1. Port Already in Use
```powershell
# Thay đổi port trong application.yml hoặc:
java -jar app.jar --server.port=8081
```

#### 2. Database Connection Failed
```powershell
# Kiểm tra PostgreSQL đang chạy:
pg_ctl status

# Kiểm tra MongoDB:
mongosh --eval "db.adminCommand('ping')"
```

#### 3. Flyway Migration Failed
```powershell
# Chạy Flyway repair nếu cần:
mvn flyway:repair -Dflyway.url=$env:DATABASE_URL
```

#### 4. JWT Token Invalid
- Kiểm tra JWT secret đúng
- Kiểm tra token chưa expired
- Kiểm tra roles format trong token

#### 5. Lombok/MapStruct Not Working
- Bật annotation processing trong IDE
- Reimport Maven project
- Clean và rebuild

---

## 📋 Khuyến Nghị Cải Thiện

### Ưu Tiên Cao
1. **Thống nhất cấu hình**: Chọn `spring.*` OR `app.*` prefixes
2. **Bảo mật JWT**: Sử dụng secure secret management
3. **Docker Compose**: Thêm PostgreSQL + MongoDB setup
4. **Environment Template**: Tạo `.env.example`

### Ưu Tiên Trung Bình
5. **Integration Tests**: Thêm Testcontainers tests
6. **CI/CD Pipeline**: GitHub Actions hoặc Azure DevOps
7. **Health Checks**: Cải thiện monitoring endpoints
8. **Logging**: Cấu hình structured logging

### Ưu Tiên Thấp
9. **API Versioning**: Strategy cho breaking changes
10. **Caching**: Redis cho performance
11. **Documentation**: OpenAPI examples và descriptions
12. **Metrics**: Prometheus/Micrometer integration

---

## 📞 Support & Resources

### Useful Commands
```powershell
# Xem dependency tree
mvn dependency:tree

# Analyze dependencies
mvn dependency:analyze

# Generate site documentation
mvn site

# Run specific test class
mvn test -Dtest=BrandServiceTest
```

### Configuration References
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Flyway Documentation](https://flywaydb.org/documentation/)

### Project Structure References
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Spring Boot Multi-Module](https://spring.io/guides/gs/multi-module/)

---

## 📝 Changelog & Notes

### Version 1.0.0-SNAPSHOT
- Initial Clean Architecture setup
- JWT Authentication implementation  
- PostgreSQL + MongoDB integration
- Flyway migrations
- Basic CRUD for Products and Brands
- OpenAPI documentation

### Known Issues
- Configuration prefix inconsistency (DatabaseConfig/MongoConfig vs application.yml)
- JWT roles mapping needs standardization
- CORS policy too permissive for production

### TODO
- [ ] Add Testcontainers integration tests
- [ ] Implement user authentication endpoints
- [ ] Add API rate limiting
- [ ] Implement audit logging for all operations
- [ ] Add Docker containerization
- [ ] Production-ready configuration profiles

---

*📅 Cập nhật lần cuối: 9 tháng 11, 2025*
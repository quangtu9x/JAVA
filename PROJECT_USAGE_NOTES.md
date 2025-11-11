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
- **File Storage**: MinIO Object Storage
- **Security**: Keycloak OAuth2/OIDC Integration
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

### Keycloak Security
```powershell
# Keycloak Server Configuration
$env:KEYCLOAK_SERVER_URL = "http://localhost:8180"
$env:KEYCLOAK_REALM = "td-webapi-realm"
$env:KEYCLOAK_CLIENT_ID = "td-webapi-client"
$env:KEYCLOAK_CLIENT_SECRET = "your-keycloak-client-secret"

# Optional: Custom Keycloak Admin (for user management)
$env:KEYCLOAK_ADMIN_USERNAME = "admin"
$env:KEYCLOAK_ADMIN_PASSWORD = "admin-password"
```

### MinIO File Storage
```powershell
# MinIO Configuration
$env:MINIO_URL = "http://localhost:9000"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_BUCKET_NAME = "td-webapi-files"
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

### Quick Setup với Docker Compose
```yaml
# docker-compose.yml (example)
version: '3.8'
services:
  keycloak:
    image: quay.io/keycloak/keycloak:22.0.1
    ports:
      - "8180:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
    
  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: tdwebapi
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_DATABASE: tdwebapi_logs
```

```powershell
# Start tất cả services
docker-compose up -d

# Chạy application
mvn -pl td-web -am spring-boot:run
```

---

## 🗃️ Cấu Hình Database & Keycloak

### Keycloak Setup
1. **Cài đặt Keycloak 22+**
   ```powershell
   # Download và chạy Keycloak
   docker run -p 8180:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:22.0.1 start-dev
   ```

2. **Cấu hình Realm và Client**:
   - Tạo realm: `td-webapi-realm`
   - Tạo client: `td-webapi-client`
   - Client Type: `confidential`
   - Authentication flow: `Standard Flow + Direct Access Grants`
   - Valid redirect URIs: `http://localhost:8080/*`

3. **Tạo Roles**:
   - `USER` - Basic user access
   - `ADMIN` - Admin access
   - `BRAND_MANAGER` - Brand management
   - `PRODUCT_MANAGER` - Product management

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

## 🔐 Cấu Hình Security với Keycloak

### Keycloak OAuth2/OIDC Configuration
- **Authorization Server**: Keycloak (port 8180)
- **Token Type**: JWT (RS256 - RSA signature)
- **Token Validation**: JWK Set từ Keycloak
- **Scopes**: `openid`, `profile`, `email`, `roles`

### API Endpoints Security
```yaml
Public Endpoints:
  - /api/health/**
  - /swagger-ui/**, /v3/api-docs/**
  - /login/oauth2/**, /oauth2/**

Protected Endpoints:
  - /api/v1/products/** (Roles: USER, ADMIN, PRODUCT_MANAGER)
  - /api/v1/brands/** (Roles: USER, ADMIN, BRAND_MANAGER)
  - /api/v1/admin/** (Roles: ADMIN)

Authentication Flow:
  1. Frontend redirect to Keycloak login
  2. User login tại Keycloak
  3. Keycloak redirect về với authorization code
  4. Backend exchange code → access token
  5. API calls với Bearer token
```

### Keycloak Roles Mapping
```yaml
Keycloak Role → Spring Authority:
  - USER → ROLE_USER
  - ADMIN → ROLE_ADMIN  
  - BRAND_MANAGER → ROLE_BRAND_MANAGER
  - PRODUCT_MANAGER → ROLE_PRODUCT_MANAGER
```

### CORS Configuration
- **Development**: Cho phép Keycloak + localhost
- **Production**: Specific domains only

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
│   ├── DatabaseConfig.java       # PostgreSQL config (app.database.*)
│   └── KeycloakConfig.java        # Keycloak OAuth2 config
└── security/
    ├── SecurityConfig.java        # Security rules & OAuth2 setup
    ├── KeycloakProperties.java    # Keycloak settings (app.keycloak.*)
    ├── KeycloakJwtConverter.java  # JWT to Spring authorities converter
    └── KeycloakRoleMapper.java    # Keycloak roles → Spring roles mapping
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

### 2. Keycloak Client Secret Security
**⚠️ CRITICAL**: 
- Client secret cần được bảo mật
- **KHÔNG BAO GIỜ** commit client secret vào code
- Sử dụng Azure Key Vault, AWS Secrets Manager, hoặc secure env vars
- Rotate client secret định kỳ

### 3. Keycloak Roles Mapping
**Cấu hình**: Keycloak roles được map từ JWT claims sang Spring authorities
**Lưu ý**: 
- Keycloak roles nằm trong `realm_access.roles` hoặc `resource_access.{client}.roles`
- Spring Security cần prefix `ROLE_` cho authorities
- Custom converter sẽ handle mapping: `USER` → `ROLE_USER`

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
  POST /api/v1/products/search     (Roles: USER, ADMIN, PRODUCT_MANAGER)
  GET  /api/v1/products/{id}       (Roles: USER, ADMIN, PRODUCT_MANAGER)
  POST /api/v1/products            (Roles: ADMIN, PRODUCT_MANAGER)
  PUT  /api/v1/products/{id}       (Roles: ADMIN, PRODUCT_MANAGER)
  DELETE /api/v1/products/{id}     (Roles: ADMIN)
  POST /api/v1/products/export     (Roles: ADMIN, PRODUCT_MANAGER)

Brands:
  POST /api/v1/brands/search       (Roles: USER, ADMIN, BRAND_MANAGER)
  GET  /api/v1/brands/{id}         (Roles: USER, ADMIN, BRAND_MANAGER)
  POST /api/v1/brands              (Roles: ADMIN, BRAND_MANAGER)
  PUT  /api/v1/brands/{id}         (Roles: ADMIN, BRAND_MANAGER)
  DELETE /api/v1/brands/{id}       (Roles: ADMIN)

Audit Logs:
  POST /api/v1/audit-logs/search   (Roles: ADMIN)

Health:
  GET /api/health                  (Public)
```

### Testing với Keycloak Token
```powershell
# 1. Get access token từ Keycloak
$response = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=YOUR_CLIENT_SECRET&grant_type=password&username=testuser&password=testpass"

$token = $response.access_token

# 2. Call API với Bearer token
$headers = @{ "Authorization" = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/search" `
  -Method POST `
  -Headers $headers `
  -ContentType "application/json" `
  -Body '{"pageIndex": 0, "pageSize": 10}'
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

#### 4. Keycloak Authentication Failed
```powershell
# Kiểm tra Keycloak server đang chạy:
curl http://localhost:8180/realms/td-webapi-realm/.well-known/openid_configuration

# Kiểm tra token validity:
curl -X POST http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token-introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=td-webapi-client&client_secret=YOUR_SECRET&token=YOUR_TOKEN"
```
- Kiểm tra Keycloak realm và client config
- Kiểm tra redirect URIs đúng
- Kiểm tra client secret không expired

#### 5. Lombok/MapStruct Not Working
- Bật annotation processing trong IDE
- Reimport Maven project
- Clean và rebuild

---

## 📋 Khuyến Nghị Cải Thiện

### Ưu Tiên Cao
1. **Thống nhất cấu hình**: Chọn `spring.*` OR `app.*` prefixes
2. **Keycloak Production Setup**: Multi-node, SSL, custom themes
3. **Docker Compose**: Thêm Keycloak + PostgreSQL + MongoDB setup
4. **Environment Template**: Tạo `.env.example` với Keycloak vars

### Ưu Tiên Trung Bình
5. **Integration Tests**: Thêm Testcontainers với Keycloak
6. **CI/CD Pipeline**: GitHub Actions với Keycloak testing
7. **Health Checks**: Monitor Keycloak connectivity
8. **Keycloak Themes**: Custom login/registration UI

### Ưu Tiên Thấp
9. **Keycloak Extensions**: Custom authenticators, protocols
10. **Social Login**: Google, Facebook, GitHub integration
11. **Advanced RBAC**: Fine-grained permissions với Keycloak
12. **SSO Integration**: SAML, LDAP, Active Directory

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
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Spring Security Adapter](https://www.keycloak.org/docs/latest/securing_apps/#_spring_security_adapter)
- [Flyway Documentation](https://flywaydb.org/documentation/)

### Project Structure References
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Spring Boot Multi-Module](https://spring.io/guides/gs/multi-module/)

---

## 📝 Changelog & Notes

### Version 1.0.0-SNAPSHOT
- Initial Clean Architecture setup
- Keycloak OAuth2/OIDC Integration
- PostgreSQL + MongoDB integration
- Flyway migrations
- Basic CRUD for Products and Brands
- OpenAPI documentation với OAuth2 security

### Known Issues
- Configuration prefix inconsistency (DatabaseConfig/MongoConfig vs application.yml)
- Keycloak roles mapping cần custom converter
- CORS policy cần cấu hình cho Keycloak origins
- Production Keycloak setup chưa có SSL

### TODO
- [ ] Add Testcontainers với Keycloak testing
- [ ] Implement Keycloak user management endpoints
- [ ] Add API rate limiting với Keycloak integration
- [ ] Implement audit logging với user context từ Keycloak
- [ ] Add Docker Compose với Keycloak
- [ ] Production-ready Keycloak configuration

---

*📅 Cập nhật lần cuối: 9 tháng 11, 2025*
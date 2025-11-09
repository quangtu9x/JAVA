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
- **Spring Security 6.2.0** - JWT Authentication
- **Spring Data JPA** - ORM và Repository pattern cho PostgreSQL
- **Spring Data MongoDB** - NoSQL database cho logging
- **PostgreSQL** - Primary relational database
- **MongoDB** - Document database cho audit logs và API logs
- **Flyway** - Database migration
- **MapStruct** - Object mapping
- **Lombok** - Code generation
- **SpringDoc OpenAPI** - API documentation
- **Maven** - Build tool

## Cài đặt và chạy

### Yêu cầu hệ thống

- Java 17 hoặc cao hơn
- Maven 3.8+
- PostgreSQL 12+ 
- MongoDB 5.0+

### Cấu hình Database

#### PostgreSQL (Primary Database)
1. Cài đặt PostgreSQL 12+
2. Tạo database tên `tdwebapi`
3. Cập nhật connection string trong `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tdwebapi
    username: postgres
    password: postgres
```

#### MongoDB (Logging Database)
1. Cài đặt MongoDB 5.0+
2. Tạo database tên `tdwebapi_logs`
3. Cập nhật MongoDB configuration trong `application.yml`:

```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: tdwebapi_logs
```

### Build và chạy ứng dụng

```bash
# Clone repository
git clone <repository-url>
cd td-webapi-java

# Build tất cả modules
mvn clean install

# Chạy ứng dụng
cd td-web
mvn spring-boot:run
```

### Hoặc chạy từ IDE

1. Import project vào IDE (IntelliJ IDEA, Eclipse, VS Code)
2. Chạy class `TdWebApiApplication` trong module `td-web`

## API Documentation

Sau khi ứng dụng chạy, truy cập:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## API Endpoints

### Products

- `POST /api/v1/products/search` - Tìm kiếm products
- `GET /api/v1/products/{id}` - Lấy thông tin product
- `POST /api/v1/products` - Tạo product mới
- `PUT /api/v1/products/{id}` - Cập nhật product
- `DELETE /api/v1/products/{id}` - Xóa product
- `POST /api/v1/products/export` - Export products

### Brands

- `POST /api/v1/brands/search` - Tìm kiếm brands
- `GET /api/v1/brands/{id}` - Lấy thông tin brand
- `POST /api/v1/brands` - Tạo brand mới
- `PUT /api/v1/brands/{id}` - Cập nhật brand
- `DELETE /api/v1/brands/{id}` - Xóa brand

### Audit Logs

- `POST /api/v1/audit-logs/search` - Tìm kiếm audit logs (Admin only)

### Health Check

- `GET /api/health` - Health check endpoint

## Security

Ứng dụng sử dụng JWT cho authentication. Để test API:

1. Tạm thời các endpoints đã được cấu hình để yêu cầu role `USER` hoặc `ADMIN`
2. Trong môi trường development, có thể tạm thời disable security để test
3. Sẽ implement đầy đủ authentication/authorization endpoints trong phiên bản tiếp theo

## Database Schema

### PostgreSQL (Primary Database)
Database được tạo tự động bằng Flyway migrations:

#### Tables
- **brands**: Quản lý thương hiệu
- **products**: Quản lý sản phẩm

#### Sample Data
Migration sẽ tự động tạo sample data cho:
- 3 brands: Samsung, Apple, Sony
- 5 products: Galaxy S24, iPhone 15 Pro, PlayStation 5, Galaxy Tab S9, MacBook Pro

### MongoDB (Logging Database)
MongoDB collections được tạo tự động:

#### Collections
- **audit_logs**: Lưu trữ audit trail cho các thao tác CRUD
- **api_logs**: Lưu trữ API request/response logs

## Development

### Code Structure

```
td-webapi-java/
├── td-domain/           # Domain layer
│   └── src/main/java/com/td/domain/
│       ├── catalog/     # Product & Brand entities
│       └── common/      # Base entities & contracts
├── td-application/      # Application layer  
│   └── src/main/java/com/td/application/
│       ├── catalog/     # Use cases & DTOs
│       └── common/      # Shared interfaces
├── td-infrastructure/   # Infrastructure layer
│   └── src/main/java/com/td/infrastructure/
│       ├── config/      # Configurations
│       ├── persistence/ # Repositories
│       └── security/    # Security implementations
└── td-web/             # Web layer
    └── src/main/java/com/td/web/
        ├── controllers/ # REST controllers
        └── config/      # Web configurations
```

### Testing

```bash
# Chạy tất cả tests
mvn test

# Chạy tests cho một module cụ thể
cd td-domain
mvn test
```

### Code Quality

- Sử dụng Lombok để giảm boilerplate code
- MapStruct cho object mapping
- Validation annotations cho input validation
- Clean Architecture patterns

## Environment Profiles

- **dev** (default): Development environment
- **staging**: Staging environment  
- **prod**: Production environment

Thay đổi profile bằng cách set environment variable:
```bash
export SPRING_PROFILES_ACTIVE=prod
```

## Contributing

1. Fork repository
2. Tạo feature branch
3. Commit changes
4. Create pull request

## License

MIT License"# JAVA" 

# Kiến trúc TD WebAPI - Java Spring Boot

## Tổng quan

TD WebAPI được xây dựng theo **Clean Architecture** (Kiến trúc sạch), đảm bảo tách biệt rõ ràng giữa business logic và technical infrastructure.

## Nguyên tắc Clean Architecture

```
┌─────────────────────────────────────────────┐
│           Web Layer (td-web)                │
│   Controllers, Main App, Configurations     │
├─────────────────────────────────────────────┤
│    Infrastructure Layer (td-infrastructure)  │
│   Repositories, Security, External Services  │
├─────────────────────────────────────────────┤
│    Application Layer (td-application)        │
│   Use Cases, DTOs, Business Workflows        │
├─────────────────────────────────────────────┤
│       Domain Layer (td-domain)               │
│   Entities, Aggregate Roots, Domain Logic    │
└─────────────────────────────────────────────┘
```

**Quy tắc phụ thuộc (Dependency Rule):**
- Domain không phụ thuộc vào layer nào khác (Pure Java)
- Application chỉ phụ thuộc vào Domain
- Infrastructure phụ thuộc vào Domain và Application
- Web phụ thuộc vào tất cả các layer khác

## Chi tiết các Module

### 1. td-domain (Domain Layer)

**Mục đích:** Chứa core business logic và domain model

**Thành phần chính:**
```
td-domain/
├── catalog/
│   ├── Product.java          # Entity sản phẩm với business methods
│   └── Brand.java            # Entity thương hiệu
├── logs/
│   ├── AuditLog.java         # MongoDB document cho audit tracking
│   └── ApiLog.java           # MongoDB document cho API logging
├── search/
│   ├── ProductDocument.java  # Elasticsearch document cho search
│   └── BrandDocument.java
├── storage/
│   └── FileMetadata.java     # Entity quản lý file metadata
└── common/
    ├── contracts/
    │   ├── AuditableEntity.java    # Base entity với audit fields
    │   ├── AbstractEntity.java      # Base entity với ID
    │   ├── IAggregateRoot.java      # Marker interface
    │   ├── ISoftDelete.java         # Interface cho soft delete
    │   └── IAuditableEntity.java
    └── events/
        └── DomainEvent.java          # Base class cho domain events
```

**Đặc điểm:**
- **Không có dependencies** ngoài Java standard library
- Entities sử dụng **JPA annotations** (jakarta.persistence.*)
- Business logic trong domain entities (VD: `Product.applyDiscount()`)
- Audit tracking tự động qua `@PrePersist` và `@PreUpdate`

**Ví dụ sử dụng:**
```java
// Tạo sản phẩm mới
Product product = new Product(
    "iPhone 15",
    "Flagship phone", 
    new BigDecimal("29990000"),
    brandId,
    "/images/iphone15.jpg"
);

// Business logic
if (product.isExpensive()) {
    product.applyDiscount(new BigDecimal("10")); // Giảm 10%
}

// Soft delete
product.markAsDeleted(userId);
```

---

### 2. td-application (Application Layer)

**Mục đích:** Orchestrate use cases và business workflows

**Thành phần chính:**
```
td-application/
├── catalog/
│   ├── products/
│   │   ├── CreateProductUseCase.java      # UC: Tạo sản phẩm
│   │   ├── UpdateProductUseCase.java      # UC: Cập nhật sản phẩm
│   │   ├── DeleteProductUseCase.java      # UC: Xóa sản phẩm
│   │   ├── GetProductUseCase.java         # UC: Lấy chi tiết sản phẩm
│   │   ├── SearchProductsUseCase.java     # UC: Tìm kiếm sản phẩm
│   │   ├── ExportProductsUseCase.java     # UC: Export Excel
│   │   ├── ProductDto.java                # DTO cho response
│   │   ├── ProductDetailsDto.java         # DTO chi tiết
│   │   ├── CreateProductRequest.java      # Request DTO
│   │   └── SearchProductsRequest.java     # Filter DTO
│   └── brands/
│       └── (Tương tự products)
├── logs/
│   └── GetAuditLogsUseCase.java           # UC: Lấy audit logs
├── search/
│   └── AdvancedSearchProductsUseCase.java # UC: Tìm kiếm nâng cao (Elasticsearch)
├── storage/
│   ├── UploadFileUseCase.java             # UC: Upload file
│   ├── DownloadFileUseCase.java           # UC: Download file
│   └── DeleteFileUseCase.java             # UC: Xóa file
└── common/
    ├── interfaces/
    │   ├── IRepository.java               # Generic repository interface
    │   └── ISearchService.java            # Search service interface
    └── models/
        ├── Result.java                    # Wrapper cho success/failure
        └── PaginationResponse.java        # Wrapper cho paginated data
```

**Đặc điểm:**
- Mỗi use case là 1 class với method `execute(Request) -> Response`
- DTOs chỉ chứa data, không có logic
- Validation qua Bean Validation annotations
- Transaction management qua `@Transactional`

**Ví dụ Use Case:**
```java
@Service
@RequiredArgsConstructor
@Transactional
public class CreateProductUseCase {
    private final IRepository<Product> productRepository;
    private final IRepository<Brand> brandRepository;

    public Result<UUID> execute(CreateProductRequest request) {
        // 1. Validate brand exists
        var brand = brandRepository.findById(request.getBrandId());
        if (brand.isEmpty()) {
            return Result.failure("Brand not found");
        }

        // 2. Create entity
        var product = new Product(
            request.getName(),
            request.getDescription(),
            request.getRate(),
            request.getBrandId(),
            request.getImagePath()
        );

        // 3. Save
        var saved = productRepository.save(product);
        
        return Result.success(saved.getId());
    }
}
```

---

### 3. td-infrastructure (Infrastructure Layer)

**Mục đích:** Technical implementation của interfaces từ Application layer

**Thành phần chính:**
```
td-infrastructure/
├── config/
│   ├── DatabaseConfig.java          # PostgreSQL configuration
│   ├── MongoConfig.java             # MongoDB configuration
│   ├── MongoClientConfig.java       # MongoDB client setup
│   ├── AuditingConfig.java          # JPA auditing
│   ├── ElasticsearchConfig.java     # Elasticsearch setup
│   └── MinIOConfig.java             # MinIO object storage
├── persistence/
│   ├── repository/
│   │   ├── BaseRepository.java            # Generic JPA repository
│   │   ├── ProductRepository.java         # Product repository với Specifications
│   │   └── BrandRepository.java
│   └── mongo/
│       ├── AuditLogRepository.java        # MongoDB repository cho audit
│       └── ApiLogRepository.java
├── search/
│   ├── ProductElasticsearchRepository.java
│   └── BrandElasticsearchRepository.java
├── storage/
│   ├── MinIOService.java                  # MinIO file operations
│   └── MinIOProperties.java
└── security/
    ├── SecurityConfig.java                # Spring Security configuration
    ├── KeycloakJwtConverter.java          # Convert Keycloak JWT to Spring Authentication
    └── KeycloakProperties.java            # Keycloak connection properties
```

**Đặc điểm:**
- Implement interfaces từ Application layer
- JPA Specification Pattern cho dynamic queries
- Spring Data MongoDB cho NoSQL
- Keycloak OAuth2 Resource Server
- MinIO cho object storage

**Ví dụ Repository:**
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, 
                                           JpaSpecificationExecutor<Product> {
    
    // Static Specification methods cho dynamic filtering
    static Specification<Product> withName(String name) {
        return (root, query, cb) -> 
            cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
    
    static Specification<Product> withBrandId(UUID brandId) {
        return (root, query, cb) -> 
            cb.equal(root.get("brandId"), brandId);
    }
    
    static Specification<Product> withRateRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("rate"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("rate"), min);
            } else if (max != null) {
                return cb.lessThanOrEqualTo(root.get("rate"), max);
            }
            return cb.conjunction();
        };
    }
}
```

**Ví dụ Security Config:**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter()))
            );
        return http.build();
    }
}
```

---

### 4. td-web (Web Layer)

**Mục đích:** HTTP/REST API endpoints

**Thành phần chính:**
```
td-web/
├── TdWebApiApplication.java              # Main Spring Boot application
├── controllers/
│   ├── BaseController.java               # Base controller với helper methods
│   └── v1/
│       ├── ProductsController.java       # Products REST API
│       ├── BrandsController.java         # Brands REST API
│       ├── AuditLogsController.java      # Audit logs API
│       └── FilesController.java          # File upload/download API
├── config/
│   └── OpenApiConfig.java                # Swagger/OpenAPI configuration
└── resources/
    ├── application.yml                   # Main configuration
    └── db/migration/
        └── V1.0.1__Create_Initial_Tables.sql  # Flyway migration
```

**Đặc điểm:**
- RESTful API với versioning (`/api/v1/`, `/api/v2/`)
- OpenAPI/Swagger documentation
- Exception handling global
- CORS configuration
- Request/Response logging

**Ví dụ Controller:**
```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductsController extends BaseController {

    private final CreateProductUseCase createProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;

    @PostMapping("/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Search products with filters")
    public ResponseEntity<PaginationResponse<ProductDto>> search(
            @Valid @RequestBody SearchProductsRequest request) {
        var response = searchProductsUseCase.execute(request);
        return ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create product")
    public ResponseEntity<Result<UUID>> create(
            @Valid @RequestBody CreateProductRequest request) {
        var result = createProductUseCase.execute(request);
        return created(result);
    }
}
```

---

## Chiến lược Database

### Hybrid Database Architecture

```
┌────────────────────────────────────────────────┐
│         Application Layer                      │
├────────────────────────────────────────────────┤
│                                                │
│  ┌──────────────┐         ┌──────────────┐    │
│  │  PostgreSQL  │         │   MongoDB    │    │
│  │  (Primary)   │         │  (Logging)   │    │
│  └──────────────┘         └──────────────┘    │
│       ↑                         ↑              │
│       │                         │              │
│  ┌────┴────────┐           ┌───┴──────┐       │
│  │ JPA Repos   │           │  Mongo   │       │
│  │ (Products,  │           │  Repos   │       │
│  │  Brands)    │           │ (Audit,  │       │
│  └─────────────┘           │  API Log)│       │
│                            └──────────┘       │
└────────────────────────────────────────────────┘
```

**PostgreSQL (Relational):**
- **Mục đích:** Primary database cho transactional data
- **Tables:** products, brands, file_metadata
- **Đặc điểm:** 
  - ACID compliance
  - Foreign keys & constraints
  - Complex queries với JOINs
  - Soft delete (deletedOn != null)
  - Audit fields tracking

**MongoDB (Document NoSQL):**
- **Mục đích:** Logging và audit trail
- **Collections:** audit_logs, api_logs
- **Đặc điểm:**
  - Schema-less
  - High write throughput
  - Dễ dàng add fields mới
  - Time-series data

**Elasticsearch (Search Engine):**
- **Mục đích:** Full-text search và analytics
- **Indices:** products, brands
- **Đặc điểm:**
  - Fast full-text search
  - Aggregations
  - Fuzzy matching
  - Autocomplete

**MinIO (Object Storage):**
- **Mục đích:** File storage (images, documents)
- **Buckets:** td-files
- **Đặc điểm:**
  - S3-compatible API
  - Scalable storage
  - Metadata trong PostgreSQL

---

## Luồng xử lý Request

### Luồng tạo sản phẩm mới (POST /api/v1/products)

```
1. HTTP Request
   ↓
   POST /api/v1/products
   Authorization: Bearer {keycloak-token}
   Body: { "name": "iPhone", "rate": 29990000, "brandId": "..." }

2. Spring Security Filter Chain
   ↓
   - Extract JWT từ Authorization header
   - Validate JWT với Keycloak public key
   - Convert Keycloak roles thành Spring authorities
   - Check @PreAuthorize("hasRole('USER')")

3. ProductsController
   ↓
   - @Valid validate CreateProductRequest (Bean Validation)
   - Call createProductUseCase.execute(request)

4. CreateProductUseCase (@Transactional)
   ↓
   - Validate brand exists (call brandRepository.findById)
   - Create Product entity (domain object)
   - Call productRepository.save(product)
   - Return Result.success(productId)

5. ProductRepository (JPA)
   ↓
   - @PrePersist callback: set createdOn, lastModifiedOn
   - INSERT INTO products (id, name, rate, brand_id, ...)
   - PostgreSQL commit transaction

6. Response
   ↓
   HTTP 201 Created
   { "succeeded": true, "data": "uuid-here" }
```

---

## Authentication & Authorization Flow

### Keycloak OAuth2 Integration

```
┌─────────────┐                  ┌──────────────┐
│   Client    │                  │   Keycloak   │
│ (Frontend)  │                  │   Server     │
└──────┬──────┘                  └──────┬───────┘
       │                                │
       │ 1. Login (username/password)   │
       │──────────────────────────────>│
       │                                │
       │ 2. JWT Access Token            │
       │<──────────────────────────────│
       │                                │
       │                                │
┌──────▼──────┐                  ┌─────▼────────┐
│   Client    │                  │  TD WebAPI   │
│             │                  │  (Resource   │
│             │                  │   Server)    │
└──────┬──────┘                  └──────┬───────┘
       │                                │
       │ 3. API Request                 │
       │    Authorization: Bearer {JWT} │
       │──────────────────────────────>│
       │                                │
       │                 4. Validate JWT│
       │                    - Verify signature
       │                    - Check expiry
       │                    - Extract roles
       │                                │
       │ 5. API Response                │
       │<──────────────────────────────│
       │                                │
```

**Các bước chi tiết:**

1. **Client Login:**
   - POST to Keycloak: `/realms/{realm}/protocol/openid-connect/token`
   - Body: `grant_type=password&username=...&password=...`

2. **Keycloak Response:**
   ```json
   {
     "access_token": "eyJhbGciOiJSUzI1...",
     "token_type": "Bearer",
     "expires_in": 300,
     "refresh_token": "eyJhbGciOiJIUzI1..."
   }
   ```

3. **Client calls API:**
   ```http
   GET /api/v1/products HTTP/1.1
   Authorization: Bearer eyJhbGciOiJSUzI1...
   ```

4. **TD WebAPI validates:**
   - KeycloakJwtConverter decode JWT
   - Verify signature với Keycloak public key
   - Extract `realm_access.roles` và convert thành Spring `GrantedAuthority`
   - Check `@PreAuthorize("hasRole('USER')")`

5. **Authorization:**
   ```java
   // Controller method
   @PreAuthorize("hasRole('USER')")  // Chỉ USER role mới gọi được
   
   @PreAuthorize("hasRole('ADMIN')") // Chỉ ADMIN role
   
   @PreAuthorize("hasAnyRole('USER', 'ADMIN')") // USER hoặc ADMIN
   ```

---

## Configuration Files

### application.yml

```yaml
spring:
  # PostgreSQL - Primary Database
  datasource:
    url: jdbc:postgresql://localhost:5432/td_webapi
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway quản lý schema
    show-sql: true
    
  # MongoDB - Logging Database
  data:
    mongodb:
      uri: mongodb://localhost:27017/td_webapi_logs
  
  # Flyway Migration
  flyway:
    enabled: true
    baseline-on-migrate: true
  
  # Security - Keycloak
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

# Elasticsearch
elasticsearch:
  uris: http://localhost:9200
```

---

## Testing Strategy

### Unit Tests
```java
@Test
void createProduct_WhenBrandExists_ShouldReturnSuccess() {
    // Arrange
    var brandId = UUID.randomUUID();
    var brand = new Brand("Apple", "Technology");
    when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
    
    var request = new CreateProductRequest();
    request.setName("iPhone");
    request.setBrandId(brandId);
    
    // Act
    var result = createProductUseCase.execute(request);
    
    // Assert
    assertTrue(result.isSucceeded());
    verify(productRepository).save(any(Product.class));
}
```

### Integration Tests
```java
@SpringBootTest
@AutoConfigureMockMvc
class ProductsControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void createProduct_WithValidData_Returns201() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + validToken)
                .content("""
                    {
                        "name": "iPhone",
                        "rate": 29990000,
                        "brandId": "%s"
                    }
                    """.formatted(brandId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.succeeded").value(true));
    }
}
```

---

## Best Practices

### 1. Domain-Driven Design
- Entities chứa business logic
- Aggregate roots quản lý consistency boundaries
- Domain events cho cross-aggregate communication

### 2. SOLID Principles
- Single Responsibility: Mỗi use case làm 1 việc
- Open/Closed: Extend qua interfaces, không modify existing code
- Dependency Inversion: Depend on abstractions (interfaces)

### 3. Error Handling
```java
// Use Result pattern thay vì throw exceptions
public Result<UUID> execute(Request request) {
    if (validation fails) {
        return Result.failure("Error message");
    }
    return Result.success(data);
}
```

### 4. Transaction Management
```java
@Transactional  // Read-write transaction
@Transactional(readOnly = true)  // Read-only optimization
```

### 5. Soft Delete
- Không xóa vật lý records
- Set `deletedOn` và `deletedBy`
- Filter `deletedOn IS NULL` trong queries

---

## Deployment

### Docker Compose
```bash
# Start tất cả services
docker-compose up -d

# Chỉ start database services
docker-compose up -d postgres mongodb minio keycloak

# Stop all
docker-compose down
```

### Build & Run Application
```bash
# Build với Maven
mvn clean install

# Run application
cd td-web
mvn spring-boot:run

# Hoặc run JAR file
java -jar td-web/target/td-web-1.0.0.jar
```

### Health Checks
- Application: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- Keycloak Admin: http://localhost:8180
- MinIO Console: http://localhost:9001

---

## Troubleshooting

### Common Issues

**1. Connection refused to PostgreSQL:**
```bash
# Kiểm tra PostgreSQL đang chạy
docker ps | grep postgres

# Check logs
docker logs td-postgres
```

**2. JWT validation failed:**
- Kiểm tra Keycloak issuer-uri trong application.yml
- Verify realm name đúng
- Check token chưa expired

**3. Flyway migration failed:**
```bash
# Repair migration
mvn flyway:repair

# Baseline existing database
mvn flyway:baseline
```

**4. MongoDB connection timeout:**
```yaml
# Tăng timeout trong application.yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/td_webapi_logs?connectTimeoutMS=10000
```

---

## Tài liệu tham khảo

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Clean Architecture by Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

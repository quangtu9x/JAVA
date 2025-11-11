# TD WebAPI - Hướng Dẫn Cài Đặt và Chạy Thử

*Cập nhật: 10 tháng 11, 2025*

## 📋 Mục Lục

1. [Cài Đặt Môi Trường](#cài-đặt-môi-trường)
2. [Khởi Động Services](#khởi-động-services)
3. [Build và Chạy Application](#build-và-chạy-application)
4. [Kiểm Tra Kết Nối](#kiểm-tra-kết-nối)
5. [Test Chức Năng Cơ Bản](#test-chức-năng-cơ-bản)
6. [Troubleshooting](#troubleshooting)

---

## 🛠️ Cài Đặt Môi Trường

### Yêu Cầu Hệ Thống

#### Bắt Buộc
- **Windows 10/11** với PowerShell 5.1+
- **Java 17+** ([Download OpenJDK](https://adoptium.net/))
- **Maven 3.8+** ([Download Maven](https://maven.apache.org/download.cgi))
- **Docker Desktop** ([Download Docker](https://www.docker.com/products/docker-desktop/))

#### Kiểm Tra Cài Đặt
```powershell
# Kiểm tra Java
java -version
# Expected: openjdk version "17.0.x" hoặc cao hơn

# Kiểm tra Maven
mvn -version
# Expected: Apache Maven 3.8.x hoặc cao hơn

# Kiểm tra Docker
docker --version
docker-compose --version
```

### Clone Repository
```powershell
# Clone dự án
git clone https://github.com/quangtu9x/JAVA.git
cd td-webapi-java

# Kiểm tra cấu trúc dự án
ls
# Expected: pom.xml, docker-compose.yml, setup-dev.ps1, td-*
```

---

## 🚀 Khởi Động Services

### Option 1: Automatic Setup (Khuyến nghị)
```powershell
# Chạy script setup tự động
.\setup-dev.ps1
```

**Script sẽ thực hiện:**
- ✅ Start Docker services (Keycloak, PostgreSQL, MongoDB, MinIO)
- ✅ Kiểm tra health các services
- ✅ Set environment variables
- ✅ Build application

### Option 2: Manual Setup
```powershell
# 1. Start infrastructure services
docker-compose up -d

# 2. Chờ services khởi động (khoảng 2-3 phút)
Write-Host "Waiting for services to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 120

# 3. Set environment variables
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/tdwebapi"
$env:DATABASE_USERNAME = "postgres"
$env:DATABASE_PASSWORD = "postgres"
$env:MONGODB_HOST = "localhost"
$env:MONGODB_PORT = "27017"
$env:MONGODB_DATABASE = "tdwebapi_logs"
$env:KEYCLOAK_SERVER_URL = "http://localhost:8180"
$env:KEYCLOAK_REALM = "td-webapi-realm"
$env:KEYCLOAK_CLIENT_ID = "td-webapi-client"
$env:KEYCLOAK_CLIENT_SECRET = "td-webapi-secret-2024"
$env:MINIO_URL = "http://localhost:9000"
$env:MINIO_ACCESS_KEY = "minioadmin"
$env:MINIO_SECRET_KEY = "minioadmin"
$env:MINIO_BUCKET_NAME = "td-webapi-files"
```

### Kiểm Tra Services
```powershell
# Check Docker containers
docker-compose ps

# Expected output:
# NAME                 IMAGE                             STATUS
# td-keycloak          quay.io/keycloak/keycloak:22.0.5  Up
# td-app-postgres      postgres:15-alpine                Up  
# td-mongodb          mongo:7-jammy                     Up
# td-minio            quay.io/minio/minio:latest        Up
```

---

## 🏗️ Build và Chạy Application

### Build Application
```powershell
# Build tất cả modules
mvn clean install

# Hoặc build nhanh (skip tests)
mvn clean install -DskipTests
```

### Start Application
```powershell
# Chạy application
mvn -pl td-web -am spring-boot:run

# Application sẽ start trên port 8080
# Logs sẽ hiển thị: "Started TdWebApiApplication in X.XXX seconds"
```

---

## ✅ Kiểm Tra Kết Nối

### Health Check
```powershell
# Kiểm tra application health
Invoke-RestMethod -Uri "http://localhost:8080/api/health"
# Expected: {"status": "UP"}
```

### Service URLs
Mở browser và kiểm tra các URLs sau:

| Service | URL | Credentials |
|---------|-----|-------------|
| **Application** | http://localhost:8080 | N/A |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | N/A |
| **Keycloak Admin** | http://localhost:8180 | admin / admin |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin |

### Keycloak Realm Check
```powershell
# Kiểm tra Keycloak realm
$keycloakUrl = "http://localhost:8180/realms/td-webapi-realm/.well-known/openid_configuration"
$config = Invoke-RestMethod -Uri $keycloakUrl
Write-Host "Keycloak Issuer: $($config.issuer)" -ForegroundColor Green
```

---

## 🧪 Test Chức Năng Cơ Bản

### 1. Authentication Test

#### Lấy Access Token
```powershell
# Test với admin user
$tokenResponse = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"

$token = $tokenResponse.access_token
Write-Host "✅ Access token obtained successfully" -ForegroundColor Green
Write-Host "Token expires in: $($tokenResponse.expires_in) seconds" -ForegroundColor Yellow
```

#### Test với các User khác
```powershell
# User thường
$userToken = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=user&password=user123"

# Product Manager
$pmToken = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=product_manager&password=pm123"

Write-Host "✅ All user tokens obtained successfully" -ForegroundColor Green
```

### 2. Brands API Test

#### Tìm kiếm Brands
```powershell
$headers = @{ "Authorization" = "Bearer $token" }

# Search brands
$searchRequest = @{
    pageIndex = 0
    pageSize = 10
} | ConvertTo-Json

$brands = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/brands/search" `
  -Method POST `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $searchRequest

Write-Host "✅ Found $($brands.items.Count) brands" -ForegroundColor Green
$brands.items | ForEach-Object { Write-Host "  - $($_.name)" -ForegroundColor Cyan }
```

#### Lấy Brand Detail
```powershell
if ($brands.items.Count -gt 0) {
    $firstBrandId = $brands.items[0].id
    $brandDetail = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/brands/$firstBrandId" `
      -Method GET `
      -Headers $headers
    
    Write-Host "✅ Brand detail retrieved: $($brandDetail.name)" -ForegroundColor Green
    Write-Host "  Description: $($brandDetail.description)" -ForegroundColor Cyan
}
```

### 3. Products API Test

#### Tìm kiếm Products
```powershell
# Search products
$productSearchRequest = @{
    pageIndex = 0
    pageSize = 5
} | ConvertTo-Json

$products = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/search" `
  -Method POST `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $productSearchRequest

Write-Host "✅ Found $($products.items.Count) products" -ForegroundColor Green
$products.items | ForEach-Object { 
    Write-Host "  - $($_.name) (Brand: $($_.brandName))" -ForegroundColor Cyan 
}
```

#### Tạo Product Mới (Admin/Product Manager only)
```powershell
$newProduct = @{
    name = "Test Product $(Get-Date -Format 'HHmmss')"
    description = "Test product created via API"
    price = 99.99
    brandId = $brands.items[0].id
} | ConvertTo-Json

try {
    $createdProduct = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products" `
      -Method POST `
      -Headers $headers `
      -ContentType "application/json" `
      -Body $newProduct
    
    Write-Host "✅ Product created successfully: $($createdProduct.name)" -ForegroundColor Green
    Write-Host "  ID: $($createdProduct.id)" -ForegroundColor Cyan
} catch {
    Write-Host "⚠️ Product creation failed (may require ADMIN or PRODUCT_MANAGER role)" -ForegroundColor Yellow
}
```

### 4. File Upload Test

#### Tạo Test File
```powershell
# Tạo file test
$testFilePath = "$env:TEMP\test-document.txt"
"This is a test document for TD WebAPI file upload.`nCreated at: $(Get-Date)" | Out-File -FilePath $testFilePath -Encoding UTF8

Write-Host "✅ Test file created: $testFilePath" -ForegroundColor Green
```

#### Upload File
```powershell
$uploadForm = @{
    file = Get-Item $testFilePath
    category = "DOCUMENT"
    description = "Test file upload"
    isPublic = "false"
}

try {
    $uploadResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/files/upload" `
      -Method POST `
      -Headers $headers `
      -Form $uploadForm
    
    Write-Host "✅ File uploaded successfully" -ForegroundColor Green
    Write-Host "  File ID: $($uploadResponse.fileMetadata.id)" -ForegroundColor Cyan
    Write-Host "  Original Name: $($uploadResponse.fileMetadata.originalFilename)" -ForegroundColor Cyan
    Write-Host "  Size: $($uploadResponse.fileMetadata.humanReadableSize)" -ForegroundColor Cyan
    
    $fileId = $uploadResponse.fileMetadata.id
} catch {
    Write-Host "❌ File upload failed: $($_.Exception.Message)" -ForegroundColor Red
}
```

#### Download File
```powershell
if ($fileId) {
    try {
        $downloadPath = "$env:TEMP\downloaded-file.txt"
        Invoke-RestMethod -Uri "http://localhost:8080/api/v1/files/download/$fileId" `
          -Headers $headers `
          -OutFile $downloadPath
        
        Write-Host "✅ File downloaded successfully to: $downloadPath" -ForegroundColor Green
        
        # Verify content
        $content = Get-Content $downloadPath
        Write-Host "  Content preview: $($content[0])" -ForegroundColor Cyan
    } catch {
        Write-Host "❌ File download failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}
```

### 5. Audit Logs Test (Admin Only)

```powershell
# Test audit logs (Admin role required)
$auditSearchRequest = @{
    pageIndex = 0
    pageSize = 5
} | ConvertTo-Json

try {
    $auditLogs = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/audit-logs/search" `
      -Method POST `
      -Headers $headers `
      -ContentType "application/json" `
      -Body $auditSearchRequest
    
    Write-Host "✅ Audit logs retrieved (Admin access confirmed)" -ForegroundColor Green
    Write-Host "  Found $($auditLogs.items.Count) audit entries" -ForegroundColor Cyan
} catch {
    Write-Host "⚠️ Audit logs access denied (requires ADMIN role)" -ForegroundColor Yellow
}
```

---

## 🔧 Troubleshooting

### Common Issues & Solutions

#### 1. Port Conflicts
```powershell
# Kiểm tra ports đang sử dụng
netstat -an | findstr "8080 8180 9000 9001 5432 27017"

# Stop conflicting services
Get-Process -Name "java" | Stop-Process -Force
docker-compose down
```

#### 2. Docker Issues
```powershell
# Reset Docker containers và volumes
docker-compose down -v
docker system prune -f

# Restart Docker Desktop nếu cần
```

#### 3. Keycloak Not Ready
```powershell
# Kiểm tra Keycloak logs
docker-compose logs keycloak | Select-Object -Last 20

# Wait cho Keycloak hoàn toàn ready
do {
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm" -TimeoutSec 5
        Write-Host "✅ Keycloak is ready" -ForegroundColor Green
        break
    } catch {
        Write-Host "⏳ Waiting for Keycloak..." -ForegroundColor Yellow
        Start-Sleep -Seconds 10
    }
} while ($true)
```

#### 4. Database Connection Issues
```powershell
# Test PostgreSQL connection
docker exec td-app-postgres pg_isready -U postgres

# Test MongoDB connection  
docker exec td-mongodb mongosh --eval "db.adminCommand('ping')"

# Check database logs
docker-compose logs app-postgres | Select-Object -Last 10
docker-compose logs mongodb | Select-Object -Last 10
```

#### 5. MinIO Issues
```powershell
# Check MinIO health
Invoke-RestMethod -Uri "http://localhost:9000/minio/health/live"

# Access MinIO console to verify buckets
Write-Host "MinIO Console: http://localhost:9001" -ForegroundColor Cyan
Write-Host "Credentials: minioadmin / minioadmin" -ForegroundColor Cyan
```

#### 6. Application Startup Issues
```powershell
# Check Java version
java -version

# Check Maven version
mvn -version

# Clean build
mvn clean install -DskipTests

# Check application logs
tail -f logs/td-webapi.log
```

### Service Status Check Script
```powershell
function Test-ServiceHealth {
    $services = @(
        @{Name="Application"; Url="http://localhost:8080/api/health"},
        @{Name="Keycloak"; Url="http://localhost:8180/realms/td-webapi-realm"},
        @{Name="MinIO"; Url="http://localhost:9000/minio/health/live"}
    )
    
    foreach ($service in $services) {
        try {
            Invoke-RestMethod -Uri $service.Url -TimeoutSec 5 | Out-Null
            Write-Host "✅ $($service.Name) is healthy" -ForegroundColor Green
        } catch {
            Write-Host "❌ $($service.Name) is not responding" -ForegroundColor Red
        }
    }
}

# Run health check
Test-ServiceHealth
```

---

## 📚 Next Steps

Sau khi test thành công các chức năng cơ bản:

1. **Explore Swagger UI**: http://localhost:8080/swagger-ui.html
2. **Review Project Documentation**: `PROJECT_USAGE_NOTES.md`
3. **Check Keycloak Admin Console**: http://localhost:8180
4. **Explore MinIO Console**: http://localhost:9001
5. **Run Integration Tests**: `mvn verify` (khi có)

### Useful Commands Reference
```powershell
# Start services
docker-compose up -d

# Stop services  
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Restart application
mvn -pl td-web -am spring-boot:run

# Clean environment
docker-compose down -v && docker system prune -f
```

---

**🎉 Chúc mừng! Bạn đã setup và test thành công TD WebAPI!**

*Để biết thêm chi tiết, tham khảo `PROJECT_USAGE_NOTES.md` và `QUICK_START.md`*
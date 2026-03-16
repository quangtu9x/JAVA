# TD WebAPI - Quick Start with Keycloak

## 🚀 Quick Setup

```powershell
# 1. Start infrastructure services
docker-compose up -d

# 2. Run setup script (optional)
.\setup-dev.ps1

# 3. Build and run application
mvn clean install
mvn -pl td-web -am spring-boot:run
```

## 🔗 Access Points

- **Application**: http://localhost:8080
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Keycloak Admin**: http://localhost:8180 (admin/admin)

## 👥 Test Users

| Username | Password | Roles |
|----------|----------|-------|
| admin | admin123 | ADMIN, USER |
| user | user123 | USER |
| product_manager | pm123 | PRODUCT_MANAGER, USER |
| brand_manager | bm123 | BRAND_MANAGER, USER |

## 🧪 Test API

```powershell
# Get access token
$response = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"

# Use token to call API
$headers = @{ "Authorization" = "Bearer $($response.access_token)" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/search" `
  -Method POST -Headers $headers `
  -ContentType "application/json" `
  -Body '{"pageIndex": 0, "pageSize": 10}'
```

## 📄 Test Documents API

```powershell
$headers = @{ "Authorization" = "Bearer $($response.access_token)" }

# 1) Thêm mới (ghi vào DB)
$createResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents" `
  -Method POST -Headers $headers `
  -ContentType "application/json" `
  -Body '{
    "title": "Quy trinh phe duyet chi phi",
    "documentType": "POLICY",
    "status": "ACTIVE",
    "content": "Noi dung van ban",
    "tags": ["finance", "policy"],
    "attributes": {
      "department": "Accounting",
      "amount": 50,
      "owner": "Alice"
    },
    "metadata": {
      "source": "quick-start"
    }
  }'

$docId = $createResponse.data

# 2) Xem danh sach
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents?pageNumber=0&pageSize=10&sortBy=lastModifiedOn&sortDirection=desc" `
  -Method GET -Headers $headers

# 3) Xem chi tiet
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method GET -Headers $headers

# 4) Sua (ghi cap nhat vao DB)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method PUT -Headers $headers `
  -ContentType "application/json" `
  -Body "{
    \"id\": \"$docId\",
    \"title\": \"Quy trinh phe duyet chi phi - v2\",
    \"documentType\": \"POLICY\",
    \"status\": \"ACTIVE\",
    \"content\": \"Noi dung cap nhat\",
    \"tags\": [\"finance\", \"policy\", \"v2\"],
    \"attributes\": {
      \"department\": \"Accounting\",
      \"amount\": 80,
      \"owner\": \"Alice\"
    },
    \"metadata\": {
      \"source\": \"quick-start\",
      \"updatedBy\": \"admin\"
    }
  }"

# 5) Tim kiem
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/search" `
  -Method POST -Headers $headers `
  -ContentType "application/json" `
  -Body '{
    "keyword": "quy trinh",
    "documentType": "POLICY",
    "status": "ACTIVE",
    "attributeFilters": {
      "department": "Accounting",
      "title": {"operator": "contains", "value": "process"},
      "amount": {"operator": "range", "from": 10, "to": 100}
    },
    "pageNumber": 0,
    "pageSize": 10,
    "sortBy": "lastModifiedOn",
    "sortDirection": "desc"
  }'

# 6) Xoa mem (ghi deleted_on vao DB)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method DELETE -Headers $headers
```

Documents API chinh:
- `POST /api/v1/documents` - Thêm mới
- `GET /api/v1/documents` - Xem danh sách
- `GET /api/v1/documents/{id}` - Xem chi tiết
- `PUT /api/v1/documents/{id}` - Sửa
- `DELETE /api/v1/documents/{id}` - Xóa mềm
- `POST /api/v1/documents/search` - Tìm kiếm

## 🗄️ Switch Database Profile

```powershell
# PostgreSQL / dev (default)
$env:SPRING_PROFILES_ACTIVE = "postgres"

# TiDB
$env:SPRING_PROFILES_ACTIVE = "tidb"
$env:DATABASE_URL = "jdbc:mysql://localhost:4000/tdwebapi"

# MariaDB
$env:SPRING_PROFILES_ACTIVE = "mariadb"
$env:DATABASE_URL = "jdbc:mariadb://localhost:3306/tdwebapi"

mvn -pl td-web -am spring-boot:run
```

Notes:
- Chỉ chọn một DB profile tại một thời điểm.
- `tidb` và `mariadb` đang tắt Flyway mặc định, nên schema tương thích cần có sẵn.

## 📚 Documentation

See `PROJECT_USAGE_NOTES.md` for detailed configuration and usage information.
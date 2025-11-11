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

## 📚 Documentation

See `PROJECT_USAGE_NOTES.md` for detailed configuration and usage information.
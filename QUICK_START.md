# TD WebAPI - Quick Start (Documents Only)

## 1) Start services

```powershell
docker-compose up -d
```

## 2) Build and run

```powershell
mvn clean install
mvn -pl td-web spring-boot:run
```

## 3) Get token

```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"

$headers = @{ "Authorization" = "Bearer $($response.access_token)" }
```

## 4) Documents API smoke test

```powershell
# Create
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
      "owner": "Alice"
    },
    "sub": "Alice finance",
    "metadata": {
      "source": "quick-start"
    }
  }'

$docId = $createResponse.data

# Partial update
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method PUT -Headers $headers `
  -ContentType "application/json" `
  -Body '{
    "title": "Quy trinh phe duyet chi phi v2",
    "manh": "da lay vo roi"
  }'

# Get details
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method GET -Headers $headers

# Soft delete
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" `
  -Method DELETE -Headers $headers
```

## 5) URLs

- Application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Keycloak: http://localhost:8180

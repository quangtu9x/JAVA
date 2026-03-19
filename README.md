# TD WebAPI - Document Management (Migration Baseline)

Du an da duoc rut gon ve dung pham vi quan ly van ban de phuc vu migration tu he thong Domino cu.

## Scope hien tai

- Quan ly van ban dong (Documents API)
- Xac thuc OAuth2/JWT qua Keycloak
- Luu tru PostgreSQL + Flyway
- Swagger/OpenAPI de test API
- Elasticsearch search module cho documents

Da loai bo khoi source:

- Product/Brand module
- Audit logs MongoDB module

## Kien truc

- `td-domain`: Domain model (documents + common)
- `td-application`: Use case layer (documents + common)
- `td-infrastructure`: Persistence + security cho documents
- `td-web`: REST API layer

## Quick start

```powershell
# 1) Start infrastructure
docker-compose up -d

# 2) Build
mvn clean install

# 3) Run API
mvn -pl td-web spring-boot:run
```

## Endpoints chinh

- `GET /api/v1/documents`
- `GET /api/v1/documents/{id}`
- `POST /api/v1/documents/search`
- `POST /api/v1/documents/search/elastic`
- `GET /api/v1/documents/search/admin/status`
- `POST /api/v1/documents/search/admin/reindex`
- `POST /api/v1/documents/search/admin/sync/{id}`
- `POST /api/v1/documents`
- `PUT /api/v1/documents/{id}`
- `DELETE /api/v1/documents/{id}`
- `DELETE /api/v1/documents/{id}/permanent`
- `POST /api/v1/documents/deleted/search`

## Authentication

Lay token tu Keycloak:

```powershell
Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123"
```

Swagger:

- `http://localhost:8080/swagger-ui.html`

## Luu y migration

Repo nay hien dang la baseline "documents-first" de tiep tuc map du lieu tu Domino.
Buoc tiep theo nen thuc hien ETL schema map + import du lieu lich su theo dot (xem checklist migration trong trao doi voi Copilot).

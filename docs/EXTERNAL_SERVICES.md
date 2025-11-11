# External Services Note

This project assumes the following services are already installed on other machines in your local network (hostnames/IPs accessible from your dev machine): PostgreSQL, MongoDB, MinIO, Keycloak. The notes below explain how to configure the application to use those remote services and include simple PowerShell commands to verify connectivity and run basic operations.

---

## 1) Fill-in the host information

Create or note the hostnames/IP addresses where each service is running. Example placeholders (replace with actual values):

- POSTGRES_HOST=pg-lam.local
- POSTGRES_PORT=5432
- POSTGRES_DB=tdwebapi
- POSTGRES_USER=postgres
- POSTGRES_PASSWORD=postgres

- MONGODB_HOST=mongo-lam.local
- MONGODB_PORT=27017
- MONGODB_DATABASE=tdwebapi_logs
- MONGODB_USERNAME=
- MONGODB_PASSWORD=

- MINIO_URL=http://minio-lam.local:9000
- MINIO_ACCESS_KEY=minioadmin
- MINIO_SECRET_KEY=minioadmin
- MINIO_BUCKET_NAME=td-webapi-files

- KEYCLOAK_SERVER_URL=http://keycloak-lam.local:8180
- KEYCLOAK_REALM=td-webapi-realm
- KEYCLOAK_CLIENT_ID=td-webapi-client
- KEYCLOAK_CLIENT_SECRET=td-webapi-secret-2024

---

## 2) Set environment variables (PowerShell)

Replace placeholders with real hostnames/IPs. Run these in PowerShell on your dev machine before starting the app:

```powershell
# PostgreSQL
$env:DATABASE_URL = "jdbc:postgresql://<POSTGRES_HOST>:5432/<POSTGRES_DB>"
$env:DATABASE_USERNAME = "<POSTGRES_USER>"
$env:DATABASE_PASSWORD = "<POSTGRES_PASSWORD>"

# MongoDB
$env:MONGODB_HOST = "<MONGODB_HOST>"
$env:MONGODB_PORT = "<MONGODB_PORT>"
$env:MONGODB_DATABASE = "<MONGODB_DATABASE>"
$env:MONGODB_USERNAME = "<MONGODB_USERNAME>"   # optional
$env:MONGODB_PASSWORD = "<MONGODB_PASSWORD>"   # optional

# MinIO
$env:MINIO_URL = "<MINIO_URL>"
$env:MINIO_ACCESS_KEY = "<MINIO_ACCESS_KEY>"
$env:MINIO_SECRET_KEY = "<MINIO_SECRET_KEY>"
$env:MINIO_BUCKET_NAME = "<MINIO_BUCKET_NAME>"

# Keycloak
$env:KEYCLOAK_SERVER_URL = "<KEYCLOAK_SERVER_URL>"
$env:KEYCLOAK_REALM = "<KEYCLOAK_REALM>"
$env:KEYCLOAK_CLIENT_ID = "<KEYCLOAK_CLIENT_ID>"
$env:KEYCLOAK_CLIENT_SECRET = "<KEYCLOAK_CLIENT_SECRET>"

# Optional: set Spring profile
$env:SPRING_PROFILES_ACTIVE = "dev"
```

Notes:
- Use `;` to put multiple commands on one line if you prefer. In PowerShell, each line above is fine.
- These environment variables will be visible to the `mvn spring-boot:run` process if run in the same session.

---

## 3) Verify network connectivity from your dev machine

Use `Test-NetConnection` to check TCP connectivity to each service port:

```powershell
Test-NetConnection -ComputerName <POSTGRES_HOST> -Port 5432
Test-NetConnection -ComputerName <MONGODB_HOST> -Port 27017
Test-NetConnection -ComputerName <MINIO_HOST> -Port 9000
Test-NetConnection -ComputerName <KEYCLOAK_HOST> -Port 8180
```

Successful `TcpTestSucceeded : True` indicates connectivity.

---

## 4) Quick checks and simple commands

### PostgreSQL
- If you have `psql` (or can run on the PostgreSQL host), create the database and a user if needed. Example remote `psql` command run on the DB host:

```bash
# on PostgreSQL host (or using psql remote connection)
psql -U postgres -c "CREATE DATABASE tdwebapi;"
psql -U postgres -c "CREATE USER tduser WITH PASSWORD 'tdpassword';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE tdwebapi TO tduser;"
```

If you can't run psql locally, ask the admin of the DB host to create the DB and user.

### Flyway migrations
- Flyway is enabled in `application.yml`. When the Spring Boot app starts, Flyway will connect to the PostgreSQL instance and run migrations from `classpath:db/migration`.
- To run migrations manually (if you prefer), you can run the Flyway Maven plugin (if available in your module):

```powershell
# from repo root (may require plugin setup in the module pom)
mvn -pl td-web flyway:migrate
```

If this fails, starting the Spring Boot app will still attempt migrations.

### MongoDB
- Simple connectivity check using `mongosh` (if installed locally):

```powershell
mongosh "mongodb://<MONGODB_HOST>:<MONGODB_PORT>/<MONGODB_DATABASE>"
# then in shell:
# show collections
show collections
```

- If MongoDB requires authentication, include username/password in the connection string.

### MinIO (recommended: install mc client)
- Install MinIO client `mc` (https://min.io/docs/minio/linux/reference/minio-mc.html). Then configure and list buckets:

```powershell
# set alias
mc alias set myminio <MINIO_URL> <MINIO_ACCESS_KEY> <MINIO_SECRET_KEY>

# create bucket (if not exists)
mc mb myminio/<MINIO_BUCKET_NAME>

# list buckets
mc ls myminio
```

If you cannot install `mc`, MinIO console is typically available at `http://<MINIO_HOST>:9001`.

### Keycloak
Two quick options to prepare Keycloak:

Option A — Admin Console (easier):
1. Open Keycloak admin at `http://<KEYCLOAK_HOST>:8180` and log in as admin.
2. Import `keycloak/realm-export.json` (if repo contains it) or create a realm named `td-webapi-realm`.
3. Create a client `td-webapi-client` (confidential) and set client secret to the value in `$env:KEYCLOAK_CLIENT_SECRET`.
4. Create test users (admin, user, product_manager, brand_manager) and assign roles.

Option B — Admin REST API (scriptable):
- Use the admin CLI or REST API to import realms. Example to obtain admin token and import a realm (run on machine with network access to Keycloak):

```powershell
# get admin token (adjust admin credentials)
$adminTokenResponse = Invoke-RestMethod -Method POST -Uri "http://<KEYCLOAK_HOST>:8180/realms/master/protocol/openid-connect/token" -ContentType "application/x-www-form-urlencoded" -Body "client_id=admin-cli&username=admin&password=<ADMIN_PASSWORD>&grant_type=password"
$adminToken = $adminTokenResponse.access_token

# import realm (assuming realm-export.json available locally)
Invoke-RestMethod -Method POST -Uri "http://<KEYCLOAK_HOST>:8180/admin/realms" -Headers @{ Authorization = "Bearer $adminToken" } -ContentType "application/json" -InFile .\keycloak\realm-export.json
```

(If you don't have admin credentials, use the Admin Console GUI and ask the Keycloak administrator to import the realm for you.)

---

## 5) Start the application (from repo root)

Set the environment variables (section 2), then run from PowerShell:

```powershell
# (in the same PowerShell session where you set env vars)
cd <repo-root>\td-web
mvn -pl td-web -am spring-boot:run
```

Or build then run jar:

```powershell
mvn clean install -DskipTests
java -jar td-web/target/td-web-<version>.jar
```

When the app starts successfully you should see logs indicating Flyway migrations ran and that the app is listening on port 8080.

---

## 6) Simple API examples (PowerShell)

### Health check (no auth)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method GET
```

### Get Keycloak token (password grant) — example for `admin`

```powershell
$tokenResp = Invoke-RestMethod -Uri "${env:KEYCLOAK_SERVER_URL}/realms/${env:KEYCLOAK_REALM}/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "client_id=${env:KEYCLOAK_CLIENT_ID}&client_secret=${env:KEYCLOAK_CLIENT_SECRET}&grant_type=password&username=admin&password=admin123"
$accessToken = $tokenResp.access_token
```

Adjust username/password to test user credentials created in Keycloak.

### Call protected API (Products search)

```powershell
$headers = @{ Authorization = "Bearer $accessToken" }
$body = @{ pageNumber = 0; pageSize = 10 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products/search" -Method POST -Headers $headers -ContentType "application/json" -Body $body
```

### Upload a file to the API (example endpoint)

```powershell
# single file upload example
$headers = @{ Authorization = "Bearer $accessToken" }
$form = @{ file = Get-Item "C:\path\to\image.jpg"; category = "products" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/files/upload" -Method POST -Headers $headers -Form $form
```

---

## 7) Verification and troubleshooting

- If Flyway fails: check the PostgreSQL connection string and DB user privileges.
- If Keycloak auth fails: ensure client secret and realm name match your Keycloak setup.
- If MinIO operations fail: verify `MINIO_URL` and credentials and confirm the bucket exists (`mc ls`).
- If MongoDB queries fail: check `mongosh` connectivity and that user has access.
- Use `Test-NetConnection` to ensure TCP ports are reachable.

---

## 8) Optional: create a PowerShell helper script

You may add a short `scripts/start-local-with-remote-services.ps1` that:
1. Sets the required env vars (hard-coded or read from `.env`)
2. Runs `mvn -pl td-web -am spring-boot:run`

Keep secrets out of repo; prefer `.env` or CI secret stores.

---

If bạn muốn, tôi có thể:
- Tạo file PowerShell mẫu `scripts/set-env-and-run.ps1` (non-sensitive, prompts for passwords),
- Hoặc tạo a quick `README` snippet in Vietnamese for your team.

Vui lòng cho biết bạn muốn mình tạo script mẫu không. 
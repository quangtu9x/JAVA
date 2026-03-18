# TD WebAPI - Document Management Setup Script
# Run this script to set up the lean development environment

Write-Host "=== TD WebAPI Document Setup ===" -ForegroundColor Green

# Step 1: Start infrastructure services
Write-Host "Starting infrastructure services (Keycloak, PostgreSQL)..." -ForegroundColor Yellow
docker-compose up -d keycloak app-postgres

Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Step 2: Verify services are running
Write-Host "Checking service status..." -ForegroundColor Yellow

# Check PostgreSQL
try {
    $pgTest = docker exec td-app-postgres pg_isready -U postgres
    Write-Host "[OK] PostgreSQL is ready" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] PostgreSQL is not ready" -ForegroundColor Red
}

# Check Keycloak
try {
    $keycloakTest = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/.well-known/openid_configuration" -TimeoutSec 5
    Write-Host "[OK] Keycloak is ready" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Keycloak is not ready yet. Please wait a few more minutes." -ForegroundColor Red
    Write-Host "   Keycloak admin console: http://localhost:8180" -ForegroundColor Yellow
    Write-Host "   Admin credentials: admin/admin" -ForegroundColor Yellow
}

# Step 3: Set environment variables
Write-Host "Setting up environment variables..." -ForegroundColor Yellow
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/tdwebapi"
$env:DATABASE_USERNAME = "postgres"
$env:DATABASE_PASSWORD = "postgres"
$env:KEYCLOAK_SERVER_URL = "http://localhost:8180"
$env:KEYCLOAK_REALM = "td-webapi-realm"
$env:KEYCLOAK_CLIENT_ID = "td-webapi-client"
$env:KEYCLOAK_CLIENT_SECRET = "td-webapi-secret-2024"
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "[OK] Environment variables set" -ForegroundColor Green

# Step 4: Build the application
Write-Host "Building the application..." -ForegroundColor Yellow
mvn clean install -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Build successful" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Build failed" -ForegroundColor Red
    exit 1
}

# Step 5: Display useful information
Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "To start the application:" -ForegroundColor Cyan
Write-Host "  mvn -pl td-web spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Cyan
Write-Host "  Application:        http://localhost:8080" -ForegroundColor White
Write-Host "  Swagger UI:         http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  Keycloak Admin:     http://localhost:8180 (admin/admin)" -ForegroundColor White
Write-Host ""
Write-Host "Test users:" -ForegroundColor Cyan
Write-Host "  admin/admin123      (ADMIN, USER)" -ForegroundColor White
Write-Host "  user/user123        (USER)" -ForegroundColor White
Write-Host ""
Write-Host "Test API token:" -ForegroundColor Cyan
Write-Host "  curl -X POST http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token ^" -ForegroundColor Gray
Write-Host "    -H \"Content-Type: application/x-www-form-urlencoded\" ^" -ForegroundColor Gray
Write-Host "    -d \"client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123\"" -ForegroundColor Gray

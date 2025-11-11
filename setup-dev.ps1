# TD WebAPI - Development Setup Script
# Run this script to set up the complete development environment

Write-Host "=== TD WebAPI Development Setup ===" -ForegroundColor Green

# Step 1: Start infrastructure services
Write-Host "Starting infrastructure services (Keycloak, PostgreSQL, MongoDB, MinIO, Elasticsearch)..." -ForegroundColor Yellow
docker-compose up -d keycloak app-postgres mongodb minio elasticsearch

Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Step 2: Verify services are running
Write-Host "Checking service status..." -ForegroundColor Yellow

# Check PostgreSQL
try {
    $pgTest = docker exec td-app-postgres pg_isready -U postgres
    Write-Host "✅ PostgreSQL is ready" -ForegroundColor Green
} catch {
    Write-Host "❌ PostgreSQL is not ready" -ForegroundColor Red
}

# Check MongoDB
try {
    $mongoTest = docker exec td-mongodb mongosh --eval "db.adminCommand('ping')"
    Write-Host "✅ MongoDB is ready" -ForegroundColor Green
} catch {
    Write-Host "❌ MongoDB is not ready" -ForegroundColor Red
}

# Check MinIO
try {
    $minioTest = Invoke-RestMethod -Uri "http://localhost:9000/minio/health/live" -TimeoutSec 5
    Write-Host "✅ MinIO is ready" -ForegroundColor Green
} catch {
    Write-Host "❌ MinIO is not ready yet" -ForegroundColor Red
    Write-Host "   You can check MinIO console at: http://localhost:9001" -ForegroundColor Yellow
    Write-Host "   Admin credentials: minioadmin/minioadmin" -ForegroundColor Yellow
}

# Check Elasticsearch
try {
    $esTest = Invoke-RestMethod -Uri "http://localhost:9200/_cluster/health" -TimeoutSec 5
    Write-Host "✅ Elasticsearch is ready" -ForegroundColor Green
} catch {
    Write-Host "❌ Elasticsearch is not ready yet" -ForegroundColor Red
    Write-Host "   You can check Elasticsearch at: http://localhost:9200" -ForegroundColor Yellow
}

# Check Keycloak
try {
    $keycloakTest = Invoke-RestMethod -Uri "http://localhost:8180/realms/td-webapi-realm/.well-known/openid_configuration" -TimeoutSec 5
    Write-Host "✅ Keycloak is ready" -ForegroundColor Green
} catch {
    Write-Host "❌ Keycloak is not ready yet. Please wait a few more minutes." -ForegroundColor Red
    Write-Host "   You can check Keycloak admin console at: http://localhost:8180" -ForegroundColor Yellow
    Write-Host "   Admin credentials: admin/admin" -ForegroundColor Yellow
}

# Step 3: Set environment variables
Write-Host "Setting up environment variables..." -ForegroundColor Yellow
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
$env:ELASTICSEARCH_HOST = "localhost:9200"
$env:ELASTICSEARCH_CLUSTER_NAME = "td-cluster"
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "✅ Environment variables set" -ForegroundColor Green

# Step 4: Build the application
Write-Host "Building the application..." -ForegroundColor Yellow
mvn clean install -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful" -ForegroundColor Green
} else {
    Write-Host "❌ Build failed" -ForegroundColor Red
    exit 1
}

# Step 5: Display useful information
Write-Host ""
Write-Host "=== Setup Complete! ===" -ForegroundColor Green
Write-Host ""
Write-Host "🚀 To start the application:" -ForegroundColor Cyan
Write-Host "   mvn -pl td-web -am spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "🔗 Service URLs:" -ForegroundColor Cyan
Write-Host "   Application:        http://localhost:8080" -ForegroundColor White
Write-Host "   Swagger UI:         http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "   Keycloak Admin:     http://localhost:8180 (admin/admin)" -ForegroundColor White
Write-Host "   Keycloak Realm:     http://localhost:8180/realms/td-webapi-realm" -ForegroundColor White
Write-Host "   MinIO Console:      http://localhost:9001 (minioadmin/minioadmin)" -ForegroundColor White
Write-Host "   MinIO API:          http://localhost:9000" -ForegroundColor White
Write-Host "   Elasticsearch:      http://localhost:9200" -ForegroundColor White
Write-Host "   Kibana (optional):  http://localhost:5601" -ForegroundColor White
Write-Host ""
Write-Host "👥 Test Users:" -ForegroundColor Cyan
Write-Host "   admin/admin123      (ADMIN, USER)" -ForegroundColor White
Write-Host "   user/user123        (USER)" -ForegroundColor White
Write-Host "   product_manager/pm123    (PRODUCT_MANAGER, USER)" -ForegroundColor White
Write-Host "   brand_manager/bm123      (BRAND_MANAGER, USER)" -ForegroundColor White
Write-Host ""
Write-Host "🧪 Test API with token:" -ForegroundColor Cyan
Write-Host "   # Get token:" -ForegroundColor Gray
Write-Host "   curl -X POST http://localhost:8180/realms/td-webapi-realm/protocol/openid-connect/token \" -ForegroundColor Gray
Write-Host "     -H 'Content-Type: application/x-www-form-urlencoded' \" -ForegroundColor Gray
Write-Host "     -d 'client_id=td-webapi-client&client_secret=td-webapi-secret-2024&grant_type=password&username=admin&password=admin123'" -ForegroundColor Gray
Write-Host ""
Write-Host "   # Call API:" -ForegroundColor Gray
Write-Host "   curl -H 'Authorization: Bearer YOUR_TOKEN' http://localhost:8080/api/v1/products/search" -ForegroundColor Gray
Write-Host ""
Write-Host "📚 Documentation: See PROJECT_USAGE_NOTES.md for detailed information" -ForegroundColor Cyan
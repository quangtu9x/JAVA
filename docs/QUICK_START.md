# Hướng dẫn Cài đặt - Những gì cần làm đầu tiên

## Tóm tắt tình huống
- **Đã có sẵn trên mạng LAN**: PostgreSQL, MongoDB, MinIO, Keycloak (trên các máy khác)
- **Máy dev của bạn cần**: Java, Maven, và công cụ hỗ trợ (tuỳ chọn)

---

## 1. CÀI ĐẶT BẮT BUỘC trên máy dev

### ✅ Java 17 hoặc cao hơn
**Kiểm tra:**
```powershell
java -version
```

**Nếu chưa có, tải:**
- **Eclipse Temurin** (khuyến nghị): https://adoptium.net/
- Hoặc **Oracle JDK**: https://www.oracle.com/java/technologies/downloads/

**Sau khi cài:**
```powershell
# Kiểm tra lại
java -version
javac -version
```

### ✅ Maven 3.8+
**Kiểm tra:**
```powershell
mvn -version
```

**Nếu chưa có:**
1. Tải Maven: https://maven.apache.org/download.cgi
2. Giải nén vào thư mục (ví dụ: `C:\Program Files\Apache\maven`)
3. Thêm vào PATH:
   ```powershell
   # Mở PowerShell với quyền admin
   [System.Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Program Files\Apache\maven", "Machine")
   [System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Apache\maven\bin", "Machine")
   ```
4. Mở PowerShell mới và kiểm tra:
   ```powershell
   mvn -version
   ```

---

## 2. XÁC NHẬN KẾT NỐI với các service trên mạng

### ✅ Kiểm tra kết nối mạng
Thay `<HOST>` bằng IP/hostname thực tế của từng máy:

```powershell
# PostgreSQL
Test-NetConnection -ComputerName <POSTGRES_HOST> -Port 5432

# MongoDB  
Test-NetConnection -ComputerName <MONGODB_HOST> -Port 27017

# MinIO
Test-NetConnection -ComputerName <MINIO_HOST> -Port 9000

# Keycloak
Test-NetConnection -ComputerName <KEYCLOAK_HOST> -Port 8180
```

**Kết quả mong đợi:** `TcpTestSucceeded : True`

### ✅ Lấy thông tin kết nối
Bạn cần biết:

**PostgreSQL:**
- Host/IP: ________________
- Port: 5432
- Database name: tdwebapi (cần tạo nếu chưa có)
- Username: ________________
- Password: ________________

**MongoDB:**
- Host/IP: ________________
- Port: 27017
- Database: tdwebapi_logs
- Username (nếu có): ________________
- Password (nếu có): ________________

**MinIO:**
- URL: http://________________:9000
- Access Key: ________________
- Secret Key: ________________
- Bucket name: td-webapi-files

**Keycloak:**
- URL: http://________________:8180
- Realm: td-webapi-realm
- Client ID: td-webapi-client
- Client Secret: ________________

---

## 3. CHUẨN BỊ DATABASE (chỉ cần làm 1 lần)

### PostgreSQL
**Cần tạo database `tdwebapi`:**

Nếu bạn có quyền truy cập PostgreSQL server:
```bash
# SSH vào máy PostgreSQL hoặc dùng psql remote
psql -h <POSTGRES_HOST> -U postgres

# Trong psql shell:
CREATE DATABASE tdwebapi;
CREATE USER tduser WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE tdwebapi TO tduser;
\q
```

**Hoặc nhờ admin PostgreSQL tạo giúp.**

### MongoDB
Không cần setup gì - collections sẽ tự động tạo khi app chạy lần đầu.

### MinIO
**Cần tạo bucket `td-webapi-files`:**

Option 1 - MinIO Console (dễ nhất):
1. Mở trình duyệt: `http://<MINIO_HOST>:9001`
2. Login với Access Key/Secret Key
3. Tạo bucket tên `td-webapi-files`

Option 2 - Dùng MinIO Client `mc` (nếu đã cài):
```powershell
mc alias set myminio http://<MINIO_HOST>:9000 <ACCESS_KEY> <SECRET_KEY>
mc mb myminio/td-webapi-files
```

### Keycloak
**Cần setup realm và users:**

1. Mở Keycloak Admin Console: `http://<KEYCLOAK_HOST>:8180`
2. Login với admin credentials
3. **Import realm** (nếu có file `keycloak/realm-export.json` trong repo):
   - Realms → Create Realm → Browse → chọn file → Create
4. **Hoặc tạo thủ công:**
   - Create realm tên: `td-webapi-realm`
   - Clients → Create → Client ID: `td-webapi-client`
   - Chọn client vừa tạo → Settings → Access Type: `confidential` → Save
   - Credentials tab → copy Client Secret
   - Roles → Create roles: ADMIN, USER, PRODUCT_MANAGER, BRAND_MANAGER
   - Users → Add users: admin, user, product_manager, brand_manager
   - Assign roles cho từng user

**Test users mẫu:**
- admin / admin123 → roles: ADMIN, USER
- user / user123 → roles: USER
- product_manager / pm123 → roles: PRODUCT_MANAGER, USER
- brand_manager / bm123 → roles: BRAND_MANAGER, USER

---

## 4. CÀI ĐẶT TUỲ CHỌN (giúp debug dễ hơn)

### 📦 MinIO Client (mc)
```powershell
# Windows (dùng Chocolatey)
choco install minio-client

# Hoặc tải binary: https://min.io/docs/minio/windows/reference/minio-mc.html
```

### 📦 MongoDB Shell (mongosh)
```powershell
# Windows (dùng Chocolatey)
choco install mongosh

# Hoặc tải: https://www.mongodb.com/try/download/shell
```

### 📦 PostgreSQL Client (psql)
```powershell
# Tải PostgreSQL tools: https://www.postgresql.org/download/windows/
# Hoặc cài full PostgreSQL và chỉ dùng client tools
```

### 📦 Git (nếu chưa có)
```powershell
# Windows
choco install git

# Hoặc: https://git-scm.com/download/win
```

---

## 5. CLONE VÀ BUILD PROJECT

```powershell
# Clone repo (nếu chưa có)
git clone <repository-url>
cd td-webapi-java

# Build lần đầu (download dependencies)
mvn clean install -DskipTests
```

**Lưu ý:** Lần đầu build sẽ mất thời gian vì Maven tải dependencies.

---

## 6. THIẾT LẬP BIẾN MÔI TRƯỜNG

Tạo file `set-env.ps1` trong thư mục gốc của project:

```powershell
# PostgreSQL
$env:DATABASE_URL = "jdbc:postgresql://<POSTGRES_HOST>:5432/tdwebapi"
$env:DATABASE_USERNAME = "<YOUR_USERNAME>"
$env:DATABASE_PASSWORD = "<YOUR_PASSWORD>"

# MongoDB
$env:MONGODB_HOST = "<MONGODB_HOST>"
$env:MONGODB_PORT = "27017"
$env:MONGODB_DATABASE = "tdwebapi_logs"

# MinIO
$env:MINIO_URL = "http://<MINIO_HOST>:9000"
$env:MINIO_ACCESS_KEY = "<YOUR_ACCESS_KEY>"
$env:MINIO_SECRET_KEY = "<YOUR_SECRET_KEY>"
$env:MINIO_BUCKET_NAME = "td-webapi-files"

# Keycloak
$env:KEYCLOAK_SERVER_URL = "http://<KEYCLOAK_HOST>:8180"
$env:KEYCLOAK_REALM = "td-webapi-realm"
$env:KEYCLOAK_CLIENT_ID = "td-webapi-client"
$env:KEYCLOAK_CLIENT_SECRET = "<YOUR_CLIENT_SECRET>"

# Database profile for documents
# Default PostgreSQL: dev hoặc postgres
$env:SPRING_PROFILES_ACTIVE = "postgres"

Write-Host "Environment variables set successfully!" -ForegroundColor Green
```

**Sử dụng:**
```powershell
# Load biến môi trường
. .\set-env.ps1

# Chạy ứng dụng
mvn -pl td-web spring-boot:run
```

---

## 7. CHẠY ỨNG DỤNG LẦN ĐẦU

```powershell
# 1. Load environment variables
. .\set-env.ps1

# 2. Chạy application
cd td-web
mvn spring-boot:run

# Hoặc chạy với DB profile khác cho documents
mvn spring-boot:run -Dspring-boot.run.profiles=tidb
mvn spring-boot:run -Dspring-boot.run.profiles=mariadb
```

Lưu ý:
- `tidb` dùng `jdbc:mysql://...` và native JSON prefilter cho module documents.
- `mariadb` dùng `jdbc:mariadb://...` và native JSON prefilter cho module documents.
- Hai profile này đang tắt Flyway mặc định; hãy chuẩn bị schema tương thích trước khi chạy.

**Quan sát logs:**
- ✅ Flyway migrations chạy thành công (tạo tables trong PostgreSQL)
- ✅ MongoDB connection established
- ✅ MinIO connection established  
- ✅ Keycloak OAuth2 configured
- ✅ Application started on port 8080

---

## 8. KIỂM TRA ỨNG DỤNG

### Health Check (không cần auth)
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/health"
```

### Lấy Access Token
```powershell
$tokenResp = Invoke-RestMethod `
  -Uri "http://<KEYCLOAK_HOST>:8180/realms/td-webapi-realm/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "client_id=td-webapi-client&client_secret=<CLIENT_SECRET>&grant_type=password&username=admin&password=admin123"

$token = $tokenResp.access_token
Write-Host "Access Token: $token"
```

### Test API
```powershell
$headers = @{ Authorization = "Bearer $token" }
$body = '{"pageNumber": 0, "pageSize": 10}' 

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/products/search" `
  -Method POST `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

### Test Documents APIs (CRUD + xem + tim kiem)
```powershell
$headers = @{ Authorization = "Bearer $token" }

# Them moi
$create = Invoke-RestMethod `
   -Uri "http://localhost:8080/api/v1/documents" `
   -Method POST `
   -Headers $headers `
   -ContentType "application/json" `
   -Body '{
      "title":"Thong bao noi bo",
      "documentType":"NOTICE",
      "status":"ACTIVE",
      "content":"Noi dung",
      "attributes":{"department":"HR"}
   }'

$docId = $create.data

# Xem danh sach
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents?pageNumber=0&pageSize=10" -Method GET -Headers $headers

# Xem chi tiet
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" -Method GET -Headers $headers

# Tim kiem
Invoke-RestMethod `
   -Uri "http://localhost:8080/api/v1/documents/search" `
   -Method POST `
   -Headers $headers `
   -ContentType "application/json" `
   -Body '{"keyword":"thong bao","pageNumber":0,"pageSize":10}'

# Sua
Invoke-RestMethod `
   -Uri "http://localhost:8080/api/v1/documents/$docId" `
   -Method PUT `
   -Headers $headers `
   -ContentType "application/json" `
   -Body "{\"id\":\"$docId\",\"title\":\"Thong bao noi bo - cap nhat\"}"

# Xoa mem
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/documents/$docId" -Method DELETE -Headers $headers
```

### Swagger UI
Mở trình duyệt: http://localhost:8080/swagger-ui.html

---

## ✅ CHECKLIST HOÀN THÀNH

- [ ] Java 17+ đã cài (`java -version`)
- [ ] Maven 3.8+ đã cài (`mvn -version`)
- [ ] Kết nối được PostgreSQL (`Test-NetConnection`)
- [ ] Kết nối được MongoDB (`Test-NetConnection`)
- [ ] Kết nối được MinIO (`Test-NetConnection`)
- [ ] Kết nối được Keycloak (`Test-NetConnection`)
- [ ] Database `tdwebapi` đã tạo trong PostgreSQL
- [ ] Bucket `td-webapi-files` đã tạo trong MinIO
- [ ] Keycloak realm `td-webapi-realm` đã setup với client và users
- [ ] File `set-env.ps1` đã tạo với thông tin đúng
- [ ] Build thành công (`mvn clean install`)
- [ ] Application chạy thành công
- [ ] Health check trả về OK
- [ ] Lấy token Keycloak thành công
- [ ] Gọi API thành công

---

## 🚨 TROUBLESHOOTING

### Lỗi connection timeout
→ Kiểm tra firewall, đảm bảo ports mở: 5432, 27017, 9000, 8180

### Flyway migration failed
→ Kiểm tra user PostgreSQL có quyền CREATE TABLE

### Keycloak unauthorized
→ Kiểm tra client secret, realm name, và user credentials

### MinIO access denied
→ Kiểm tra access key/secret key và bucket đã tạo chưa

### Port 8080 already in use
→ Đổi port trong `application.yml` hoặc dừng process đang dùng port 8080

---

## 📚 TÀI LIỆU THAM KHẢO

- Chi tiết cấu hình: `docs/EXTERNAL_SERVICES.md`
- README chính: `README.md`
- Swagger UI khi app chạy: http://localhost:8080/swagger-ui.html

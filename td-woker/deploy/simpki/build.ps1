# =====================================================================
# Build td-worker.jar + đóng gói 1 thư mục "deploy bundle" sẵn sàng chép lên máy chủ test
# (docs/18-trien-khai-test-simpki.md). Dùng cho Windows. Bản Linux: build.sh.
#
# Mặc định KHÔNG copy secret thật (.env/*.p12) vào bundle — chỉ copy file .example + truststore CA
# công khai. Thêm -WithSecrets để copy CẢ .env thật + keystore .p12 thật (CHỈ dùng khi sắp chuyển
# ngay lên máy chủ test qua kênh an toàn; KHÔNG để bundle có secret nằm lâu/upload công khai).
#
# Kết quả: JAVA/td-woker/target/simpki-deploy/ (nằm trong target/ — đã .gitignore, không lọt git).
# =====================================================================
param(
    [switch]$WithSecrets
)
$ErrorActionPreference = "Stop"

$DeployDir = $PSScriptRoot
$ProjectDir = (Resolve-Path (Join-Path $DeployDir "..\..")).Path
$OutDir = Join-Path $ProjectDir "target\simpki-deploy"

Write-Host "== Build td-worker.jar (mvn -DskipTests package) =="
Push-Location $ProjectDir
try {
    & mvn -q -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw "mvn package that bai (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

Write-Host "== Dong goi deploy bundle: $OutDir =="
if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "certs\simpki") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "logs") | Out-Null

Copy-Item (Join-Path $ProjectDir "target\td-worker.jar") (Join-Path $OutDir "td-worker.jar")
Copy-Item (Join-Path $DeployDir "run-simpki.sh") (Join-Path $OutDir "run-simpki.sh")
Copy-Item (Join-Path $DeployDir "run-simpki.ps1") (Join-Path $OutDir "run-simpki.ps1")
Copy-Item (Join-Path $DeployDir "publish-test-signal.sh") (Join-Path $OutDir "publish-test-signal.sh")
Copy-Item (Join-Path $DeployDir "publish-test-signal.ps1") (Join-Path $OutDir "publish-test-signal.ps1")
Copy-Item (Join-Path $DeployDir "publish-test-http.sh") (Join-Path $OutDir "publish-test-http.sh")
Copy-Item (Join-Path $DeployDir "publish-test-http.ps1") (Join-Path $OutDir "publish-test-http.ps1")
Copy-Item (Join-Path $DeployDir ".env.simpki.example") (Join-Path $OutDir ".env.simpki.example")

$trustStore = Join-Path $ProjectDir "certs\simpki\truststore-bcy.jks"
if (Test-Path $trustStore) {
    Copy-Item $trustStore (Join-Path $OutDir "certs\simpki\truststore-bcy.jks")
    Write-Host "Da copy truststore-bcy.jks (CA cong khai, khong phai secret)."
}

if ($WithSecrets) {
    Write-Host "!! -WithSecrets: copy .env that + keystore .p12 that vao bundle"
    $envFile = Join-Path $ProjectDir ".env"
    if (Test-Path $envFile) {
        Copy-Item $envFile (Join-Path $OutDir ".env")
    } else {
        Write-Warning "Khong thay $envFile de copy."
    }
    $p12Dir = Join-Path $ProjectDir "certs\simpki"
    if (Test-Path $p12Dir) {
        Get-ChildItem -Path $p12Dir -Filter "*.p12" -ErrorAction SilentlyContinue | ForEach-Object {
            Copy-Item $_.FullName (Join-Path $OutDir "certs\simpki\$($_.Name)")
            Write-Host "Da copy $($_.Name)"
        }
    }
    Write-Host "!! CANH BAO: $OutDir gio CHUA SECRET THAT (private key/mat khau)."
    Write-Host "!! Chi chuyen qua kenh an toan (scp/rsync qua SSH, hoac copy thu cong qua RDP noi bo)."
    Write-Host "!! KHONG upload len noi cong khai. Xoa khoi may build sau khi chuyen xong neu khong con can."
} else {
    Write-Host "Bo qua secret that (.env/*.p12) - dung -WithSecrets khi san sang chuyen len may chu test."
}

Write-Host "== Xong: $OutDir =="
Write-Host "Chuyen thu muc nay len may chu test, dam bao co .env + certs/simpki/*.p12 that roi chay run-simpki.ps1 (hoac run-simpki.sh neu may chu la Linux)."

# TD WebAPI - chay app cuc bo, ket noi vao server dev dung xa (10.10.10.116 ...)
# Nap bien moi truong tu .env (KHONG duoc tu dong doc boi Spring Boot / Maven),
# sau do goi mvn spring-boot:run.
#
# Cach dung:
#   1. Copy .env.example -> .env (da lam san neu chua co), chinh sua gia tri neu can.
#   2. .\run-dev.ps1

$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Host "[ERROR] Khong tim thay $envFile - hay copy .env.example thanh .env truoc." -ForegroundColor Red
    exit 1
}

Write-Host "Nap bien moi truong tu $envFile ..." -ForegroundColor Yellow

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }

    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }

    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()

    Set-Item -Path "Env:$key" -Value $value
    Write-Host "  $key" -ForegroundColor DarkGray
}

Write-Host "[OK] Da nap bien moi truong" -ForegroundColor Green
Write-Host ""
# Bugfix 03/09/2026 (bao loi nguoi dung: sua code xong build+restart bang script nay nhung KHONG
# thay hieu luc - phai build/restart LAN NUA moi thay, du chi 1 lan la du binh thuong; sua danh
# muc/danh sach cung phai restart moi thay - dung 1 mau hinh voi "build/restart khong ra hieu luc
# ngay") - "mvn install" (KHONG co "clean") chi bien dich lai file .java co timestamp MOI HON file
# .class tuong ung trong target/ - build incremental nay co the bo sot thay doi trong 1 so tinh
# huong (nhieu module build xen ke IDE + CLI, xoa/doi ten class, cache plugin...), khien
# spring-boot:run chay tren bytecode CU cua 1 vai class dua tren nghi ngo hop ly nhat sau khi da
# loai tru duoc nguyen nhan du lieu/cache Redis (da xac nhan qua truy van CSDL truc tiep - du lieu
# dung nhung API van tra sai/rong, code moi cung khong thay log moi them vao). Them "clean" de MOI
# lan chay script nay LUON bien dich lai TOAN BO tu dau, loai tru han kha nang nay - danh doi 1 chut
# thoi gian build de dam bao chac chan dang chay dung code moi nhat.
Write-Host "Chay: mvn -pl td-web -am clean install -DskipTests -q" -ForegroundColor Cyan
mvn -pl td-web -am clean install -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Build that bai, dung lai truoc khi spring-boot:run." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Chay: mvn -pl td-web spring-boot:run" -ForegroundColor Cyan
mvn -pl td-web spring-boot:run

# =====================================================================
# Chạy td-worker CHỈ với module simpki.sign (profile "simpki", docs/18-trien-khai-test-simpki.md).
# Dùng cho Windows (PowerShell 5.1+). Bản Linux tương ứng: run-simpki.sh (cùng thư mục).
#
# Yêu cầu TRƯỚC khi chạy (xem docs/18-trien-khai-test-simpki.md):
#   1. td-worker.jar (copy từ target/td-worker.jar sau khi build, hoặc dùng build.ps1).
#   2. .env (copy từ .env.simpki.example, điền giá trị thật — KHÔNG commit).
#   3. certs/simpki/truststore-bcy.jks + certs/simpki/<keystore-that>.p12 (KHÔNG commit).
#   4. Database tdworker đã tạo (create-database.sql) — Worker tự migrate bảng lúc khởi động.
#
# Script LUÔN chạy TỪ thư mục chứa chính nó — mọi đường dẫn tương đối trong .env
# (certs/simpki/..., logs/...) đều tính từ đây.
#
# QUAN TRỌNG (đã tự phát hiện bằng thực nghiệm — không suy đoán): Spring Boot đọc ".env" qua
# "spring.config.import: optional:file:.env[.properties]" — cơ chế này KHÔNG áp dụng "relaxed
# binding" đầy đủ (không tự map WORKER_TASKS_SIMPKI_ENABLED -> worker.tasks.simpki.enabled) cho các
# property KHÔNG có placeholder ${...} tương ứng khai báo sẵn trong application.yml (vd
# worker.tasks.simpki.*, worker.tasks.guinhanquocgia.*, worker.debug.endpoint.* — các module đăng
# ký qua @ConfigurationProperties riêng, không đụng application.yml). Chỉ đọc file KHÔNG đủ — script
# này tự đặt từng dòng KEY=VALUE trong .env thành biến môi trường HỆ ĐIỀU HÀNH THẬT ($env:) trước khi
# chạy java (biến môi trường thật luôn được Spring relaxed-bind đúng, đã xác nhận bằng thực nghiệm).
# =====================================================================
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$JarFile = if ($env:SIMPKI_JAR) { $env:SIMPKI_JAR } else { "td-worker.jar" }
if (-not (Test-Path $JarFile)) {
    Write-Error "Khong tim thay $JarFile trong $PSScriptRoot - chay build.ps1 truoc, hoac copy target/td-worker.jar vao day."
    exit 1
}
if (Test-Path ".env") {
    Write-Host "Nap .env thanh bien moi truong that (xem ghi chu o tren)..."
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        Set-Item -Path "Env:$key" -Value $value
    }
} else {
    Write-Warning "Khong thay .env trong $PSScriptRoot - copy .env.simpki.example thanh .env va dien gia tri that truoc khi chay that."
}

New-Item -ItemType Directory -Force -Path "logs" | Out-Null

# Console Windows mac dinh dung code page KHONG phai UTF-8 (vd 1252/850) -> log tieng Viet ghi
# dung UTF-8 (logback-spring.xml da cau hinh dung) hien SAI tren console (vd "tß║íi" thay vi "tại")
# - chi la loi HIEN THI, file log (logs/td-worker.log) van dung tu truoc, khong can sua gi khac.
# Doi code page cua CUA SO console hien tai sang 65001 (UTF-8), khong anh huong tien trinh khac.
try { & chcp 65001 | Out-Null } catch {}
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = "simpki" }
$JavaOptsRaw = if ($env:JAVA_OPTS) { $env:JAVA_OPTS } else { "-Xms256m -Xmx512m" }
$JavaOptsArgs = $JavaOptsRaw -split '\s+' | Where-Object { $_ -ne "" }
# -Dsun.stdout/stderr.encoding: bat JVM (Java 13+) ghi System.out/err bang UTF-8 thay vi code page
# console mac dinh cua Windows - can cho ca chcp 65001 O TREN lan CO nay thi log tieng Viet moi
# hien dung tren console (da kiem chung tren Java 17, deploy/simpki).
$EncodingArgs = @("-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8", "-Dfile.encoding=UTF-8")

Write-Host "Khoi dong td-worker (profile=$($env:SPRING_PROFILES_ACTIVE)) tu $PSScriptRoot ..."
& java @EncodingArgs @JavaOptsArgs -jar $JarFile
exit $LASTEXITCODE

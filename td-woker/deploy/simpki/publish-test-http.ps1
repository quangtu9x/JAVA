# =====================================================================
# Trigger 1 envelope "simpki.sign" thử nghiệm qua HTTP TRỰC TIẾP (KHÔNG cần RabbitMQ) trên Windows —
# xem docs/18-trien-khai-test-simpki.md mục "Test độc lập không cần RabbitMQ". Bản Linux:
# publish-test-http.sh. Yêu cầu Worker đang chạy với WORKER_DEBUG_ENDPOINT_ENABLED=true trong .env.
#
# Dùng:
#   .\publish-test-http.ps1 -Msisdn 84972495193 -HashText "noi dung test"
# =====================================================================
param(
    [string]$Msisdn = "84900000000",
    [string]$HashText = "noi dung test simpki $(Get-Date -UFormat %s)",
    [string]$WorkerUrl = $(if ($env:WORKER_HTTP_URL) { $env:WORKER_HTTP_URL } else { "http://localhost:8090" })
)
$ErrorActionPreference = "Stop"

$HashBytes = [System.Text.Encoding]::UTF8.GetBytes($HashText)
$HashB64 = [Convert]::ToBase64String($HashBytes)

$Ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$Rand = Get-Random
$MessageId = "msg-simpki-http-$Ts-$Rand"
$UnitId = "u-simpki-http-$Ts-$Rand"
$CreatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$Envelope = [ordered]@{
    messageId     = $MessageId
    taskType      = "simpki.sign"
    correlationId = $MessageId
    source        = "publish-test-http.ps1"
    createdAt     = $CreatedAt
    headers       = @{}
    units         = @(
        [ordered]@{
            unitId   = $UnitId
            tenantId = "test"
            payload  = [ordered]@{
                documentId = "test-doc-1"
                msisdn     = $Msisdn
                hashBase64 = $HashB64
            }
            target   = @{}
        }
    )
} | ConvertTo-Json -Depth 10

Write-Host "POST $WorkerUrl/internal/debug/publish  messageId=$MessageId unitId=$UnitId msisdn=$Msisdn"
Write-Host "(Luu y: neu ky that, request nay CHO toi worker.tasks.simpki.timeout-seconds - mac dinh"
Write-Host " 300s - vi dang cho nguoi dung xac nhan tren dien thoai.)"

try {
    $response = Invoke-RestMethod -Uri "$WorkerUrl/internal/debug/publish" -Method Post -ContentType "application/json" -Body $Envelope
    Write-Host ($response | ConvertTo-Json -Compress)
} catch {
    Write-Host $_.Exception.Message
    if ($_.ErrorDetails) { Write-Host $_.ErrorDetails.Message }
    exit 1
}

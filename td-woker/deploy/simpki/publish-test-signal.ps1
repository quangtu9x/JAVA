# =====================================================================
# Publish 1 envelope "simpki.sign" thử nghiệm vào RabbitMQ (qua REST API của plugin management)
# để kiểm tra end-to-end trên Windows — xem docs/18-trien-khai-test-simpki.md. Bản Linux:
# publish-test-signal.sh.
#
# Dùng:
#   .\publish-test-signal.ps1 -Msisdn 84972495193 -HashText "noi dung test"
# =====================================================================
param(
    [string]$Msisdn = "84900000000",
    [string]$HashText = "noi dung test simpki $(Get-Date -UFormat %s)",
    [string]$MgmtUrl = $(if ($env:RABBITMQ_MGMT_URL) { $env:RABBITMQ_MGMT_URL } else { "http://localhost:15672" }),
    [string]$MgmtUser = $(if ($env:RABBITMQ_MGMT_USER) { $env:RABBITMQ_MGMT_USER } else { "guest" }),
    [string]$MgmtPass = $(if ($env:RABBITMQ_MGMT_PASS) { $env:RABBITMQ_MGMT_PASS } else { "guest" }),
    [string]$VHost = $(if ($env:RABBITMQ_VHOST) { $env:RABBITMQ_VHOST } else { "/" }),
    [string]$Exchange = $(if ($env:WORKER_RABBIT_EXCHANGE) { $env:WORKER_RABBIT_EXCHANGE } else { "worker.exchange" })
)
$ErrorActionPreference = "Stop"

$HashBytes = [System.Text.Encoding]::UTF8.GetBytes($HashText)
$HashB64 = [Convert]::ToBase64String($HashBytes)

$Ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$Rand = Get-Random
$MessageId = "msg-simpki-test-$Ts-$Rand"
$UnitId = "u-simpki-test-$Ts-$Rand"
$CreatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$Envelope = [ordered]@{
    messageId     = $MessageId
    taskType      = "simpki.sign"
    correlationId = $MessageId
    source        = "publish-test-signal.ps1"
    createdAt     = $CreatedAt
    headers       = @{}
    units         = @(
        [ordered]@{
            unitId   = $UnitId
            tenantId = "test"
            payload  = [ordered]@{
                documentId  = "test-doc-1"
                msisdn      = $Msisdn
                hashBase64  = $HashB64
            }
            target   = @{}
        }
    )
}
$EnvelopeJson = $Envelope | ConvertTo-Json -Depth 10 -Compress

$VHostEnc = [uri]::EscapeDataString($VHost)
$PublishBody = @{
    properties       = @{ delivery_mode = 2; content_type = "application/json" }
    routing_key      = "worker.simpki.sign"
    payload          = $EnvelopeJson
    payload_encoding = "string"
} | ConvertTo-Json -Depth 10

Write-Host "Publish messageId=$MessageId, unitId=$UnitId, msisdn=$Msisdn -> exchange=$Exchange, routing_key=worker.simpki.sign"

$Pair = "$($MgmtUser):$($MgmtPass)"
$Basic = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes($Pair))
$Headers = @{ Authorization = "Basic $Basic" }
$Url = "$MgmtUrl/api/exchanges/$VHostEnc/$Exchange/publish"

$Response = Invoke-RestMethod -Uri $Url -Method Post -Headers $Headers -ContentType "application/json" -Body $PublishBody
Write-Host "RabbitMQ response: $($Response | ConvertTo-Json -Compress)"

if ($Response.routed -eq $true) {
    Write-Host "OK - message da duoc route toi it nhat 1 queue. Kiem tra log Worker + bang worker_simpki_sign_log."
} else {
    Write-Warning "routed=false - KHONG queue nao bind dung routing key 'worker.simpki.sign'."
    Write-Warning "Kiem tra: Worker (profile simpki) da khoi dong + khai bao topology chua (log 'RabbitMQ topology: ... routing=worker.simpki.sign')."
    exit 1
}

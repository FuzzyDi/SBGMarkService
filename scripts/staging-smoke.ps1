param(
    [string]$BaseUrl = "http://localhost:8080"
)

$healthUrl = "$BaseUrl/actuator/health"
$summaryUrl = "$BaseUrl/api/v1/reports/summary"
$historyUrl = "$BaseUrl/api/v1/reports/history?limit=5"

try {
    $health = Invoke-RestMethod -Method Get -Uri $healthUrl -TimeoutSec 10
}
catch {
    throw "Health request failed: $healthUrl"
}

if ($health.status -ne "UP") {
    throw "Unexpected health status: $($health.status)"
}

Write-Host "Health status: $($health.status)"

try {
    $summary = Invoke-RestMethod -Method Get -Uri $summaryUrl -TimeoutSec 10
    $history = Invoke-RestMethod -Method Get -Uri $historyUrl -TimeoutSec 10
}
catch {
    throw "Reports endpoint check failed."
}

Write-Host "Summary endpoint is reachable."
Write-Host ($summary | ConvertTo-Json -Depth 5)
Write-Host "History endpoint is reachable."
Write-Host ($history | ConvertTo-Json -Depth 5)

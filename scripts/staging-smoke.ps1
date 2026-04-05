param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Role = "",
    [string]$Token = ""
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = (Resolve-Path (Join-Path $scriptDir "..")).Path
$envFile = Join-Path $rootDir ".env"

function Read-EnvFile {
    param([string]$Path)
    $values = @{}
    if (!(Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }
        $pair = $trimmed -split "=", 2
        if ($pair.Count -eq 2) {
            $values[$pair[0].Trim()] = $pair[1].Trim()
        }
    }
    return $values
}

$envValues = Read-EnvFile -Path $envFile
$authEnabled = (($envValues["SBG_MARKING_AUTH_ENABLED"] + "").ToLower() -eq "true")
$requestHeaders = @{}

if ($authEnabled) {
    $resolvedRole = if ([string]::IsNullOrWhiteSpace($Role)) { "OPERATOR" } else { $Role.Trim().ToUpperInvariant() }
    $resolvedToken = if ([string]::IsNullOrWhiteSpace($Token)) { $envValues["SBG_MARKING_AUTH_OPERATOR_TOKEN"] } else { $Token }

    if ([string]::IsNullOrWhiteSpace($resolvedToken)) {
        throw "Auth is enabled but token is empty. Set SBG_MARKING_AUTH_OPERATOR_TOKEN in .env or pass -Token."
    }

    $requestHeaders["X-SBG-Role"] = $resolvedRole
    $requestHeaders["X-SBG-Token"] = $resolvedToken
    $requestHeaders["X-SBG-User"] = "staging-smoke"
    $requestHeaders["X-Request-Id"] = "smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
}

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
    $summary = Invoke-RestMethod -Method Get -Uri $summaryUrl -TimeoutSec 10 -Headers $requestHeaders
    $history = Invoke-RestMethod -Method Get -Uri $historyUrl -TimeoutSec 10 -Headers $requestHeaders
}
catch {
    throw "Reports endpoint check failed."
}

Write-Host "Summary endpoint is reachable."
Write-Host ($summary | ConvertTo-Json -Depth 5)
Write-Host "History endpoint is reachable."
Write-Host ($history | ConvertTo-Json -Depth 5)

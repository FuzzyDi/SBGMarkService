param(
    [switch]$SkipBuild,
    [switch]$SkipHealthCheck,
    [int]$HealthRetries = 20,
    [int]$HealthDelaySec = 2
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = (Resolve-Path (Join-Path $scriptDir "..")).Path

Set-Location $rootDir

$envFile = Join-Path $rootDir ".env"
$envExample = Join-Path $rootDir ".env.staging.example"

if (!(Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath $envExample -Destination $envFile
    Write-Host "Created .env from .env.staging.example"
}

$dockerArgs = @("compose", "-f", "docker-compose.staging.yml", "up", "-d")
if (!$SkipBuild) {
    $dockerArgs += "--build"
}

& docker @dockerArgs
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed"
}

if ($SkipHealthCheck) {
    Write-Host "Staging stack started. Health check was skipped."
    return
}

for ($attempt = 1; $attempt -le $HealthRetries; $attempt++) {
    try {
        $health = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5
        if ($health.status -eq "UP") {
            Write-Host "Staging server is UP."
            return
        }
    }
    catch {
        # Service may still be starting up.
    }

    Start-Sleep -Seconds $HealthDelaySec
}

throw "Health check failed after $HealthRetries attempts."

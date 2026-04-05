param(
    [switch]$WithVolumes
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = (Resolve-Path (Join-Path $scriptDir "..")).Path

Set-Location $rootDir

$dockerArgs = @("compose", "-f", "docker-compose.staging.yml", "down")
if ($WithVolumes) {
    $dockerArgs += "-v"
}

& docker @dockerArgs
if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed"
}

Write-Host "Staging stack stopped."

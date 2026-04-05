param(
    [string]$PidFile = "$PSScriptRoot\run\sbg-marking-server.pid"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (!(Test-Path -LiteralPath $PidFile)) {
    Write-Host "PID file not found. Server is likely not running."
    exit 0
}

$pidValue = (Get-Content -LiteralPath $PidFile | Select-Object -First 1).Trim()
if (!($pidValue -match '^[0-9]+$')) {
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    throw "Invalid PID file content: $pidValue"
}

$process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $process.Id -Force
    Write-Host "Server process stopped. PID=$($process.Id)"
} else {
    Write-Host "Process PID=$pidValue not found."
}

Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue

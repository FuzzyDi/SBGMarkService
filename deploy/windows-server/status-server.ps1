param(
    [string]$EnvFile = "$PSScriptRoot\marking-server.env",
    [string]$PidFile = "$PSScriptRoot\run\sbg-marking-server.pid",
    [string]$BaseUrl = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-EnvValue {
    param([string]$Path, [string]$Name)

    if (!(Test-Path -LiteralPath $Path)) {
        return $null
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }

        $pair = $trimmed -split '=', 2
        if ($pair.Count -ne 2) {
            continue
        }

        if ($pair[0].Trim() -eq $Name) {
            return $pair[1].Trim()
        }
    }

    return $null
}

$running = $false
if (Test-Path -LiteralPath $PidFile) {
    $pidValue = (Get-Content -LiteralPath $PidFile | Select-Object -First 1).Trim()
    if ($pidValue -match '^[0-9]+$') {
        $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
        if ($process) {
            $running = $true
            Write-Host "Process: RUNNING (PID=$pidValue)"
        }
    }
}

if (-not $running) {
    Write-Host "Process: STOPPED"
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $port = Read-EnvValue -Path $EnvFile -Name 'SERVER_PORT'
    if ([string]::IsNullOrWhiteSpace($port)) {
        $port = '8080'
    }
    $BaseUrl = "http://127.0.0.1:$port"
}

try {
    $health = Invoke-RestMethod -Method Get -Uri ("$BaseUrl/actuator/health") -TimeoutSec 5
    Write-Host "Health: $($health.status) ($BaseUrl)"
}
catch {
    Write-Host "Health: UNREACHABLE ($BaseUrl)"
}

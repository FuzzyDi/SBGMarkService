param(
    [string]$EnvFile = "$PSScriptRoot\marking-server.env",
    [string]$JarPath = "$PSScriptRoot\sbg-marking-server.jar",
    [string]$PidFile = "$PSScriptRoot\run\sbg-marking-server.pid",
    [switch]$Foreground
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Load-EnvFile {
    param([string]$Path)

    if (!(Test-Path -LiteralPath $Path)) {
        return
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

        $key = $pair[0].Trim()
        $value = $pair[1].Trim()
        Set-Item -Path ("Env:{0}" -f $key) -Value $value
    }
}

function Get-JavaExe {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    return 'java'
}

function Parse-Args {
    param([string]$Raw)

    if ([string]::IsNullOrWhiteSpace($Raw)) {
        return @()
    }

    return $Raw.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
}

Load-EnvFile -Path $EnvFile

if (!(Test-Path -LiteralPath $JarPath)) {
    throw "Jar not found: $JarPath"
}

$runDir = Split-Path -Parent $PidFile
$logDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if ((Test-Path -LiteralPath $PidFile) -and -not $Foreground) {
    $existingPid = (Get-Content -LiteralPath $PidFile | Select-Object -First 1).Trim()
    if ($existingPid -match '^[0-9]+$') {
        $existingProcess = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
        if ($existingProcess) {
            Write-Host "Server is already running. PID=$existingPid"
            exit 0
        }
    }
}

$javaExe = Get-JavaExe
$javaArgs = @()
$javaArgs += Parse-Args -Raw $env:HEAP_OPTS
$javaArgs += Parse-Args -Raw $env:JAVA_OPTS
$javaArgs += @('-jar', (Resolve-Path -LiteralPath $JarPath).Path)

if ($Foreground) {
    Write-Host "Starting in foreground..."
    & $javaExe @javaArgs
    exit $LASTEXITCODE
}

$stdoutLog = Join-Path $logDir 'server.out.log'
$stderrLog = Join-Path $logDir 'server.err.log'
$process = Start-Process -FilePath $javaExe -ArgumentList $javaArgs -WorkingDirectory $PSScriptRoot -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
Set-Content -LiteralPath $PidFile -Value $process.Id

Write-Host "Server started. PID=$($process.Id)"
Write-Host "Logs: $stdoutLog"
Write-Host "Errors: $stderrLog"

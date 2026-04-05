param(
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$bundleDir = Join-Path $rootDir 'deploy\windows-server'
$jarSource = Join-Path $rootDir 'sbg-marking-server\target\sbg-marking-server-1.0.0-SNAPSHOT.jar'
$jarTarget = Join-Path $bundleDir 'sbg-marking-server.jar'

if (-not $SkipBuild) {
    Push-Location $rootDir
    try {
        & mvn -B -V -ntp -pl sbg-marking-server -am -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw 'Maven build failed.'
        }
    }
    finally {
        Pop-Location
    }
}

if (!(Test-Path -LiteralPath $jarSource)) {
    throw "Server jar not found: $jarSource"
}

Copy-Item -LiteralPath $jarSource -Destination $jarTarget -Force

$envSample = Join-Path $bundleDir 'marking-server.env.example'
$envTarget = Join-Path $bundleDir 'marking-server.env'
if (!(Test-Path -LiteralPath $envTarget) -and (Test-Path -LiteralPath $envSample)) {
    Copy-Item -LiteralPath $envSample -Destination $envTarget
}

Write-Host "Windows bundle ready: $bundleDir"
Write-Host "Jar copied: $jarTarget"

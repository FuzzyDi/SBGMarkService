param(
    [string]$TaskName = 'SBG-Marking-Server',
    [string]$StartScriptPath = "$PSScriptRoot\start-server.ps1"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (!(Test-Path -LiteralPath $StartScriptPath)) {
    throw "Start script not found: $StartScriptPath"
}

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ("-NoProfile -ExecutionPolicy Bypass -File `"{0}`"" -f $StartScriptPath)
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Hours 0)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -RunLevel Highest -User 'SYSTEM' -Force | Out-Null
Write-Host "Startup task installed: $TaskName"

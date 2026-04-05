param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)

if (!(Test-Path -LiteralPath $JarPath)) {
    throw "File not found: $JarPath"
}

$bytes = [System.IO.File]::ReadAllBytes($JarPath)
$base64 = [System.Convert]::ToBase64String($bytes)

Set-Content -LiteralPath ".\set10_api_jar.base64.txt" -Value $base64 -Encoding ascii
Write-Host "Created set10_api_jar.base64.txt"
Write-Host "Use this value for GitHub Secret: SET10_API_JAR_BASE64"

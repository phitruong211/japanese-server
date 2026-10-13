param(
    [string]$Java = 'java',
    [string]$Jar = 'target/japanese-learning-api-0.0.1-SNAPSHOT.jar',
    [string]$Snapshot = '',
    [switch]$UseExistingSnapshot
)
$ErrorActionPreference = 'Stop'
$dictionaryRoot = Split-Path -Parent $PSScriptRoot
$dictionaryData = Join-Path $dictionaryRoot '.dictionary-data'
New-Item -ItemType Directory -Force -Path $dictionaryData | Out-Null
if (-not $Snapshot) { $Snapshot = Join-Path $dictionaryData ('JMdict_e-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.gz') }
if (-not $UseExistingSnapshot) {
    Invoke-WebRequest -Uri 'https://www.edrdg.org/pub/Nihongo/JMdict_e.gz' -OutFile $Snapshot
}
if (-not (Test-Path -LiteralPath $Snapshot -PathType Leaf)) { throw 'Dictionary snapshot is missing.' }
$Snapshot = (Resolve-Path -LiteralPath $Snapshot).Path
$dictionaryHash = (Get-FileHash -LiteralPath $Snapshot -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Snapshot SHA-256: $dictionaryHash"
# DB_URL / DB_USERNAME / DB_PASSWORD must point to the intended database.
# Run from the backend root so Spring can read its configured .env if desired.
Push-Location $dictionaryRoot
try {
    & $Java -jar $Jar '--spring.main.web-application-type=none' "--dictionary.import.file=$Snapshot"
    if ($LASTEXITCODE -ne 0) { throw "Dictionary import failed (exit $LASTEXITCODE). Previous catalog remains active." }
} finally { Pop-Location }

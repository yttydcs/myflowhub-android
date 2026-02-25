$ErrorActionPreference = 'Stop'

param(
  [string]$Target = 'android/arm64',
  [string]$JavaPkg = 'com.myflowhub.native',
  [string]$OutFile = 'app/libs/myflowhub.aar'
)

Write-Host "Build AAR via gomobile" -ForegroundColor Cyan
Write-Host "  Target : $Target"
Write-Host "  JavaPkg: $JavaPkg"
Write-Host "  OutFile: $OutFile"

$repoRoot = Split-Path -Parent $PSCommandPath | Split-Path -Parent
Set-Location $repoRoot

$env:GOWORK = 'off'

if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
  Write-Host "gomobile not found, installing..." -ForegroundColor Yellow
  go install golang.org/x/mobile/cmd/gomobile@latest
  $env:Path = "$env:Path;$env:GOPATH\\bin"
}

if (-not (Test-Path (Split-Path -Parent $OutFile))) {
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutFile) | Out-Null
}

Write-Host "Running: gomobile init" -ForegroundColor Cyan
gomobile init

Push-Location hubmobile
try {
  Write-Host "Running: gomobile bind" -ForegroundColor Cyan
  gomobile bind -target $Target -javapkg $JavaPkg -o (Join-Path $repoRoot $OutFile) .
} finally {
  Pop-Location
}

Write-Host "Done: $OutFile" -ForegroundColor Green


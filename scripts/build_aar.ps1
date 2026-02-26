param(
  [string]$Target = 'android/arm64',
  [int]$AndroidApi = 26,
  [string]$JavaPkg = 'com.myflowhub.gomobile',
  [string]$OutFile = 'app/libs/myflowhub.aar'
)

$ErrorActionPreference = 'Stop'

Write-Host "Build AAR via gomobile" -ForegroundColor Cyan
Write-Host "  Target : $Target"
Write-Host "  AndroidApi: $AndroidApi"
Write-Host "  JavaPkg: $JavaPkg"
Write-Host "  OutFile: $OutFile"

$repoRoot = Split-Path -Parent $PSCommandPath | Split-Path -Parent
Set-Location $repoRoot

$env:GOWORK = 'off'

if ($AndroidApi -le 0) {
  throw "AndroidApi 非法：$AndroidApi（期望为正整数）"
}
if ($AndroidApi -lt 21) {
  throw "AndroidApi 过低：$AndroidApi（NDK r26 仅支持 21..34，且本项目 minSdk=26）"
}

if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
  Write-Host "gomobile not found, installing..." -ForegroundColor Yellow
  go install golang.org/x/mobile/cmd/gomobile@latest
  $goPath = (go env GOPATH).Trim()
  if ($goPath) {
    $env:Path = "$env:Path;$goPath\\bin"
  }
}

if (-not (Test-Path (Split-Path -Parent $OutFile))) {
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutFile) | Out-Null
}

Write-Host "Running: gomobile init" -ForegroundColor Cyan
gomobile init

Push-Location hubmobile
try {
  Write-Host "Running: gomobile bind" -ForegroundColor Cyan
  gomobile bind -target $Target -androidapi $AndroidApi -javapkg $JavaPkg -o (Join-Path $repoRoot $OutFile) .
} finally {
  Pop-Location
}

Write-Host "Done: $OutFile" -ForegroundColor Green

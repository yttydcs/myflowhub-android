# Context: This file supports the Android app or gomobile host flow around build_aar.

param(
  [string]$Target = 'android/arm64',
  [int]$AndroidApi = 26,
  [string]$JavaPkg = 'com.myflowhub.gomobile',
  [string]$OutFile = 'app/libs/myflowhub.aar'
)

$ErrorActionPreference = 'Stop'

function Invoke-NativeChecked {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$FilePath,
    [string[]]$Arguments = @()
  )

  & $FilePath @Arguments
  $exitCode = $LASTEXITCODE
  if ($exitCode -ne 0) {
    throw "$Label failed with exit code $exitCode"
  }
}

function Ensure-GoBinInPath {
  $goPath = (& go env GOPATH | Out-String).Trim()
  $exitCode = $LASTEXITCODE
  if ($exitCode -ne 0) {
    throw "go env GOPATH failed with exit code $exitCode"
  }
  if ([string]::IsNullOrWhiteSpace($goPath)) {
    return
  }

  $goBin = Join-Path $goPath 'bin'
  $pathEntries = @($env:Path -split ';') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  if ($pathEntries -notcontains $goBin) {
    $env:Path = "$env:Path;$goBin"
  }
}

function Resolve-XMobileVersion {
  param(
    [Parameter(Mandatory = $true)][string]$RepoRoot
  )

  Push-Location (Join-Path $RepoRoot 'hubmobile')
  try {
    $version = (& go list -m -f '{{.Version}}' golang.org/x/mobile 2>$null | Out-String).Trim()
    $exitCode = $LASTEXITCODE
  } finally {
    Pop-Location
  }

  if ($exitCode -ne 0) {
    return $null
  }
  if ([string]::IsNullOrWhiteSpace($version) -or $version -eq '(devel)') {
    return $null
  }
  return $version
}

function Install-GomobileTools {
  param(
    [Parameter(Mandatory = $true)][string]$RepoRoot
  )

  $mobileVersion = Resolve-XMobileVersion -RepoRoot $RepoRoot
  if ($mobileVersion) {
    Write-Host "Installing gomobile/gobind: $mobileVersion" -ForegroundColor Cyan
    Invoke-NativeChecked -Label 'go install gomobile' -FilePath 'go' -Arguments @('install', "golang.org/x/mobile/cmd/gomobile@$mobileVersion")
    Invoke-NativeChecked -Label 'go install gobind' -FilePath 'go' -Arguments @('install', "golang.org/x/mobile/cmd/gobind@$mobileVersion")
  } else {
    Write-Warning '无法从 hubmobile/go.mod 解析 golang.org/x/mobile 版本，fallback 到 latest'
    Invoke-NativeChecked -Label 'go install gomobile' -FilePath 'go' -Arguments @('install', 'golang.org/x/mobile/cmd/gomobile@latest')
    Invoke-NativeChecked -Label 'go install gobind' -FilePath 'go' -Arguments @('install', 'golang.org/x/mobile/cmd/gobind@latest')
  }
  Ensure-GoBinInPath
}

function Resolve-AndroidSdkRoot {
  $sdkRoot = $env:ANDROID_HOME
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    $sdkRoot = $env:ANDROID_SDK_ROOT
  }
  if ([string]::IsNullOrWhiteSpace($sdkRoot) -and -not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $defaultSdk) {
      $sdkRoot = $defaultSdk
    }
  }
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw '未找到 Android SDK；请设置 ANDROID_HOME 或 ANDROID_SDK_ROOT，或安装到 %LOCALAPPDATA%\Android\Sdk'
  }

  $resolved = Resolve-Path -LiteralPath $sdkRoot -ErrorAction SilentlyContinue
  if ($null -eq $resolved) {
    throw "Android SDK 路径不存在：$sdkRoot"
  }

  $sdkPath = $resolved.ProviderPath
  $env:ANDROID_HOME = $sdkPath
  $env:ANDROID_SDK_ROOT = $sdkPath
  return $sdkPath
}

function Resolve-AndroidNdkHome {
  param(
    [Parameter(Mandatory = $true)][string]$SdkRoot
  )

  if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_NDK_HOME)) {
    $resolved = Resolve-Path -LiteralPath $env:ANDROID_NDK_HOME -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
      throw "ANDROID_NDK_HOME 路径不存在：$env:ANDROID_NDK_HOME"
    }
    $env:ANDROID_NDK_HOME = $resolved.ProviderPath
    return $env:ANDROID_NDK_HOME
  }

  $ndkRoot = Join-Path $SdkRoot 'ndk'
  if (-not (Test-Path $ndkRoot)) {
    throw "未找到 Android NDK；请先安装 ndk;26.1.10909125，或设置 ANDROID_NDK_HOME（当前 SDK：$SdkRoot）"
  }

  $candidate = Get-ChildItem -LiteralPath $ndkRoot -Directory |
    Sort-Object {
      try {
        [version]$_.Name
      } catch {
        [version]'0.0'
      }
    } |
    Select-Object -Last 1
  if ($null -eq $candidate) {
    throw "Android NDK 目录为空：$ndkRoot"
  }

  $env:ANDROID_NDK_HOME = $candidate.FullName
  return $env:ANDROID_NDK_HOME
}

Write-Host "Build AAR via gomobile" -ForegroundColor Cyan
Write-Host "  Target : $Target"
Write-Host "  AndroidApi: $AndroidApi"
Write-Host "  JavaPkg: $JavaPkg"
Write-Host "  OutFile: $OutFile"

$repoRoot = Split-Path -Parent $PSCommandPath | Split-Path -Parent
Set-Location $repoRoot

$env:GOWORK = 'off'

if ($AndroidApi -le 0) {
  throw "AndroidApi invalid: $AndroidApi (expected positive integer)"
}
if ($AndroidApi -lt 21) {
  throw "AndroidApi too low: $AndroidApi (NDK r26 supports 21..34; minSdk=26)"
}

$sdkRoot = Resolve-AndroidSdkRoot
$ndkRoot = Resolve-AndroidNdkHome -SdkRoot $sdkRoot
Write-Host "  AndroidSdk: $sdkRoot"
Write-Host "  AndroidNdk: $ndkRoot"

Ensure-GoBinInPath
if (-not (Get-Command gomobile -ErrorAction SilentlyContinue) -or -not (Get-Command gobind -ErrorAction SilentlyContinue)) {
  Write-Host "gomobile/gobind not found, installing..." -ForegroundColor Yellow
  Install-GomobileTools -RepoRoot $repoRoot
}

if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
  throw 'gomobile still not found after install'
}
if (-not (Get-Command gobind -ErrorAction SilentlyContinue)) {
  throw 'gobind still not found after install'
}

& gomobile version | Out-Host
if ($LASTEXITCODE -ne 0) {
  Write-Warning "gomobile version failed with exit code $LASTEXITCODE"
}

$outPath = if ([System.IO.Path]::IsPathRooted($OutFile)) { $OutFile } else { Join-Path $repoRoot $OutFile }
$outDir = Split-Path -Parent $outPath
if (-not (Test-Path $outDir)) {
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

Write-Host "Running: gomobile init" -ForegroundColor Cyan
Invoke-NativeChecked -Label 'gomobile init' -FilePath 'gomobile' -Arguments @('init')

Push-Location hubmobile
try {
  Write-Host "Running: gomobile bind" -ForegroundColor Cyan
  Invoke-NativeChecked -Label 'gomobile bind' -FilePath 'gomobile' -Arguments @(
    'bind',
    '-target', $Target,
    '-androidapi', "$AndroidApi",
    '-javapkg', $JavaPkg,
    '-o', $outPath,
    '.'
  )
} finally {
  Pop-Location
}

if (-not (Test-Path $outPath)) {
  throw "gomobile bind completed but output file was not created: $outPath"
}

Write-Host "Done: $outPath" -ForegroundColor Green

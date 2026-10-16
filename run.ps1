param([switch]$Demo)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$candidates += @(Get-ChildItem "$env:ProgramFiles\Java\jdk*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)
$candidates += @(Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium\jdk*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)
$jdk = $candidates | Where-Object { Test-Path (Join-Path $_ 'bin/java.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'Install JDK 21 or later and set JAVA_HOME to its folder.' }
if (-not (Test-Path 'build/WasteWise.jar')) { & "$PSScriptRoot/build.ps1" }
New-Item -ItemType Directory -Force 'build/javafx-cache' | Out-Null
$appArgs = @('--enable-native-access=javafx.graphics','-Djavafx.cachedir=build/javafx-cache','--module-path','lib','--add-modules','javafx.controls','-cp','build/WasteWise.jar','com.group6.wastewise.WasteWiseApp')
if ($Demo) { $appArgs += '--demo' }
& "$jdk/bin/java.exe" @appArgs
if ($LASTEXITCODE -ne 0) { throw 'Application failed. Run build.ps1 -Test to check the installation.' }

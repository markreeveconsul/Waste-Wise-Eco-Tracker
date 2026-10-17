param([switch]$Test, [switch]$UiTest)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jdkCandidates = @()
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$jdkCandidates += @(Get-ChildItem "$env:ProgramFiles\Java\jdk*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)
$jdkCandidates += @(Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium\jdk*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName)
$jdk = $jdkCandidates | Where-Object { Test-Path (Join-Path $_ 'bin/javac.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'Install JDK 21 or later and set JAVA_HOME to its folder.' }
New-Item -ItemType Directory -Force 'build/classes','build/test-classes','build/javafx-cache' | Out-Null
$sources = @(Get-ChildItem 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$jdk/bin/javac.exe" --release 21 --module-path lib --add-modules javafx.controls -d build/classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
Copy-Item 'src/main/resources/*' 'build/classes' -Recurse -Force
if ($Test -or $UiTest) {
    $tests = @(Get-ChildItem 'src/test/java' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & "$jdk/bin/javac.exe" --release 21 -cp build/classes -d build/test-classes @tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & "$jdk/bin/java.exe" -cp 'build/classes;build/test-classes' com.group6.wastewise.model.WasteTrackerTest
    if ($LASTEXITCODE -ne 0) { throw 'Model tests failed.' }
}
if ($UiTest) {
    & "$jdk/bin/java.exe" --enable-native-access=javafx.graphics '-Djavafx.cachedir=build/javafx-cache' '-Dwastewise.snapshots=build/qa' --module-path lib --add-modules javafx.controls -cp build/classes com.group6.wastewise.WasteWiseApp --smoke-test
    if ($LASTEXITCODE -ne 0) { throw 'UI tests failed.' }
}
$artifact = 'build/WasteWise-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '.jar'
& "$jdk/bin/jar.exe" "-J-Djava.io.tmpdir=$PSScriptRoot/build" --create --file $artifact --main-class com.group6.wastewise.WasteWiseApp -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Write-Host 'Build ready: run Launch WasteWise.vbs or Demo WasteWise.vbs.'

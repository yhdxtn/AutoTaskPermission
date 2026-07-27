@echo off
setlocal EnableExtensions

set "GRADLE_VERSION=9.5.0"
set "GRADLE_SHA256=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"

if defined GRADLE_USER_HOME (
  set "GRADLE_BASE=%GRADLE_USER_HOME%\autotask-wrapper"
) else (
  set "GRADLE_BASE=%USERPROFILE%\.gradle\autotask-wrapper"
)

set "CACHE_DIR=%GRADLE_BASE%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
set "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto runGradle

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "New-Item -ItemType Directory -Force -Path $env:CACHE_DIR | Out-Null;" ^
  "$download=$true;" ^
  "if (Test-Path $env:ZIP_FILE) { $hash=(Get-FileHash -Algorithm SHA256 $env:ZIP_FILE).Hash.ToLowerInvariant(); if ($hash -eq $env:GRADLE_SHA256) { $download=$false } else { Remove-Item -Force $env:ZIP_FILE } };" ^
  "if ($download) { Write-Host ('Downloading Gradle ' + $env:GRADLE_VERSION + ' ...'); Invoke-WebRequest -UseBasicParsing -Uri $env:DIST_URL -OutFile $env:ZIP_FILE };" ^
  "$hash=(Get-FileHash -Algorithm SHA256 $env:ZIP_FILE).Hash.ToLowerInvariant(); if ($hash -ne $env:GRADLE_SHA256) { Remove-Item -Force $env:ZIP_FILE; throw 'Gradle download checksum failed' };" ^
  "if (Test-Path $env:GRADLE_HOME) { Remove-Item -Recurse -Force $env:GRADLE_HOME };" ^
  "Expand-Archive -Force -Path $env:ZIP_FILE -DestinationPath $env:CACHE_DIR"
if errorlevel 1 exit /b 1

:runGradle
call "%GRADLE_BIN%" %*
exit /b %errorlevel%

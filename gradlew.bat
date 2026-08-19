@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 未安装  请使用 Android Studio 或 GitHub Actions
  exit /b 1
)
gradle %*

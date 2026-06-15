# For You, Actually - one-command local run (Windows / PowerShell)
# Launches the Spring Boot backend and the Angular dev server in separate windows.
#
#   ./run.ps1
#
# API keys are read from backend/.env (TMDB_API_KEY, GEMINI_API_KEY) via spring-dotenv,
# so you do not need to export anything first.

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not (Test-Path "$root\backend\.env")) {
    Write-Warning "backend\.env not found. Create it with TMDB_API_KEY and GEMINI_API_KEY before running."
}

Write-Host "Starting For You, Actually..." -ForegroundColor Cyan

# Backend - Spring Boot on :8080
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location `"$root\backend`"; mvn spring-boot:run"
)

# Frontend - Angular dev server on :4300 (installs deps on first run)
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location `"$root\frontend`"; if (-not (Test-Path node_modules)) { npm install }; npm start -- --port 4300"
)

Write-Host ""
Write-Host "  Backend  -> http://localhost:8080/api/health" -ForegroundColor Green
Write-Host "  Frontend -> http://localhost:4300" -ForegroundColor Green
Write-Host ""
Write-Host "Give the frontend ~20-30s to compile on first launch, then open the URL above." -ForegroundColor DarkGray

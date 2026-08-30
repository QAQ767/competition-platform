@echo off
title Competition Platform - Start All
cd /d "%~dp0"

echo ========================================
echo   Competition Team Platform - One-Click Start
echo ========================================
echo.

REM ====== Step 1: check Docker engine ======
echo [1/3] Checking Docker engine...
docker info >nul 2>&1
if not errorlevel 1 goto dockerready

echo    Docker is not running, starting Docker Desktop...
start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
set tries=0
:waitdocker
set /a tries+=1
docker info >nul 2>&1
if not errorlevel 1 goto dockerready
if %tries% GEQ 18 goto dockerfail
echo    Waiting for engine (%tries%/18)...
timeout /t 5 /nobreak >nul
goto waitdocker

:dockerfail
echo    [FAILED] Docker engine not ready. Please open Docker Desktop manually.
pause
exit /b 1

:dockerready
echo    [OK] Docker engine ready
echo.

REM ====== Step 2: check Ollama (local LLM) ======
echo [2/3] Checking Ollama local LLM (AI recommendation)...
powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>&1
if not errorlevel 1 goto ollamaok
echo    Ollama is not running, trying to start...
if exist "%LOCALAPPDATA%\Programs\Ollama\ollama app.exe" (
    start "" "%LOCALAPPDATA%\Programs\Ollama\ollama app.exe"
    timeout /t 6 /nobreak >nul
    goto ollamaok
)
echo    [WARNING] Ollama not found. AI recommendation will use rule engine.
:ollamaok
echo    [OK] LLM ready
echo.

REM ====== Step 3: start containers ======
echo [3/3] Starting MySQL + Redis + Backend + Frontend containers...
docker compose up -d

echo.
echo ========================================
echo   All services started!
echo   Open browser:  http://localhost
echo   Status:        docker compose ps
echo   Logs:          docker compose logs -f backend
echo   Stop all:      stop-all.bat
echo ========================================
pause

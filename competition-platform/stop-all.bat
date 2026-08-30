@echo off
title Competition Platform - Stop All
cd /d "%~dp0"

echo ========================================
echo   Competition Platform - One-Click Stop
echo ========================================
echo.
echo Stopping containers (data kept in Docker volumes)...
docker compose down

echo.
echo Done. You may quit Docker Desktop from the tray icon.
echo ========================================
pause

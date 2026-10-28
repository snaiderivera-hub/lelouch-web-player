@echo off
title LELOUCH PLUS Web Player — Lanzador Oficial PC
chcp 65001 > nul

echo =========================================================
echo   LELOUCH PLUS WEB PLAYER — Modo Local PC (Sin limites)
echo   Conectado en vivo a Supabase (public.playlists)
echo =========================================================
echo.
echo Iniciando servicios desde la carpeta iptv-app:
echo   [1/2] Gateway CORS IPTV   (http://localhost:7878)
echo   [2/2] Servidor Web Local  (http://localhost:8686)
echo.

REM Cerrar instancias previas colgadas si existen
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*iptv-app*proxy.ps1*' -or $_.CommandLine -like '*iptv-app*server.ps1*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

REM Iniciar Proxy CORS en ventana minimizada
start "LELOUCH CORS Proxy :7878" /MIN powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iptv-app\proxy.ps1"

timeout /t 1 /nobreak > nul

REM Iniciar Servidor Web Local en ventana minimizada (abre el navegador en http://localhost:8686)
start "LELOUCH Web Server :8686" /MIN powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iptv-app\server.ps1"

echo Listo! El navegador se abrira automaticamente en http://localhost:8686
timeout /t 3 > nul


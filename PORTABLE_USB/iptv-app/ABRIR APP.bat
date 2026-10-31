@echo off
title IPTV Data Architect — Iniciando...
chcp 65001 > nul

echo Iniciando IPTV Data Architect...
echo.
echo Se abriran DOS ventanas:
echo   1. Servidor de la aplicacion (puerto 8686)
echo   2. Proxy CORS                (puerto 7878)
echo.
echo Deja ambas ventanas abiertas mientras usas la app.
echo Cierra esta ventana cuando quieras detenerlo todo.
echo.

REM Iniciar Proxy CORS en nueva ventana
start "IPTV CORS Proxy :7878" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0proxy.ps1"

REM Esperar 1 segundo
timeout /t 1 /nobreak > nul

REM Iniciar Servidor App en nueva ventana (abre el navegador automaticamente)
start "IPTV App Server :8686" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server.ps1"

echo Listo. El navegador abrira automaticamente.
echo Presiona cualquier tecla para cerrar esta ventana de inicio.
pause > nul

@echo off
title Cerrar Lelouch IPTV y Liberar Puertos
chcp 65001 > nul

echo =========================================================
echo   Deteniendo Lelouch IPTV Player y liberando puertos
echo =========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*iptv-app*proxy.ps1*' -or $_.CommandLine -like '*iptv-app*server.ps1*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"

echo.
echo ✓ Servidor y Proxy detenidos con éxito.
echo ✓ Puertos 7878 y 8686 liberados.
echo ✓ Ya puedes retirar tu memoria USB de forma segura.
echo.
timeout /t 3 > nul
exit

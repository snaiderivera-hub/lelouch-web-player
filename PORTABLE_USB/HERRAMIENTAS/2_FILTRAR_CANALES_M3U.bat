@echo off
title M3U Filter & Extractor de Canales - LELOUCH
chcp 65001 > nul

echo ================================================================
echo   M3U Filter & Extractor de Enlaces (LELOUCH)
echo ================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Set-Location -Path '%~dp0'; .\m3u_filter.ps1 }"

echo.
echo ================================================================
echo Proceso finalizado. Presiona cualquier tecla para salir.
echo ================================================================
pause > nul


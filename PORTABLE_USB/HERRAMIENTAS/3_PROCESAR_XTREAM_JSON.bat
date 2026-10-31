@echo off
title Procesador IPTV Xtream Codes / M3U a JSON - LELOUCH
chcp 65001 > nul

echo ================================================================
echo   Procesador y Estructurador de Servidores Xtream / M3U
echo ================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Set-Location -Path '%~dp0'; .\procesar_iptv.ps1 }"

echo.
echo ================================================================
echo Proceso finalizado. Presiona cualquier tecla para salir.
echo ================================================================
pause > nul


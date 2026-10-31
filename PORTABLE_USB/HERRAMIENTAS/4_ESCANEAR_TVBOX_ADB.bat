@echo off
title Escaner de Red WiFi para Android TV / TV Box (ADB) - LELOUCH
chcp 65001 > nul

echo ================================================================
echo   Escaner de Dispositivos Android TV y TV Box en Red Local
echo ================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Set-Location -Path '%~dp0'; .\scan_adb.ps1 }"

echo.
echo ================================================================
echo Escaneo finalizado. Presiona cualquier tecla para salir.
echo ================================================================
pause > nul


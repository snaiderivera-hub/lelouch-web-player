@echo off
chcp 65001 > nul
title Procesador IPTV TO JSON
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0procesar_iptv.ps1"
echo.
pause

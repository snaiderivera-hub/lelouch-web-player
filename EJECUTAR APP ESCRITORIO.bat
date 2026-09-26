@echo off
title IPTV Data Architect — Escritorio Windows
chcp 65001 > nul

echo ==================================================
echo   Iniciando IPTV Data Architect (Ejecutable .EXE)
echo ==================================================
echo Limpiando instancias previas para liberar puertos...
taskkill /F /IM "IPTV-Data-Architect.exe" 2>nul
timeout /t 1 /nobreak > nul

echo Ejecutando aplicacion standalone para Windows...
start "" "%~dp0IPTV-Data-Architect.exe" --open-browser
exit

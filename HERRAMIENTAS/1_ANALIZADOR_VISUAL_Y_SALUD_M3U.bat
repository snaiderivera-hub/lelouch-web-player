@echo off
title Analizador Visual M3U y Verificador de Salud de Streams - LELOUCH
chcp 65001 > nul

echo ================================================================
echo   Iniciando Analizador Visual M3U y Stream Health Verifier
echo ================================================================
echo.
echo Abriendo extractor_visual.html en tu navegador predeterminado...
echo.

start "" "%~dp0extractor_visual.html"

timeout /t 2 > nul
exit


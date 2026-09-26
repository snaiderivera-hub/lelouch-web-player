@echo off
title Cerrar Lelouch IPTV
chcp 65001 > nul
echo ==================================================
echo   Deteniendo Lelouch IPTV Player y liberando puertos
echo ==================================================
taskkill /F /IM "IPTV-Data-Architect.exe" 2>nul
echo.
echo ✓ Proceso cerrado con éxito.
echo ✓ Ya puedes retirar tu memoria USB de forma segura.
ping -n 3 127.0.0.1 > nul
exit
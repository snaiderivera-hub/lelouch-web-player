@echo off
title IPTV Web Player en Brave
chcp 65001 > nul

set APP_URL=http://localhost:8686/
set BRAVE_PATH=C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe

echo ==================================================
echo   Abriendo Lelouch IPTV Web Player en Brave Browser
echo ==================================================
echo.

if exist "%BRAVE_PATH%" (
    start "" "%BRAVE_PATH%" "%APP_URL%"
) else (
    start "" "%APP_URL%"
)

exit

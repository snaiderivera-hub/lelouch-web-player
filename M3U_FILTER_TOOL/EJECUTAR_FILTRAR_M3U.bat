@echo off
chcp 65001 >nul
title M3U Filter ^& Extractor de Enlaces - LELOUCH
cd /d "%~dp0"

echo ============================================================
echo         M3U FILTER ^& EXTRACTOR DE ENLACES (LELOUCH)
echo ============================================================
echo.
echo Selecciona como deseas ejecutar el extractor:
echo.
echo   [1] Filtrar M3U usando lista de canales (canales_filtro.txt)
echo   [2] Extraer TODOS los enlaces directos de una lista M3U
echo   [3] Abrir Extractor Visual en el Navegador Web (Facil)
echo   [4] Ejecutar con Python (si tienes Python instalado)
echo   [5] Salir
echo.
set /p opt="Elige una opcion (1-5): "

if "%opt%"=="1" goto OP1
if "%opt%"=="2" goto OP2
if "%opt%"=="3" goto OP3
if "%opt%"=="4" goto OP4
if "%opt%"=="5" exit /b
goto OP1

:OP1
echo.
echo Ingrese el archivo .M3U o la URL del stream:
set /p m3upath="Ruta o URL [por defecto: lista.m3u]: "
if "%m3upath%"=="" set m3upath=lista.m3u

echo.
echo Ejecutando filtrado con PowerShell...
pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0m3u_filter.ps1" "%m3upath%" "canales_filtro.txt" "lista_filtrada.m3u"
if %errorlevel% neq 0 (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0m3u_filter.ps1" "%m3upath%" "canales_filtro.txt" "lista_filtrada.m3u"
)
pause
exit /b

:OP2
echo.
echo Ingrese el archivo .M3U o la URL del stream para extraer TODO:
set /p m3upath="Ruta o URL [por defecto: lista.m3u]: "
if "%m3upath%"=="" set m3upath=lista.m3u

echo.
echo Extrayendo todos los enlaces...
pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0m3u_filter.ps1" "%m3upath%" "" "todos_los_canales.m3u"
if %errorlevel% neq 0 (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0m3u_filter.ps1" "%m3upath%" "" "todos_los_canales.m3u"
)
pause
exit /b

:OP3
echo.
echo Abriendo extractor visual en el navegador...
start "" "%~dp0extractor_visual.html"
exit /b

:OP4
echo.
set /p m3upath="Ruta o URL del M3U: "
set /p filterpath="Ruta del TXT con canales [canales_filtro.txt]: "
if "%filterpath%"=="" set filterpath=canales_filtro.txt
python m3u_filter.py "%m3upath%" "%filterpath%" "out.m3u"
pause
exit /b

@echo off
title Lelouch IPTV - Servidor Portable USB
chcp 65001 > nul
cd /d "%~dp0"
cls

echo ========================================================
echo   LELOUCH IPTV PLAYER - SUITE PORTABLE USB
echo ========================================================
echo.

:: 1. Cerrar instancias previas
taskkill /F /IM "IPTV-Data-Architect.exe" 2>nul
ping -n 2 127.0.0.1 > nul

:: 2. Iniciar servidor local y proxy CORS en segundo plano
start "" "%~dp0IPTV-Data-Architect.exe"
ping -n 3 127.0.0.1 > nul

:: 3. Detectar navegadores instalados en esta computadora
set "BRAVE="
if exist "C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe" set "BRAVE=C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe"
if not defined BRAVE if exist "%LOCALAPPDATA%\BraveSoftware\Brave-Browser\Application\brave.exe" set "BRAVE=%LOCALAPPDATA%\BraveSoftware\Brave-Browser\Application\brave.exe"

set "CHROME="
if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" set "CHROME=C:\Program Files\Google\Chrome\Application\chrome.exe"
if not defined CHROME if exist "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" set "CHROME=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not defined CHROME if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" set "CHROME=%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"

set "EDGE="
if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" set "EDGE=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if not defined EDGE if exist "C:\Program Files\Microsoft\Edge\Application\msedge.exe" set "EDGE=C:\Program Files\Microsoft\Edge\Application\msedge.exe"

set "FIREFOX="
if exist "C:\Program Files\Mozilla Firefox\firefox.exe" set "FIREFOX=C:\Program Files\Mozilla Firefox\firefox.exe"
if not defined FIREFOX if exist "C:\Program Files (x86)\Mozilla Firefox\firefox.exe" set "FIREFOX=C:\Program Files (x86)\Mozilla Firefox\firefox.exe"

set "APP_URL=http://localhost:8686/"

echo   Elige el navegador para abrir la aplicacion:
echo.

if defined BRAVE (
    echo   [1] Brave Browser      -- Recomendado [Detectado]
) else (
    echo   [1] Brave Browser      -- No detectado
)

if defined CHROME (
    echo   [2] Google Chrome      -- [Detectado]
) else (
    echo   [2] Google Chrome      -- No detectado
)

if defined EDGE (
    echo   [3] Microsoft Edge     -- [Detectado]
) else (
    echo   [3] Microsoft Edge     -- No detectado
)

if defined FIREFOX (
    echo   [4] Mozilla Firefox    -- [Detectado]
) else (
    echo   [4] Mozilla Firefox    -- No detectado
)

echo   [5] Navegador por defecto de Windows
echo.
echo ========================================================

set "DEF_OP=1"
if not defined BRAVE (
    if defined CHROME (set "DEF_OP=2") else (set "DEF_OP=3")
)

echo.
echo   Presiona [1, 2, 3, 4 o 5] o espera 5s para iniciar con [%DEF_OP%]...
choice /c 12345 /n /t 5 /d %DEF_OP% /m "  Tu seleccion [1-5]: "
set "OP=%ERRORLEVEL%"

if "%OP%"=="1" (
    if defined BRAVE (
        echo   Abriendo en Brave Browser...
        start "" "%BRAVE%" "%APP_URL%"
    ) else (
        echo   Brave no encontrado. Abriendo navegador predeterminado...
        start "" "%APP_URL%"
    )
)
if "%OP%"=="2" (
    if defined CHROME (
        echo   Abriendo en Google Chrome...
        start "" "%CHROME%" "%APP_URL%"
    ) else (
        echo   Chrome no encontrado. Abriendo navegador predeterminado...
        start "" "%APP_URL%"
    )
)
if "%OP%"=="3" (
    if defined EDGE (
        echo   Abriendo en Microsoft Edge...
        start "" "%EDGE%" "%APP_URL%"
    ) else (
        start "" "%APP_URL%"
    )
)
if "%OP%"=="4" (
    if defined FIREFOX (
        echo   Abriendo en Mozilla Firefox...
        start "" "%FIREFOX%" "%APP_URL%"
    ) else (
        start "" "%APP_URL%"
    )
)
if "%OP%"=="5" (
    echo   Abriendo en navegador predeterminado de Windows...
    start "" "%APP_URL%"
)

echo.
echo ========================================================
echo   Lelouch IPTV esta activo en http://localhost:8686/
echo   El servidor seguira funcionando en segundo plano.
echo.
echo   [AVISO] Si presionas cualquier tecla por error, el servidor 
echo   podria cerrarse. Para cerrar de forma segura escribe 'S' y Enter.
echo ========================================================
:wait_close
set /p "CLOSE_OPT=  Escribe S para cerrar el servidor: "
if /I not "%CLOSE_OPT%"=="S" goto wait_close

:: Al confirmar, cerrar el servidor ordenadamente
taskkill /F /IM "IPTV-Data-Architect.exe" 2>nul
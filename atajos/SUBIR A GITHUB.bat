@echo off
cls
echo ==================================================
echo   Lelouch IPTV - Subir a GitHub
echo   https://github.com/snaiderivera-hub/lelouch-web-player.git
echo ==================================================
echo.
cd /d "%~dp0"
echo Enviando archivos a GitHub...
"C:\Program Files\Git\cmd\git.exe" push -u origin main
echo.
if %ERRORLEVEL% equ 0 (
    echo ==================================================
    echo   EXITO: Todo el proyecto se subio a GitHub!
    echo ==================================================
) else (
    echo ==================================================
    echo   Hubo un error al subir. Verifica la sesion de GitHub.
    echo ==================================================
)
echo.
pause
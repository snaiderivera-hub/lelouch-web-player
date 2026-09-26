@echo off
title Subir Respaldo a GitHub - Lelouch IPTV
chcp 65001 > nul
echo ==================================================
echo   Subiendo cambios a https://github.com/snaiderivera-hub/lelouch-web-player.git
echo ==================================================
cd /d "%~dp0"
"C:\Program Files\Git\cmd\git.exe" push -u origin main
echo.
if %ERRORLEVEL% EQU 0 (
  echo ✓ Respaldo subido a GitHub exitosamente.
) else (
  echo ❌ Error al subir. Verifica que hayas creado el repositorio en GitHub y aceptado la autenticacion.
)
echo.
pause

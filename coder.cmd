@echo off
REM Lelouch AI Assistant - Ollama Runner for qwen2.5-coder:7b
if "%~1"=="" (
    echo Iniciando sesion interactiva con Qwen 2.5 Coder en terminal...
    ollama run qwen2.5-coder:7b
) else (
    ollama run qwen2.5-coder:7b %*
)


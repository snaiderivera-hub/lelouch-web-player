<#
.SYNOPSIS
    Ejecuta el agente local Qwen 2.5 Coder (7B) descargado en Ollama.
.EXAMPLE
    .\scripts\coder.ps1
    .\scripts\coder.ps1 "Explica como funciona Hls.js en IPTV"
#>
param (
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Prompt
)

if (-not (Get-Command "ollama" -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Error: Ollama no esta instalado en el sistema o no esta en el PATH." -ForegroundColor Red
    exit 1
}

$model = "qwen2.5-coder:7b"

if ($Prompt -and $Prompt.Count -gt 0) {
    $fullPrompt = $Prompt -join " "
    & ollama run $model $fullPrompt
} else {
    Write-Host "🤖 Conectando con $model en Ollama..." -ForegroundColor Cyan
    Write-Host "💡 Escribe '/bye' o 'exit' para salir." -ForegroundColor Yellow
    & ollama run $model
}


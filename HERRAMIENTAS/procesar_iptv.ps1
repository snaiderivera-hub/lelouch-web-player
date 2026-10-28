<#
.SYNOPSIS
    Script para extraer y estructurar informacion de un servidor IPTV Xtream Codes / M3U.
#>

param(
    [string]$Url = ""
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  PROCESADOR DE LISTAS IPTV (LIVE & VOD) TO JSON " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# Si no se envio URL por parametro, solicitar al usuario
if ([string]::IsNullOrWhiteSpace($Url)) {
    Write-Host ""
    Write-Host "Pega tu enlace M3U / Xtream Codes a continuacion:" -ForegroundColor Yellow
    Write-Host "(O presiona ENTER para usar la URL guardada por defecto)" -ForegroundColor Gray
    $inputUrl = Read-Host "URL"
    
    if ([string]::IsNullOrWhiteSpace($inputUrl)) {
        $Url = "http://liontv.es:80/get.php?username=Hermanos503&password=BysckXDynC&type=m3u_plus&output=m3u8"
    } else {
        $Url = $inputUrl.Trim()
    }
}

$OutputDir = $PSScriptRoot

# 1. Parsear la URL introducida
$username = ""
$password = ""
$serverUrl = ""

try {
    $uri = [System.Uri]$Url
    
    # Extraer Query String
    $queryString = $uri.Query
    if ($queryString.StartsWith("?")) { $queryString = $queryString.Substring(1) }
    
    $pairs = $queryString.Split("&")
    foreach ($pair in $pairs) {
        $kv = $pair.Split("=")
        if ($kv.Length -ge 2) {
            if ($kv[0] -eq "username") { $username = $kv[1] }
            if ($kv[0] -eq "password") { $password = $kv[1] }
        }
    }
    
    # Construir Host Base
    $serverUrl = "$($uri.Scheme)://$($uri.Host)"
    if (-not $uri.IsDefaultPort) {
        $serverUrl += ":$($uri.Port)"
    }
} catch {
    # Fallback Regex
    if ($Url -match "username=([^&]+)") { $username = $Matches[1] }
    if ($Url -match "password=([^&]+)") { $password = $Matches[1] }
    if ($Url -match "(http[s]?://[^/]+)") { $serverUrl = $Matches[1] }
}

if (-not $username -or -not $password -or -not $serverUrl) {
    Write-Host ""
    Write-Host "Error: No se pudieron extraer las credenciales (username/password/server) de la URL." -ForegroundColor Red
    Write-Host "Asegurate de pegar la URL completa, ejemplo:" -ForegroundColor Yellow
    Write-Host "http://servidor.com:80/get.php?username=USUARIO&password=CLAVE&type=m3u_plus&output=m3u8" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "Servidor: $serverUrl" -ForegroundColor Green
Write-Host "Usuario : $username" -ForegroundColor Green

# Helper para Sanitizar Nombres de Archivo
function Get-SafeFileName([string]$name) {
    $invalidChars = [System.IO.Path]::GetInvalidFileNameChars()
    foreach ($char in $invalidChars) {
        $name = $name.Replace($char, '_')
    }
    return $name.Trim()
}

# 2. Intentar consultar via Xtream Codes Player API (Metodo JSON Directo)
$apiUrl = "$serverUrl/player_api.php?username=$username&password=$password"
Write-Host "`n[1/4] Consultando API de Xtream Codes..." -ForegroundColor Green

$accountInfo = $null
$useApi = $false

try {
    $response = Invoke-RestMethod -Uri $apiUrl -Method Get -TimeoutSec 15 -Headers @{ "User-Agent" = "Mozilla/5.0" }
    if ($response.user_info) {
        $accountInfo = $response
        $useApi = $true
        Write-Host " -> Conexion exitosa con Player API." -ForegroundColor Green
    }
} catch {
    Write-Host " -> Player API no respondio directamente. Se procedera mediante lectura de la lista M3U." -ForegroundColor Yellow
}

# Crear carpetas de salida
$liveDir = Join-Path $OutputDir "LIVE"
$vodDir  = Join-Path $OutputDir "VOD"

if (-not (Test-Path $liveDir)) { New-Item -ItemType Directory -Path $liveDir | Out-Null }
if (-not (Test-Path $vodDir))  { New-Item -ItemType Directory -Path $vodDir  | Out-Null }

if ($useApi) {
    # -------------------------------------------------------------
    # MODO A: Xtream API Native JSON Processing
    # -------------------------------------------------------------
    
    # Obtener Categorias Live y VOD
    Write-Host "`n[2/4] Obteniendo categorias..." -ForegroundColor Green
    $liveCatsRaw = @()
    $vodCatsRaw  = @()
    try {
        $liveCatsRaw = Invoke-RestMethod -Uri "$apiUrl&action=get_live_categories" -TimeoutSec 15
        $vodCatsRaw  = Invoke-RestMethod -Uri "$apiUrl&action=get_vod_categories" -TimeoutSec 15
    } catch {}

    $liveCatMap = @{}
    foreach ($c in $liveCatsRaw) { $liveCatMap["$($c.category_id)"] = $c.category_name }
    
    $vodCatMap = @{}
    foreach ($c in $vodCatsRaw) { $vodCatMap["$($c.category_id)"] = $c.category_name }

    # Guardar Info de Cuenta
    $userInfo = $accountInfo.user_info
    $serverInfo = $accountInfo.server_info
    
    $fechaVenc = ""
    if ($userInfo.exp_date) {
        try {
            $origin = [DateTime]::Parse("1970-01-01 00:00:00")
            $fechaVenc = $origin.AddSeconds([double]$userInfo.exp_date).ToString("dd/MM/yyyy HH:mm")
        } catch { $fechaVenc = $userInfo.exp_date }
    }

    $infoCuentaObj = [ordered]@{
        "servidor" = [ordered]@{
            "url"       = $serverUrl
            "protocolo" = $serverInfo.server_protocol.ToUpper()
            "puerto"    = "$($serverInfo.port)"
        }
        "cuenta" = [ordered]@{
            "usuario"            = $userInfo.username
            "estado"             = if ($userInfo.status -eq "Active") { "ACTIVA" } else { $userInfo.status }
            "conexiones_maximas" = "$($userInfo.max_connections)"
            "conexiones_activas" = "$($userInfo.active_cons)"
            "fecha_creacion"     = if ($userInfo.created_at) { $userInfo.created_at } else { "" }
        }
        "vencimiento" = [ordered]@{
            "fecha"          = $fechaVenc
            "dias_restantes" = 0
            "estado"         = "OK"
        }
        "categorias" = [ordered]@{
            "live_tv"       = $liveCatMap.Count
            "peliculas_vod" = $vodCatMap.Count
            "series"        = 0
            "total"         = ($liveCatMap.Count + $vodCatMap.Count)
        }
        "generado" = (Get-Date -Format "dd/MM/yyyy HH:mm:ss")
    }

    $infoJson = $infoCuentaObj | ConvertTo-Json -Depth 5
    Set-Content -Path (Join-Path $liveDir "_INFO_CUENTA.json") -Value $infoJson -Encoding UTF8
    Set-Content -Path (Join-Path $vodDir "_INFO_CUENTA.json") -Value $infoJson -Encoding UTF8

    # Procesar Live Streams
    Write-Host "`n[3/4] Descargando y organizando Canales en Vivo (LIVE)..." -ForegroundColor Green
    try {
        $liveStreams = Invoke-RestMethod -Uri "$apiUrl&action=get_live_streams" -TimeoutSec 30
        $liveGrouped = @{}
        
        foreach ($s in $liveStreams) {
            $catName = if ($liveCatMap.ContainsKey("$($s.category_id)")) { $liveCatMap["$($s.category_id)"] } else { "SIN CATEGORIA" }
            if (-not $liveGrouped.ContainsKey($catName)) { $liveGrouped[$catName] = @() }
            
            $ext = if ($s.container_extension) { $s.container_extension } else { "m3u8" }
            $streamUrl = "$serverUrl/live/$username/$password/$($s.stream_id).$ext"

            $item = [ordered]@{
                "id"       = "$($s.stream_id)"
                "name"     = $s.name
                "logo"     = if ($s.stream_icon) { $s.stream_icon } else { "" }
                "category" = $catName
                "url"      = $streamUrl
                "type"     = "live"
                "rating"   = if ($s.rating) { "$($s.rating)" } else { "" }
                "added"    = if ($s.added) { "$($s.added)" } else { "0" }
            }
            $liveGrouped[$catName] += $item
        }

        $totalLiveItems = 0
        foreach ($catName in $liveGrouped.Keys) {
            $safeName = Get-SafeFileName $catName
            $filePath = Join-Path $liveDir "$safeName.json"
            $jsonContent = $liveGrouped[$catName] | ConvertTo-Json -Depth 4
            Set-Content -Path $filePath -Value $jsonContent -Encoding UTF8
            $totalLiveItems += $liveGrouped[$catName].Count
        }

        # Generar _resumen.json para LIVE
        $resumenLive = [ordered]@{
            "servidor"        = $serverUrl
            "usuario"         = $username
            "tipo"            = "live"
            "total_categorias"= $liveGrouped.Count
            "total_items"     = $totalLiveItems
            "generado_el"     = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
        } | ConvertTo-Json -Depth 3
        Set-Content -Path (Join-Path $liveDir "_resumen.json") -Value $resumenLive -Encoding UTF8

        Write-Host " -> Guardados $totalLiveItems canales en $($liveGrouped.Count) archivos JSON dentro de LIVE/." -ForegroundColor Green
    } catch {
        Write-Host " -> Error procesando Live Streams: $_" -ForegroundColor Red
    }

    # Procesar VOD Streams
    Write-Host "`n[4/4] Descargando y organizando Películas/Series (VOD)..." -ForegroundColor Green
    try {
        $vodStreams = Invoke-RestMethod -Uri "$apiUrl&action=get_vod_streams" -TimeoutSec 40
        $vodGrouped = @{}

        foreach ($s in $vodStreams) {
            $catName = if ($vodCatMap.ContainsKey("$($s.category_id)")) { $vodCatMap["$($s.category_id)"] } else { "SIN CATEGORIA" }
            if (-not $vodGrouped.ContainsKey($catName)) { $vodGrouped[$catName] = @() }

            $ext = if ($s.container_extension) { $s.container_extension } else { "mp4" }
            $streamUrl = "$serverUrl/movie/$username/$password/$($s.stream_id).$ext"

            $item = [ordered]@{
                "id"       = "$($s.stream_id)"
                "name"     = $s.name
                "logo"     = if ($s.stream_icon) { $s.stream_icon } else { "" }
                "category" = $catName
                "url"      = $streamUrl
                "type"     = "vod"
                "rating"   = if ($s.rating) { "$($s.rating)" } else { "0" }
                "added"    = if ($s.added) { "$($s.added)" } else { "0" }
            }
            $vodGrouped[$catName] += $item
        }

        $totalVodItems = 0
        foreach ($catName in $vodGrouped.Keys) {
            $safeName = Get-SafeFileName $catName
            $filePath = Join-Path $vodDir "$safeName.json"
            $jsonContent = $vodGrouped[$catName] | ConvertTo-Json -Depth 4
            Set-Content -Path $filePath -Value $jsonContent -Encoding UTF8
            $totalVodItems += $vodGrouped[$catName].Count
        }

        # Generar _resumen.json para VOD
        $resumenVod = [ordered]@{
            "servidor"        = $serverUrl
            "usuario"         = $username
            "tipo"            = "vod"
            "total_categorias"= $vodGrouped.Count
            "total_items"     = $totalVodItems
            "generado_el"     = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
        } | ConvertTo-Json -Depth 3
        Set-Content -Path (Join-Path $vodDir "_resumen.json") -Value $resumenVod -Encoding UTF8

        Write-Host " -> Guardados $totalVodItems peliculas VOD en $($vodGrouped.Count) archivos JSON dentro de VOD/." -ForegroundColor Green
    } catch {
        Write-Host " -> Error procesando VOD Streams: $_" -ForegroundColor Red
    }

} else {
    # -------------------------------------------------------------
    # MODO B: Fallback M3U Text Parsing
    # -------------------------------------------------------------
    Write-Host "`n[2/3] Descargando playlist M3U desde get.php..." -ForegroundColor Green
    $m3uContent = ""
    try {
        $webClient = New-Object System.Net.WebClient
        $webClient.Encoding = [System.Text.Encoding]::UTF8
        $webClient.Headers.Add("User-Agent", "Mozilla/5.0")
        $m3uContent = $webClient.DownloadString($Url)
    } catch {
        Write-Host "Error al descargar M3U: $_" -ForegroundColor Red
        exit 1
    }

    Write-Host "`n[3/3] Parseando entradas M3U..." -ForegroundColor Green
    $lines = $m3uContent.Split("`n")
    $liveGrouped = @{}
    $vodGrouped  = @{}
    
    $i = 0
    $totalCount = 0

    while ($i -lt $lines.Count) {
        $line = $lines[$i].Trim()
        if ($line.StartsWith("#EXTINF:")) {
            $name = ""
            $category = "GENERAL"
            $logo = ""
            $id = ""

            if ($line -match ',(.+)$') { $name = $Matches[1].Trim() }
            if ($line -match 'group-title="([^"]*)"') { $category = $Matches[1].Trim() }
            if ($line -match 'tvg-logo="([^"]*)"') { $logo = $Matches[1].Trim() }
            if ($line -match 'tvg-id="([^"]*)"') { $id = $Matches[1].Trim() }

            $streamUrl = ""
            if (($i + 1) -lt $lines.Count -and -not $lines[$i + 1].StartsWith("#")) {
                $streamUrl = $lines[$i + 1].Trim()
                $i++
            }

            if ($streamUrl) {
                $isVod = ($streamUrl -like "*/movie/*") -or ($category -like "*MOVIE*") -or ($category -like "*VOD*") -or ($category -like "*PELICULA*")
                $type = if ($isVod) { "vod" } else { "live" }
                
                if (-not $id -and $streamUrl -match '/(\d+)\.[a-zA-Z0-9]+$') {
                    $id = $Matches[1]
                }

                $item = [ordered]@{
                    "id"       = "$id"
                    "name"     = $name
                    "logo"     = $logo
                    "category" = $category
                    "url"      = $streamUrl
                    "type"     = $type
                    "rating"   = ""
                    "added"    = "0"
                }

                if ($isVod) {
                    if (-not $vodGrouped.ContainsKey($category)) { $vodGrouped[$category] = @() }
                    $vodGrouped[$category] += $item
                } else {
                    if (-not $liveGrouped.ContainsKey($category)) { $liveGrouped[$category] = @() }
                    $liveGrouped[$category] += $item
                }
                $totalCount++
            }
        }
        $i++
    }

    # Escribir LIVE
    $totalLiveItems = 0
    foreach ($catName in $liveGrouped.Keys) {
        $safeName = Get-SafeFileName $catName
        $filePath = Join-Path $liveDir "$safeName.json"
        $jsonContent = $liveGrouped[$catName] | ConvertTo-Json -Depth 4
        Set-Content -Path $filePath -Value $jsonContent -Encoding UTF8
        $totalLiveItems += $liveGrouped[$catName].Count
    }
    
    # Escribir VOD
    $totalVodItems = 0
    foreach ($catName in $vodGrouped.Keys) {
        $safeName = Get-SafeFileName $catName
        $filePath = Join-Path $vodDir "$safeName.json"
        $jsonContent = $vodGrouped[$catName] | ConvertTo-Json -Depth 4
        Set-Content -Path $filePath -Value $jsonContent -Encoding UTF8
        $totalVodItems += $vodGrouped[$catName].Count
    }

    Write-Host " -> Parseo M3U finalizado: $totalLiveItems elementos LIVE, $totalVodItems elementos VOD." -ForegroundColor Green
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host " ¡PROCESO COMPLETADO EXITOSAMENTE!" -ForegroundColor Cyan
Write-Host " Archivos guardados en:" -ForegroundColor White
Write-Host "   - LIVE: $liveDir" -ForegroundColor Gray
Write-Host "   - VOD : $vodDir" -ForegroundColor Gray
Write-Host "==================================================" -ForegroundColor Cyan

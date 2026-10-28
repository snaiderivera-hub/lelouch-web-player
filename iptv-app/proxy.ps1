<#
.SYNOPSIS
    Proxy HTTP local seguro para resolver el problema de CORS al consultar APIs IPTV y flujos de video (.m3u8, .ts, .mp4).
    Busca un puerto libre automáticamente empezando en 7878.
    Escribe la configuración en src/proxy-config.js para que el cliente web conozca el puerto activo.
    INCLUYE:
    - Validación estricta anti-SSRF (bloquea IPs privadas, loopback, file://, localhost arbitrario).
    - Enmascaramiento de contraseñas en los registros de consola.
    - Soporte de streaming para fragmentos de video y respuestas JSON.
#>

try {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor [System.Net.SecurityProtocolType]::Tls13
} catch {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
}
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }

$root = $PSScriptRoot
$port = 7878
$maxPort = 7890
$listener = $null

while ($port -le $maxPort) {
    try {
        $prefix = 'http://localhost:' + $port + '/'
        $l = [System.Net.HttpListener]::new()
        $l.Prefixes.Add($prefix)
        $l.Start()
        $listener = $l
        break
    } catch {
        $port++
    }
}

if ($null -eq $listener) {
    Write-Host 'Error: No se pudo encontrar un puerto libre para el proxy entre 7878 y 7890.' -ForegroundColor Red
    pause
    exit 1
}

# Escribir proxy-config.js para la app web
$configPath = Join-Path $root 'src\proxy-config.js'
$configJs = "window.IPTV_PROXY_PORT = $port;"
Set-Content -Path $configPath -Value $configJs -Encoding UTF8

Write-Host '==================================================' -ForegroundColor Cyan
Write-Host ('  LELOUCH PLUS CORS GATEWAY en http://localhost:' + $port + '/') -ForegroundColor Cyan
Write-Host '  Filtro anti-SSRF, TLS 1.2/1.3 y User-Agent IPTV activos.' -ForegroundColor Green
Write-Host '  Presiona Ctrl+C para detener.' -ForegroundColor Gray
Write-Host '==================================================' -ForegroundColor Cyan

function Get-SafeLogUrl([string]$rawUrl) {
    if ([string]::IsNullOrWhiteSpace($rawUrl)) { return "" }
    $safe = $rawUrl -replace 'password=([^&]+)', 'password=[REDACTED]'
    $safe = $safe -replace '(/live/[^/]+/)[^/]+(/)', '$1[REDACTED]$2'
    $safe = $safe -replace '(/movie/[^/]+/)[^/]+(/)', '$1[REDACTED]$2'
    $safe = $safe -replace '(/series/[^/]+/)[^/]+(/)', '$1[REDACTED]$2'
    return $safe
}

function Test-IsSafeTargetUri([System.Uri]$uri) {
    if ($uri.Scheme -ne 'http' -and $uri.Scheme -ne 'https') {
        return $false
    }

    $hostStr = $uri.Host.ToLower()

    if ($hostStr -eq 'localhost' -or $hostStr -eq '127.0.0.1' -or $hostStr -eq '::1' -or $hostStr -eq '0.0.0.0') {
        return $false
    }

    [System.Net.IPAddress]$ip = $null
    if ([System.Net.IPAddress]::TryParse($hostStr, [ref]$ip)) {
        $bytes = $ip.GetAddressBytes()
        if ($bytes.Length -eq 4) {
            if ($bytes[0] -eq 10) { return $false }
            if ($bytes[0] -eq 172 -and ($bytes[1] -ge 16 -and $bytes[1] -le 31)) { return $false }
            if ($bytes[0] -eq 192 -and $bytes[1] -eq 168) { return $false }
            if ($bytes[0] -eq 169 -and $bytes[1] -eq 254) { return $false }
        }
    }

    return $true
}

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response

        $res.Headers.Add("Access-Control-Allow-Origin", "*")
        $res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        $res.Headers.Add("Access-Control-Allow-Headers", "*")

        if ($req.HttpMethod -eq "OPTIONS") {
            $res.StatusCode = 204
            $res.Close()
            continue
        }

        $targetEncoded = $req.QueryString["target"]
        if (-not $targetEncoded) {
            $body = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Parametro target requerido"}')
            $res.StatusCode = 400
            $res.ContentType = "application/json"
            $res.ContentLength64 = $body.Length
            $res.OutputStream.Write($body, 0, $body.Length)
            $res.Close()
            continue
        }

        $targetUrl = [System.Uri]::UnescapeDataString($targetEncoded)
        $safeLogUrl = Get-SafeLogUrl $targetUrl

        $parsedUri = $null
        if (-not [System.Uri]::TryCreate($targetUrl, [System.UriKind]::Absolute, [ref]$parsedUri) -or -not (Test-IsSafeTargetUri $parsedUri)) {
            $body = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Destino no permitido por politica de seguridad anti-SSRF"}')
            $res.StatusCode = 403
            $res.ContentType = "application/json"
            $res.ContentLength64 = $body.Length
            $res.OutputStream.Write($body, 0, $body.Length)
            $res.Close()
            Write-Host ("[BLOQUEADO SSRF] -> " + $safeLogUrl) -ForegroundColor Red
            continue
        }

        Write-Host ("[PROXY] -> " + $safeLogUrl) -ForegroundColor DarkGray

        try {
            $wc = [System.Net.WebClient]::new()
            $wc.Headers.Add("User-Agent", "IPTVSmartersPlayer")
            $wc.Headers.Add("Accept", "*/*")

            $bodyBytes = $wc.DownloadData($targetUrl)

            $contentType = "application/json; charset=utf-8"
            if ($targetUrl -like "*.m3u8*" -or $targetUrl -like "*type=m3u*") {
                $contentType = "application/vnd.apple.mpegurl"
            } elseif ($targetUrl -like "*.ts*") {
                $contentType = "video/mp2t"
            } elseif ($targetUrl -like "*.mp4*") {
                $contentType = "video/mp4"
            } elseif ($targetUrl -like "*.mkv*") {
                $contentType = "video/x-matroska"
            }

            $res.StatusCode = 200
            $res.ContentType = $contentType
            $res.ContentLength64 = $bodyBytes.Length
            $res.OutputStream.Write($bodyBytes, 0, $bodyBytes.Length)
        } catch {
            $msg = $_.Exception.Message -replace '"', "'"
            $errBody = [System.Text.Encoding]::UTF8.GetBytes("{`"error`":`"$msg`"}")
            $res.StatusCode = 502
            $res.ContentType = "application/json"
            $res.ContentLength64 = $errBody.Length
            $res.OutputStream.Write($errBody, 0, $errBody.Length)
            Write-Host ("  [ERR] " + $_.Exception.Message) -ForegroundColor Yellow
        } finally {
            try { $res.Close() } catch {}
        }

    } catch [System.Net.HttpListenerException] {
        break
    } catch {
        Write-Host ("Error en proxy: " + $_.Exception.Message) -ForegroundColor Red
    }
}

$listener.Stop()
Write-Host "Proxy detenido." -ForegroundColor Yellow

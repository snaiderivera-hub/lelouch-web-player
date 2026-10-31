<#
.SYNOPSIS
    LELOUCH PLUS CORS GATEWAY (Puerto 7878)
    Proxy HTTP local con Streaming en Tiempo Real (Chunked 64KB).
#>

try {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor [System.Net.SecurityProtocolType]::Tls13
} catch {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
}
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
[System.Net.ServicePointManager]::DefaultConnectionLimit = 128

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
    Write-Host 'Error: No se pudo encontrar un puerto libre entre 7878 y 7890.' -ForegroundColor Red
    pause
    exit 1
}

# Configuracion para el cliente Web Player
$configPath = Join-Path $root 'src\proxy-config.js'
$configJs = "window.IPTV_PROXY_PORT = $port;"
Set-Content -Path $configPath -Value $configJs -Encoding UTF8

Write-Host '==================================================' -ForegroundColor Cyan
Write-Host ('  LELOUCH PLUS CORS GATEWAY en http://localhost:' + $port + '/') -ForegroundColor Cyan
Write-Host '  Modo Streaming por Bloques (Chunked 64KB) ACTIVO.' -ForegroundColor Green
Write-Host '  Auto-fallback TLS/HTTP y User-Agent IPTV listos.' -ForegroundColor Green
Write-Host '  Deja esta ventana abierta mientras usas la web.' -ForegroundColor Yellow
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
            $body = [System.Text.Encoding]::UTF8.GetBytes('{"error":"Destino no permitido por politica anti-SSRF"}')
            $res.StatusCode = 403
            $res.ContentType = "application/json"
            $res.ContentLength64 = $body.Length
            $res.OutputStream.Write($body, 0, $body.Length)
            $res.Close()
            Write-Host ("[BLOQUEADO SSRF] -> " + $safeLogUrl) -ForegroundColor Red
            continue
        }

        # Auto-fallback para puertos IPTV no-SSL
        $effectiveUrl = $targetUrl
        $nonSslPorts = @("80", "8080", "8880", "2095", "25461", "8000", "8888")
        if ($effectiveUrl -match "^https://([^:/]+):(\d+)") {
            $p = $matches[2]
            if ($nonSslPorts -contains $p) {
                $effectiveUrl = $effectiveUrl -replace "^https://", "http://"
            }
        }

        Write-Host ("[STREAM] -> " + $safeLogUrl) -ForegroundColor DarkGray

        # Crear HttpWebRequest para streaming de baja latencia
        $webReq = $null
        try {
            $webReq = [System.Net.HttpWebRequest]::Create($effectiveUrl)
        } catch {
            if ($effectiveUrl -like "https://*") {
                $effectiveUrl = $effectiveUrl -replace "^https://", "http://"
                $webReq = [System.Net.HttpWebRequest]::Create($effectiveUrl)
            } else {
                throw $_
            }
        }

        $webReq.UserAgent = "IPTVSmartersPlayer"
        $webReq.Accept = "*/*"
        $webReq.Timeout = 20000
        $webReq.ReadWriteTimeout = 30000
        $webReq.AllowAutoRedirect = $true

        $webRes = $null
        try {
            $webRes = $webReq.GetResponse()
        } catch [System.Net.WebException] {
            if ($effectiveUrl -like "https://*") {
                $effectiveUrl = $effectiveUrl -replace "^https://", "http://"
                $webReq = [System.Net.HttpWebRequest]::Create($effectiveUrl)
                $webReq.UserAgent = "IPTVSmartersPlayer"
                $webReq.Accept = "*/*"
                $webReq.Timeout = 20000
                $webReq.ReadWriteTimeout = 30000
                $webRes = $webReq.GetResponse()
            } else {
                throw $_
            }
        }

        # Determinar Content-Type
        $contentType = $webRes.ContentType
        if ([string]::IsNullOrWhiteSpace($contentType) -or $contentType -eq "application/octet-stream") {
            if ($effectiveUrl -like "*.m3u8*" -or $effectiveUrl -like "*type=m3u*") {
                $contentType = "application/vnd.apple.mpegurl"
            } elseif ($effectiveUrl -like "*.ts*") {
                $contentType = "video/mp2t"
            } elseif ($effectiveUrl -like "*.mp4*") {
                $contentType = "video/mp4"
            } elseif ($effectiveUrl -like "*.mkv*") {
                $contentType = "video/x-matroska"
            } else {
                $contentType = "application/json; charset=utf-8"
            }
        }

        $res.StatusCode = [int]$webRes.StatusCode
        $res.ContentType = $contentType

        # Streaming directo por bloques de 64 KB en tiempo real (Chunked)
        $inStream = $webRes.GetResponseStream()
        $outStream = $res.OutputStream
        $buffer = New-Object byte[] 65536
        
        try {
            while (($bytesRead = $inStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $outStream.Write($buffer, 0, $bytesRead)
                $outStream.Flush()
            }
        } catch {
            # Conexión cerrada normalmente al cambiar de canal
        } finally {
            if ($null -ne $inStream) { $inStream.Close() }
            if ($null -ne $webRes) { $webRes.Close() }
            try { $res.Close() } catch {}
        }

    } catch [System.Net.HttpListenerException] {
        break
    } catch {
        $msg = $_.Exception.Message -replace '"', "'"
        $errBody = [System.Text.Encoding]::UTF8.GetBytes("{`"error`":`"$msg`"}")
        $res.StatusCode = 502
        $res.ContentType = "application/json"
        $res.ContentLength64 = $errBody.Length
        try {
            $res.OutputStream.Write($errBody, 0, $errBody.Length)
            $res.Close()
        } catch {}
        Write-Host ("  [ERR] " + $_.Exception.Message) -ForegroundColor Yellow
    }
}

$listener.Stop()
Write-Host "Proxy detenido." -ForegroundColor Yellow


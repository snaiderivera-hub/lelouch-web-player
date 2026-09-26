<#
.SYNOPSIS
    Servidor HTTP local para servir la aplicación IPTV Data Architect en el navegador.
    Busca un puerto libre automáticamente empezando en 8686.
#>

$root = $PSScriptRoot
$port = 8686
$maxPort = 8700
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
    Write-Host 'Error: No se pudo encontrar un puerto libre entre 8686 y 8700.' -ForegroundColor Red
    pause
    exit 1
}

$mimeTypes = @{
  '.html' = 'text/html; charset=utf-8'
  '.css'  = 'text/css; charset=utf-8'
  '.js'   = 'application/javascript; charset=utf-8'
  '.json' = 'application/json; charset=utf-8'
  '.png'  = 'image/png'
  '.jpg'  = 'image/jpeg'
  '.svg'  = 'image/svg+xml'
  '.ico'  = 'image/x-icon'
  '.webp' = 'image/webp'
}

Write-Host '==================================================' -ForegroundColor Cyan
Write-Host '  IPTV Data Architect — Servidor Local' -ForegroundColor Cyan
Write-Host ('  Abierto en: http://localhost:' + $port) -ForegroundColor Green
Write-Host '  Deja esta ventana abierta.' -ForegroundColor Yellow
Write-Host '  Ctrl+C para detener.' -ForegroundColor Gray
Write-Host '==================================================' -ForegroundColor Cyan

Start-Process ('http://localhost:' + $port)

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response

        $urlPath = $req.Url.LocalPath
        if ($urlPath -eq '/' -or $urlPath -eq '') { $urlPath = '/index.html' }

        $filePath = Join-Path $root ($urlPath.TrimStart('/').Replace('/', '\'))

        if (Test-Path $filePath -PathType Leaf) {
            $ext = [System.IO.Path]::GetExtension($filePath).ToLower()
            $contentType = if ($mimeTypes.ContainsKey($ext)) { $mimeTypes[$ext] } else { 'application/octet-stream' }

            $content = [System.IO.File]::ReadAllBytes($filePath)
            $res.StatusCode = 200
            $res.ContentType = $contentType
            $res.ContentLength64 = $content.Length
            $res.OutputStream.Write($content, 0, $content.Length)
        } else {
            $notFoundMsg = '404 - No encontrado: ' + $urlPath
            $body = [System.Text.Encoding]::UTF8.GetBytes($notFoundMsg)
            $res.StatusCode = 404
            $res.ContentType = 'text/plain; charset=utf-8'
            $res.ContentLength64 = $body.Length
            $res.OutputStream.Write($body, 0, $body.Length)
            Write-Host ('404: ' + $urlPath) -ForegroundColor DarkGray
        }

        $res.Close()
    } catch [System.Net.HttpListenerException] {
        break
    } catch {
        Write-Host ('Error: ' + $_.Exception.Message) -ForegroundColor Red
        try { $ctx.Response.Close() } catch {}
    }
}

$listener.Stop()
Write-Host 'Servidor detenido.' -ForegroundColor Yellow

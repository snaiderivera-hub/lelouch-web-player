$wc = [System.Net.WebClient]::new()
$wc.Headers.Add('User-Agent', 'VLC/3.0.18')
try {
    $bytes = $wc.DownloadData('http://liontv.es:80/live/Hermanos503/BysckXDynC/1976611.ts')
    Write-Host ('SUCCESS: Recibidos ' + $bytes.Length + ' bytes del canal live') -ForegroundColor Green
} catch {
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
}

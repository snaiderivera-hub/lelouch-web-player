$token = "pByk2IfABSGuLwSC9b14z6Y7penWElnYjbgzmI3R"
Write-Host "1. Testing Vercel format=version..."
try {
    $res = Invoke-RestMethod -Uri "https://lelouch-web-player.vercel.app/api/playlist?token=$token&format=version" -Method Get -TimeoutSec 15
    $res | ConvertTo-Json | Write-Host
} catch {
    Write-Host "Error version: $_"
}

Write-Host "2. Testing Vercel format=json (Manifest)..."
try {
    $mRes = Invoke-WebRequest -Uri "https://lelouch-web-player.vercel.app/api/playlist?token=$token&format=json" -Method Get -TimeoutSec 30
    Write-Host "Status: $($mRes.StatusCode), Length: $($mRes.RawContentLength)"
    $json = $mRes.Content | ConvertFrom-Json
    Write-Host "Items in manifest: $($json.items.Count)"
} catch {
    Write-Host "Error manifest: $_"
}

Write-Host "3. Testing Vercel M3U..."
try {
    $m3u = Invoke-WebRequest -Uri "https://lelouch-web-player.vercel.app/api/playlist?token=$token" -Method Get -TimeoutSec 30
    Write-Host "Status: $($m3u.StatusCode), Length: $($m3u.RawContentLength)"
    $firstLines = ($m3u.Content.Split("`n") | Select-Object -First 10) -join "`n"
    Write-Host "First lines:`n$firstLines"
} catch {
    Write-Host "Error M3U: $_"
}

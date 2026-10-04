$key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
$headers = @{
    "apikey" = $key
    "Authorization" = "Bearer $key"
}

Write-Host "=== 1. CUSTOM PLAYLISTS ==="
$cp = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=*" -Headers $headers
$cp | Format-Table id, name, enabled, version, updated_at

Write-Host "=== 2. TOKENS ==="
$tok = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_access_tokens?select=*" -Headers $headers
$tok | Format-Table id, playlist_id, token, enabled, expires_at

Write-Host "=== 3. CHECK NULLS OR CORRUPTED ROWS IN v_resolved_playlist_items ==="
# Check rows with null direct_url and null resolved_stream_url
$nullUrls = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?resolved_stream_url=is.null&direct_url=is.null&select=id,name,position,media_type&limit=10" -Headers $headers
Write-Host "Rows with both URLs null count (first 10): $($nullUrls.Count)"

# Check rows with null name
$nullNames = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?name=is.null&direct_name=is.null&select=id,position&limit=10" -Headers $headers
Write-Host "Rows with name null: $($nullNames.Count)"

Write-Host "=== 4. TEST VERCEL PLAYLIST ENDPOINT ==="
try {
    $token = $tok[0].token
    Write-Host "Testing token: $token"
    $vRes = Invoke-WebRequest -Uri "https://lelouch-web-player.vercel.app/api/playlist?token=$token" -Method Head -TimeoutSec 15
    Write-Host "Vercel Response Status: $($vRes.StatusCode)"
    Write-Host "Vercel Headers:"
    $vRes.Headers | Out-String | Write-Host
} catch {
    Write-Host "Vercel call error: $_"
}

Write-Host "=== 5. TEST GET EXACT ROW COUNT VIA POSTGREST RANGE HEADER ==="
$req = [System.Net.HttpWebRequest]::Create("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?select=id")
$req.Headers.Add("apikey", $key)
$req.Headers.Add("Authorization", "Bearer $key")
$req.Headers.Add("Range-Unit", "items")
$req.Headers.Add("Range", "0-0")
$req.Headers.Add("Prefer", "count=exact")
$resp = $req.GetResponse()
$contentRange = $resp.Headers["Content-Range"]
Write-Host "Exact total count in v_resolved_playlist_items: $contentRange"
$resp.Close()

$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
    'Prefer' = 'return=representation'
}
$playlistId = "c9da4a22-534c-41f9-af70-42ee9f6682df"

$deletedTotal = 0
while ($true) {
    $items = @(Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.$playlistId&media_type=eq.movie&select=id&limit=400" -Headers $headers)
    if ($items.Count -eq 0 -or $null -eq $items[0]) {
        break
    }
    $ids = ($items | ForEach-Object { $_.id }) -join ','
    $delUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?id=in.($ids)"
    $res = Invoke-RestMethod -Uri $delUrl -Method DELETE -Headers $headers
    $count = if ($res) { $res.Count } else { 0 }
    if ($count -eq 0) { break }
    $deletedTotal += $count
    Write-Output "Deleted batch: $count. Total: $deletedTotal"
    Start-Sleep -Milliseconds 80
}

Write-Output "COMPLETED! Total movies deleted: $deletedTotal"

# Bump version in custom_playlists
$patchBody = @{ updated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") } | ConvertTo-Json
Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?id=eq.$playlistId" -Method PATCH -Headers $headers -Body $patchBody -ContentType "application/json" | Out-Null
Write-Output "Playlist version bumped!"

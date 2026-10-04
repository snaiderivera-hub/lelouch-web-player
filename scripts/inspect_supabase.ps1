$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}

$playlists = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/custom_playlists?select=*" -Headers $headers
Write-Output "Playlists in custom_playlists: $($playlists.Count)"
foreach ($p in $playlists) {
    Write-Output "ID: $($p.id) | Name: $($p.name) | Token: $($p.access_token)"
}

$items = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?select=id,media_type,name,playlist_id&limit=2000" -Headers $headers
Write-Output "Total playlist_items: $($items.Count)"
$byPl = $items | Group-Object playlist_id
foreach ($b in $byPl) {
    Write-Output "Playlist $($b.Name): $($b.Count) items"
    $byType = $b.Group | Group-Object media_type
    foreach ($bt in $byType) {
        Write-Output "   -> $($bt.Name): $($bt.Count)"
    }
}

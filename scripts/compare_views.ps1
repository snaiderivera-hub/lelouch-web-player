$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}

# Total rows in playlist_items for the custom playlist
$plItems = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?select=id,media_type,item_type,enabled,direct_url,source_id&limit=5000" -Headers $headers
Write-Output "playlist_items first page (limit 5000): $($plItems.Count)"
$byMedia = $plItems | Group-Object media_type
foreach ($m in $byMedia) {
    Write-Output "  media_type: $($m.Name) -> $($m.Count)"
}
$byItemType = $plItems | Group-Object item_type
foreach ($it in $byItemType) {
    Write-Output "  item_type: $($it.Name) -> $($it.Count)"
}

# Now check v_resolved_playlist_items
$vItems = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?select=id,media_type,resolved_stream_url&limit=5000" -Headers $headers
Write-Output "v_resolved_playlist_items first page: $($vItems.Count)"
$byVMedia = $vItems | Group-Object media_type
foreach ($m in $byVMedia) {
    Write-Output "  v_resolved media_type: $($m.Name) -> $($m.Count)"
}

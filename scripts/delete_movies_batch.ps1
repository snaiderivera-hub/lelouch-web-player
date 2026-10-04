$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}
$playlistId = "c9da4a22-534c-41f9-af70-42ee9f6682df"

$deletedTotal = 0
do {
    $items = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.$playlistId&media_type=eq.movie&select=id&limit=100" -Headers $headers
    if (-not $items -or $items.Count -eq 0) {
        break
    }
    $ids = ($items | ForEach-Object { $_.id }) -join ','
    $delUrl = "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?id=in.($ids)"
    Invoke-RestMethod -Uri $delUrl -Method DELETE -Headers $headers | Out-Null
    $deletedTotal += $items.Count
    Write-Output "Deleted batch of $($items.Count) movies. Total so far: $deletedTotal"
    Start-Sleep -Milliseconds 200
} while ($items.Count -gt 0)

Write-Output "All movies deleted successfully! Total: $deletedTotal"

$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
    'Prefer' = 'count=exact'
}

$types = @('live', 'movie', 'series')
foreach ($t in $types) {
    $null = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?select=id&playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&media_type=eq.$t&limit=1" -Headers $headers -ResponseHeadersVariable h
    Write-Output "Type '$t' in playlist_items: $($h['content-range'])"
}

# Check items without media_type or other media_types
$null = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?select=id&playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&media_type=is.null&limit=1" -Headers $headers -ResponseHeadersVariable hNull
Write-Output "Type NULL in playlist_items: $($hNull['content-range'])"

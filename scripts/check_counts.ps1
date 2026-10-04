$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}

$items = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?select=id,media_type,name,group&limit=5000" -Headers $headers
Write-Output "Total items fetched: $($items.Count)"

$grouped = $items | Group-Object media_type
foreach ($g in $grouped) {
    Write-Output "media_type '$($g.Name)': $($g.Count)"
}

$nullGroup = $items | Where-Object { [string]::IsNullOrWhiteSpace($_.media_type) }
Write-Output "null/empty media_type: $($nullGroup.Count)"

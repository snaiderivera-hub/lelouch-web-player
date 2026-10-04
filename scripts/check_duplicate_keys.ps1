$key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
$headers = @{ "apikey" = $key; "Authorization" = "Bearer $key" }

Write-Host "Checking duplicate names or streamIds in first 1000 items..."
$items = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/v_resolved_playlist_items?limit=1000&select=id,name,media_type,direct_url,resolved_stream_url" -Headers $headers
Write-Host "Total items retrieved: $($items.Count)"

$streamIds = @{}
$duplicates = @()
foreach ($it in $items) {
    $url = if ($it.resolved_stream_url) { $it.resolved_stream_url } else { $it.direct_url }
    # Algorithm used in XtreamCatalogSyncManager:
    $str = ($it.name + $url)
    $hash = [Math]::Abs($str.GetHashCode())
    if ($streamIds.ContainsKey($hash)) {
        $duplicates += @{
            hash = $hash
            name1 = $streamIds[$hash].name
            name2 = $it.name
            url1 = $streamIds[$hash].url
            url2 = $url
        }
    } else {
        $streamIds[$hash] = @{ name = $it.name; url = $url }
    }
}

Write-Host "Duplicate hashes found in first 1000 items: $($duplicates.Count)"
if ($duplicates.Count -gt 0) {
    $duplicates | Select-Object -First 5 | Format-List
}

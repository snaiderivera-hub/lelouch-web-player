$key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
$headers = @{ "apikey" = $key; "Authorization" = "Bearer $key" }

Write-Host "Querying 1000 sample rows from playlist_items..."
$sample = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&select=id,direct_name,custom_name,direct_url,media_type&limit=1000" -Headers $headers
$grouped = $sample | Group-Object -Property direct_name | Where-Object { $_.Count -gt 1 }
Write-Host "In 1000 sample rows, direct_names that appear more than once: $($grouped.Count)"
$grouped | Select-Object -First 10 | ForEach-Object {
    Write-Host "  Name: '$($_.Name)' repeated $($_.Count) times"
}

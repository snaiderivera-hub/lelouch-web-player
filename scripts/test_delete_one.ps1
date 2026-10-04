$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
    'Prefer' = 'return=representation'
}
$one = (Invoke-RestMethod -Uri 'https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?media_type=eq.movie&limit=1' -Headers $headers).id
Write-Output "Deleting ID: $one"
$delRes = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?id=eq.$one" -Method DELETE -Headers $headers
Write-Output "Result: $(ConvertTo-Json $delRes)"

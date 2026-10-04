$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}
$playlistId = "c9da4a22-534c-41f9-af70-42ee9f6682df"
$res = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.$playlistId&media_type=eq.movie&select=id,custom_name,direct_name,created_at&limit=5" -Headers $headers
$res | Format-Table -AutoSize

$key = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo'
$headers = @{
    'apikey' = $key
    'Authorization' = "Bearer $key"
}
$req = [System.Net.HttpWebRequest]::Create("https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&media_type=eq.movie&select=id")
$req.Headers.Add("apikey", $key)
$req.Headers.Add("Authorization", "Bearer $key")
$req.Headers.Add("Range-Unit", "items")
$req.Headers.Add("Range", "0-0")
$req.Headers.Add("Prefer", "count=exact")
$resp = $req.GetResponse()
$cr = $resp.Headers["Content-Range"]
Write-Output "Exact remaining movies in Supabase: $cr"
$resp.Close()

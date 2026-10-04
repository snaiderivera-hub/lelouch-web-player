$key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
$headers = @{ "apikey" = $key; "Authorization" = "Bearer $key" }

Write-Host "Consultando grupos en Supabase..."
$items = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&media_type=eq.live&order=position.asc&select=direct_group,custom_group,position&limit=1000" -Headers $headers
$groups = $items | ForEach-Object { if ($_.custom_group) { $_.custom_group } else { $_.direct_group } } | Select-Object -Unique
Write-Host "Grupos encontrados en el primer lote (1000 items):"
$groups | ForEach-Object { Write-Host " - $_" }

Write-Host "`nConsultando mas alla de 1000 items (offset=1000)..."
$items2 = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items?playlist_id=eq.c9da4a22-534c-41f9-af70-42ee9f6682df&media_type=eq.live&order=position.asc&select=direct_group,custom_group,position&limit=1000&offset=1000" -Headers $headers
$groups2 = $items2 | ForEach-Object { if ($_.custom_group) { $_.custom_group } else { $_.direct_group } } | Select-Object -Unique
Write-Host "Grupos encontrados en el segundo lote (offset 1000):"
$groups2 | ForEach-Object { Write-Host " - $_" }

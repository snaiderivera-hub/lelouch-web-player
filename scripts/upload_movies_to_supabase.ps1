param(
    [string]$PlaylistId = "c9da4a22-534c-41f9-af70-42ee9f6682df",
    [string]$M3uPath = "M3U_FILTER_TOOL\lista_filtrada.m3u",
    [int]$BatchSize = 250,
    [int]$MaxItems = 15000
)

$apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJvdHVwYmRlbGpnZmRkeXdyeWhrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNjA2MzksImV4cCI6MjEwNTkzNjYzOX0.zDVgrQo_IU5FhfSMpl0-MbS9Eod43czS21TBi5Z3Lvo"
$headers = @{
    apikey = $apiKey
    Authorization = "Bearer $apiKey"
    "Content-Type" = "application/json"
    Prefer = "return=minimal"
}

Write-Host "Iniciando lectura de $M3uPath..."
if (-not (Test-Path $M3uPath)) {
    Write-Error "No se encontró el archivo $M3uPath"
    exit 1
}

$lines = Get-Content -Path $M3uPath
$items = @()
$currentName = ""
$currentLogo = ""
$currentGroup = "Películas"
$groupRegex = [regex]'group-title="([^"]+)"'
$logoRegex = [regex]'tvg-logo="([^"]+)"'

$pos = 100
for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i].Trim()
    if ($line.StartsWith("#EXTINF:")) {
        $gMatch = $groupRegex.Match($line)
        if ($gMatch.Success) { $currentGroup = $gMatch.Groups[1].Value } else { $currentGroup = "Películas" }

        $lMatch = $logoRegex.Match($line)
        if ($lMatch.Success) { $currentLogo = $lMatch.Groups[1].Value } else { $currentLogo = "" }

        $idxComma = $line.LastIndexOf(",")
        if ($idxComma -ge 0) {
            $currentName = $line.Substring($idxComma + 1).Trim()
        } else {
            $currentName = "Película"
        }
    } elseif ($line.StartsWith("http://") -or $line.StartsWith("https://")) {
        if ($currentName -ne "") {
            $items += @{
                playlist_id = $PlaylistId
                item_type = "direct"
                media_type = "movie"
                direct_name = $currentName
                direct_url = $line
                direct_group = $currentGroup
                direct_logo = $currentLogo
                position = $pos
                enabled = $true
            }
            $pos++
            $currentName = ""
            $currentLogo = ""

            if ($items.Count -ge $MaxItems) {
                break
            }
        }
    }
}

Write-Host "Total de películas procesadas para insertar: $($items.Count)"

$total = $items.Count
$inserted = 0

for ($i = 0; $i -lt $total; $i += $BatchSize) {
    $end = [Math]::Min($i + $BatchSize, $total)
    $chunk = $items[$i..($end - 1)]
    $jsonBody = $chunk | ConvertTo-Json -Depth 5 -Compress

    $success = $false
    $retry = 0
    while (-not $success -and $retry -lt 3) {
        try {
            $res = Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlist_items" -Method POST -Headers $headers -Body ([System.Text.Encoding]::UTF8.GetBytes($jsonBody))
            $success = $true
            $inserted += $chunk.Count
            Write-Host "Insertado lote: $inserted de $total películas..."
        } catch {
            $retry++
            Write-Warning "Error en lote $($i)-$($end). Reintento $retry/3: $_"
            Start-Sleep -Milliseconds 800
        }
    }
}

# Actualizar el contador de películas en la tabla playlists
$updateHeaders = @{
    apikey = $apiKey
    Authorization = "Bearer $apiKey"
    "Content-Type" = "application/json"
}
$patchBody = @{
    movies_count = $inserted
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://rotupbdeljgfddywryhk.supabase.co/rest/v1/playlists?id=eq.a43bc8b7-1b66-4eb6-97b3-a276ca3257f3" -Method PATCH -Headers $updateHeaders -Body $patchBody

Write-Host "Proceso completado exitosamente: $inserted películas guardadas en Supabase."

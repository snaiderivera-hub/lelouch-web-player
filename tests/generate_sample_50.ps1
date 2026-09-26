param(
    [string]$OutputDir = "$PSScriptRoot"
)

$targetFiles = @(
    "SPAIN DEPORTES.json",
    "SPAIN LOCALES.json",
    "SPAIN CINEMA.json",
    "MEXICO.json",
    "USA NEWS.json",
    "SPAIN KIDS.json",
    "SPAIN DOCUMENTALES.json",
    "ARGENTINA.json"
)

$sampleChannels = [System.Collections.Generic.List[psobject]]::new()
$perFile = 7

foreach ($fn in $targetFiles) {
    $path = Join-Path "$PSScriptRoot\..\LIVE" $fn
    if (Test-Path $path) {
        $raw = Get-Content -Path $path -Raw -Encoding UTF8 | ConvertFrom-Json
        $count = 0
        foreach ($item in $raw) {
            if ($count -ge $perFile) { break }
            if ($item.id -and $item.name) {
                $sampleChannels.Add([PSCustomObject]@{
                    id = [string]$item.id
                    name = [string]$item.name
                    category = [string]$item.category
                    streamUrl = "http://liontv.es:80/live/Hermanos503/BysckXDynC/$($item.id).m3u8"
                })
                $count++
            }
        }
    }
}

# Añadir 2 canales ficticios/inválidos para verificar detección estricta de OFFLINE
$sampleChannels.Add([PSCustomObject]@{
    id = "99999991"
    name = "CANAL_TEST_INEXISTENTE_1"
    category = "TESTS"
    streamUrl = "http://liontv.es:80/live/Hermanos503/BysckXDynC/99999991.m3u8"
})

$sampleChannels.Add([PSCustomObject]@{
    id = "99999992"
    name = "CANAL_TEST_INEXISTENTE_2"
    category = "TESTS"
    streamUrl = "http://liontv.es:80/live/Hermanos503/BysckXDynC/99999992.m3u8"
})

$final50 = $sampleChannels | Select-Object -First 50
$outPath = Join-Path $OutputDir "sample_50_channels.json"
$final50 | ConvertTo-Json -Depth 4 | Set-Content -Path $outPath -Encoding UTF8
Write-Host "Generada muestra controlada de 50 canales en: $outPath (Total: $($final50.Count))" -ForegroundColor Green

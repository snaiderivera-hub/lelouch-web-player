<#
.SYNOPSIS
    Benchmark de Rendimiento de Búsqueda: Índice Precomputado vs Búsqueda Anterior
    Mide las mismas 10 consultas del QA para generar el reporte de ANTES vs DESPUÉS.
#>

param(
    [string]$OutputDir = "$PSScriptRoot\qa_artifacts"
)

$queries = @("avengers", "espn", "noticias", "batman", "hbo", "mexico", "2024", "futbol", "spider", "disney")

Write-Host "Cargando catalogos para construir el indice precomputado..." -ForegroundColor Cyan

# Cargar catalogos
$liveSample = @()
Get-ChildItem -Path "$PSScriptRoot\..\LIVE" -Filter "*.json" | Select-Object -First 30 | ForEach-Object {
    $liveSample += (Get-Content $_.FullName -Raw | ConvertFrom-Json)
}
$moviesSample = @()
Get-ChildItem -Path "$PSScriptRoot\..\VOD" -Filter "*.json" | Select-Object -First 30 | ForEach-Object {
    $moviesSample += (Get-Content $_.FullName -Raw | ConvertFrom-Json)
}
$seriesList = Invoke-RestMethod -Uri "http://liontv.es:80/player_api.php?username=Hermanos503&password=BysckXDynC&action=get_series" -TimeoutSec 30

Write-Host "Total items en catalogo: $($liveSample.Count + $moviesSample.Count + $seriesList.Count)" -ForegroundColor Green

# ── Construcción del índice precomputado (Se calcula 1 sola vez) ──
$swIndex = [System.Diagnostics.Stopwatch]::StartNew()

function Normalize-Text([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return "" }
    $normalized = $text.Normalize([System.Text.NormalizationForm]::FormD)
    $sb = [System.Text.StringBuilder]::new()
    foreach ($c in $normalized.ToCharArray()) {
        $uc = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($c)
        if ($uc -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($c)
        }
    }
    return $sb.ToString().ToLower().Trim()
}

$precomputedLive = [System.Collections.Generic.List[psobject]]::new($liveSample.Count)
foreach ($item in $liveSample) {
    $title = if ($item.name) { $item.name } else { $item.title }
    $cat = if ($item.category_name) { $item.category_name } else { $item.categoryName }
    $precomputedLive.Add([PSCustomObject]@{
        Id = $item.id
        Type = "live"
        Title = $title
        NormalizedTitle = Normalize-Text $title
        Category = $cat
        NormalizedCategory = Normalize-Text $cat
        Item = $item
    })
}

$precomputedMovies = [System.Collections.Generic.List[psobject]]::new($moviesSample.Count)
foreach ($item in $moviesSample) {
    $title = if ($item.name) { $item.name } else { $item.title }
    $cat = if ($item.category_name) { $item.category_name } else { $item.categoryName }
    $precomputedMovies.Add([PSCustomObject]@{
        Id = $item.id
        Type = "movie"
        Title = $title
        NormalizedTitle = Normalize-Text $title
        Category = $cat
        NormalizedCategory = Normalize-Text $cat
        Item = $item
    })
}

$precomputedSeries = [System.Collections.Generic.List[psobject]]::new($seriesList.Count)
foreach ($item in $seriesList) {
    $title = if ($item.name) { $item.name } else { $item.title }
    $cat = if ($item.category_name) { $item.category_name } else { $item.categoryName }
    $precomputedSeries.Add([PSCustomObject]@{
        Id = $item.series_id
        Type = "series"
        Title = $title
        NormalizedTitle = Normalize-Text $title
        Category = $cat
        NormalizedCategory = Normalize-Text $cat
        Item = $item
    })
}

$swIndex.Stop()
Write-Host "Indice precomputado creado en: $($swIndex.ElapsedMilliseconds) ms" -ForegroundColor Green

# ── Ejecución de búsquedas sobre el índice optimizado ──
$results = @()
$sw = [System.Diagnostics.Stopwatch]::new()

function Fast-Search($query, $maxLive = 3, $maxMovies = 8, $maxSeries = 4) {
    $q = Normalize-Text $query
    if ([string]::IsNullOrEmpty($q)) { return @() }

    $liveMatches = [System.Collections.Generic.List[psobject]]::new()
    for ($i = 0; $i -lt $precomputedLive.Count; $i++) {
        if ($precomputedLive[$i].NormalizedTitle.Contains($q)) {
            $liveMatches.Add($precomputedLive[$i])
            if ($liveMatches.Count -ge $maxLive) { break }
        }
    }

    $movieMatches = [System.Collections.Generic.List[psobject]]::new()
    for ($i = 0; $i -lt $precomputedMovies.Count; $i++) {
        if ($precomputedMovies[$i].NormalizedTitle.Contains($q)) {
            $movieMatches.Add($precomputedMovies[$i])
            if ($movieMatches.Count -ge $maxMovies) { break }
        }
    }

    $seriesMatches = [System.Collections.Generic.List[psobject]]::new()
    for ($i = 0; $i -lt $precomputedSeries.Count; $i++) {
        if ($precomputedSeries[$i].NormalizedTitle.Contains($q)) {
            $seriesMatches.Add($precomputedSeries[$i])
            if ($seriesMatches.Count -ge $maxSeries) { break }
        }
    }

    return @{
        Live = $liveMatches
        Movies = $movieMatches
        Series = $seriesMatches
        Total = $liveMatches.Count + $movieMatches.Count + $seriesMatches.Count
    }
}

foreach ($q in $queries) {
    $sw.Restart()
    $searchRes = Fast-Search -query $q -maxLive 3 -maxMovies 8 -maxSeries 4
    $sw.Stop()

    $results += [PSCustomObject]@{
        Query = $q
        TimeMs = [math]::Round($sw.Elapsed.TotalMilliseconds, 2)
        Matches = $searchRes.Total
        LiveCount = $searchRes.Live.Count
        MoviesCount = $searchRes.Movies.Count
        SeriesCount = $searchRes.Series.Count
    }
}

$times = $results | ForEach-Object { $_.TimeMs }
$minFast = ($times | Measure-Object -Minimum).Minimum
$maxFast = ($times | Measure-Object -Maximum).Maximum
$avgFast = [math]::Round(($times | Measure-Object -Average).Average, 2)

Write-Host "`n=== RESULTADOS SEARCH PERFORMANCE (INDICE PRECOMPUTADO) ===" -ForegroundColor Cyan
foreach ($r in $results) {
    Write-Host "  -> '$($r.Query)': $($r.TimeMs) ms (Coincidencias: $($r.Matches))" -ForegroundColor White
}
Write-Host "`nMIN: $minFast ms | AVG: $avgFast ms | MAX: $maxFast ms" -ForegroundColor Green

$benchmarkJson = [PSCustomObject]@{
    Before = @{
        MinMs = 230.69
        AvgMs = 626.45
        MaxMs = 1008.44
    }
    After = @{
        MinMs = $minFast
        AvgMs = $avgFast
        MaxMs = $maxFast
        Queries = $results
    }
    Improvement = "$([math]::Round((626.45 / [math]::Max($avgFast, 0.01)), 1))x mas rapido"
} | ConvertTo-Json -Depth 5

Set-Content -Path "$OutputDir\search_benchmark_comparison.json" -Value $benchmarkJson -Encoding UTF8
Write-Host "Benchmark guardado en $OutputDir\search_benchmark_comparison.json" -ForegroundColor Green

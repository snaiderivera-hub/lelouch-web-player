# ============================================================
# M3U Filter & Stream Link Extractor (PowerShell 7 Nativo)
# Funciona en Windows sin necesidad de tener Python instalado
# ============================================================

[CmdletBinding()]
param (
    [Parameter(Position = 0)]
    [string]$M3uPath,

    [Parameter(Position = 1)]
    [string]$FilterPath,

    [Parameter(Position = 2)]
    [string]$OutPath = "out.m3u"
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "         M3U FILTER & EXTRACTOR DE ENLACES (LELOUCH)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Si no se pasan parámetros, pedir interactivamente
if (-not $M3uPath) {
    $M3uPath = Read-Host "Ruta al archivo .M3U o URL del stream (ej: lista.m3u o http://...)"
}
if (-not $FilterPath) {
    if (Test-Path "canales_filtro.txt") {
        $FilterPath = "canales_filtro.txt"
        Write-Host "[*] Usando archivo de filtros por defecto: canales_filtro.txt" -ForegroundColor Yellow
    } else {
        $FilterPath = Read-Host "Ruta al archivo de texto con canales a buscar (ej: canales.txt)"
    }
}

if (-not $M3uPath) {
    Write-Host "ERROR: Debes indicar el archivo M3U." -ForegroundColor Red
    exit 1
}

# 1. Cargar M3U (Local o URL)
$m3uRaw = ""
if ($M3uPath.StartsWith("http://") -or $M3uPath.StartsWith("https://")) {
    Write-Host "[*] Descargando lista M3U desde URL..." -ForegroundColor Gray
    try {
        $m3uRaw = (Invoke-RestMethod -Uri $M3uPath -TimeoutSec 45)
    } catch {
        Write-Host "ERROR al descargar M3U: $_" -ForegroundColor Red
        exit 1
    }
} else {
    if (-not (Test-Path $M3uPath)) {
        Write-Host "ERROR: No existe el archivo '$M3uPath'" -ForegroundColor Red
        exit 1
    }
    Write-Host "[*] Leyendo archivo M3U local: $M3uPath..." -ForegroundColor Gray
    $m3uRaw = Get-Content -Path $M3uPath -Raw -Encoding UTF8
}

# 2. Cargar filtros
$filters = @()
if (Test-Path $FilterPath) {
    Write-Host "[*] Leyendo lista de filtros: $FilterPath..." -ForegroundColor Gray
    $filters = Get-Content -Path $FilterPath -Encoding UTF8 | 
        ForEach-Object { $_.Trim() } | 
        Where-Object { $_ -ne "" -and -not $_.StartsWith("#") }
    Write-Host "[✓] $($filters.Count) filtros cargados." -ForegroundColor Green
} else {
    Write-Host "AVISO: No se encontró '$FilterPath'. Extrayendo TODOS los canales." -ForegroundColor Yellow
}

# 3. Parsear canales
Write-Host "[*] Procesando canales..." -ForegroundColor Gray
$lines = $m3uRaw -split "\r?\n"
$entries = [System.Collections.Generic.List[PSObject]]::new()

$currentExtinf = $null
$currentExtras = [System.Collections.Generic.List[string]]::new()

foreach ($line in $lines) {
    $trim = $line.Trim()
    if ($trim -eq "" -or $trim.StartsWith("#EXTM3U")) { continue }

    if ($trim.StartsWith("#EXTINF:")) {
        $currentExtinf = $trim
        $currentExtras.Clear()
    } elseif ($trim.StartsWith("#") -and $currentExtinf) {
        $currentExtras.Add($trim)
    } elseif (-not $trim.StartsWith("#") -and $currentExtinf) {
        $url = $trim

        # Extraer nombre tras la última coma
        $commaIdx = $currentExtinf.LastIndexOf(",")
        $name = if ($commaIdx -ge 0) { $currentExtinf.Substring($commaIdx + 1).Trim() } else { "Canal" }

        # Extraer grupo/categoría
        $category = ""
        if ($currentExtinf -match 'group-title="([^"]*)"') {
            $category = $Matches[1].Trim()
        }

        # Extraer logo
        $logo = ""
        if ($currentExtinf -match 'tvg-logo="([^"]*)"') {
            $logo = $Matches[1].Trim()
        }

        # Extraer tvg-name
        $tvgName = $name
        if ($currentExtinf -match 'tvg-name="([^"]*)"') {
            $tvgName = $Matches[1].Trim()
        }

        $entries.Add([PSCustomObject]@{
            Extinf   = $currentExtinf
            Extras   = ($currentExtras -join "`n")
            Url      = $url
            Name     = $name
            TvgName  = $tvgName
            Category = $category
            Logo     = $logo
        })

        $currentExtinf = $null
        $currentExtras.Clear()
    }
}

Write-Host "[✓] Se leyeron $($entries.Count) canales del archivo original." -ForegroundColor Green

# 4. Filtrar canales
$matched = [System.Collections.Generic.List[PSObject]]::new()
$seenUrls = [System.Collections.Generic.HashSet[string]]::new()

foreach ($e in $entries) {
    $isMatch = $false
    $termMatched = ""

    if ($filters.Count -eq 0) {
        $isMatch = $true
    } else {
        foreach ($f in $filters) {
            $pattern = [regex]::Escape($f)
            if ($e.Name -match "(?i)$pattern" -or $e.TvgName -match "(?i)$pattern" -or $e.Category -match "(?i)$pattern") {
                $isMatch = $true
                $termMatched = $f
                break
            }
        }
    }

    if ($isMatch -and -not $seenUrls.Contains($e.Url)) {
        [void]$seenUrls.Add($e.Url)
        $e | Add-Member -MemberType NoteProperty -Name "Filtro" -Value $termMatched -Force
        $matched.Add($e)
    }
}

# 5. Generar Archivos de Salida
$baseName = [System.IO.Path]::GetFileNameWithoutExtension($OutPath)
$dir = [System.IO.Path]::GetDirectoryName($OutPath)
if (-not $dir) { $dir = "." }

$outM3uFile = Join-Path $dir "$baseName.m3u"
$outTxtFile = Join-Path $dir "${baseName}_enlaces.txt"
$outCsvFile = Join-Path $dir "${baseName}_canales.csv"

# A. Escribir .M3U
$sbM3u = [System.Text.StringBuilder]::new()
[void]$sbM3u.AppendLine('#EXTM3U name="Lista Filtrada LELOUCH"')
[void]$sbM3u.AppendLine()

foreach ($m in $matched) {
    [void]$sbM3u.AppendLine($m.Extinf)
    if ($m.Extras) { [void]$sbM3u.AppendLine($m.Extras) }
    [void]$sbM3u.AppendLine($m.Url)
    [void]$sbM3u.AppendLine()
}
[System.IO.File]::WriteAllText($outM3uFile, $sbM3u.ToString(), [System.Text.Encoding]::UTF8)

# B. Escribir TXT de Enlaces Directos
$sbTxt = [System.Text.StringBuilder]::new()
[void]$sbTxt.AppendLine("# ENLACES DIRECTOS EXTRAÍDOS ($($matched.Count) canales)")
[void]$sbTxt.AppendLine("# Formato: [Nombre del Canal] -> URL Directa")
[void]$sbTxt.AppendLine()

foreach ($m in $matched) {
    $cat = if ($m.Category) { " [$( $m.Category )]" } else { "" }
    [void]$sbTxt.AppendLine("$( $m.Name )$cat")
    [void]$sbTxt.AppendLine($m.Url)
    [void]$sbTxt.AppendLine()
}
[System.IO.File]::WriteAllText($outTxtFile, $sbTxt.ToString(), [System.Text.Encoding]::UTF8)

# C. Escribir CSV
$matched | Select-Object Name, Category, Url, Logo, Filtro |
    Export-Csv -Path $outCsvFile -NoTypeInformation -Encoding UTF8

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " 🎉 ¡FILTRADO COMPLETADO! ($($matched.Count) canales extraídos)" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " 📄 1. Lista M3U Filtrada : $outM3uFile" -ForegroundColor Cyan
Write-Host " 🔗 2. Enlaces Directos   : $outTxtFile" -ForegroundColor Cyan
Write-Host " 📊 3. Hoja Excel/CSV     : $outCsvFile" -ForegroundColor Cyan
Write-Host "------------------------------------------------------------" -ForegroundColor Gray

# Mostrar primeros 15 resultados
$limit = [Math]::Min(15, $matched.Count)
for ($i = 0; $i -lt $limit; $i++) {
    $ch = $matched[$i]
    Write-Host "  $($i + 1). $( $ch.Name ) -> $( $ch.Url )" -ForegroundColor White
}
if ($matched.Count -gt 15) {
    Write-Host "  ... y $($matched.Count - 15) canales más guardados en los archivos." -ForegroundColor DarkGray
}
Write-Host ""

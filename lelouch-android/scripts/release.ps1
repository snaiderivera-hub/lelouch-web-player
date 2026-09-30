# =============================================================================
# release.ps1 — Lelouch Android Auto-Release
# Uso: .\scripts\release.ps1 [-BumpType patch|minor|major] [-GithubToken "ghp_xxx"]
# Incrementa la version, compila el APK y crea el release en GitHub automaticamente.
# =============================================================================
param(
    [ValidateSet("patch","minor","major")]
    [string]$BumpType = "patch",

    # Token de GitHub con permisos: repo + write:packages
    # Si no se pasa por parametro, se lee de la variable de entorno GITHUB_TOKEN
    [string]$GithubToken = $env:GITHUB_TOKEN
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir   = $PSScriptRoot
$AndroidDir  = Split-Path $ScriptDir -Parent
$BuildGradle = Join-Path $AndroidDir "app\build.gradle.kts"
$VersionJson = Join-Path (Split-Path $AndroidDir -Parent) "version.json"
$ApkPath     = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$GithubRepo  = "snaiderivera-hub/lelouch-web-player"
$GithubApi   = "https://api.github.com"

# ── 1. Leer version actual ───────────────────────────────────────────────────
Write-Host "`n[1/6] Leyendo version actual..." -ForegroundColor Cyan
$content = Get-Content $BuildGradle -Raw

if ($content -notmatch 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"') {
    Write-Error "No se pudo parsear versionName en $BuildGradle"
    exit 1
}
$major = [int]$Matches[1]
$minor = [int]$Matches[2]
$patch = [int]$Matches[3]
Write-Host "   Version actual: $major.$minor.$patch" -ForegroundColor Gray

# ── 2. Incrementar version ───────────────────────────────────────────────────
Write-Host "[2/6] Incrementando version ($BumpType)..." -ForegroundColor Cyan
switch ($BumpType) {
    "major" { $major++; $minor = 0; $patch = 0 }
    "minor" { $minor++; $patch = 0 }
    "patch" { $patch++ }
}
$newVersionName = "$major.$minor.$patch"
$newVersionCode = $major * 1000000 + $minor * 1000 + $patch
$newTag         = "ver.$newVersionName"
Write-Host "   Nueva version: $newVersionName  (versionCode=$newVersionCode, tag=$newTag)" -ForegroundColor Green

# ── 3. Actualizar build.gradle.kts ──────────────────────────────────────────
Write-Host "[3/6] Actualizando build.gradle.kts..." -ForegroundColor Cyan
$content = $content `
    -replace '(?m)(versionCode\s*=\s*)\d+',     "`${1}$newVersionCode" `
    -replace '(?m)(versionName\s*=\s*)"[\d\.]+"', "`${1}`"$newVersionName`""
Set-Content -Path $BuildGradle -Value $content -NoNewline -Encoding UTF8
Write-Host "   OK: versionCode=$newVersionCode, versionName=`"$newVersionName`"" -ForegroundColor Gray

# ── 4. Compilar APK ─────────────────────────────────────────────────────────
Write-Host "[4/6] Compilando APK (assembleDebug)..." -ForegroundColor Cyan
Push-Location $AndroidDir
try {
    & .\gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle fallo con codigo $LASTEXITCODE" }
} finally {
    Pop-Location
}
if (-not (Test-Path $ApkPath)) { throw "APK no encontrado en: $ApkPath" }
$apkSizeMb = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
Write-Host "   OK: APK generado ($apkSizeMb MB)" -ForegroundColor Gray

# ── 5. Crear Release en GitHub y subir APK ──────────────────────────────────
Write-Host "[5/6] Subiendo release a GitHub ($newTag)..." -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($GithubToken)) {
    Write-Warning "GITHUB_TOKEN no configurado. Saltando upload a GitHub."
    Write-Host "   Para subir automaticamente configura el token:" -ForegroundColor Yellow
    Write-Host "   `$env:GITHUB_TOKEN = 'ghp_TU_TOKEN'" -ForegroundColor Yellow
    Write-Host "   .\scripts\release.ps1" -ForegroundColor Yellow
} else {
    $headers = @{
        "Authorization"        = "Bearer $GithubToken"
        "Accept"               = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    $changelog = "Release $newTag - compilado automaticamente."

    $releaseBody = @{
        tag_name   = $newTag
        name       = "ver.$newVersionName"
        body       = $changelog
        draft      = $false
        prerelease = $false
    } | ConvertTo-Json -Depth 3

    try {
        $release = Invoke-RestMethod `
            -Uri "$GithubApi/repos/$GithubRepo/releases" `
            -Method POST `
            -Headers $headers `
            -Body $releaseBody `
            -ContentType "application/json"
        Write-Host "   Release creado: $($release.html_url)" -ForegroundColor Gray
    } catch {
        Write-Host "   Tag ya existe, buscando release..." -ForegroundColor Yellow
        $releases = Invoke-RestMethod -Uri "$GithubApi/repos/$GithubRepo/releases" -Headers $headers
        $release = $releases | Where-Object { $_.tag_name -eq $newTag } | Select-Object -First 1
        if (-not $release) { throw "No se pudo crear ni encontrar el release $newTag" }
    }

    # Subir APK
    $uploadUrl = $release.upload_url -replace '\{\?name,label\}', ''
    $apkBytes  = [System.IO.File]::ReadAllBytes($ApkPath)
    Invoke-RestMethod `
        -Uri "$($uploadUrl)?name=app-debug.apk&label=app-debug.apk" `
        -Method POST `
        -Headers $headers `
        -Body $apkBytes `
        -ContentType "application/vnd.android.package-archive" | Out-Null
    Write-Host "   APK subido exitosamente!" -ForegroundColor Green

    # Actualizar version.json
    Write-Host "[6/6] Actualizando version.json..." -ForegroundColor Cyan
    $apkUrl = "https://github.com/$GithubRepo/releases/download/$newTag/app-debug.apk"
    $vJson = @{
        versionName = $newVersionName
        versionCode = $newVersionCode
        apkUrl      = $apkUrl
        changelog   = $changelog
    } | ConvertTo-Json -Depth 2
    Set-Content -Path $VersionJson -Value $vJson -Encoding UTF8
    Write-Host "   version.json actualizado" -ForegroundColor Gray
}

Write-Host "`n✅ Release $newTag completado!" -ForegroundColor Green
Write-Host "   APK: $ApkPath" -ForegroundColor Gray

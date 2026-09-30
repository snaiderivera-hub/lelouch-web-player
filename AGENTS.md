# Lelouch IPTV & Android Project Instructions

## Regla de Compilación Android — Incremento Automático de Versión
Cada vez que el usuario solicite compilar o generar el APK de Android (`compilar`, `build`, `generar apk`, `nueva version`, etc.):

1. **Auto-Incrementar la versión**:
   - Subir el patch (ej. `1.0.1` -> `1.0.2`).
   - Calcular `versionCode = major * 1000000 + minor * 1000 + patch` (ej. `1.0.2` -> `1000002`).
   - Actualizar `lelouch-android/app/build.gradle.kts`.
   - Actualizar `version.json` en la raíz.
2. **Compilar**:
   - `.\gradlew assembleDebug` en `lelouch-android`.
3. **Alternativamente**:
   - Ejecutar `.\scripts\release.ps1` desde `lelouch-android`.

# Regla de Compilación Android — Auto-Bump de Versión Obligatorio

Esta regla es de cumplimiento OBLIGATORIO en este proyecto.

## Instrucción del Usuario
Cada vez que el usuario solicite compilar, generar APK o hacer build de la aplicación Android (`compilar`, `build`, `generar apk`, `nueva version`, `saca apk`, etc.), el agente DEBE OBLIGATORIAMENTE:

1. **Incrementar la versión** antes de compilar:
   - Por defecto se incrementa el **patch** (ejemplo: `1.0.1` -> `1.0.2`, `1.0.2` -> `1.0.3`).
   - El cálculo de `versionCode` DEBE seguir la fórmula semver:
     $$\text{versionCode} = \text{major} \times 1\,000\,000 + \text{minor} \times 1\,000 + \text{patch}$$
     Ejemplo: `1.0.2` = `1000002`.
   - Modificar `lelouch-android/app/build.gradle.kts`:
     ```kotlin
     versionCode = 1000002
     versionName = "1.0.2"
     ```
   - Actualizar `version.json` en la raíz del proyecto con la nueva versión y el nuevo `versionCode`.

2. **Compilar el proyecto**:
   - Ejecutar `.\gradlew assembleDebug` dentro de `lelouch-android`.
   - Verificar que termine con `BUILD SUCCESSFUL`.

3. **Manejo de Release / Entrega**:
   - Abrir o indicar la ruta del APK generado (`lelouch-android/app/build/outputs/apk/debug/app-debug.apk`).
   - El script automatizado `lelouch-android/scripts/release.ps1` realiza este proceso integralmente.

4. **Regla de Oro**:
   NUNCA compilar dejando el mismo `versionCode` o `versionName` que la compilación anterior si el usuario va a probar actualizaciones OTA, pues Android y el `AppUpdateManager` requieren que el `versionCode` remoto sea estrictamente mayor que el instalado en el teléfono/TV.

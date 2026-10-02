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

## FASE 31 — Qué NO debe hacer Antigravity (Límites y Guardarraíles Estrictos)
Bajo ninguna circunstancia Antigravity debe:
1. **Reescribir toda la web**: Trabajar incrementalmente sobre la arquitectura vanilla JS modular existente.
2. **Eliminar XtreamAdapter**: Preservar intacto `XtreamAdapter.js` como base de integración con Xtream Codes.
3. **Crear otra base de datos paralela**: Utilizar únicamente la infraestructura existente en Supabase (`custom_playlists`, `playlist_items`, `v_resolved_playlist_items`).
4. **Duplicar todo el catálogo**: No volcar miles de canales en la BD; resolver dinámicamente y almacenar solo selecciones del usuario.
5. **Exponer service_role**: Jamás filtrar o enviar `SUPABASE_SERVICE_ROLE_KEY` al cliente o en respuestas de endpoints públicos.
6. **Guardar contraseñas en localStorage**: No almacenar contraseñas en texto plano si existe la solución segura basada en tokens hash SHA-256.
7. **Meter credenciales en logs**: Aplicar siempre `redactSensitiveUrl` antes de emitir cualquier log o mensaje de error en Vercel o consola.
8. **Hacer proxy de video por Vercel**: Vercel entrega únicamente playlists de texto (#EXTM3U/JSON); el stream de vídeo se consume directamente entre el reproductor y el proveedor IPTV.
9. **Crear MAC Checker**: Prohibido crear checkers de direcciones MAC Stalker.
10. **Crear Combo Checker**: Prohibido crear herramientas de fuerza bruta o chequeo masivo de combos.
11. **Implementar búsqueda de credenciales**: Prohibido implementar funciones de scraping o búsqueda de cuentas ajenas.
12. **Cambiar TV/Mobile todavía**: No alterar los layouts ni lógica de Android TV/Mobile de forma prematura sin orden explícita.
13. **Romper Descargar M3U**: Mantener siempre operativa la descarga física del archivo `.m3u` en disco local.
14. **Generar una URL falsa sin endpoint real**: Toda URL pública generada debe mapear a `/api/playlist/:token` con resolución funcional en Vercel/Supabase.
15. **Declarar "terminado" solamente porque npm build pasa**: Exigir siempre verificación funcional de extremo a extremo con tests reales.

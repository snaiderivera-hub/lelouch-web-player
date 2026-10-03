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

## FASE 32 — Directiva de Rediseño Unificado Android (TV Box y Móvil)
El usuario ha ordenado explícitamente adoptar la interfaz del **Portal Dashboard de Lelouch Web Player** como el Home oficial tanto en Android TV (TV Box) como en Teléfono móvil:
- **Cabecera Central:**
  - Título: `REPRODUCTOR LELOUCH`
  - Subtítulo: `M3U • XTREAM CODES • HLS`
  - Indicadores: Pill badge con servidor/cuenta activa, botón `🔄 RECARGAR` y fecha de expiración `Vence: DD/MM/YYYY`.
- **4 Tarjetas Principales Hero (Portal Dashboard con resplandor neón cian y contadores en tiempo real):**
  1. 📺 **TV EN VIVO** (con badge de cantidad de canales e integración de la lista personalizada de 67 canales).
  2. 🎬 **PELÍCULAS** (con badge de películas VOD).
  3. 🎞️ **SERIES** (con badge de series disponibles).
  4. ⚽ **DEPORTES** (con badge de canales deportivos).
- **Fila Inferior de Acciones Rápidas (Navegación fluida con foco D-Pad en TV y táctil en móvil):**
  - `📄 Descargar M3U`
  - `🔄 Recargar Catálogo`
  - `⚙️ Ajustes y Listas` (con acceso a cuentas guardadas y a la lista personalizada generada).
  - `🩺 Diagnóstico`

## FASE 33 — Adopción Oficial del Framework MREA (Multi-Role Enterprise Agents)
Antigravity operará como el **Enterprise Orchestrator (Plano de Control)** bajo la metodología de gobernanza MREA:
1. **Evidence-First**: Todo diagnóstico y plan se sustenta en evidencias reales del código fuente, diferenciando Hechos (FACT) de Inferencias (INFERENCE).
2. **Ponytail Philosophy**: El código es un pasivo. Reutilizar antes de escribir, simplificar antes de abstraer, y escribir únicamente el código mínimo indispensable sin overengineering.
3. **Flujo de Roles Especializados**:
   - 🧠 **Orchestrator**: Dirige el flujo y clasifica el riesgo (Material vs No Material).
   - 📐 **Architect (Software / Design)**: Formula la solución técnica o visual.
   - 🛡️ **Auditor (Technical / Design)**: Cuestiona la complejidad y valida seguridad / estándares de forma independiente.
   - 🚪 **Human Approval Gate**: Solicita y espera la confirmación explícita del usuario para cambios materiales antes de modificar archivos críticos.
   - 👷 **Implementer**: Aplica los cambios aprobados sin desviarse, sin rediseños arbitrarios y con evidencia comprobable.
4. **Skills MREA integradas y activas**: En `.agents/skills/` (`engineering-protocol`, `evidence-based-validation`, `happy-path`, `ponytail-philosophy`, `rollback-strategy`, `security-baseline`).

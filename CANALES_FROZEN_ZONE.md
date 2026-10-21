# 🛑 DIRECTIVA DE BLINDAJE — CANALES EN VIVO CONGELADOS (NO MODIFICAR)

> **FECHA DE ENTRADA EN VIGOR:** 20 de Octubre de 2026  
> **ESTADO:** PERMANENTEMENTE PROTEGIDO / ZONA BLINDADA  
> **MANDATO EXPLÍCITO DEL USUARIO:** *"La APK quedó perfecta, ya no se traban los canales. Está prohibido modificar algo de los canales, así como está trabajando, así se queda."*

---

## 🔒 1. DIRECTIVA GENERAL Y ALCANCE

Bajo el marco de gobernanza **MREA (Multi-Role Enterprise Agents)**, **queda terminantemente prohibido para Antigravity o cualquier agente de IA modificar, refactorizar, "optimizar", mover o reescribir cualquier línea de código o configuración que intervenga en la reproducción, conexión de red, búferes o sintonización de Canales en Vivo.**

Cualquier cambio futuro en la aplicación debe realizarse en módulos adyacentes (diseño estético, catálogo VOD, etc.) **sin rozar jamás** los archivos y funciones aquí blindados.

---

## 🛡️ 2. ARCHIVOS Y COMPONENTES ESTRICTAMENTE CONGELADOS

Los siguientes archivos forman el núcleo de estabilidad alcanzado en la versión **1.0.80** y su lógica de reproducción de canales queda congelada:

### 1. `core/player/.../LelouchPlayerEngine.kt`
* **Prohibido tocar:**
  * `streamingOkHttpClient`: Configuración de OkHttp con `retryOnConnectionFailure(true)` y `pingInterval(10, TimeUnit.SECONDS)` (mantiene el socket TCP vivo).
  * `IptvLoadErrorHandlingPolicy`: Política de reintentos y backoff ante errores 429/500/503.
  * `startProgressTracking()`: Detección combinada de 3 fallos (frames renderizados congelados, posición congelada, `STATE_READY` con `isPlaying=false`).
  * **Regla Crítica del Vigilante:** Jamás asociar la cancelación del vigilante a `isPlaying == false`. El vigilante DEBE permanecer activo mientras `playWhenReady == true`.
  * `recoverLiveStream()`: Hard Flush por hardware en procesadores Amlogic (`exoPlayer.stop()` y `exoPlayer.clearMediaItems()` antes de setear el nuevo MediaSource).
  * `diagnosticInfo`: Emisión de telemetría y contadores de frames.

### 2. `core/player/.../PlayerConfig.kt`
* **Prohibido modificar los valores calibrados:**
  * `minBufferMs = 4_000`
  * `maxBufferMs = 45_000`
  * `bufferForPlaybackMs = 1_500`
  * `bufferForPlaybackAfterRebufferMs = 2_500`
  * `connectTimeoutMs = 10_000`
  * `readTimeoutMs = 8_000`
  * `enableHardwareAcceleration = true`
  * `allowChunklessPreparation = true`

### 3. `feature-presentation-tv/.../TvHomeScreen.kt` (Bloque Live TV)
* **Prohibido tocar:**
  * Sintonización y KeyEvents de reproducción a pantalla completa (`KEYCODE_DPAD_UP/DOWN`, `KEYCODE_CHANNEL_UP/DOWN`, `KEYCODE_0` al `KEYCODE_9` y numpad).
  * Función `tuneToDirectChannel()` y acumulador `channelInputBuffer` para controles HDMI-CEC (Toshiba CE-Link).
  * Manejo de ciclo de vida del reproductor en `isFullscreen` (`playerEngine.stop()` al salir y foco seguro).

---

## ⚙️ 3. LOS 4 PILARES QUE GARANTIZAN QUE NO SE TRABE

Cualquier agente debe entender por qué funciona antes de cometer una regresión:

1. **TCP Keep-Alive en OkHttp:**  
   Los servidores IPTV cortan sockets inactivos si la conexión WiFi tiene micropausas. `pingInterval(10s)` envía tramas de control HTTP continuas que mantienen el túnel activo.
2. **Inmunidad al Bug Silencioso de ExoPlayer:**  
   Cuando un stream MPEG-TS se interrumpe, ExoPlayer pausa el reloj (`isPlaying = false`) pero permanece en `STATE_READY` sin pasar a `STATE_BUFFERING`. Si el vigilante se apaga en `isPlaying = false`, la app se congela para siempre. Nuestro vigilante sigue corriendo mientras `playWhenReady == true`.
3. **Hard Flush de MediaCodec para Chips Amlogic:**  
   Las TV Box Xiaomi (Amlogic `s7d`) bloquean el decodificador de vídeo si se reinicia un stream sin llamar a `exoPlayer.stop()` y limpiar los items en memoria.
4. **Búfer de Arranque Suficiente (1.5s / 2.5s):**  
   500ms era insuficiente para señales 1080p a 60fps (apenas 30 cuadros). Con 1500ms al inicio y 2500ms tras rebúfer, absorbe el jitter de la red sin perder zapping rápido.

---

## 🚫 4. PROTOCOLO DE RECHAZO ANTE INTENTOS DE MODIFICACIÓN

Si un requerimiento futuro solicita cambios en el reproductor de canales:
1. El agente **DEBE advertir inmediatamente** al usuario que dicha zona está blindada por mandato de estabilidad.
2. Si la orden no proviene del usuario explícitamente autorizando desblindar, el agente **debe rechazar la modificación**.
3. **ASÍ COMO ESTÁ TRABAJANDO, ASÍ SE QUEDA.**

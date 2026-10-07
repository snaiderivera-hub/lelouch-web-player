# Documentación de Resolución de Errores - Reproductor Móvil (FASE 33)

## Problema 1: Controles de Interfaz Fijos (No Ocultables)
**Descripción:** 
El reproductor en Android Móvil (`MobileHomeScreen.kt`) tenía los controles de la interfaz (Barra Superior e Inferior) fijos en la pantalla cuando se reproducía en orientación horizontal a pantalla completa. A diferencia del reproductor de TV, no desaparecían tras unos segundos de reproducción, ni tampoco se podían ocultar tocando la pantalla, lo cual estorbaba la visión.

**Causa Raíz:**
La migración de la interfaz nativa a la interfaz moderna (Compose) no incluía la lógica de estado para manejar la visibilidad condicional (`AnimatedVisibility`) ni los temporizadores (`LaunchedEffect`) para auto-ocultarse.

**Resolución (v1.0.68):**
1. Se introdujo el estado `isOverlayVisible` inicializado en `true`.
2. Se programó un `LaunchedEffect` que escucha los cambios del motor de reproducción (`playbackState`) y, si el video está en estado `Playing`, inicia un contador de 4 segundos para ocultar la interfaz.
3. Se añadió un modificador `clickable` sobre la caja principal del reproductor que altera el estado de `isOverlayVisible` al tacto, permitiendo mostrar u ocultar las barras manualmente sin interrumpir la reproducción.
4. Las barras superiores e inferiores fueron envueltas en bloques `AnimatedVisibility` con transiciones de deslizamiento suaves (`slideInVertically` / `slideOutVertically`).

---

## Problema 2: Error ExoPlayer (Código BAD_VALUE) y Bucle de Omisión
**Descripción:** 
Al hacer Zapping rápido (deslizando a los lados) o al caer una señal de un canal Xtream Codes, el sistema mostraba persistentemente el panel de error de ExoPlayer ("URL Vacía") y entraba en un bucle frenético de omisión automática de canales (hasta 5 saltos) donde todos fallaban consecutivamente.

**Causa Raíz:**
1. **Zapping por Swipe:** La lógica antigua asumía que los canales inválidos tenían un `streamUrl` en blanco y trataba de saltarlos mediante un bloque `while (streamUrl.isBlank())`. Para las cuentas de Xtream, la URL en la base de datos *siempre* viene en blanco ya que el motor la ensambla dinámicamente usando el `streamId` y los credenciales del servidor. Al interpretar que todas estaban en blanco, el Zapping saltaba todos los canales sin éxito.
2. **Auto-Skip por Caída de Señal:** Al igual que en el caso anterior, el bloque `DisposableEffect` encargado de omitir canales caídos (`playerEngine.onChannelUnavailable`) intentaba reproducir la siguiente pista inyectando el campo crudo `nextChannel.streamUrl`. Al llegar un string vacío al motor de ExoPlayer, éste detonaba la excepción `PlaybackException.ERROR_CODE_BAD_VALUE` instantáneamente, detonando otra caída en bucle.

**Resolución (v1.0.69):**
1. Se inyectó explícitamente el uso de `StreamUrlResolver.resolveLive(...)` tanto en el bloque de Swipes como en el bloque de Auto-Skip.
2. Se eliminó la validación manual de cadenas en blanco `while(isBlank())` en la lógica de Swipe, delegando la responsabilidad de resolución de URLs (Xtream dinámico vs M3U Directo) puramente a la clase arquitectónica `StreamUrlResolver` que tiene acceso directo al `activeSource` (Servidor, User, Pass).
3. Se garantizó que el motor siempre reciba la cadena completamente ensamblada antes de invocar `playStream(targetUrl)`.

---
*Compilación: Android Móvil - v1.0.69*
*Fecha: Octubre 2026*

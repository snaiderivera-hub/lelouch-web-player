# Especificación y Verificación del Endpoint de Playlists (/api/playlist/:token)

## FASE 27 — Arquitectura, Matriz de Estados HTTP y Suite de Pruebas

El endpoint `/api/playlist/:token` es el punto de publicación público y seguro de playlists de **LELOUCH IPTV** hacia reproductores externos (TiviMate, VLC, OTT Navigator, IPTV Smarters, Smart TVs) y clientes nativos (Lelouch Android TV, Web Player).

---

## 1. Principios Arquitectónicos Clave

1. **NO Proxy de Vídeo (FASE 21)**:
   - **Vercel** únicamente resuelve y publica el texto del manifiesto M3U o JSON.
   - El reproductor cliente (TV Box, móvil, PC) conecta **directamente** al servidor del proveedor IPTV para reproducir los flujos de 10+ Mbps.
   - Si alguna URL de base de datos contiene envolturas de proxy (`/api/proxy?target=...`), el generador la desenvuelve automáticamente (`unwrapProxyUrl`) a la URL directa del proveedor.
2. **Autenticación por Token Seguro sin Sesión de Usuario (FASE 22)**:
   - Las Smart TVs y TV Boxes no requieren sesión interactiva en Supabase; el token público de alta entropía es su credencial.
   - La validación del token y la consulta de ítems se resuelven server-side en la función Serverless usando `SUPABASE_SERVICE_ROLE_KEY` o anon key con hash SHA-256.
3. **Anti-Indexación Obligatoria (FASE 20)**:
   - Encabezado `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet` en todas las respuestas para evitar la indexación por motores de búsqueda.
4. **Precedencia de Personalización**:
   - `custom_name` prevalece sobre el nombre original del proveedor (`direct_name` o catálogo Xtream).
   - `custom_group` prevalece sobre la categoría o grupo original (`direct_group` o catálogo).
   - `custom_logo` prevalece sobre el logo original del proveedor.

---

## 2. Matriz de Códigos de Respuesta HTTP

| Escenario | Método | Código HTTP | Content-Type | Comportamiento |
|---|---|---|---|---|
| **Token Válido** | `GET` | **`200 OK`** | `application/vnd.apple.mpegurl; charset=utf-8` | Devuelve el texto completo M3U con cabecera `#EXTM3U`, directivas `#EXTINF`, `Content-Length` y `ETag`. |
| **Token Válido (HEAD)** | `HEAD` | **`200 OK`** | `application/vnd.apple.mpegurl; charset=utf-8` | Devuelve todas las cabeceras (`Content-Length`, `ETag`, `X-Playlist-Version`), **sin transferir cuerpo de datos**. |
| **Token Inexistente** | `GET` / `HEAD` | **`404 Not Found`** | `text/plain; charset=utf-8` | No se encontró el hash SHA-256 en `playlist_access_tokens`. |
| **Token Desactivado** | `GET` / `HEAD` | **`410 Gone`** | `text/plain; charset=utf-8` | El token tiene `enabled = false` o `is_active = false`. |
| **Token Expirado** | `GET` / `HEAD` | **`410 Gone`** | `text/plain; charset=utf-8` | La fecha `expires_at` es anterior a la fecha actual (`expires_at < now`). |
| **Playlist Vacía** | `GET` | **`200 OK`** | `application/vnd.apple.mpegurl; charset=utf-8` | Manifiesto M3U válido con `#EXTM3U name="..."`, exactamente **0** directivas `#EXTINF`. |
| **Playlist con 100 Ítems** | `GET` | **`200 OK`** | `application/vnd.apple.mpegurl; charset=utf-8` | Manifiesto con exactamente **100** bloques `#EXTINF` íntegros. |
| **Formato de Token Inválido** | `GET` / `HEAD` | **`400 Bad Request`** | `text/plain; charset=utf-8` | Longitud inferior a 16 caracteres o caracteres no permitidos (`[^a-zA-Z0-9_-]`). |
| **Método No Permitido** | `POST`, `PUT`, `DELETE` | **`405 Method Not Allowed`** | `application/json; charset=utf-8` | Solo se admiten métodos `GET`, `HEAD` y `OPTIONS`. Cabecera `Allow: GET, HEAD, OPTIONS`. |

---

## 3. Soporte del Método `HEAD`

Muchos clientes IPTV avanzados (como TiviMate, OTT Navigator o scripts de comprobación periódica) envían peticiones `HEAD` antes de descargar la lista completa para verificar:
1. Si el enlace sigue activo (`200` vs `404` / `410`).
2. El tamaño en bytes del archivo M3U (`Content-Length`).
3. La versión y frescura de la lista (`ETag`, `X-Playlist-Version`).

### Implementación en `api/playlist.js`:
- El handler calcula el contenido, genera el `Content-Length` exacto en bytes UTF-8 (`Buffer.byteLength(m3uContent, 'utf8')`).
- Establece todas las cabeceras `Content-Type`, `Content-Length`, `ETag`, `X-Playlist-Version`, `Content-Disposition`.
- Si `req.method === 'HEAD'`, ejecuta inmediatamente `res.status(200).end()` sin transmitir el cuerpo, ahorrando ancho de banda y tiempo de CPU tanto en Vercel como en el TV Box.

---

## 4. Resultados de la Suite de Pruebas Unitarias (FASE 27)

Archivo de pruebas: [iptv-app/tests/test_phase27.html](file:///c:/Users/Lelouch/Downloads/LIVE_VOD_ALVARADO2023_20260924/iptv-app/tests/test_phase27.html)

Ejecución verificada con Microsoft Edge Headless Engine:

```
=== PASS/FAIL LINES: 11 / 11 (100% PASS) ===

[PASS] 1. GET token válido -> 200 (OK con M3U válido y Content-Type correcto)
       Status: 200 | Content-Type: application/vnd.apple.mpegurl; charset=utf-8 | Content-Length: 304 bytes

[PASS] 2. token inexistente -> 404 (Not Found al no encontrar hash en BD)
       Status: 404 | Body: #EXTM3U\n#EXTINF:-1,Enlace M3U no encontrado o revocado\nhttp://localhost/error

[PASS] 3. token desactivado -> 404 o 410 (Gone/Not Found para token con enabled=false)
       Status: 410 | Body: #EXTM3U\n#EXTINF:-1,Este enlace M3U ha sido desactivado\nhttp://localhost/error

[PASS] 4. token expirado -> 410 (Gone cuando expires_at < now)
       Status: 410 | Body: #EXTM3U\n#EXTINF:-1,Este enlace M3U ha expirado\nhttp://localhost/error

[PASS] 5. playlist vacía -> M3U válida sin items (#EXTM3U presente, exactamente 0 EXTINF)
       Status: 200 | Cabecera válida: true | Total EXTINF: 0 | Contenido: "#EXTM3U name=\"Lista Vacia de Canales\"\n"

[PASS] 6. playlist 100 items -> exactamente 100 EXTINF (integridad y cuenta exacta)
       Status: 200 | Conteo de directivas #EXTINF: 100 / 100 esperados | Último canal: Canal Premium #100

[PASS] 7. orden cambiado -> nuevo orden reflejado (posicion ASC respetada fielmente)
       Status: 200 | Orden verificado: Canal C (pos 1) -> Canal A (pos 2) -> Canal B (pos 3)

[PASS] 8. canal deshabilitado -> no aparece (los canales con enabled=false se excluyen)
       Status: 200 | Canales activos (1 y 3): presentes | Canales inactivos (2 y 4): omitidos

[PASS] 9. nombre personalizado -> aparece nombre personalizado (custom_name sobrescribe el del proveedor)
       Status: 200 | "⭐ Mi Canal Favorito UHD" presente | Nombre crudo "Original Provider Stream" ausente

[PASS] 10. grupo personalizado -> aparece grupo personalizado (custom_group sobrescribe)
       Status: 200 | group-title="Favoritos de la Familia" presente | group-title="RAW_CHANNELS" ausente

[PASS] 11. Soporte del Método HEAD (Devuelve 200, Content-Length y ETag sin transferir cuerpo de datos)
       HEAD Token Válido: Status 200, Content-Length presente, ETag presente, Cuerpo vacío: true
       HEAD Token Inexistente: Status 404, Cuerpo vacío: true
       HEAD Token Expirado: Status 410, Cuerpo vacío: true
```

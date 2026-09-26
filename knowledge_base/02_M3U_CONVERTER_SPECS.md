# Especificación de Conversor Xtream a M3U Plus y EPG XMLTV

Basado en el análisis del repositorio `ovosimpatico/xtream2m3u`.

---

## 1. Estructura del Generador M3U Plus

Un archivo M3U Plus estándar generado a partir de la API de Xtream Codes debe contener encabezados enriquecidos con metadatos:

```m3u
#EXTM3U x-tvg-url="http://{server}/xmltv.php?username={user}&password={pass}"

#EXTINF:-1 tvg-id="espn.us" tvg-name="ESPN HD" tvg-logo="http://server/logos/espn.png" group-title="Deportes",ESPN HD (FHD)
http://server:80/live/user/pass/101.m3u8

#EXTINF:-1 tvg-id="hbo.us" tvg-name="HBO East" tvg-logo="http://server/logos/hbo.png" group-title="Cine & Series" tv-archive="1" tv-archive-duration="7" catchup="default" catchup-source="http://server:80/timeshift/user/pass/{duration}/{start}/102.m3u8",HBO East HD
http://server:80/live/user/pass/102.m3u8
```

---

## 2. Etiquetas de Catchup / Timeshift

Para que reproductores modernos como TiviMate, Televizo, VLC o Kodi reconocen el rebobinado de canales:
- `tv-archive="1"`
- `tv-archive-duration="7"` (días disponibles)
- `catchup="default"`
- `catchup-source="http://{server}/timeshift/{user}/{pass}/{duration}/{start}/{stream_id}.m3u8"`

---

## 3. Algoritmo de Generación Personalizable

1. **Autenticación en Xtream API**: Obtener `user_info` y `server_info`.
2. **Obtención de Categorías**: Filtrar grupos incluidos o excluidos seleccionados por el usuario.
3. **Mapeo de Canales**: Iterar sobre `get_live_streams`, `get_vod_streams`, `get_series` y aplicar etiquetas `#EXTINF`.
4. **Exportación**: Descarga directa `.m3u8` / `.m3u` o enlace dinámico vía proxy.

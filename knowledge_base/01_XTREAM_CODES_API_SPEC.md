# Especificación Técnica Completa de Xtream Codes API (v2.9.2)

Compilación realizada a partir del análisis profundo de `worldofiptvcom/xtream-codes-api-documentation`, `humolot/Api-PHP-Xtream-Codes` y `tellytv/go.xtream-codes`.

---

## 1. Endpoints Principales y Parámetros

### 1.1 Player API (`/player_api.php`)
- **Autenticación Básica**:
  `GET /player_api.php?username={user}&password={pass}`
  Devuelve `user_info` (status, exp_date, max_connections, active_cons) y `server_info` (url, port, rtmp_port, timezone).

- **Categorías de Canales en Vivo**:
  `GET /player_api.php?username={user}&password={pass}&action=get_live_categories`

- **Canales en Vivo**:
  `GET /player_api.php?username={user}&password={pass}&action=get_live_streams`
  Parámetro opcional: `category_id={id}`

- **Categorías de VOD / Películas**:
  `GET /player_api.php?username={user}&password={pass}&action=get_vod_categories`

- **Streams de Películas (VOD)**:
  `GET /player_api.php?username={user}&password={pass}&action=get_vod_streams`
  Parámetro opcional: `category_id={id}`

- **Información Detallada de Película (VOD Info)**:
  `GET /player_api.php?username={user}&password={pass}&action=get_vod_info&vod_id={stream_id}`
  Devuelve sinopsis, TMDB ID, reparto, director, imágenes backdrop, trailer de YouTube, duración en segundos.

- **Categorías de Series**:
  `GET /player_api.php?username={user}&password={pass}&action=get_series_categories`

- **Listado de Series**:
  `GET /player_api.php?username={user}&password={pass}&action=get_series`
  Parámetro opcional: `category_id={id}`

- **Detalles y Episodios de Serie**:
  `GET /player_api.php?username={user}&password={pass}&action=get_series_info&series_id={series_id}`
  Devuelve objeto `seasons` agrupado por número de temporada, con lista de episodios, URLs de stream, duración y sinopsis por episodio.

- **EPG Corto / Guía de Programación**:
  `GET /player_api.php?username={user}&password={pass}&action=get_short_epg&stream_id={stream_id}&limit={n}`

---

## 2. Formatos de URLs de Reproducción

- **Live Stream (Canal en Vivo)**:
  `http://{server}:{port}/live/{username}/{password}/{stream_id}.m3u8`
  `http://{server}:{port}/live/{username}/{password}/{stream_id}.ts`

- **VOD Stream (Película)**:
  `http://{server}:{port}/movie/{username}/{password}/{stream_id}.{container_extension}`

- **Series Episode Stream (Episodio de Serie)**:
  `http://{server}:{port}/series/{username}/{password}/{stream_id}.{container_extension}`

- **Timeshift / Catchup (Grabación/Rebobinado)**:
  `http://{server}:{port}/timeshift/{username}/{password}/{duration}/{start_timestamp}/{stream_id}.m3u8`

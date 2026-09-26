# Especificaciones de Parseo de EPG (XMLTV & Xtream Short EPG)

---

## 1. Formato Xtream `get_short_epg`

La API `player_api.php?action=get_short_epg&stream_id={id}` devuelve un array de programas en formato JSON:

```json
{
  "epg_listings": [
    {
      "id": "12345",
      "epg_id": "channel.us",
      "title": "Noticias de la Tarde",
      "lang": "es",
      "start": "2025-12-24 18:00:00",
      "end": "2025-12-24 19:00:00",
      "description": "Resumen de las noticias principales del día.",
      "channel_id": "channel_101",
      "start_timestamp": "1735063200",
      "stop_timestamp": "1735066800",
      "has_archive": 1
    }
  ]
}
```

---

## 2. Formato XMLTV Standard (`/xmltv.php`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tv generator-info-name="Xtream Codes EPG Generator">
  <channel id="channel.us">
    <display-name>ESPN HD</display-name>
    <icon src="http://server/logos/espn.png"/>
  </channel>

  <programme start="20251224180000 +0000" stop="20251224190000 +0000" channel="channel.us">
    <title lang="es">Sports Center</title>
    <desc lang="es">Noticiero deportivo en vivo con análisis y mejores jugadas.</desc>
    <category lang="es">Sports</category>
  </programme>
</tv>
```

---

## 3. Integración en el Cliente Web

- Parsea marcas de tiempo UNIX (`start_timestamp` / `stop_timestamp`).
- Calcula el programa en emisión en el tiempo local actual: `start_timestamp <= now <= stop_timestamp`.
- Renderiza el porcentaje de barra de progreso: `((now - start) / (stop - start)) * 100`.

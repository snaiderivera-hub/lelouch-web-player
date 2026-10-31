/**
 * @module ExportService
 * Exporta los datos IPTV normalizados a JSON por categoría o a listas M3U Plus con etiquetas Catchup.
 */

/**
 * Descarga un objeto como archivo JSON.
 * @param {Object|Array} data
 * @param {string} filename
 */
export function downloadJson(data, filename) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.json') ? filename : `${filename}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Genera y descarga un archivo M3U Plus enriquecido con etiquetas Catchup/Timeshift y tvg-id.
 * Basado en las especificaciones del conversor xtream2m3u.
 * @param {Array} streams Listado de canales en vivo
 * @param {Object} account Credentials & server info
 * @param {string} filename
 */
export function downloadM3UPlus(streams, account, filename = 'lista_iptv_custom.m3u8') {
  let m3u = `#EXTM3U x-tvg-url="${account?.serverUrl || ''}/xmltv.php?username=${account?.username || ''}&password=${account?.password || ''}"\n\n`;

  streams.forEach(stream => {
    const epgId = stream.epgChannelId || '';
    const logo = stream.icon || '';
    const group = stream.categoryName || 'General';
    const name = stream.name || 'Canal';
    const streamUrl = stream.streamUrl || '';

    let catchupAttrs = '';
    if (stream.tvArchive) {
      const durationDays = stream.tvArchiveDuration || 7;
      catchupAttrs = ` tv-archive="1" tv-archive-duration="${durationDays}" catchup="default"`;
    }

    m3u += `#EXTINF:-1 tvg-id="${epgId}" tvg-name="${name}" tvg-logo="${logo}" group-title="${group}"${catchupAttrs},${name}\n`;
    m3u += `${streamUrl}\n\n`;
  });

  const blob = new Blob([m3u], { type: 'audio/x-mpegurl;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.m3u8') || filename.endsWith('.m3u') ? filename : `${filename}.m3u8`;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Agrupa un array de items por la propiedad categoryName.
 * @param {Array} items
 * @returns {Object.<string, Array>}
 */
export function groupByCategory(items) {
  return items.reduce((acc, item) => {
    const key = item.categoryName || 'SIN CATEGORIA';
    if (!acc[key]) acc[key] = [];
    acc[key].push(item);
    return acc;
  }, {});
}

/**
 * Exporta el estado completo de la importación como múltiples archivos JSON.
 * @param {Object} state  iptvService.state
 */
export function exportAll(state) {
  const { account, server, live, movies, series } = state;

  if (account) {
    downloadJson({ account, server, generado: new Date().toISOString() }, 'account.json');
  }

  const liveGroups = groupByCategory(live);
  for (const [cat, items] of Object.entries(liveGroups)) {
    downloadJson(items, `LIVE_${sanitizeFilename(cat)}.json`);
  }

  const vodGroups = groupByCategory(movies);
  for (const [cat, items] of Object.entries(vodGroups)) {
    downloadJson(items, `VOD_${sanitizeFilename(cat)}.json`);
  }

  if (series.length > 0) {
    const seriesGroups = groupByCategory(series);
    for (const [cat, items] of Object.entries(seriesGroups)) {
      downloadJson(items, `SERIES_${sanitizeFilename(cat)}.json`);
    }
  }
}

/**
 * Exporta solo una categoría específica.
 * @param {Array} items
 * @param {string} categoryName
 * @param {'live'|'vod'|'series'} type
 */
export function exportCategory(items, categoryName, type) {
  downloadJson(items, `${type.toUpperCase()}_${sanitizeFilename(categoryName)}.json`);
}

function sanitizeFilename(name) {
  return name.replace(/[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\s\-_]/g, '').trim().slice(0, 60);
}

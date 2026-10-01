/**
 * @module M3uParserAdapter
 * Adaptador que envuelve librerías externas de M3U (como iptv-m3u-playlist-parser).
 * 
 * Actúa como Anti-Corruption Layer (Capa Anticorrupción) para que el resto del ecosistema
 * Lelouch (UI, componentes, generador, reproductor) no dependa directamente de ninguna
 * biblioteca externa de terceros.
 * 
 * Cumple exhaustivamente con la FASE 4:
 * Preserva:
 * - #EXTM3U y cabecera
 * - #EXTINF y duration (exacta)
 * - tvg-id, tvg-name, tvg-logo, tvg-country, tvg-language, group-title
 * - #EXTGRP (override o fallback de categoría)
 * - #EXTVLCOPT (User-Agent, Referer, Cookie)
 * - #KODIPROP (todas las propiedades adaptive/DRM)
 * - catchup, catchup-source, catchup-days, catchup-hours
 * - Atributos desconocidos (se conservan en extraAttributes en lugar de descartarlos)
 * - URL original íntegra
 */

const KNOWN_ATTRS = new Set([
  'tvg-id', 'tvg-name', 'tvg-logo', 'tvg-country', 'tvg-language', 'tvg-rec', 'tvg-shift',
  'group-title', 'catchup', 'catchup-days', 'catchup-hours', 'catchup-source',
  'tv-archive', 'tv-archive-duration', 'radio'
]);

/**
 * FASE 25: Umbrales y límites razonables para listas extremadamente grandes.
 */
export const M3U_LIMITS = {
  // 50 MB límite recomendado para release 1 en browser
  MAX_RECOMMENDED_SIZE_BYTES: 50 * 1024 * 1024,
  // 100 MB límite estricto de seguridad para evitar OOM crash del navegador
  CRITICAL_SIZE_LIMIT_BYTES: 100 * 1024 * 1024,
  // 50,000 entradas recomendadas en hilo principal
  MAX_RECOMMENDED_ENTRIES: 50000,
  // 100,000 entradas umbral crítico (roadmap: streaming parser / worker)
  CRITICAL_ENTRIES_LIMIT: 100000,
  // Límite por defecto para descargas automáticas (50 MB)
  DEFAULT_FETCH_MAX_BYTES: 50 * 1024 * 1024
};

/**
 * Parsea el valor de los atributos de una línea #EXTINF o #EXTM3U.
 * Soporta comillas dobles con comillas simples internas: "foo 'bar' baz"
 * y comillas simples con comillas dobles internas: 'foo "bar" baz'.
 * @param {string} attrString
 * @returns {Record<string, string>}
 */
export function parseAttributes(attrString) {
  const attrs = {};
  if (!attrString) return attrs;

  const regex = /([a-zA-Z0-9_\-]+)=(?:"([^"]*)"|'([^']*)'|([^\s"']+))/g;
  let match;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1].toLowerCase();
    const val = match[2] !== undefined ? match[2] : (match[3] !== undefined ? match[3] : match[4]);
    attrs[key] = val;
  }

  return attrs;
}

export class M3uParserAdapter {
  /**
   * Instancia externa de parser configurada (opcional).
   * @type {any}
   */
  static _customParser = null;

  /**
   * Obtiene los límites de configuración actuales.
   * @returns {typeof M3U_LIMITS}
   */
  static getLimits() {
    return { ...M3U_LIMITS };
  }

  /**
   * Permite inyectar una librería externa específica (ej. iptv-m3u-playlist-parser).
   * @param {any} parserInstance
   */
  static setExternalParser(parserInstance) {
    this._customParser = parserInstance;
  }

  /**
   * Obtiene el parser externo disponible (inyectado, en window o en globalThis).
   * @returns {any|null}
   */
  static getExternalParser() {
    if (this._customParser) return this._customParser;
    if (typeof window !== 'undefined' && window.iptvPlaylistParser) {
      return window.iptvPlaylistParser;
    }
    if (typeof globalThis !== 'undefined' && globalThis.iptvPlaylistParser) {
      return globalThis.iptvPlaylistParser;
    }
    return null;
  }

  /**
   * Parsea un manifiesto M3U devolviendo el modelo externo estandarizado.
   * Si una librería externa (como iptv-m3u-playlist-parser) está disponible, la invoca;
   * si no, ejecuta el motor nativo interno garantizando compatibilidad 100%.
   * 
   * @param {string} content - Contenido en texto plano del archivo M3U
   * @param {any} [overrideParser] - Parser opcional para pruebas o inyección ad-hoc
   * @param {Object} [options] - Opciones adicionales (ej. maxEntries)
   * @param {number} [options.maxEntries] - Cantidad máxima de entradas a parsear
   * @returns {import('./PlaylistTypes.js').ExternalM3uPlaylist}
   */
  static parse(content, overrideParser = null, options = {}) {
    if (!content || typeof content !== 'string') {
      return {
        header: { attrs: {}, raw: '' },
        items: []
      };
    }

    const maxEntries = typeof options?.maxEntries === 'number' ? options.maxEntries : Infinity;
    const parser = overrideParser || this.getExternalParser();

    // 1. Si existe una librería externa configurada (ej. iptv-m3u-playlist-parser)
    if (parser && (typeof parser.parse === 'function' || typeof parser.parsePlaylist === 'function')) {
      try {
        const parseFn = typeof parser.parsePlaylist === 'function' ? parser.parsePlaylist : parser.parse;
        const rawResult = parseFn.call(parser, content);
        const adapted = this._adaptExternalOutput(rawResult);
        if (maxEntries < adapted.items.length) {
          adapted.items = adapted.items.slice(0, maxEntries);
        }
        return adapted;
      } catch (err) {
        console.warn('[M3uParserAdapter] Error en parser externo, usando motor fallback nativo:', err);
      }
    }

    // 2. Motor nativo de M3uParserAdapter (robusto, preservación completa y sin dependencias)
    return this._parseInternal(content, maxEntries);
  }

  /**
   * FASE 25 — Diagnóstico y Medición de Rendimiento
   * Mide tamaño, número de entradas, tiempo de parse y huella de memoria.
   * Permite evaluar si una playlist entra cómodamente o si requiere Web Worker en la Etapa 2.
   * 
   * @param {string} content - Contenido M3U a evaluar
   * @param {Object} [options]
   * @param {number} [options.maxEntries] - Límite opcional de entradas
   * @param {boolean} [options.executeParse=true] - Si ejecuta el parse completo o conteo rápido
   * @param {boolean} [options.includePlaylist=false] - Si incluye el objeto parseado en el resultado
   * @returns {import('./PlaylistTypes.js').M3uPerformanceMetrics}
   */
  static measureM3u(content, options = {}) {
    if (!content || typeof content !== 'string') {
      return {
        sizeBytes: 0,
        sizeMB: '0.00',
        entryCount: 0,
        parseTimeMs: 0,
        throughputMBps: '0.00',
        entriesPerSecond: 0,
        memoryEstimate: {
          jsHeapUsedMB: null,
          estimatedObjectMemoryMB: 0
        },
        status: 'OPTIMAL',
        recommendation: 'Contenido vacío.'
      };
    }

    // 1. Medir tamaño exacto en bytes
    const sizeBytes = typeof Blob !== 'undefined' 
      ? new Blob([content]).size 
      : (typeof Buffer !== 'undefined' ? Buffer.byteLength(content, 'utf8') : content.length);
    const sizeMB = (sizeBytes / (1024 * 1024)).toFixed(2);

    // 2. Medir memoria heap previa (si está disponible en Chromium)
    const initialHeap = typeof performance !== 'undefined' && performance.memory 
      ? performance.memory.usedJSHeapSize 
      : null;

    // 3. Medir tiempo de parse
    const t0 = typeof performance !== 'undefined' ? performance.now() : Date.now();
    let playlist = null;
    if (options.executeParse !== false) {
      playlist = this.parse(content, null, options);
    }
    const t1 = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const parseTimeMs = Math.max(0.01, Number((t1 - t0).toFixed(2)));

    // 4. Conteo de entradas
    const entryCount = playlist ? playlist.items.length : (content.match(/#EXTINF:/g) || []).length;

    // 5. Cálculo de throughput
    const timeSec = parseTimeMs / 1000;
    const throughputMBps = timeSec > 0 ? ((sizeBytes / (1024 * 1024)) / timeSec).toFixed(2) : '0.00';
    const entriesPerSecond = timeSec > 0 ? Math.round(entryCount / timeSec) : 0;

    // 6. Memoria heap posterior y estimación de huella de objetos (~420 bytes por item en V8)
    const finalHeap = typeof performance !== 'undefined' && performance.memory 
      ? performance.memory.usedJSHeapSize 
      : null;
    const jsHeapUsedMB = finalHeap ? Number((finalHeap / (1024 * 1024)).toFixed(2)) : null;
    const estimatedObjectMemoryMB = Number(((entryCount * 420) / (1024 * 1024)).toFixed(2));

    // 7. Evaluación de Estado y Límites Razonables (FASE 25)
    let status = 'OPTIMAL';
    let recommendation = '';

    const isCriticalSize = sizeBytes > M3U_LIMITS.CRITICAL_SIZE_LIMIT_BYTES;
    const isCriticalEntries = entryCount > M3U_LIMITS.CRITICAL_ENTRIES_LIMIT;
    const isWarnSize = sizeBytes > M3U_LIMITS.MAX_RECOMMENDED_SIZE_BYTES;
    const isWarnEntries = entryCount > M3U_LIMITS.MAX_RECOMMENDED_ENTRIES;

    if (isCriticalSize || isCriticalEntries) {
      status = 'CRITICAL_OVERSIZED';
      recommendation = `⚠️ LISTA CRÍTICA: ${entryCount.toLocaleString()} entradas (${sizeMB} MB). Excede límites del primer release. Se requiere Web Worker o streaming parser server-side (Etapa 2) para no congelar la UI.`;
    } else if (isWarnSize || isWarnEntries) {
      status = 'LARGE_WARNING';
      recommendation = `⚡ LISTA GRANDE: ${entryCount.toLocaleString()} entradas (${sizeMB} MB). Funciona en el primer release pero está en el límite recomendado. Recomendado Web Worker en próxima etapa.`;
    } else if (sizeBytes > 15 * 1024 * 1024 || entryCount > 15000) {
      status = 'MODERATE';
      recommendation = `✅ LISTA MODERADA: ${entryCount.toLocaleString()} entradas (${sizeMB} MB). Procesamiento óptimo con virtualización activa.`;
    } else {
      status = 'OPTIMAL';
      recommendation = `🚀 LISTA ÓPTIMA: ${entryCount.toLocaleString()} entradas (${sizeMB} MB). Rendimiento instantáneo sin impacto perceptible en memoria.`;
    }

    return {
      sizeBytes,
      sizeMB,
      entryCount,
      parseTimeMs,
      throughputMBps,
      entriesPerSecond,
      memoryEstimate: {
        jsHeapUsedMB,
        estimatedObjectMemoryMB
      },
      status,
      recommendation,
      playlist: options.includePlaylist ? playlist : undefined
    };
  }

  /**
   * Parsea el contenido M3U midiendo métricas de rendimiento y memoria.
   * Emite diagnósticos en consola si la lista excede los umbrales recomendados.
   * 
   * @param {string} content
   * @param {Object} [options]
   * @returns {{ playlist: import('./PlaylistTypes.js').ExternalM3uPlaylist, metrics: import('./PlaylistTypes.js').M3uPerformanceMetrics }}
   */
  static parseWithMetrics(content, options = {}) {
    const metricsResult = this.measureM3u(content, {
      ...options,
      executeParse: true,
      includePlaylist: true
    });

    const playlist = metricsResult.playlist || { header: { attrs: {} }, items: [] };
    delete metricsResult.playlist;

    if (metricsResult.status === 'CRITICAL_OVERSIZED' || metricsResult.status === 'LARGE_WARNING') {
      console.warn(`[M3uParserAdapter] ${metricsResult.recommendation}`);
    }

    return {
      playlist,
      metrics: metricsResult
    };
  }

  /**
   * FASE 25 — Descarga Segura y Controlada de M3U
   * Evita 'fetch().text()' descontrolado de cientos de MB inspeccionando
   * Content-Length y leyendo vía ReadableStream con límite de bytes abortable.
   * 
   * @param {string} url - URL del archivo M3U a descargar
   * @param {Object} [options]
   * @param {number} [options.maxSizeBytes=52428800] - Límite de seguridad en bytes (default: 50MB)
   * @param {number} [options.timeoutMs=30000] - Timeout en milisegundos
   * @param {HeadersInit} [options.headers] - Cabeceras HTTP adicionales
   * @param {AbortSignal} [options.signal] - Señal de cancelación externa
   * @returns {Promise<{ content: string, sizeBytes: number, metrics: import('./PlaylistTypes.js').M3uPerformanceMetrics }>}
   */
  static async safeFetchM3U(url, options = {}) {
    if (!url || typeof url !== 'string') {
      throw new Error('[M3uParserAdapter.safeFetchM3U] URL inválida');
    }

    const maxSizeBytes = options.maxSizeBytes || M3U_LIMITS.MAX_RECOMMENDED_SIZE_BYTES;
    const timeoutMs = options.timeoutMs || 30000;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(new Error(`Timeout de descarga (${timeoutMs}ms)`)), timeoutMs);

    if (options.signal) {
      options.signal.addEventListener('abort', () => controller.abort(options.signal.reason));
    }

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*',
          ...(options.headers || {})
        },
        signal: controller.signal
      });

      if (!response.ok) {
        throw new Error(`Error HTTP ${response.status}: ${response.statusText}`);
      }

      // 1. Verificación previa por cabecera Content-Length antes de descargar cuerpo
      const contentLengthHeader = response.headers.get('content-length');
      if (contentLengthHeader) {
        const contentLength = parseInt(contentLengthHeader, 10);
        if (!isNaN(contentLength) && contentLength > maxSizeBytes) {
          controller.abort();
          throw new Error(
            `[Lelouch SafeFetch] M3U rechazada: El tamaño de la lista (${(contentLength / (1024 * 1024)).toFixed(2)} MB) excede el límite seguro permitido de ${(maxSizeBytes / (1024 * 1024)).toFixed(0)} MB. Se requiere procesamiento en background o streaming.`
          );
        }
      }

      // 2. Lectura controlada con ReadableStream para no permitir descargas infinitas
      let content = '';
      let receivedBytes = 0;

      if (response.body && typeof response.body.getReader === 'function') {
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          receivedBytes += value.byteLength;
          if (receivedBytes > maxSizeBytes) {
            await reader.cancel();
            controller.abort();
            throw new Error(
              `[Lelouch SafeFetch] Descarga abortada en caliente: El archivo superó el límite de ${(maxSizeBytes / (1024 * 1024)).toFixed(0)} MB durante la transferencia streaming.`
            );
          }

          content += decoder.decode(value, { stream: true });
        }
        content += decoder.decode();
      } else {
        // Fallback para entornos sin streams de respuesta
        content = await response.text();
        receivedBytes = typeof Blob !== 'undefined' ? new Blob([content]).size : content.length;
        if (receivedBytes > maxSizeBytes) {
          throw new Error(
            `[Lelouch SafeFetch] Contenido descargado excede el límite de ${(maxSizeBytes / (1024 * 1024)).toFixed(0)} MB.`
          );
        }
      }

      clearTimeout(timer);

      // Medir rendimiento y métricas del contenido descargado
      const metrics = this.measureM3u(content, { executeParse: false });
      return {
        content,
        sizeBytes: receivedBytes,
        metrics
      };
    } catch (err) {
      clearTimeout(timer);
      throw err;
    }
  }

  /**
   * Adapta la salida heterogénea de bibliotecas de terceros al contrato ExternalM3uPlaylist.
   * Preserva atributos no reconocidos en extraAttributes para no descartar nada.
   * @private
   */
  static _adaptExternalOutput(raw) {
    if (!raw) return { header: { attrs: {} }, items: [] };

    const header = {
      attrs: raw.header?.attrs || {},
      raw: raw.header?.raw || ''
    };


    const items = (raw.items || []).map(item => {
      // Extraer y preservar atributos desconocidos
      const rawAttrs = item.attrs || item.attributes || {};
      const extraAttributes = {};
      for (const [k, v] of Object.entries(rawAttrs)) {
        if ((!KNOWN_ATTRS.has(k.toLowerCase()) || k.toLowerCase() === 'radio') && v !== undefined && v !== null) {
          extraAttributes[k] = String(v);
        }
      }

      return {
        name: item.name || '',
        url: item.url || '',
        duration: typeof item.duration === 'number' ? item.duration : -1,
        tvg: {
          id: item.tvg?.id || null,
          name: item.tvg?.name || null,
          logo: item.tvg?.logo || null,
          country: item.tvg?.country || null,
          language: item.tvg?.language || null,
          rec: item.tvg?.rec || null,
          shift: item.tvg?.shift || null,
        },
        group: {
          title: (Array.isArray(item.group) ? item.group[0] : item.group?.title) || null
        },
        http: {
          referrer: item.http?.referrer || item.http?.['referrer'] || item.http?.['Referer'] || item.http?.['http-referrer'] || null,
          userAgent: item.http?.userAgent || item.http?.['user-agent'] || item.http?.['User-Agent'] || item.http?.['http-user-agent'] || null,
          cookie: item.http?.cookie || item.http?.['Cookie'] || item.http?.['http-cookie'] || null,
        },
        catchup: item.catchup ? {
          type: item.catchup.type || null,
          days: item.catchup.days || null,
          hours: item.catchup.hours || null,
          source: item.catchup.source || null,
        } : null,
        kodiProps: item.kodiProps || {},
        extraAttributes: Object.keys(extraAttributes).length > 0 ? extraAttributes : null,
        raw: item.raw || ''
      };
    });

    return { header, items };
  }

  /**
   * Motor nativo de parseo optimizado para LELOUCH.
   * Procesa streaming de líneas sin descartar ningún metadato.
   * @private
   */
  static _parseInternal(content, maxEntries = Infinity) {
    const lines = content.split(/\r?\n/);
    const header = { attrs: {}, raw: '' };
    const items = [];

    let currentEntry = null;

    for (let i = 0; i < lines.length; i++) {
      if (items.length >= maxEntries) {
        break;
      }
      const line = lines[i].trim();
      if (!line) continue;

      // Cabecera #EXTM3U
      if (line.startsWith('#EXTM3U')) {
        header.raw = line;
        const attrStr = line.slice(7).trim();
        header.attrs = parseAttributes(attrStr);
        continue;
      }

      // Directiva #EXTINF
      if (line.startsWith('#EXTINF:')) {
        const colonIndex = line.indexOf(':');
        const commaIndex = line.lastIndexOf(',');

        let duration = -1;
        let attrStr = '';
        let title = 'Canal sin nombre';

        if (commaIndex > colonIndex) {
          const beforeComma = line.slice(colonIndex + 1, commaIndex).trim();
          title = line.slice(commaIndex + 1).trim();

          const firstSpace = beforeComma.indexOf(' ');
          if (firstSpace !== -1) {
            duration = parseFloat(beforeComma.slice(0, firstSpace)) || -1;
            attrStr = beforeComma.slice(firstSpace + 1).trim();
          } else {
            duration = parseFloat(beforeComma) || -1;
          }
        } else {
          const afterColon = line.slice(colonIndex + 1).trim();
          const firstSpace = afterColon.indexOf(' ');
          if (firstSpace !== -1) {
            duration = parseFloat(afterColon.slice(0, firstSpace)) || -1;
            attrStr = afterColon.slice(firstSpace + 1).trim();
          } else {
            duration = parseFloat(afterColon) || -1;
          }
        }

        const attributes = parseAttributes(attrStr);

        // Identificar y almacenar atributos desconocidos y directivas especiales (ej. radio)
        const extraAttributes = {};
        for (const [k, v] of Object.entries(attributes)) {
          if (!KNOWN_ATTRS.has(k.toLowerCase()) || k.toLowerCase() === 'radio') {
            extraAttributes[k] = v;
          }
        }

        const catchupDays = attributes['catchup-days'] || attributes['tv-archive-duration'] || null;
        const catchupHours = attributes['catchup-hours'] || null;
        const catchupType = attributes['catchup'] || (attributes['tv-archive'] === '1' ? 'default' : null);
        const catchupSource = attributes['catchup-source'] || null;
        const hasCatchup = !!(catchupType || catchupDays || catchupHours || catchupSource);

        currentEntry = {
          name: title,
          url: '',
          duration,
          tvg: {
            id: attributes['tvg-id'] || null,
            name: attributes['tvg-name'] || null,
            logo: attributes['tvg-logo'] || null,
            country: attributes['tvg-country'] || null,
            language: attributes['tvg-language'] || null,
            rec: attributes['tvg-rec'] || null,
            shift: attributes['tvg-shift'] || null,
          },
          group: {
            title: attributes['group-title'] || null
          },
          http: {
            referrer: null,
            userAgent: null,
            cookie: null
          },
          catchup: hasCatchup ? {
            type: catchupType || 'default',
            days: catchupDays ? parseInt(String(catchupDays), 10) : null,
            hours: catchupHours ? parseInt(String(catchupHours), 10) : null,
            source: catchupSource
          } : null,
          kodiProps: {},
          extraAttributes: Object.keys(extraAttributes).length > 0 ? extraAttributes : null,
          raw: line
        };
        continue;
      }

      if (!currentEntry) continue;

      // Directiva #EXTGRP: (override o fallback de grupo)
      if (line.startsWith('#EXTGRP:')) {
        const grp = line.slice(8).trim();
        if (grp) currentEntry.group.title = grp;
        continue;
      }

      // Directiva #EXTVLCOPT:
      if (line.startsWith('#EXTVLCOPT:')) {
        const opt = line.slice(11).trim();
        const eqIdx = opt.indexOf('=');
        if (eqIdx !== -1) {
          const optKey = opt.slice(0, eqIdx).trim().toLowerCase();
          const optVal = opt.slice(eqIdx + 1).trim();
          if (optKey === 'http-user-agent' || optKey === 'user-agent') currentEntry.http.userAgent = optVal;
          if (optKey === 'http-referrer' || optKey === 'http-referer' || optKey === 'referer') currentEntry.http.referrer = optVal;
          if (optKey === 'http-cookie' || optKey === 'cookie') currentEntry.http.cookie = optVal;
        }
        continue;
      }

      // Directiva #KODIPROP:
      if (line.startsWith('#KODIPROP:')) {
        const prop = line.slice(10).trim();
        const eqIdx = prop.indexOf('=');
        if (eqIdx !== -1) {
          const propKey = prop.slice(0, eqIdx).trim();
          const propVal = prop.slice(eqIdx + 1).trim();
          if (!currentEntry.kodiProps) {
            currentEntry.kodiProps = {};
          }
          currentEntry.kodiProps[propKey] = propVal;
        }
        continue;
      }

      if (line.startsWith('#')) continue;

      // URL del stream original
      currentEntry.url = line;
      items.push(currentEntry);
      currentEntry = null;
    }

    return { header, items };
  }

  /**
   * Extrae credenciales Xtream de una URL.
   * @param {string} url
   */
  static extractXtreamInfo(url) {
    if (!url || typeof url !== 'string') return { isXtream: false };

    try {
      const parsed = new URL(url.trim());
      const path = (parsed.pathname || '').replace(/\/+$/, '');

      const pathMatch = path.match(/^\/(live|movie|series)\/([^\/]+)\/([^\/]+)\/(\d+)(?:\.([a-z0-9]+))?$/i);
      if (pathMatch) {
        return {
          isXtream: true,
          serverBaseUrl: parsed.origin,
          type: pathMatch[1].toLowerCase(),
          username: decodeURIComponent(pathMatch[2]),
          password: decodeURIComponent(pathMatch[3]),
          streamId: pathMatch[4],
          extension: pathMatch[5] || (pathMatch[1].toLowerCase() === 'live' ? 'm3u8' : 'mp4')
        };
      }

      const username = parsed.searchParams.get('username');
      const password = parsed.searchParams.get('password');
      if (username && password) {
        return {
          isXtream: true,
          serverBaseUrl: parsed.origin,
          type: 'xtream_m3u',
          username,
          password
        };
      }
    } catch {}

    return { isXtream: false };
  }

  /**
   * Helper opcional para comprobar rápidamente si una URL es de tipo Xtream.
   * @param {string} url
   * @returns {boolean}
   */
  static isXtreamUrl(url) {
    return this.extractXtreamInfo(url).isXtream;
  }

  /**
   * Helper opcional para generar una URL de descarga M3U desde credenciales Xtream.
   * @param {string} serverBaseUrl
   * @param {string} username
   * @param {string} password
   * @param {{ type?: string, output?: string }} [options]
   * @returns {string}
   */
  static buildXtreamM3uUrl(serverBaseUrl, username, password, options = {}) {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const type = options.type || 'm3u_plus';
    const output = options.output || 'm3u8';
    return `${base}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=${type}&output=${output}`;
  }

  /**
   * Helper opcional para construir URLs de timeshift/catchup estándar de Xtream Codes.
   * @param {string} serverBaseUrl
   * @param {string} username
   * @param {string} password
   * @param {string} streamId
   * @param {number} [durationMinutes]
   * @param {number} [startTimestamp]
   * @returns {string}
   */
  static buildXtreamCatchupUrl(
    serverBaseUrl,
    username,
    password,
    streamId,
    durationMinutes = 60,
    startTimestamp = Date.now()
  ) {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const dateObj = new Date(startTimestamp);
    const pad = n => String(n).padStart(2, '0');
    const startStr = `${dateObj.getFullYear()}-${pad(dateObj.getMonth() + 1)}-${pad(dateObj.getDate())}:${pad(dateObj.getHours())}-${pad(dateObj.getMinutes())}`;
    return `${base}/streaming/timeshift.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&stream=${streamId}&duration=${durationMinutes}&start=${startStr}`;
  }
}

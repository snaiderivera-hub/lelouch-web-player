/**
 * @module M3uParserAdapter
 * Adaptador que envuelve librerías externas de M3U (como iptv-m3u-playlist-parser) en TypeScript.
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

import { ExternalM3uPlaylist, ExternalPlaylistItem, M3uPerformanceMetrics, M3uLimitsConfig } from './PlaylistTypes';

const KNOWN_ATTRS = new Set([
  'tvg-id', 'tvg-name', 'tvg-logo', 'tvg-country', 'tvg-language', 'tvg-rec', 'tvg-shift',
  'group-title', 'catchup', 'catchup-days', 'catchup-hours', 'catchup-source',
  'tv-archive', 'tv-archive-duration', 'radio'
]);

/**
 * FASE 25: Umbrales y límites razonables para listas extremadamente grandes.
 */
export const M3U_LIMITS: M3uLimitsConfig = {
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

export function parseAttributes(attrString: string): Record<string, string> {
  const attrs: Record<string, string> = {};
  if (!attrString) return attrs;

  const regex = /([a-zA-Z0-9_\-]+)=(?:"([^"]*)"|'([^']*)'|([^\s"']+))/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1].toLowerCase();
    const val = match[2] !== undefined ? match[2] : (match[3] !== undefined ? match[3] : match[4]);
    attrs[key] = val;
  }

  return attrs;
}

export class M3uParserAdapter {
  private static _customParser: any = null;

  static getLimits(): M3uLimitsConfig {
    return { ...M3U_LIMITS };
  }

  static setExternalParser(parserInstance: any): void {
    this._customParser = parserInstance;
  }

  static getExternalParser(): any | null {
    if (this._customParser) return this._customParser;
    if (typeof window !== 'undefined' && (window as any).iptvPlaylistParser) {
      return (window as any).iptvPlaylistParser;
    }
    if (typeof globalThis !== 'undefined' && (globalThis as any).iptvPlaylistParser) {
      return (globalThis as any).iptvPlaylistParser;
    }
    return null;
  }

  static parse(content: string, overrideParser: any = null, options: { maxEntries?: number } = {}): ExternalM3uPlaylist {
    if (!content || typeof content !== 'string') {
      return {
        header: { attrs: {}, raw: '' },
        items: []
      };
    }

    const maxEntries = typeof options?.maxEntries === 'number' ? options.maxEntries : Infinity;
    const parser = overrideParser || this.getExternalParser();

    if (parser && typeof parser.parse === 'function') {
      try {
        const rawResult = parser.parse(content);
        const adapted = this._adaptExternalOutput(rawResult);
        if (maxEntries < adapted.items.length) {
          adapted.items = adapted.items.slice(0, maxEntries);
        }
        return adapted;
      } catch (err) {
        console.warn('[M3uParserAdapter] Error en parser externo, usando motor fallback nativo:', err);
      }
    }

    return this._parseInternal(content, maxEntries);
  }

  static measureM3u(content: string, options: { maxEntries?: number; executeParse?: boolean; includePlaylist?: boolean } = {}): M3uPerformanceMetrics {
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

    const sizeBytes = typeof Blob !== 'undefined' 
      ? new Blob([content]).size 
      : (typeof Buffer !== 'undefined' ? Buffer.byteLength(content, 'utf8') : content.length);
    const sizeMB = (sizeBytes / (1024 * 1024)).toFixed(2);

    const initialHeap = typeof performance !== 'undefined' && (performance as any).memory 
      ? (performance as any).memory.usedJSHeapSize 
      : null;

    const t0 = typeof performance !== 'undefined' ? performance.now() : Date.now();
    let playlist: ExternalM3uPlaylist | null = null;
    if (options.executeParse !== false) {
      playlist = this.parse(content, null, options);
    }
    const t1 = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const parseTimeMs = Math.max(0.01, Number((t1 - t0).toFixed(2)));

    const entryCount = playlist ? playlist.items.length : (content.match(/#EXTINF:/g) || []).length;
    const timeSec = parseTimeMs / 1000;
    const throughputMBps = timeSec > 0 ? ((sizeBytes / (1024 * 1024)) / timeSec).toFixed(2) : '0.00';
    const entriesPerSecond = timeSec > 0 ? Math.round(entryCount / timeSec) : 0;

    const finalHeap = typeof performance !== 'undefined' && (performance as any).memory 
      ? (performance as any).memory.usedJSHeapSize 
      : null;
    const jsHeapUsedMB = finalHeap ? Number((finalHeap / (1024 * 1024)).toFixed(2)) : null;
    const estimatedObjectMemoryMB = Number(((entryCount * 420) / (1024 * 1024)).toFixed(2));

    let status: 'OPTIMAL' | 'MODERATE' | 'LARGE_WARNING' | 'CRITICAL_OVERSIZED' = 'OPTIMAL';
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
      playlist: options.includePlaylist && playlist ? playlist : undefined
    };
  }

  static parseWithMetrics(content: string, options: { maxEntries?: number } = {}): { playlist: ExternalM3uPlaylist; metrics: M3uPerformanceMetrics } {
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

    return { playlist, metrics: metricsResult };
  }

  static async safeFetchM3U(url: string, options: { maxSizeBytes?: number; timeoutMs?: number; headers?: Record<string, string>; signal?: AbortSignal } = {}): Promise<{ content: string; sizeBytes: number; metrics: M3uPerformanceMetrics }> {
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

      let content = '';
      let receivedBytes = 0;

      if (response.body && typeof (response.body as any).getReader === 'function') {
        const reader = (response.body as any).getReader();
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
        content = await response.text();
        receivedBytes = typeof Blob !== 'undefined' ? new Blob([content]).size : content.length;
        if (receivedBytes > maxSizeBytes) {
          throw new Error(
            `[Lelouch SafeFetch] Contenido descargado excede el límite de ${(maxSizeBytes / (1024 * 1024)).toFixed(0)} MB.`
          );
        }
      }

      clearTimeout(timer);
      const metrics = this.measureM3u(content, { executeParse: false });
      return { content, sizeBytes: receivedBytes, metrics };
    } catch (err) {
      clearTimeout(timer);
      throw err;
    }
  }


  private static _adaptExternalOutput(raw: any): ExternalM3uPlaylist {
    if (!raw) return { header: { attrs: {} }, items: [] };

    const header = {
      attrs: raw.header?.attrs || {},
      raw: raw.header?.raw || ''
    };

    const items: ExternalPlaylistItem[] = (raw.items || []).map((item: any) => {
      // Extraer y preservar atributos desconocidos
      const rawAttrs = item.attrs || item.attributes || {};
      const extraAttributes: Record<string, string> = {};
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
          title: item.group?.title || null
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

  private static _parseInternal(content: string, maxEntries: number = Infinity): ExternalM3uPlaylist {
    const lines = content.split(/\r?\n/);
    const header = { attrs: {}, raw: '' };
    const items: ExternalPlaylistItem[] = [];

    let currentEntry: ExternalPlaylistItem | null = null;

    for (let i = 0; i < lines.length; i++) {
      if (items.length >= maxEntries) {
        break;
      }
      const line = lines[i].trim();
      if (!line) continue;

      if (line.startsWith('#EXTM3U')) {
        header.raw = line;
        const attrStr = line.slice(7).trim();
        header.attrs = parseAttributes(attrStr);
        continue;
      }

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
        const extraAttributes: Record<string, string> = {};
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

      if (line.startsWith('#EXTGRP:')) {
        const grp = line.slice(8).trim();
        if (grp) currentEntry.group.title = grp;
        continue;
      }

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

      currentEntry.url = line;
      items.push(currentEntry);
      currentEntry = null;
    }

    return { header, items };
  }

  static extractXtreamInfo(url: string): {
    isXtream: boolean;
    serverBaseUrl?: string;
    username?: string;
    password?: string;
    streamId?: string;
    type?: string;
    extension?: string;
  } {
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
   */
  static isXtreamUrl(url: string): boolean {
    return this.extractXtreamInfo(url).isXtream;
  }

  /**
   * Helper opcional para generar una URL de descarga M3U desde credenciales Xtream.
   */
  static buildXtreamM3uUrl(
    serverBaseUrl: string,
    username: string,
    password: string,
    options: { type?: string; output?: string } = {}
  ): string {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const type = options.type || 'm3u_plus';
    const output = options.output || 'm3u8';
    return `${base}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=${type}&output=${output}`;
  }

  /**
   * Helper opcional para construir URLs de timeshift/catchup estándar de Xtream Codes.
   */
  static buildXtreamCatchupUrl(
    serverBaseUrl: string,
    username: string,
    password: string,
    streamId: string,
    durationMinutes: number = 60,
    startTimestamp: number = Date.now()
  ): string {
    const base = serverBaseUrl.replace(/\/+$/, '');
    const dateObj = new Date(startTimestamp);
    const pad = (n: number) => String(n).padStart(2, '0');
    const startStr = `${dateObj.getFullYear()}-${pad(dateObj.getMonth() + 1)}-${pad(dateObj.getDate())}:${pad(dateObj.getHours())}-${pad(dateObj.getMinutes())}`;
    return `${base}/streaming/timeshift.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&stream=${streamId}&duration=${durationMinutes}&start=${startStr}`;
  }
}


/**
 * @module UrlParser
 * Parser robusto de URLs IPTV usando la API nativa URL del navegador.
 * Nunca usa split("&") manual.
 * Las contraseñas siempre se enmascaran en los objetos de salida.
 */

import { SourceType } from '../types/iptv.types.js';

/**
 * Parsea una URL IPTV y detecta su tipo de fuente.
 * @param {string} rawUrl
 * @returns {import('../types/iptv.types.js').ParsedIPTVUrl}
 * @throws {Error} Si la URL es inválida o no se puede parsear
 */
export function parseIPTVUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') {
    throw new Error('URL inválida o vacía.');
  }

  const trimmed = rawUrl.trim();

  let url;
  try {
    url = new URL(trimmed);
  } catch {
    throw new Error(`La URL proporcionada no tiene formato válido: "${trimmed}"`);
  }

  const params = {};
  for (const [key, value] of url.searchParams.entries()) {
    params[key] = value;
  }

  const username = params['username'] || '';
  const password = params['password'] || '';
  const port = url.port || (url.protocol === 'https:' ? '443' : '80');

  const serverBaseUrl = url.port
    ? `${url.protocol}//${url.hostname}:${url.port}`
    : `${url.protocol}//${url.hostname}`;

  const sourceType = detectSourceType(url.pathname, params, trimmed);

  return {
    rawUrl: trimmed,
    sourceType,
    protocol: url.protocol.replace(':', ''),
    hostname: url.hostname,
    port,
    serverBaseUrl,
    username,
    maskedPassword: password ? '********' : '',
    _password: password,  // privado, solo para uso interno del adapter
    pathname: url.pathname,
    params,
  };
}

/**
 * Detecta el tipo de fuente basado en la URL.
 * @param {string} pathname
 * @param {Object} params
 * @param {string} rawUrl
 * @returns {string} SourceType
 */
function detectSourceType(pathname, params, rawUrl) {
  const path = pathname.toLowerCase();
  const hasCredentials = params['username'] && params['password'];

  if (path.includes('/player_api.php')) {
    return SourceType.XTREAM_API;
  }

  if (path.includes('/get.php') && hasCredentials) {
    // Podría ser Xtream M3U — se verificará en el adapter
    return SourceType.XTREAM_M3U;
  }

  if (path.includes('/xmltv.php')) {
    return SourceType.XMLTV;
  }

  if (rawUrl.endsWith('.m3u') || rawUrl.endsWith('.m3u8') || params['type'] === 'm3u_plus') {
    return hasCredentials ? SourceType.XTREAM_M3U : SourceType.M3U_REMOTE;
  }

  if (path.includes('/api/') || path.includes('.json')) {
    return SourceType.JSON_API;
  }

  return SourceType.UNKNOWN;
}

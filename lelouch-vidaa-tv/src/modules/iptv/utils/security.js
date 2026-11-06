/**
 * @module security
 * Utilidades de seguridad para el manejo de credenciales IPTV.
 * Las credenciales nunca se imprimen completas en logs.
 */

/**
 * Enmascara una contraseña para su uso en logs o UI.
 * @param {string} password
 * @returns {string}
 */
export function maskPassword(password) {
  if (!password) return '';
  return '********';
}

/**
 * Enmascara parcialmente un nombre de usuario.
 * Muestra los primeros 3 caracteres y enmascara el resto.
 * @param {string} username
 * @returns {string}
 */
export function maskUsername(username) {
  if (!username) return '';
  if (username.length <= 3) return username;
  return username.slice(0, 3) + '*'.repeat(Math.min(username.length - 3, 5));
}

/**
 * Construye una URL de API sin credenciales visibles (para logs).
 * @param {string} baseUrl
 * @param {string} action
 * @returns {string}
 */
export function buildSafeLogUrl(baseUrl, action) {
  return `${baseUrl}/player_api.php?username=[REDACTED]&password=[REDACTED]${action ? `&action=${action}` : ''}`;
}

/**
 * Construye la URL real de la API Xtream con credenciales.
 * NUNCA usar en logs — solo para uso interno de los adapters.
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @param {string|null} action
 * @returns {string}
 */
export function buildApiUrl(serverBaseUrl, username, password, action = null) {
  let url = `${serverBaseUrl}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
  if (action) url += `&action=${action}`;
  return url;
}

function formatPathCredential(val) {
  return encodeURIComponent(String(val ?? '')).replace(/%40/g, '@');
}

/**
 * Construye la URL de stream para un canal live.
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @param {string|number} streamId
 * @param {string} extension
 * @returns {string}
 */
export function buildLiveStreamUrl(serverBaseUrl, username, password, streamId, extension = 'm3u8') {
  return `${serverBaseUrl}/live/${formatPathCredential(username)}/${formatPathCredential(password)}/${streamId}.${extension}`;
}

/**
 * Construye la URL de stream para una película VOD.
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @param {string|number} streamId
 * @param {string} extension
 * @returns {string}
 */
export function buildVodStreamUrl(serverBaseUrl, username, password, streamId, extension = 'mp4') {
  return `${serverBaseUrl}/movie/${formatPathCredential(username)}/${formatPathCredential(password)}/${streamId}.${extension}`;
}

/**
 * Construye la URL de stream para un episodio de serie.
 * @param {string} serverBaseUrl
 * @param {string} username
 * @param {string} password
 * @param {string|number} streamId
 * @param {string} extension
 * @returns {string}
 */
export function buildSeriesStreamUrl(serverBaseUrl, username, password, streamId, extension = 'mp4') {
  return `${serverBaseUrl}/series/${formatPathCredential(username)}/${formatPathCredential(password)}/${streamId}.${extension}`;
}

/**
 * FASE 19 — Función Central de Redacción de Credenciales y URLs Sensibles.
 * Convierte username=AAAA y password=BBBB en username=*** y password=***,
 * además de enmascarar URLs estilo Xtream (/live/USER/PASS/id.m3u8, get.php, etc.)
 * antes de cualquier console.log, Vercel log, error message o debug HUD.
 * 
 * @param {string|any} input URL o texto con posibles credenciales sensibles
 * @returns {string} URL o texto con credenciales redactadas
 */
export function redactSensitiveUrl(input) {
  if (!input) return '';
  let str = typeof input === 'string' ? input : String(input);

  // 1. Redactar parámetros query sensibles: username, password, pass, user, token
  str = str.replace(/([?&](?:username|user|usr)=)[^& \n\r\t"']+/gi, '$1***');
  str = str.replace(/([?&](?:password|pass|pwd)=)[^& \n\r\t"']+/gi, '$1***');
  str = str.replace(/([?&](?:token|auth|secret)=)[^& \n\r\t"']+/gi, '$1***');

  // 2. Redactar rutas Xtream: /live/USER/PASS/..., /movie/USER/PASS/..., /series/USER/PASS/...
  str = str.replace(/(\/(?:live|movie|series)\/)[^/ \n\r\t"']+\/[^/ \n\r\t"']+(\/[^ \n\r\t"']*)/gi, '$1***/***$2');

  // 3. Redactar peticiones get.php completas
  str = str.replace(/(get\.php\?[^ \n\r\t"']+)/gi, (match) => {
    return match
      .replace(/([?&](?:username|user|usr)=)[^&]+/gi, '$1***')
      .replace(/([?&](?:password|pass|pwd)=)[^&]+/gi, '$1***');
  });

  return str;
}

/**
 * Redacta cualquier objeto, error o texto arbitrario antes de logging.
 * @param {any} item
 * @returns {any}
 */
export function redactSensitiveText(item) {
  if (item === null || item === undefined) return item;
  if (typeof item === 'string') return redactSensitiveUrl(item);
  if (item instanceof Error) {
    const cloned = new Error(redactSensitiveUrl(item.message));
    if (item.stack) cloned.stack = redactSensitiveUrl(item.stack);
    return cloned;
  }
  if (typeof item === 'object') {
    try {
      const jsonStr = JSON.stringify(item);
      const redactedJson = redactSensitiveUrl(jsonStr);
      return JSON.parse(redactedJson);
    } catch {
      return item;
    }
  }
  return item;
}

if (typeof window !== 'undefined') {
  window.redactSensitiveUrl = redactSensitiveUrl;
  window.redactSensitiveText = redactSensitiveText;
}

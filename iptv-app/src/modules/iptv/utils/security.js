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
  return `${serverBaseUrl}/live/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.${extension}`;
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
  return `${serverBaseUrl}/movie/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.${extension}`;
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
  return `${serverBaseUrl}/series/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${streamId}.${extension}`;
}

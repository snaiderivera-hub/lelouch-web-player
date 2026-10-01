/**
 * Vercel File-Based Dynamic Route: /api/playlist/[token]
 *
 * Este archivo es el punto de entrada nativo de Vercel para rutas dinámicas.
 * Vercel lo mapea automáticamente a /api/playlist/<cualquier-valor>.
 * El parámetro dinámico está disponible en req.query.token directamente.
 *
 * Re-exporta la misma función handler de /api/playlist.js para mantener
 * una sola fuente de verdad sin duplicar lógica.
 *
 * Rutas resultantes:
 *   /api/playlist/<TOKEN>           → entrega M3U
 *   /api/playlist/version/<TOKEN>   → no aplica aquí (manejado por sub-directorio)
 *   /api/playlist/<TOKEN>?format=json → entrega manifest JSON
 */

export { default, config } from '../playlist.js';

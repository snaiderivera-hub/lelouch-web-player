/**
 * Vercel File-Based Dynamic Route: /api/playlist/manifest/[token]
 *
 * Responde a peticiones de manifest JSON nativo:
 *   /api/playlist/manifest/<TOKEN>  → JSON con { playlist, items[] }
 *
 * Inyecta format=json al query antes de pasar al handler principal.
 */

export { config } from '../../playlist.js';
import handler from '../../playlist.js';

export default async function manifestHandler(req, res) {
  // Inyectar format=json para que el handler principal lo detecte
  req.query = { ...req.query, format: 'json' };
  return handler(req, res);
}

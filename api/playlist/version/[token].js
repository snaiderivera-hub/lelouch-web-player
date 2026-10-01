/**
 * Vercel File-Based Dynamic Route: /api/playlist/version/[token]
 *
 * Responde a peticiones de comprobación de versión ligera:
 *   /api/playlist/version/<TOKEN>  → JSON con { version, updatedAt, inSync }
 *
 * Inyecta format=version al query antes de pasar al handler principal.
 */

export { config } from '../../playlist.js';
import handler from '../../playlist.js';

export default async function versionHandler(req, res) {
  // Inyectar format=version para que el handler principal lo detecte
  req.query = { ...req.query, format: 'version' };
  return handler(req, res);
}

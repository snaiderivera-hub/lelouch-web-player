-- =====================================================================
-- SCRIPT DE CIERRE DE SEGURIDAD SUPABASE (RLS LOCKDOWN)
-- =====================================================================
-- IMPORTANTE:
-- Ejecutar este script en el SQL Editor de Supabase cuando quieras cerrar el acceso
-- público con la llave `anon` a tus tablas sensibles.
-- NOTA (Opción B): Al cerrar estas políticas, el APK público dejará de poder leer
-- automáticamente `playlists` con solo la `anon` key hasta que migres la sincronización
-- a un endpoint autenticado en Vercel (`SUPABASE_SERVICE_ROLE_KEY`).
-- Tu endpoint de Vercel (`/api/playlist/<token>`) seguirá funcionando normalmente
-- si tiene configurada la variable `SUPABASE_SERVICE_ROLE_KEY`.
-- =====================================================================

-- 1. Asegurar RLS habilitado en todas las tablas sensibles
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_health ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;

-- 2. Eliminar políticas abiertas al rol público `anon`
DROP POLICY IF EXISTS "Acceso total playlists anon" ON public.playlists;
DROP POLICY IF EXISTS "playlists_full_access" ON public.playlists;
DROP POLICY IF EXISTS "Acceso total favorites anon" ON public.favorites;
DROP POLICY IF EXISTS "Acceso total watch_history anon" ON public.watch_history;
DROP POLICY IF EXISTS "Acceso total channel_health anon" ON public.channel_health;
DROP POLICY IF EXISTS "Acceso total catalog_cache anon" ON public.catalog_cache;

DROP POLICY IF EXISTS "Acceso total custom_playlists anon" ON public.custom_playlists;
DROP POLICY IF EXISTS "custom_playlists_anon_all" ON public.custom_playlists;
DROP POLICY IF EXISTS "custom_playlists_full_access" ON public.custom_playlists;

DROP POLICY IF EXISTS "Acceso total playlist_items anon" ON public.playlist_items;
DROP POLICY IF EXISTS "playlist_items_anon_all" ON public.playlist_items;
DROP POLICY IF EXISTS "playlist_items_full_access" ON public.playlist_items;

DROP POLICY IF EXISTS "Acceso total playlist_access_tokens anon" ON public.playlist_access_tokens;
DROP POLICY IF EXISTS "tokens_anon_all" ON public.playlist_access_tokens;
DROP POLICY IF EXISTS "tokens_full_access" ON public.playlist_access_tokens;

-- 3. Revocar permisos directos de tablas y vista sensible para el rol `anon`
REVOKE ALL ON TABLE public.playlists FROM anon;
REVOKE ALL ON TABLE public.favorites FROM anon;
REVOKE ALL ON TABLE public.watch_history FROM anon;
REVOKE ALL ON TABLE public.channel_health FROM anon;
REVOKE ALL ON TABLE public.catalog_cache FROM anon;
REVOKE ALL ON TABLE public.custom_playlists FROM anon;
REVOKE ALL ON TABLE public.playlist_items FROM anon;
REVOKE ALL ON TABLE public.playlist_access_tokens FROM anon;
REVOKE ALL ON public.v_resolved_playlist_items FROM anon;

-- 4. Mantener acceso exclusivo de backend para `service_role` (Vercel Serverless)
GRANT ALL ON TABLE public.playlists TO service_role;
GRANT ALL ON TABLE public.favorites TO service_role;
GRANT ALL ON TABLE public.watch_history TO service_role;
GRANT ALL ON TABLE public.channel_health TO service_role;
GRANT ALL ON TABLE public.catalog_cache TO service_role;
GRANT ALL ON TABLE public.custom_playlists TO service_role;
GRANT ALL ON TABLE public.playlist_items TO service_role;
GRANT ALL ON TABLE public.playlist_access_tokens TO service_role;
GRANT SELECT ON public.v_resolved_playlist_items TO service_role;

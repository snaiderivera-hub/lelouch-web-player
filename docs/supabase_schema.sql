-- ====================================================================
-- NEXUS IPTV WEB PLAYER — ESQUEMA COMPLETO PARA SUPABASE (POSTGRESQL)
-- ====================================================================
-- Este script crea todas las tablas para almacenamiento persistente en la nube:
-- 1. playlists: Cuentas y listas IPTV guardadas (evita pérdidas al borrar caché)
-- 2. favorites: Canales, películas y series favoritas
-- 3. watch_history: Historial "Continuar viendo" con tiempos y progreso exacto
-- 4. channel_health: Caché de salud, latencia y clasificación de canales
-- 5. catalog_cache: Respaldo en la nube de catálogos y metadatos
-- ====================================================================

-- 1. TABLA: PLAYLISTS / CUENTAS
CREATE TABLE IF NOT EXISTS public.playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    url TEXT NOT NULL UNIQUE,
    server_url TEXT,
    username TEXT,
    password TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    channels_count INTEGER DEFAULT 0,
    movies_count INTEGER DEFAULT 0,
    series_count INTEGER DEFAULT 0,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. TABLA: FAVORITOS
CREATE TABLE IF NOT EXISTS public.favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('live', 'vod', 'series')),
    title TEXT NOT NULL,
    logo TEXT,
    stream_url TEXT,
    category_name TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_favorite_content UNIQUE (content_id, type)
);

-- 3. TABLA: HISTORIAL DE REPRODUCCIÓN ("CONTINUAR VIENDO")
CREATE TABLE IF NOT EXISTS public.watch_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('live', 'vod', 'series')),
    title TEXT NOT NULL,
    poster TEXT,
    "current_time" NUMERIC DEFAULT 0,
    duration NUMERIC DEFAULT 0,
    stream_url TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_history_content UNIQUE (content_id, type)
);

-- 4. TABLA: SALUD Y CLASIFICACIÓN DE CANALES
CREATE TABLE IF NOT EXISTS public.channel_health (
    channel_id TEXT PRIMARY KEY,
    channel_name TEXT NOT NULL,
    category TEXT,
    quality TEXT,
    genre TEXT,
    country TEXT,
    status TEXT NOT NULL CHECK (status IN ('ONLINE', 'OFFLINE', 'DEGRADED', 'UNKNOWN')),
    latency_ms INTEGER,
    checked_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. TABLA: CACHÉ DE METADATOS Y CATÁLOGO
CREATE TABLE IF NOT EXISTS public.catalog_cache (
    cache_key TEXT PRIMARY KEY,
    data JSONB NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. TABLA: PLAYLISTS PERSONALIZADAS (Creador M3U Dinámico - FASE 6)
CREATE TABLE IF NOT EXISTS public.custom_playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    name TEXT NOT NULL DEFAULT 'Mi Lista LELOUCH',
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. TABLA: ELEMENTOS DE PLAYLIST (Catálogo o Directo - FASE 7)
CREATE TABLE IF NOT EXISTS public.playlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    item_type TEXT NOT NULL DEFAULT 'catalog' CHECK (item_type IN ('catalog', 'direct')),
    source_id UUID REFERENCES public.playlists(id) ON DELETE SET NULL,
    catalog_item_id TEXT,
    media_type TEXT NOT NULL DEFAULT 'live' CHECK (media_type IN ('live', 'movie', 'series', 'radio', 'direct')),
    
    -- Overrides para catálogo
    custom_name TEXT,
    custom_group TEXT,
    custom_logo TEXT,

    -- Campos para canales directos
    direct_name TEXT,
    direct_url TEXT,
    direct_group TEXT,
    direct_logo TEXT,

    tvg_id TEXT,
    tvg_name TEXT,
    container_extension TEXT DEFAULT 'm3u8',
    position INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Si la tabla ya había sido creada previamente sin las columnas de la FASE 7, se añaden automáticamente:
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS item_type TEXT NOT NULL DEFAULT 'catalog';
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_name TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_url TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_group TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_logo TEXT;

-- 8. TABLA: TOKENS DE ACCESO PÚBLICO PERMANENTE (FASE 9: HASH SHA-256)
CREATE TABLE IF NOT EXISTS public.playlist_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    token_prefix TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    request_count BIGINT NOT NULL DEFAULT 0,
    token_preview TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    access_count BIGINT DEFAULT 0,
    name TEXT DEFAULT 'Dispositivo'
);

ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS token_prefix TEXT;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMPTZ;

-- ====================================================================
-- VISTA: RESOLUCIÓN DINÁMICA DE URLs VIVAS
-- ====================================================================
DROP VIEW IF EXISTS public.v_resolved_playlist_items CASCADE;

CREATE OR REPLACE VIEW public.v_resolved_playlist_items AS
SELECT 
    pi.id,
    pi.playlist_id,
    pi.item_type,
    pi.source_id,
    pi.catalog_item_id,
    pi.media_type,
    -- Nombre resuelto (FASE 27: custom_name toma precedencia sobre nombre importado)
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.custom_name, pi.direct_name, 'Canal Directo')
        ELSE COALESCE(pi.custom_name, 'Canal sin nombre')
    END AS name,
    -- Grupo / Categoría resuelta (FASE 27: custom_group toma precedencia sobre grupo importado)
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.custom_group, pi.direct_group, 'Directos')
        ELSE COALESCE(pi.custom_group, 'General')
    END AS "group",
    -- Logo resuelto (FASE 27: custom_logo toma precedencia sobre logo importado)
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.custom_logo, pi.direct_logo)
        ELSE pi.custom_logo
    END AS logo,
    pi.direct_name,
    pi.direct_url,
    pi.direct_group,
    pi.direct_logo,
    pi.tvg_id,
    pi.tvg_name,
    pi.container_extension,
    pi.position,
    pi.enabled,
    pi.metadata,
    p.name AS provider_name,
    p.server_url AS provider_server_url,
    p.is_active AS provider_active,
    CASE 
        WHEN pi.item_type = 'direct' OR pi.source_id IS NULL OR pi.catalog_item_id IS NULL THEN 
            COALESCE(pi.direct_url, '')
        WHEN pi.media_type = 'live' THEN 
            RTRIM(p.server_url, '/') || '/live/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'm3u8')
        WHEN pi.media_type = 'movie' THEN 
            RTRIM(p.server_url, '/') || '/movie/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'mp4')
        WHEN pi.media_type = 'series' THEN 
            RTRIM(p.server_url, '/') || '/series/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'mp4')
        ELSE COALESCE(pi.direct_url, '')
    END AS resolved_stream_url,
    pi.created_at,
    pi.updated_at
FROM public.playlist_items pi
LEFT JOIN public.playlists p ON pi.source_id = p.id;

-- ====================================================================
-- ÍNDICES DE RENDIMIENTO (BÚSQUEDAS INSTANTÁNEAS)
-- ====================================================================
CREATE INDEX IF NOT EXISTS idx_favorites_type ON public.favorites(type);
CREATE INDEX IF NOT EXISTS idx_history_updated ON public.watch_history(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_channel_status ON public.channel_health(status);
CREATE INDEX IF NOT EXISTS idx_channel_genre ON public.channel_health(genre);
CREATE INDEX IF NOT EXISTS idx_playlist_items_playlist_pos ON public.playlist_items(playlist_id, position ASC);
CREATE INDEX IF NOT EXISTS idx_playlist_items_source_item ON public.playlist_items(source_id, catalog_item_id);
CREATE INDEX IF NOT EXISTS idx_tokens_hash ON public.playlist_access_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_tokens_playlist ON public.playlist_access_tokens(playlist_id);

-- ====================================================================
-- POLÍTICAS DE SEGURIDAD (ROW LEVEL SECURITY - RLS)
-- Permite acceso completo con tu anon public key para uso inmediato
-- ====================================================================
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_health ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;

-- Políticas permisivas para cliente web (anon key)
DROP POLICY IF EXISTS "Acceso total playlists anon" ON public.playlists;
CREATE POLICY "Acceso total playlists anon" ON public.playlists FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total favorites anon" ON public.favorites;
CREATE POLICY "Acceso total favorites anon" ON public.favorites FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total watch_history anon" ON public.watch_history;
CREATE POLICY "Acceso total watch_history anon" ON public.watch_history FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total channel_health anon" ON public.channel_health;
CREATE POLICY "Acceso total channel_health anon" ON public.channel_health FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total catalog_cache anon" ON public.catalog_cache;
CREATE POLICY "Acceso total catalog_cache anon" ON public.catalog_cache FOR ALL TO anon USING (true) WITH CHECK (true);

-- FASE 22: RLS Granular para Listas Personalizadas
-- Usuario autenticado solo administra SUS playlists, items y tokens
DROP POLICY IF EXISTS "Acceso total custom_playlists anon" ON public.custom_playlists;
DROP POLICY IF EXISTS "custom_playlists_auth_all" ON public.custom_playlists;
CREATE POLICY "custom_playlists_auth_all" ON public.custom_playlists
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "custom_playlists_anon_all" ON public.custom_playlists;
CREATE POLICY "custom_playlists_anon_all" ON public.custom_playlists
    FOR ALL TO anon
    USING (user_id IS NULL)
    WITH CHECK (user_id IS NULL);

DROP POLICY IF EXISTS "Acceso total playlist_items anon" ON public.playlist_items;
DROP POLICY IF EXISTS "playlist_items_auth_all" ON public.playlist_items;
CREATE POLICY "playlist_items_auth_all" ON public.playlist_items
    FOR ALL TO authenticated
    USING (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()))
    WITH CHECK (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "playlist_items_anon_all" ON public.playlist_items;
CREATE POLICY "playlist_items_anon_all" ON public.playlist_items
    FOR ALL TO anon
    USING (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id IS NULL))
    WITH CHECK (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id IS NULL));

DROP POLICY IF EXISTS "Acceso total playlist_access_tokens anon" ON public.playlist_access_tokens;
DROP POLICY IF EXISTS "tokens_auth_all" ON public.playlist_access_tokens;
CREATE POLICY "tokens_auth_all" ON public.playlist_access_tokens
    FOR ALL TO authenticated
    USING (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()))
    WITH CHECK (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "tokens_anon_all" ON public.playlist_access_tokens;
CREATE POLICY "tokens_anon_all" ON public.playlist_access_tokens
    FOR ALL TO anon
    USING (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id IS NULL))
    WITH CHECK (playlist_id IN (SELECT id FROM public.custom_playlists WHERE user_id IS NULL));

-- Permisos directos
GRANT ALL ON TABLE public.playlists TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.favorites TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.watch_history TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.channel_health TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.catalog_cache TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.custom_playlists TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.playlist_items TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.playlist_access_tokens TO anon, authenticated, service_role;
GRANT SELECT ON public.v_resolved_playlist_items TO anon, authenticated, service_role;

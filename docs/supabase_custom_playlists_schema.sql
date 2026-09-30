-- ====================================================================
-- LELOUCH WEB PLAYER — ESQUEMA DE PERSISTENCIA M3U DINÁMICA (FASE 6)
-- ====================================================================
-- Este script implementa:
-- 1. custom_playlists: Listas M3U personalizadas creadas por el usuario
-- 2. playlist_items: Elementos referenciados por catalog_item_id (sin duplicar stream_url)
-- 3. playlist_access_tokens: Tokens de acceso permanente seguro (SHA-256)
-- 4. v_resolved_playlist_items: Vista que resuelve en tiempo real la URL viva del proveedor
-- ====================================================================

-- 1. TABLA: PLAYLISTS PERSONALIZADAS (Creador M3U)
CREATE TABLE IF NOT EXISTS public.custom_playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,                                      -- Opcional (null en modo anónimo / local)
    name TEXT NOT NULL DEFAULT 'Mi Lista LELOUCH',
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,                 -- Incrementado en cada modificación
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. TABLA: ELEMENTOS DE LA PLAYLIST (ITEMS)
-- Representa items de CATÁLOGO (referenciados) o DIRECTOS (manuales)
CREATE TABLE IF NOT EXISTS public.playlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    item_type TEXT NOT NULL DEFAULT 'catalog' CHECK (item_type IN ('catalog', 'direct')),
    source_id UUID REFERENCES public.playlists(id) ON DELETE SET NULL,
    catalog_item_id TEXT,
    media_type TEXT NOT NULL DEFAULT 'live' CHECK (media_type IN ('live', 'movie', 'series', 'radio', 'direct')),
    custom_name TEXT,
    custom_group TEXT,
    custom_logo TEXT,
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

-- 3. TABLA: TOKENS DE ACCESO PÚBLICO PERMANENTE
-- Almacena el hash SHA-256 del token, nunca el token en texto plano
CREATE TABLE IF NOT EXISTS public.playlist_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,                   -- SHA-256 del secreto
    token_preview TEXT NOT NULL,                       -- Ej: 'lel_...8f2a' para mostrar en UI
    name TEXT NOT NULL DEFAULT 'Acceso Dispositivo',    -- Ej: 'TV Sala', 'VLC Laptop'
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,                            -- NULL = Permanente
    last_accessed_at TIMESTAMPTZ,
    access_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================================================================
-- 4. VISTA: RESOLUCIÓN DINÁMICA DE URLs VIVAS
-- Resuelve la URL real en el momento de consulta combinando las
-- credenciales vigentes de public.playlists con catalog_item_id
-- si es catálogo, o direct_url si es enlace directo.
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
    -- Nombre resuelto
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.direct_name, pi.custom_name, 'Canal Directo')
        ELSE COALESCE(pi.custom_name, 'Canal sin nombre')
    END AS name,
    -- Grupo / Categoría resuelta
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.direct_group, pi.custom_group, 'Directos')
        ELSE COALESCE(pi.custom_group, 'General')
    END AS "group",
    -- Logo resuelto
    CASE 
        WHEN pi.item_type = 'direct' THEN COALESCE(pi.direct_logo, pi.custom_logo)
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
    -- URL resuelta
    CASE 
        -- 1. Cuando es canal directo: usa direct_url
        WHEN pi.item_type = 'direct' OR pi.source_id IS NULL OR pi.catalog_item_id IS NULL THEN 
            COALESCE(pi.direct_url, '')
        -- 2. Cuando es catálogo Xtream Live
        WHEN pi.media_type = 'live' THEN 
            RTRIM(p.server_url, '/') || '/live/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'm3u8')
        -- 3. Cuando es catálogo Xtream Movie
        WHEN pi.media_type = 'movie' THEN 
            RTRIM(p.server_url, '/') || '/movie/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'mp4')
        -- 4. Cuando es catálogo Xtream Series
        WHEN pi.media_type = 'series' THEN 
            RTRIM(p.server_url, '/') || '/series/' || p.username || '/' || p.password || '/' || pi.catalog_item_id || '.' || COALESCE(pi.container_extension, 'mp4')
        ELSE COALESCE(pi.direct_url, '')
    END AS resolved_stream_url,
    pi.created_at,
    pi.updated_at
FROM public.playlist_items pi
LEFT JOIN public.playlists p ON pi.source_id = p.id;

-- ====================================================================
-- 5. ÍNDICES DE RENDIMIENTO
-- ====================================================================
CREATE INDEX IF NOT EXISTS idx_playlist_items_playlist_pos 
    ON public.playlist_items(playlist_id, position ASC);

CREATE INDEX IF NOT EXISTS idx_playlist_items_source_item 
    ON public.playlist_items(source_id, catalog_item_id);

CREATE INDEX IF NOT EXISTS idx_tokens_hash 
    ON public.playlist_access_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_tokens_playlist 
    ON public.playlist_access_tokens(playlist_id);

-- ====================================================================
-- 6. POLÍTICAS DE SEGURIDAD (RLS)
-- ====================================================================
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Acceso total custom_playlists anon" ON public.custom_playlists;
CREATE POLICY "Acceso total custom_playlists anon" 
    ON public.custom_playlists FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total playlist_items anon" ON public.playlist_items;
CREATE POLICY "Acceso total playlist_items anon" 
    ON public.playlist_items FOR ALL TO anon USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Acceso total playlist_access_tokens anon" ON public.playlist_access_tokens;
CREATE POLICY "Acceso total playlist_access_tokens anon" 
    ON public.playlist_access_tokens FOR ALL TO anon USING (true) WITH CHECK (true);

GRANT ALL ON TABLE public.custom_playlists TO anon;
GRANT ALL ON TABLE public.playlist_items TO anon;
GRANT ALL ON TABLE public.playlist_access_tokens TO anon;
GRANT SELECT ON public.v_resolved_playlist_items TO anon;

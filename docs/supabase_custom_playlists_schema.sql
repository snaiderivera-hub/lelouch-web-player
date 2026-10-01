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

-- Si la tabla ya había sido creada previamente sin las columnas de la FASE 7 y FASE 16, se añaden automáticamente:
ALTER TABLE public.custom_playlists ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS item_type TEXT NOT NULL DEFAULT 'catalog';
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_name TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_url TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_group TEXT;
ALTER TABLE public.playlist_items ADD COLUMN IF NOT EXISTS direct_logo TEXT;

-- 3. TABLA: TOKENS DE ACCESO PÚBLICO PERMANENTE (FASE 9: HASH SHA-256)
-- Almacena el hash SHA-256 del token, nunca el token en texto plano
CREATE TABLE IF NOT EXISTS public.playlist_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES public.custom_playlists(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,                   -- SHA-256 del secreto (NUNCA texto plano)
    token_prefix TEXT,                                 -- Primeros caracteres para identificación segura (ej. a8F3kP92)
    enabled BOOLEAN NOT NULL DEFAULT TRUE,             -- Estado activo / inactivo
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,                            -- NULL = Permanente
    last_accessed_at TIMESTAMPTZ,                      -- Último acceso registrado
    request_count BIGINT NOT NULL DEFAULT 0,           -- Contador total de solicitudes
    -- Compatibilidad con drafts previos
    token_preview TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    access_count BIGINT DEFAULT 0,
    name TEXT DEFAULT 'Dispositivo'
);

-- Asegurar columnas en tablas existentes (auto-migración)
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS token_prefix TEXT;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMPTZ;
ALTER TABLE public.playlist_access_tokens ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

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

-- FASE 24: Índice compuesto para consultas ultra-rápidas de items activos de una playlist específica
CREATE INDEX IF NOT EXISTS idx_playlist_items_active 
    ON public.playlist_items(playlist_id, enabled, position ASC);

CREATE INDEX IF NOT EXISTS idx_custom_playlists_id_enabled 
    ON public.custom_playlists(id, enabled);

CREATE INDEX IF NOT EXISTS idx_playlist_items_source_item 
    ON public.playlist_items(source_id, catalog_item_id);

CREATE INDEX IF NOT EXISTS idx_tokens_hash 
    ON public.playlist_access_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_tokens_playlist 
    ON public.playlist_access_tokens(playlist_id);

-- ====================================================================
-- 6. POLÍTICAS DE SEGURIDAD (RLS — FASE 22)
-- El usuario autenticado (authenticated) solo administra SUS playlists,
-- SUS playlist_items y SUS tokens.
-- El TV Box / Reproductor externo no requiere sesión Supabase: el token
-- es su mecanismo de acceso resuelto server-side (SECURITY DEFINER / service_role).
-- ====================================================================
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;

-- Limpieza de políticas permisivas obsoletas
DROP POLICY IF EXISTS "Acceso total custom_playlists anon" ON public.custom_playlists;
DROP POLICY IF EXISTS "Acceso total playlist_items anon" ON public.playlist_items;
DROP POLICY IF EXISTS "Acceso total playlist_access_tokens anon" ON public.playlist_access_tokens;

-- --------------------------------------------------------------------
-- A. USUARIO AUTENTICADO: GESTIÓN EXCLUSIVA DE SUS PROPIAS PLAYLISTS
-- --------------------------------------------------------------------

-- custom_playlists (SELECT, INSERT, UPDATE, DELETE)
DROP POLICY IF EXISTS "custom_playlists_auth_select" ON public.custom_playlists;
CREATE POLICY "custom_playlists_auth_select" ON public.custom_playlists
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "custom_playlists_auth_insert" ON public.custom_playlists;
CREATE POLICY "custom_playlists_auth_insert" ON public.custom_playlists
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "custom_playlists_auth_update" ON public.custom_playlists;
CREATE POLICY "custom_playlists_auth_update" ON public.custom_playlists
    FOR UPDATE TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "custom_playlists_auth_delete" ON public.custom_playlists;
CREATE POLICY "custom_playlists_auth_delete" ON public.custom_playlists
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- playlist_items (SELECT, INSERT, UPDATE, DELETE)
DROP POLICY IF EXISTS "playlist_items_auth_select" ON public.playlist_items;
CREATE POLICY "playlist_items_auth_select" ON public.playlist_items
    FOR SELECT TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "playlist_items_auth_insert" ON public.playlist_items;
CREATE POLICY "playlist_items_auth_insert" ON public.playlist_items
    FOR INSERT TO authenticated
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "playlist_items_auth_update" ON public.playlist_items;
CREATE POLICY "playlist_items_auth_update" ON public.playlist_items
    FOR UPDATE TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    )
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "playlist_items_auth_delete" ON public.playlist_items;
CREATE POLICY "playlist_items_auth_delete" ON public.playlist_items
    FOR DELETE TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

-- playlist_access_tokens (SELECT, INSERT, UPDATE, DELETE)
DROP POLICY IF EXISTS "tokens_auth_select" ON public.playlist_access_tokens;
CREATE POLICY "tokens_auth_select" ON public.playlist_access_tokens
    FOR SELECT TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "tokens_auth_insert" ON public.playlist_access_tokens;
CREATE POLICY "tokens_auth_insert" ON public.playlist_access_tokens
    FOR INSERT TO authenticated
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "tokens_auth_update" ON public.playlist_access_tokens;
CREATE POLICY "tokens_auth_update" ON public.playlist_access_tokens
    FOR UPDATE TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    )
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "tokens_auth_delete" ON public.playlist_access_tokens;
CREATE POLICY "tokens_auth_delete" ON public.playlist_access_tokens
    FOR DELETE TO authenticated
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id = auth.uid()
        )
    );

-- --------------------------------------------------------------------
-- B. MODO LOCAL / ANÓNIMO (BORRADORES WEB SIN LOGIN)
-- Permite uso local aislado: solo ven y modifican registros donde user_id IS NULL.
-- Jamás acceden a playlists pertenecientes a usuarios registrados.
-- --------------------------------------------------------------------
DROP POLICY IF EXISTS "custom_playlists_anon_all" ON public.custom_playlists;
CREATE POLICY "custom_playlists_anon_all" ON public.custom_playlists
    FOR ALL TO anon
    USING (user_id IS NULL)
    WITH CHECK (user_id IS NULL);

DROP POLICY IF EXISTS "playlist_items_anon_all" ON public.playlist_items;
CREATE POLICY "playlist_items_anon_all" ON public.playlist_items
    FOR ALL TO anon
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id IS NULL
        )
    )
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id IS NULL
        )
    );

DROP POLICY IF EXISTS "tokens_anon_all" ON public.playlist_access_tokens;
CREATE POLICY "tokens_anon_all" ON public.playlist_access_tokens
    FOR ALL TO anon
    USING (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id IS NULL
        )
    )
    WITH CHECK (
        playlist_id IN (
            SELECT id FROM public.custom_playlists WHERE user_id IS NULL
        )
    );

GRANT ALL ON TABLE public.custom_playlists TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.playlist_items TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.playlist_access_tokens TO anon, authenticated, service_role;
GRANT SELECT ON public.v_resolved_playlist_items TO anon, authenticated, service_role;

-- --------------------------------------------------------------------
-- C. RESOLUCIÓN SERVER-SIDE DEL TOKEN (TV BOX & PLAYERS EXTERNOS)
-- Función con SECURITY DEFINER que permite al backend de Vercel (/api/playlist/token)
-- resolver la playlist y sus items mediante el hash del token sin que el TV Box
-- requiera credenciales ni sesión en Supabase.
-- --------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolve_playlist_by_token(p_token_hash TEXT)
RETURNS TABLE (
    playlist_id UUID,
    playlist_name TEXT,
    playlist_version BIGINT,
    playlist_enabled BOOLEAN,
    token_enabled BOOLEAN,
    token_expires_at TIMESTAMPTZ,
    item_id UUID,
    item_name TEXT,
    resolved_url TEXT,
    item_group TEXT,
    item_logo TEXT,
    tvg_id TEXT,
    tvg_name TEXT,
    media_type TEXT,
    "position" INTEGER,
    item_enabled BOOLEAN,
    item_type TEXT,
    metadata JSONB
)
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    -- 1. Actualizar métricas del token de acceso de forma atómica
    UPDATE public.playlist_access_tokens
    SET last_accessed_at = NOW(),
        request_count = COALESCE(request_count, 0) + 1,
        access_count = COALESCE(access_count, 0) + 1
    WHERE token_hash = p_token_hash;

    -- 2. Retornar los items resueltos de la vista
    RETURN QUERY
    SELECT 
        cp.id AS playlist_id,
        cp.name AS playlist_name,
        cp.version AS playlist_version,
        cp.enabled AS playlist_enabled,
        pat.enabled AS token_enabled,
        pat.expires_at AS token_expires_at,
        v.id AS item_id,
        v.name AS item_name,
        v.resolved_stream_url AS resolved_url,
        v."group" AS item_group,
        v.logo AS item_logo,
        v.tvg_id,
        v.tvg_name,
        v.media_type,
        v.position AS "position",
        v.enabled AS item_enabled,
        v.item_type,
        v.metadata
    FROM public.playlist_access_tokens pat
    JOIN public.custom_playlists cp ON pat.playlist_id = cp.id
    LEFT JOIN public.v_resolved_playlist_items v ON v.playlist_id = cp.id AND v.enabled = TRUE
    WHERE pat.token_hash = p_token_hash
    ORDER BY v.position ASC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.resolve_playlist_by_token(TEXT) TO anon, authenticated, service_role;

-- ====================================================================
-- 7. VERSIONADO AUTOMÁTICO (FASE 16)
-- Cada modificación: ADD, REMOVE, REORDER, RENAME, CHANGE GROUP, ENABLE, DISABLE
-- incrementa version = version + 1
-- ====================================================================
CREATE OR REPLACE FUNCTION public.trg_increment_custom_playlist_version()
RETURNS TRIGGER AS $$
DECLARE
    target_playlist_id UUID;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        target_playlist_id := OLD.playlist_id;
    ELSE
        target_playlist_id := NEW.playlist_id;
    END IF;

    IF target_playlist_id IS NOT NULL THEN
        UPDATE public.custom_playlists
        SET version = COALESCE(version, 1) + 1,
            updated_at = NOW()
        WHERE id = target_playlist_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_playlist_items_version_sync ON public.playlist_items;
CREATE TRIGGER trg_playlist_items_version_sync
AFTER INSERT OR UPDATE OR DELETE ON public.playlist_items
FOR EACH ROW
EXECUTE FUNCTION public.trg_increment_custom_playlist_version();

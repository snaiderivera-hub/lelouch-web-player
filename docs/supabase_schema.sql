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

-- ====================================================================
-- ÍNDICES DE RENDIMIENTO (BÚSQUEDAS INSTANTÁNEAS)
-- ====================================================================
CREATE INDEX IF NOT EXISTS idx_favorites_type ON public.favorites(type);
CREATE INDEX IF NOT EXISTS idx_history_updated ON public.watch_history(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_channel_status ON public.channel_health(status);
CREATE INDEX IF NOT EXISTS idx_channel_genre ON public.channel_health(genre);

-- ====================================================================
-- POLÍTICAS DE SEGURIDAD (ROW LEVEL SECURITY - RLS)
-- Permite acceso completo con tu anon public key para uso inmediato
-- ====================================================================
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.channel_health ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.catalog_cache ENABLE ROW LEVEL SECURITY;

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

-- Permisos directos al rol anon
GRANT ALL ON TABLE public.playlists TO anon;
GRANT ALL ON TABLE public.favorites TO anon;
GRANT ALL ON TABLE public.watch_history TO anon;
GRANT ALL ON TABLE public.channel_health TO anon;
GRANT ALL ON TABLE public.catalog_cache TO anon;

-- =====================================================================
-- SCRIPT DEFINITIVO SUPABASE: PERSISTENCIA TOTAL DESDE WEB APP Y CLASIFICACIÓN AUTOMÁTICA
-- Ejecuta este script completo en el SQL Editor de Supabase (botón RUN verde)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. QUITAR LA ALERTA DE SEGURIDAD DEL ASESOR (Imagen 2)
-- ---------------------------------------------------------------------
ALTER VIEW public.v_resolved_playlist_items SET (security_invoker = on);

-- ---------------------------------------------------------------------
-- 2. POLÍTICAS RLS ABIERTAS: PERMITE A LA WEB APP GUARDAR, MODIFICAR Y BORRAR SIN RESTRICCIÓN
-- ---------------------------------------------------------------------
ALTER TABLE public.custom_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlist_access_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "playlist_items_anon_all" ON public.playlist_items;
DROP POLICY IF EXISTS "playlist_items_auth_all" ON public.playlist_items;
DROP POLICY IF EXISTS "Acceso total playlist_items anon" ON public.playlist_items;
CREATE POLICY "playlist_items_full_access" ON public.playlist_items
    FOR ALL TO anon, authenticated
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "custom_playlists_anon_all" ON public.custom_playlists;
DROP POLICY IF EXISTS "custom_playlists_auth_all" ON public.custom_playlists;
CREATE POLICY "custom_playlists_full_access" ON public.custom_playlists
    FOR ALL TO anon, authenticated
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "tokens_anon_all" ON public.playlist_access_tokens;
DROP POLICY IF EXISTS "tokens_auth_all" ON public.playlist_access_tokens;
CREATE POLICY "tokens_full_access" ON public.playlist_access_tokens
    FOR ALL TO anon, authenticated
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "playlists_full_access" ON public.playlists;
CREATE POLICY "playlists_full_access" ON public.playlists
    FOR ALL TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- Permisos globales para anon y authenticated
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON ALL ROUTINES IN SCHEMA public TO anon, authenticated, service_role;

-- ---------------------------------------------------------------------
-- 3. TRIGGER AUTOMÁTICO EN SUPABASE: CLASIFICA EN TIEMPO REAL
-- Todo lo que la Web App inserte o actualice será clasificado automáticamente:
-- Si contiene /movie/ o palabras de cine -> 'movie'
-- Si contiene /series/ o 'serie'        -> 'series'
-- Lo demás                              -> 'live'
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_auto_classify_media_type()
RETURNS TRIGGER AS $$
BEGIN
    -- Si viene nulo, vacío o como 'live', inferir por URL o Grupo
    IF NEW.media_type IS NULL OR NEW.media_type = 'live' OR NEW.media_type = '' THEN
        IF NEW.direct_url ILIKE '%/movie/%' 
           OR NEW.direct_group ILIKE '%película%' 
           OR NEW.direct_group ILIKE '%movie%'
           OR NEW.direct_group ILIKE '%hbo%'
           OR NEW.direct_group ILIKE '%cinema%'
           OR NEW.direct_group ILIKE '%cine%' THEN
            NEW.media_type := 'movie';
        ELSIF NEW.direct_url ILIKE '%/series/%' 
           OR NEW.direct_group ILIKE '%serie%' THEN
            NEW.media_type := 'series';
        ELSE
            NEW.media_type := 'live';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auto_classify_playlist_items ON public.playlist_items;
CREATE TRIGGER trg_auto_classify_playlist_items
    BEFORE INSERT OR UPDATE ON public.playlist_items
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_auto_classify_media_type();

-- ---------------------------------------------------------------------
-- 4. ACTUALIZAR LAS 450 PELÍCULAS EXISTENTES QUE ESTABAN COMO 'live'
-- ---------------------------------------------------------------------
UPDATE public.playlist_items
SET media_type = 'movie'
WHERE direct_url ILIKE '%/movie/%'
   OR direct_group ILIKE '%película%'
   OR direct_group ILIKE '%movie%'
   OR direct_group ILIKE '%hbo%'
   OR direct_group ILIKE '%cinema%'
   OR direct_group ILIKE '%cine%';

UPDATE public.playlist_items
SET media_type = 'series'
WHERE direct_url ILIKE '%/series/%'
   OR direct_group ILIKE '%serie%';

-- ---------------------------------------------------------------------
-- 5. ACTUALIZAR LOS CONTADORES EN LA TABLA playlists
-- ---------------------------------------------------------------------
UPDATE public.playlists
SET 
    channels_count = (SELECT COUNT(*) FROM public.playlist_items WHERE media_type = 'live' AND enabled = true),
    movies_count   = (SELECT COUNT(*) FROM public.playlist_items WHERE media_type = 'movie' AND enabled = true),
    series_count   = (SELECT COUNT(*) FROM public.playlist_items WHERE media_type = 'series' AND enabled = true),
    updated_at     = NOW()
WHERE id = 'a43bc8b7-1b66-4eb6-97b3-a276ca3257f3';

-- ---------------------------------------------------------------------
-- 6. VERIFICACIÓN FINAL: MUESTRA EL RESUMEN CLASIFICADO
-- ---------------------------------------------------------------------
SELECT 
    media_type AS "Tipo de Contenido",
    COUNT(*) AS "Total Guardados",
    COUNT(CASE WHEN enabled = true THEN 1 END) AS "Activos"
FROM public.playlist_items
GROUP BY media_type;

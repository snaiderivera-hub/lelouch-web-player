-- =====================================================================
-- MIGRACIÓN SUPABASE: PERSISTENCIA DE FILTROS OCULTOS POR LISTA
-- Permite que cada lista guarde y restaure en la nube las categorías que el
-- usuario oculta ("lo que oculto y no quiero que se vea").
-- Ejecutar en el SQL Editor de Supabase (botón RUN verde).
-- =====================================================================

ALTER TABLE public.playlists 
ADD COLUMN IF NOT EXISTS hidden_categories JSONB DEFAULT '{"live":[], "movies":[], "series":[]}'::jsonb;

-- Comentario descriptivo de la columna
COMMENT ON COLUMN public.playlists.hidden_categories IS 'Categorías ocultas por lista en formato JSON {"live":[], "movies":[], "series":[]}';


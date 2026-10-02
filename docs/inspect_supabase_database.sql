-- =====================================================================
-- SCRIPT DE INSPECCIÓN GENERAL: BASE DE DATOS SUPABASE (LELOUCH PLAYER)
-- Puedes ejecutar este script completo o bloque por bloque en Supabase SQL Editor.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. CONSULTAR TODAS LAS PLAYLISTS / CUENTAS REGISTRADAS
-- ---------------------------------------------------------------------
SELECT 
    id,
    name AS "Nombre de Lista",
    username AS "Usuario",
    server_url AS "Servidor",
    url AS "URL Completa",
    channels_count AS "Canales",
    movies_count AS "Películas",
    series_count AS "Series",
    is_active AS "Activa",
    updated_at AS "Última Actualización"
FROM public.playlists
ORDER BY updated_at DESC;

-- ---------------------------------------------------------------------
-- 2. CONSULTAR LISTAS PERSONALIZADAS CREADAS
-- ---------------------------------------------------------------------
SELECT 
    id,
    name AS "Nombre Lista Personalizada",
    description AS "Descripción",
    enabled AS "Habilitada",
    version AS "Versión",
    updated_at AS "Fecha Modificación"
FROM public.custom_playlists
ORDER BY updated_at DESC;

-- ---------------------------------------------------------------------
-- 3. CONSULTAR TOKENS DE ACCESO PÚBLICOS (Con token_preview y token_prefix)
-- (Nota: Por seguridad, el token completo se almacena como hash SHA-256)
-- ---------------------------------------------------------------------
SELECT 
    t.id,
    p.name AS "Playlist Vinculada",
    t.name AS "Nombre Enlace",
    t.token_preview AS "Token (Preview)",
    t.token_prefix AS "Prefijo",
    t.enabled AS "Activo",
    t.request_count AS "Total Peticiones",
    t.last_accessed_at AS "Último Acceso"
FROM public.playlist_access_tokens t
LEFT JOIN public.custom_playlists p ON p.id = t.playlist_id
ORDER BY t.created_at DESC;

-- ---------------------------------------------------------------------
-- 4. RESUMEN: CANTIDAD DE ÍTEMS GUARDADOS POR LISTA Y TIPO DE MEDIO
-- ---------------------------------------------------------------------
SELECT 
    p.name AS "Nombre de Lista",
    i.media_type AS "Tipo de Medio (live/movie/series)",
    COUNT(*) AS "Total Guardados",
    COUNT(CASE WHEN i.enabled = true THEN 1 END) AS "Activos",
    COUNT(CASE WHEN i.enabled = false THEN 1 END) AS "Ocultos/Deshabilitados"
FROM public.playlist_items i
JOIN public.custom_playlists p ON p.id = i.playlist_id
GROUP BY p.name, i.media_type
ORDER BY p.name, i.media_type;

-- ---------------------------------------------------------------------
-- 5. RESUMEN: ÍTEMS POR CATEGORÍA/GRUPO EN LA LISTA PERSONALIZADA
-- ---------------------------------------------------------------------
SELECT 
    COALESCE(custom_group, direct_group, 'General') AS "Categoría",
    COUNT(*) AS "Cantidad de Canales"
FROM public.playlist_items
WHERE playlist_id = 'c9da4a22-534c-41f9-af70-42ee9f6682df'
GROUP BY COALESCE(custom_group, direct_group, 'General')
ORDER BY "Cantidad de Canales" DESC;

-- ---------------------------------------------------------------------
-- 6. DETALLE: TODOS LOS CANALES GUARDADOS EN TU LISTA (PRIMEROS 100)
-- ---------------------------------------------------------------------
SELECT 
    position AS "#",
    COALESCE(custom_name, direct_name) AS "Nombre",
    COALESCE(custom_group, direct_group) AS "Categoría",
    media_type AS "Tipo",
    direct_url AS "Enlace Stream",
    enabled AS "Visible"
FROM public.playlist_items
WHERE playlist_id = 'c9da4a22-534c-41f9-af70-42ee9f6682df'
ORDER BY position ASC
LIMIT 100;

-- ---------------------------------------------------------------------
-- 7. REVISAR CANALES DEPORTIVOS EN TU LISTA PERSONALIZADA
-- ---------------------------------------------------------------------
SELECT 
    position AS "#",
    COALESCE(custom_name, direct_name) AS "Canal Deportivo",
    COALESCE(custom_group, direct_group) AS "Categoría",
    direct_url AS "Enlace Stream"
FROM public.playlist_items
WHERE playlist_id = 'c9da4a22-534c-41f9-af70-42ee9f6682df'
  AND (
      LOWER(COALESCE(custom_name, direct_name)) LIKE '%espn%'
      OR LOWER(COALESCE(custom_name, direct_name)) LIKE '%fox sport%'
      OR LOWER(COALESCE(custom_name, direct_name)) LIKE '%dazn%'
      OR LOWER(COALESCE(custom_name, direct_name)) LIKE '%tudn%'
      OR LOWER(COALESCE(custom_name, direct_name)) LIKE '%deport%'
      OR LOWER(COALESCE(custom_group, direct_group)) LIKE '%deport%'
      OR LOWER(COALESCE(custom_group, direct_group)) LIKE '%sport%'
  )
ORDER BY position ASC;

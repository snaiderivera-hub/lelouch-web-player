# Patrones de Diseño UI y UX Inspirados en neTV (`jvdillon/netv`)

---

## 1. Guía EPG en Parrilla Horaria (Timeline Grid)

- Eje Horizontal: Horas del día en bloques de 30 minutos (ej: 18:00, 18:30, 19:00).
- Eje Vertical: Lista de canales con logo y número.
- Celdas de Programa: Ancho proporcional a la duración del programa, con barra de progreso que indica el porcentaje transcurrido en tiempo real.
- Interacción: Al hacer clic en un programa actual, abre directamente el reproductor con el canal en vivo. Si el canal tiene `tv_archive`, permite reproducir programas pasados (Catchup).

---

## 2. Modal de Detalles de Película (VOD)

- Imagen de fondo en alta resolución (`backdrop_path`) con degradado oscuro suave.
- Poster oficial de la película en el lado izquierdo.
- Título, Rating (TMDB), Géneros, Año, Director, Reparto y Sinopsis.
- Botón principal destacado: **"▶️ Reproducir Película"**.
- Indicador de progreso guardado para reanudar la reproducción desde el último segundo visto.

---

## 3. Modal de Series y Selección de Temporadas/Episodios

- Selector de temporadas en pestañas (Temporada 1, Temporada 2, ...).
- Lista de episodios con miniatura, número de episodio, nombre, sinopsis y duración.
- Cada episodio cuenta con su propio botón directo para iniciar la reproducción.

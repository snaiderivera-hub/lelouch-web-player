#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M3U Filter & Stream Link Extractor (Versión Mejorada y Sin Errores)
Inspirado en el script de filtrado M3U, corregido para soportar:
- Codificación UTF-8 / Latin-1 / Windows-1252 automática.
- Cualquier formato de URL (http, https, ts, m3u8, mp4, etc.).
- Búsqueda flexible (coincidencia exacta o por palabras clave).
- Exportación en M3U, CSV y TXT de enlaces directos.
"""

import sys
import os
import re
import urllib.request
import csv

def print_help():
    print("""
============================================================
           M3U FILTER & LINK EXTRACTOR (LELOUCH)
============================================================
Uso:
  python m3u_filter.py <archivo.m3u | URL> <filtro_canales.txt> [salida.m3u]

Argumentos:
  1. archivo.m3u o URL : Ruta al archivo M3U local o enlace HTTP(S).
  2. filtro_canales.txt: Archivo de texto con los nombres de canales deseados (uno por línea).
  3. salida.m3u        : (Opcional) Nombre del archivo M3U resultante (por defecto: out.m3u).

Ejemplo:
  python m3u_filter.py mi_lista.m3u mis_canales.txt lista_filtrada.m3u
============================================================
""")

def safe_read_file(path_or_url):
    """Lee un archivo local o URL web detectando codificación."""
    if path_or_url.startswith("http://") or path_or_url.startswith("https://"):
        print(f"[*] Descargando lista M3U desde URL: {path_or_url}...")
        req = urllib.request.Request(
            path_or_url,
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
        )
        with urllib.request.urlopen(req, timeout=30) as response:
            raw_data = response.read()
    else:
        if not os.path.exists(path_or_url):
            sys.exit(f"ERROR: No se encontró el archivo '{path_or_url}'")
        with open(path_or_url, "rb") as f:
            raw_data = f.read()

    # Detectar codificación
    for enc in ["utf-8-sig", "utf-8", "latin-1", "cp1252"]:
        try:
            return raw_data.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw_data.decode("utf-8", errors="replace")

def parse_m3u_entries(content):
    """
    Parsea de manera robusta los bloques de canales M3U
    incluso si hay líneas intermedias como #EXTVLCOPT o comentarios.
    """
    entries = []
    lines = content.splitlines()
    
    current_extinf = None
    current_extras = []
    
    for line in lines:
        line_clean = line.strip()
        if not line_clean:
            continue
            
        if line_clean.startswith("#EXTM3U"):
            continue
            
        if line_clean.startswith("#EXTINF:"):
            # Si teníamos un canal pendiente sin URL, lo descartamos
            current_extinf = line_clean
            current_extras = []
        elif line_clean.startswith("#") and current_extinf is not None:
            # Líneas de metadatos adicionales (ej: #EXTVLCOPT, #EXTGRP, etc.)
            current_extras.append(line_clean)
        elif not line_clean.startswith("#") and current_extinf is not None:
            # Esta es la URL del stream
            url = line_clean
            
            # Extraer Nombre tras la última coma en la línea #EXTINF
            comma_idx = current_extinf.rfind(",")
            if comma_idx != -1:
                name = current_extinf[comma_idx + 1:].strip()
            else:
                name = "Sin Nombre"
                
            # Extraer Categoría / group-title si existe
            grp_match = re.search(r'group-title="([^"]*)"', current_extinf, re.IGNORECASE)
            category = grp_match.group(1).strip() if grp_match else ""
            
            # Extraer Logo si existe
            logo_match = re.search(r'tvg-logo="([^"]*)"', current_extinf, re.IGNORECASE)
            logo = logo_match.group(1).strip() if logo_match else ""
            
            # Extraer tvg-name
            tvg_name_match = re.search(r'tvg-name="([^"]*)"', current_extinf, re.IGNORECASE)
            tvg_name = tvg_name_match.group(1).strip() if tvg_name_match else name

            entries.append({
                "extinf": current_extinf,
                "extras": current_extras,
                "url": url,
                "name": name,
                "tvg_name": tvg_name,
                "category": category,
                "logo": logo
            })
            
            current_extinf = None
            current_extras = []
            
    return entries

def main():
    if len(sys.argv) < 3:
        print_help()
        sys.exit(1)

    m3u_input = sys.argv[1]
    filter_input = sys.argv[2]
    out_m3u_path = sys.argv[3] if len(sys.argv) >= 4 else "out.m3u"

    print(f"[*] Leyendo lista M3U: {m3u_input}...")
    m3u_content = safe_read_file(m3u_input)
    
    entries = parse_m3u_entries(m3u_content)
    print(f"[✓] Se cargaron {len(entries)} canales en total.")

    print(f"[*] Leyendo filtros deseados desde: {filter_input}...")
    filter_content = safe_read_file(filter_input)
    filter_lines = [line.strip().lower() for line in filter_content.splitlines() if line.strip() and not line.startswith("#")]
    print(f"[✓] Se cargaron {len(filter_lines)} términos/canales a buscar.")

    # Filtrar
    matched = []
    seen_urls = set()

    for entry in entries:
        name_lower = entry["name"].lower()
        tvg_name_lower = entry["tvg_name"].lower()
        cat_lower = entry["category"].lower()
        
        # Coincidencia si cualquiera de los términos está en el nombre o categoría
        is_match = False
        matched_term = None
        for term in filter_lines:
            if term == name_lower or term == tvg_name_lower or term in name_lower or term in cat_lower:
                is_match = True
                matched_term = term
                break
                
        if is_match and entry["url"] not in seen_urls:
            seen_urls.add(entry["url"])
            entry["matched_by"] = matched_term
            matched.append(entry)

    # 1. Guardar archivo .m3u filtrado
    with open(out_m3u_path, "w", encoding="utf-8") as out_m3u:
        out_m3u.write('#EXTM3U name="Lista Filtrada LELOUCH"\n\n')
        for m in matched:
            out_m3u.write(f'{m["extinf"]}\n')
            for ex in m["extras"]:
                out_m3u.write(f"{ex}\n")
            out_m3u.write(f'{m["url"]}\n\n')

    # 2. Guardar archivo de enlaces directos extraídos (.txt)
    links_txt_path = os.path.splitext(out_m3u_path)[0] + "_enlaces.txt"
    with open(links_txt_path, "w", encoding="utf-8") as out_txt:
        out_txt.write(f"# ENLACES DIRECTOS EXTRAÍDOS ({len(matched)} canales)\n")
        out_txt.write("# Formato: [Nombre del Canal] -> URL Directa\n\n")
        for m in matched:
            cat_str = f" [{m['category']}]" if m['category'] else ""
            out_txt.write(f"{m['name']}{cat_str}\n{m['url']}\n\n")

    # 3. Guardar archivo CSV detallado
    csv_path = os.path.splitext(out_m3u_path)[0] + "_canales.csv"
    with open(csv_path, "w", newline="", encoding="utf-8-sig") as out_csv:
        writer = csv.writer(out_csv)
        writer.writerow(["Numero", "Canal", "Categoria", "Logo", "URL_Stream", "Filtro_Coincidente"])
        for idx, m in enumerate(matched, 1):
            writer.writerow([idx, m["name"], m["category"], m["logo"], m["url"], m.get("matched_by", "")])

    print("\n" + "=" * 60)
    print(f"🎉 ¡FILTRADO COMPLETADO CON ÉXITO! ({len(matched)} canales encontrados)")
    print("=" * 60)
    print(f"📄 1. Lista M3U filtrada      : {out_m3u_path}")
    print(f"🔗 2. Enlaces directos (TXT)  : {links_txt_path}")
    print(f"📊 3. Hoja de cálculo (CSV)   : {csv_path}")
    print("-" * 60)
    
    print("\nCanales encontrados:")
    for i, m in enumerate(matched[:20], 1):
        print(f"  {i:2d}. {m['name']} -> {m['url']}")
    if len(matched) > 20:
        print(f"  ... y {len(matched) - 20} canales más guardados en {out_m3u_path}.")

if __name__ == "__main__":
    main()

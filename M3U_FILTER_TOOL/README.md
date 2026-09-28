# 🛠️ M3U Filter & Extractor de Enlaces (LELOUCH)

Esta carpeta contiene todas las herramientas para **filtrar listas M3U**, **extraer enlaces de streaming directos** y **crear listas personalizadas** a partir de tus archivos o enlaces de IPTV.

---

## 📁 Archivos Disponibles en esta Carpeta

| Archivo | Descripción |
| :--- | :--- |
| **`EJECUTAR_FILTRAR_M3U.bat`** | 🚀 **Doble clic en Windows**. Abre un menú automático para filtrar o extraer sin escribir comandos. |
| **`extractor_visual.html`** | 🌐 **Extractor visual en el navegador**. Arrastra tu `.m3u` o pega una URL, filtra canales en vivo y copia o descarga los enlaces. |
| **`m3u_filter.ps1`** | ⚡ **Script nativo de PowerShell 7**. Rápido y funciona en tu Windows sin instalar nada adicional. |
| **`m3u_filter.py`** | 🐍 **Script de Python corregido y mejorado** (el script que solicitaste, reparado para evitar caídas y con codificación UTF-8). |
| **`canales_filtro.txt`** | 📝 Archivo de ejemplo con nombres y categorías de canales a extraer. Puedes editarlo a tu gusto. |

---

## 🚀 Métodos de Uso

### Método 1: Doble Clic en Windows (Recomendado)
1. Coloca tu archivo `.m3u` dentro de esta carpeta (o ten a mano la URL de tu stream).
2. Haz doble clic en **`EJECUTAR_FILTRAR_M3U.bat`**.
3. Selecciona la opción deseada (ejemplo: opción `1` para filtrar por canales).
4. El programa generará automáticamente:
   * **`lista_filtrada.m3u`**: Tu nueva lista lista para VLC o TV Box.
   * **`lista_filtrada_enlaces.txt`**: Todos los enlaces directos en texto plano.
   * **`lista_filtrada_canales.csv`**: Tabla de canales con sus enlaces y logos para Excel.

---

### Método 2: Extractor Visual en el Navegador Web
1. Haz doble clic en **`extractor_visual.html`** (se abrirá en Chrome, Edge o Brave).
2. Arrastra tu archivo `.m3u` al recuadro o pega la URL del proveedor.
3. Escribe los nombres de los canales que quieres conservar (ej: *ESPN, Fox, HBO, Deportes*).
4. Pulsa **⚡ Filtrar y Extraer Enlaces**.
5. Puedes pulsar **🔗 Copiar** en cualquier canal individual, o **📋 Copiar Enlaces** para copiarlos todos juntos, o descargarlo en `.m3u` o `.csv`.

---

### Método 3: Con PowerShell 7 (Línea de comandos)
```powershell
pwsh m3u_filter.ps1 "mi_lista.m3u" "canales_filtro.txt" "resultado.m3u"
```

---

### Método 4: Con Python 3
Si tienes Python instalado:
```bash
python m3u_filter.py mi_lista.m3u canales_filtro.txt out.m3u
```

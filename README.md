# Lelouch IPTV & Web Player 📺⚡

> **Plataforma Integral de IPTV:** Reproductor Web de alto rendimiento, Gestor de Listas Dinámicas en la Nube con Tokens Criptográficos y Aplicación Nativa Android TV / Mobile con Actualizaciones OTA.

[![JavaScript](https://img.shields.io/badge/Frontend-Vanilla%20JS%20(ES6%2B)-F7DF1E?logo=javascript&logoColor=black)](#)
[![Vercel](https://img.shields.io/badge/Serverless-Vercel%20Edge%20Functions-000000?logo=vercel&logoColor=white)](#)
[![Supabase](https://img.shields.io/badge/Database-Supabase%20PostgreSQL-3ECF8E?logo=supabase&logoColor=white)](#)
[![Android TV](https://img.shields.io/badge/Android%20TV-Compose%20Multiplatform-3DDC84?logo=android&logoColor=white)](#)
[![HLS.js](https://img.shields.io/badge/Streaming-HLS%20%7C%20MPEG--TS-blue)](#)
[![Security](https://img.shields.io/badge/Security-SHA--256%20Tokens%20%7C%20Zero--Proxy-10B981)](#)

---

## 🌟 Características Principales

### 1. 🌐 Servicio de Playlist M3U Dinámica en la Nube (API Token)
- Generación de URLs públicas permanentes y sincronizadas (`/api/playlist/:token`).
- **Resolución Server-Side en Tiempo Real:** El reproductor consulta la vista `v_resolved_playlist_items` en Supabase; los canales agregados o eliminados en la web se reflejan de inmediato en tus TV Boxes sin reconfigurar enlaces.
- **Seguridad Criptográfica:** Autenticación por token aleatorio de alta entropía con hash SHA-256 en base de datos.
- **Revocación Inmediata:** Desactiva o regenera tokens con un solo clic si deseas revocar el acceso a reproductores antiguos.

### 2. 🚀 3 Maneras de Consumir la Misma Playlist (FASE 30)
Flexibilidad total para disfrutar tu lista personalizada según el dispositivo o caso de uso:

| Opción | Método | Formato | Destino Recomendado | Ventaja |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **COPIAR** | Texto `#EXTM3U` plano | Portapapeles / Editores / Scripts | Inmediato, sin guardar archivos |
| **2** | **DESCARGAR** | Archivo `.m3u` físico | Pendrive USB / Smart TVs / TV Box | 100% offline, para reproductores locales |
| **3** | **GENERAR URL** | Enlace persistente en la nube | TiviMate / IPTV Smarters / VLC | Sincronizado en tiempo real por token |

*Adicionalmente, pulsa **📺 Ver Mi Lista en TV en Vivo** para reproducir tus canales personalizados al instante dentro de la misma aplicación web.*

### 3. 🎬 Reproductor Web Universal y Diagnósticos
- **Motores de Vídeo:** Soporte dual para streaming **HLS** (`hls.min.js`) y flujos directos **MPEG-TS** (`mpegts.min.js`).
- **Guía EPG Interactiva:** Integración con XMLTV y cuadrícula de programación en vivo.
- **Control Parental & Categorías:** Oculta, reorganiza o bloquea con PIN categorías de adultos o canales sensibles.
- **Diagnóstico de Servidor:** Prueba de latencia, ping y estado de paneles Xtream Codes.

### 4. 📱 App Nativa Android TV & Mobile (`lelouch-android`)
- Diseñada con **Jetpack Compose Multiplatform** y **Material 3**.
- Optimización de foco mediante control remoto D-Pad para Xiaomi TV Stick, Chromecast con Google TV y Smart TVs.
- **Actualizaciones OTA (Over-The-Air) por Wi-Fi:** Notifica e instala automáticamente nuevas versiones del APK sin requerir cables ni formateos.

### 5. 💾 Modo Portable USB
- Paquete independiente en [`PORTABLE_USB/`](PORTABLE_USB/) listo para ser ejecutado directamente desde una memoria USB en cualquier PC con Windows sin necesidad de instalación previa.

---

## 🚀 Inicio Rápido

### Opción A: Modo Escritorio en PC (Windows)
1. Haz doble clic en el archivo:
   ```cmd
   EJECUTAR APP ESCRITORIO.bat
   ```
2. Se iniciará automáticamente el servidor local en `http://localhost:8686`, activará el gateway anti-SSRF y abrirá tu navegador predeterminado.

### Opción B: Modo Servidor Local (PowerShell)
```powershell
# Iniciar servidor web y proxy local en puerto 8686
.\iptv-app\server.ps1
```

### Opción C: Despliegue en la Nube (Vercel)
El proyecto está preconfigurado para Vercel Serverless Functions:
1. Clona el repositorio e instala Vercel CLI o vincula tu cuenta de GitHub.
2. Configura las variables de entorno:
   - `SUPABASE_URL`: URL del proyecto Supabase.
   - `SUPABASE_ANON_KEY`: Llave pública anónima de Supabase.
   - `SUPABASE_SERVICE_ROLE_KEY`: Llave privada del servidor (opcional para resolver saltando RLS).
3. Despliega con:
   ```bash
   vercel --prod
   ```

---

## 🏛️ Arquitectura del Sistema

```
                      ┌─────────────────────────────────────────┐
                      │        LELOUCH WEB / ANDROID APP        │
                      │  (Crea y gestiona lista personalizada)   │
                      └────────────────────┬────────────────────┘
                                           │  Sincroniza ítems
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │          SUPABASE POSTGRESQL            │
                      │  • custom_playlists                     │
                      │  • playlist_items                       │
                      │  • v_resolved_playlist_items            │
                      └────────────────────┬────────────────────┘
                                           │  Consulta en tiempo real
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │       VERCEL SERVERLESS FUNCTION        │
                      │       /api/playlist/:token              │
                      │  • Valida hash SHA-256                  │
                      │  • Genera manifiesto #EXTM3U            │
                      │  • Desenvuelve URLs directas (No-Proxy) │
                      └────────────────────┬────────────────────┘
                                           │  Entrega playlist #EXTM3U
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │         REPRODUCTOR CLIENTE             │
                      │    (TiviMate / IPTV Smarters / VLC)     │
                      └────────────────────┬────────────────────┘
                                           │  Stream de vídeo directo (HLS/TS)
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │          PROVEEDOR IPTV ORIGEN          │
                      │    (Conexión directa TV <-> Servidor)   │
                      └─────────────────────────────────────────┘
```

> **Zero Proxy de Vídeo:** Vercel entrega exclusivamente texto (`#EXTM3U` o JSON); el tráfico de vídeo se transmite directamente desde el proveedor al dispositivo, garantizando máxima velocidad y evitando costos de servidor.

---

## 📁 Estructura del Proyecto

```
├── api/                           # Vercel Serverless Functions
│   ├── playlist.js                # Generador dinámico de M3U con resolución Supabase
│   ├── playlist/[token].js        # Endpoint dinámico /api/playlist/<token>
│   ├── playlist/manifest/[token].js # Endpoint JSON para clientes API
│   └── proxy.js                   # Gateway CORS seguro para metadata y XMLTV
├── iptv-app/                      # Aplicación Web Frontend (Vanilla ES6+ Modules)
│   ├── index.html                 # Interfaz principal con glassmorphism
│   ├── src/
│   │   ├── app.js                 # Controlador central y orquestador
│   │   ├── modules/iptv/          # Servicios IPTV, XtreamAdapter, UrlParser, EPG
│   │   ├── services/              # SupabaseService, CacheService, Storage
│   │   ├── styles/main.css        # Sistema de diseño y variables CSS
│   │   └── libs/                  # Motores de vídeo (hls.min.js, mpegts.min.js)
│   └── tests/                     # Suites de validación funcionales (Fases 29, 30)
├── lelouch-android/               # App nativa Android (Compose Multiplatform / TV)
├── PORTABLE_USB/                  # Versión portable autoejecutable para memorias USB
├── AGENTS.md                      # Instrucciones del proyecto y Guardarraíles FASE 31
├── GUIA_ACTUALIZACIONES_OTA.md    # Manual para emitir actualizaciones de Android por Wi-Fi
├── LELOUCH_DYNAMIC_M3U_AUDIT.md   # Especificación técnica y auditoría del sistema M3U
├── EJECUTAR APP ESCRITORIO.bat    # Lanzador de un solo clic para Windows
├── IPTV-Data-Architect.exe        # AppHost nativo compilado para Windows
├── vercel.json                    # Enrutamiento, headers de seguridad y rewrites Vercel
└── version.json                   # Manifiesto de control de versiones y canal OTA
```

---

## 🛡️ Guardarraíles y Seguridad (FASE 31)

El desarrollo del proyecto está sujeto a reglas estrictas de seguridad y estabilidad:
1. **Sin reescrituras innecesarias:** Trabajo incremental sobre la arquitectura modular vanilla JS.
2. **Preservación de XtreamAdapter:** Mantenimiento íntegro de la compatibilidad con Xtream Codes.
3. **Privacidad de Credenciales:** Encriptación SHA-256 de tokens; contraseñas nunca expuestas en `localStorage` ni en URLs públicas.
4. **Redacción en Logs:** Función `redactSensitiveUrl()` activa para ocultar usuarios y contraseñas de cualquier registro.
5. **Anti-SSRF y Anti-Indexación:** Headers `X-Robots-Tag: noindex, nofollow` para evitar que bots indexen tus enlaces de streaming.
6. **Integridad de Endpoints:** Cada URL generada mapea a un endpoint real respaldado por pruebas de extremo a extremo.

---

## 🧪 Pruebas y Validación

Puedes ejecutar las pruebas interactivas directamente en tu navegador abriendo:
- **`iptv-app/tests/test_phase29.html`**: Prueba de sincronización dinámica (Canal A+B+C ➔ A+C+D manteniendo la misma URL).
- **`iptv-app/tests/test_phase30.html`**: Matriz de compatibilidad y verificación de las 3 salidas de consumo (Copiar, Descargar, Generar URL).

---

## 📄 Licencia y Créditos

Desarrollado como parte del ecosistema **Lelouch IPTV**.  
Todos los derechos reservados.

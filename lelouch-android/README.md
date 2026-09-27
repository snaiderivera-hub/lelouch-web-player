# LELOUCH ANDROID — Native Multiplatform IPTV Client

LELOUCH ANDROID es una aplicación nativa en **Kotlin** y **Jetpack Compose** diseñada bajo una arquitectura modular limpia para ejecutarse en:
1. **Android Phone** (Interfaz táctil, navegación inferior, hero compacto).
2. **Android Tablet** (Layout adaptativo de 2 columnas con Navigation Rail).
3. **Android TV & Google TV** (10-foot Leanback UI, D-Pad focus first, Spotlight Hero y Carruseles de Rieles Cinemáticos estilo Xbox / EveryCine).

---

## 🏗️ Arquitectura Modular

```
lelouch-android/
├── app/                            # Punto de entrada unificado y Activity adaptativa
├── core/
│   ├── designsystem/               # Tokens de diseño (#02070D, #00E5FF), Tipografía y Temas (Mobile & TV)
│   ├── model/                      # Modelos de datos puros (Live, VOD, Series, EPG, Config)
│   ├── database/                   # Room Database 2.6, Tablas Virtuales FTS y DAOs
│   ├── network/                    # OkHttp (Sanitizado y Whitelisted), Retrofit Xtream Codes API
│   └── domain/                     # Interfaces de Repositorios y Casos de Uso
└── feature/
    ├── presentation-mobile/        # UI táctil Material 3 para teléfonos y tablets
    └── presentation-tv/            # UI cinemática Compose for TV para Smart TVs y TV Boxes
```

---

## 🎨 Principios Visuales en Android TV (10-Foot UI)
* **Spotlight Hero Dinámico:** Ocupa el 55% superior con degradado suave hacia `#02070D`. Sintoniza la señal de video en vivo de fondo al navegar por los canales en los carruseles.
* **Top Navigation Bar:** Barra superior con isotipo LELOUCH, pestañas horizontales de navegación (`Inicio`, `En Vivo`, `Películas`, `Series`, `Animes`, `Favoritos`), buscador y perfil.
* **Carruseles de Rieles Horizontales (`TvLazyRow`):** Tarjetas con radio de 12dp. Al recibir foco D-pad, aumentan a escala 1.08x con un resplandor neón cian (`#00E5FF`).
* **Zapping sin Latencia:** Al detener el foco 300ms sobre una tarjeta de canal, el video en vivo se proyecta en el fondo de la pantalla. Al presionar `OK`, entra a pantalla completa 100% de inmediato.

---

## 🚀 Compilación y Ejecución

### Requisitos:
* **JDK:** OpenJDK 17 LTS (instalado en `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` o variable `JAVA_HOME`).
* **Android SDK:** API 34 (Android 14) con minSdk 24 (Android 7.0+).

### Comandos de Terminal:
```bash
# Ver proyectos y módulos reconocidos
./gradlew projects

# Compilar APK de depuración
./gradlew assembleDebug

# Ejecutar pruebas unitarias
./gradlew test
```

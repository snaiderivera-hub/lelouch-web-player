# LELOUCH TV — Paquete Oficial para Smart TV VIDAA (Toshiba / Hisense)

Este paquete ha sido creado en una carpeta totalmente aislada (`lelouch-vidaa-tv`) para no interferir con la app de Android (`lelouch-android`) ni con el reproductor portable de PC.

---

## 📁 Contenido de este Paquete:

- **`manifest.json`**: Manifiesto estándar de aplicación web para Smart TVs VIDAA y navegadores embebidos.
- **`config.xml`**: Configuración W3C Widget para Smart TV.
- **`index.html`**: Interfaz optimizada para pantallas grandes a 10 pies (1080p / 4K).
- **`src/vidaa-keys.js`**: Controlador de teclas nativo que intercepta y mapea el control remoto de Toshiba VIDAA:
  - ⬆️ ⬇️ ⬅️ ➡️ **D-Pad**: Navegación fluida entre canales, carruseles y opciones.
  - 🔘 **OK / Enter**: Sintonizar canal o reproducir contenido.
  - ↩️ **Atrás (Back / Return)**: Cierra modales y sale del reproductor sin cerrar la aplicación.
  - 🔴 **Botón Rojo**: Acceso rápido a TV en Vivo.
  - 🟢 **Botón Verde**: Acceso rápido a Películas.
  - 🟡 **Botón Amarillo**: Acceso rápido a Series.
  - 🔵 **Botón Azul**: Ajustes.
  - ⏯️ **Play / Pause / Stop**: Control multimedia del reproductor.
- **`LELOUCH-TV-VIDAA.zip`**: Paquete listo para subir al Portal de Desarrolladores de VIDAA o instalar vía USB/servidor.

---

## 🚀 Métodos para Utilizarlo en tu Toshiba VIDAA:

### Opción A (La más rápida y directa — Desde el Navegador VIDAA):
1. Enciende tu televisor **Toshiba**.
2. Abre la aplicación **Navegador Web** (Browser) que viene instalada en VIDAA.
3. En la barra de direcciones escribe:
   `http://192.168.18.23:8686`  *(o tu enlace de Vercel)*
4. Presiona el botón de **Opciones** del navegador de VIDAA y selecciona **"Añadir a Marcadores"** o **"Añadir a la Pantalla de Inicio"**.
5. ¡Listo! Tendrás acceso directo a Lelouch TV con control remoto nativo desde la pantalla de inicio de tu Toshiba sin necesidad de instalar archivos extra.

---

### Opción B (Instalación Oficial mediante VIDAA Developer Portal):
1. Regístrate gratis en el portal oficial de desarrolladores de VIDAA:  
   👉 [https://developer.vidaa.com](https://developer.vidaa.com)
2. En tu televisor Toshiba, activa el modo desarrollador con el código técnico:
   - Ve a **Ajustes ➔ Sonido ➔ Avanzado ➔ Balance (en 0)** y presiona **`1 9 6 9`**.
3. En el portal de VIDAA, ve a **App Management ➔ Sideload / Test App**.
4. Sube el archivo `LELOUCH-TV-VIDAA.zip` (ubicado en tu carpeta de Descargas).
5. Ingresa la IP de tu TV Toshiba (`192.168.18.14`) para enviar la aplicación directamente a tu televisor.


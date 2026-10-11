# 🚀 Guía de Actualizaciones Over-The-Air (OTA) - Lelouch IPTV

Esta guía explica paso a paso cómo publicar y distribuir actualizaciones automáticas para la aplicación **Lelouch IPTV** tanto en **Android TV Box** como en **Teléfonos Móviles / Tablets Android**, eliminando por completo la necesidad de usar memorias USB o conectar cables cada vez que compiles una nueva versión.

---

## 📍 1. ¿Dónde se encuentra el APK compilado?

Cada vez que compiles el proyecto en Android Studio o por terminal con Gradle, el archivo APK generado se ubica en:

```
lelouch-android\app\build\outputs\apk\debug\app-debug.apk
```

> **Tip:** Puedes renombrar el archivo a `lelouch-app.apk` o `app-debug.apk` antes de subirlo; ambos son válidos.

---

## 🌐 2. Método Oficial: Publicar en GitHub Releases (Recomendado)

La aplicación tiene integrado un cliente que consulta automáticamente las entregas en tu repositorio:  
👉 **[https://github.com/snaiderivera-hub/lelouch-web-player/releases](https://github.com/snaiderivera-hub/lelouch-web-player/releases)**

### Pasos para publicar una nueva versión:

1. **Compilar el proyecto:**
   Asegúrate de haber compilado tu último APK con tus cambios y mejoras.
2. **Entrar a GitHub Releases:**
   Abre tu navegador y ve a:
   [https://github.com/snaiderivera-hub/lelouch-web-player/releases](https://github.com/snaiderivera-hub/lelouch-web-player/releases)
3. **Crear la entrega:**
   * Haz clic en el botón verde **"Draft a new release"** (o *"Create a new release"*).
   * **Choose a tag:** Escribe la etiqueta de la versión, por ejemplo: `v1.0.1` (o `v1.0.2`, `v1.1.0`, etc.).
   * **Release title:** Un título descriptivo, por ejemplo: `Lelouch IPTV v1.0.1 - Novedades y Correcciones`.
   * **Describe this release:** Escribe la lista de novedades y correcciones (esto se mostrará dentro de la app en la pantalla del usuario).
4. **Adjuntar el APK:**
   * Arrastra el archivo `app-debug.apk` a la caja de archivos adjuntos (Assets).
5. **Publicar:**
   * Haz clic en el botón verde **"Publish release"**.

---

## 📱 3. ¿Cómo actualizan los usuarios en sus dispositivos?

### En Teléfonos Móviles y Tablets:
1. Abrir la app y dirigirse a la pestaña de **Ajustes** (icono de engranaje ⚙️ o Tab 4).
2. Tocar el botón **"Actualizar"** o la tarjeta **`🚀 Actualizaciones de App (OTA)`**.
3. El sistema verificará en GitHub si hay una versión superior a la instalada.
4. Mostrará las novedades y el botón **"Descargar e Instalar"**.
5. Al finalizar la descarga, se abrirá el asistente oficial de instalación de Android.

### En Android TV Box:
1. En la pantalla principal, presionar el botón de **Ajustes / Panel de Administración** (⚙️) con el control remoto.
2. En la sección **"⚡ Sincronización & Ajustes"**, seleccionar el botón **`🚀 Actualizar App (OTA)`**.
3. La interfaz adaptada a control remoto (D-Pad) mostrará la nueva versión disponible.
4. Presionar el botón **OK** en **"DESCARGAR E INSTALAR"**.
5. Una vez descargado, el instalador de Android TV se abrirá automáticamente para confirmar la instalación.

---

## 🏠 4. Método Alternativo: Instalación por Red Wi-Fi Local (Sin subir a internet)

Si acabas de compilar en tu computadora y deseas probarlo de inmediato en tu TV Box o teléfono sin publicar nada en GitHub:

1. **Abrir la ventana de actualización en la app** (en la TV o en el teléfono).
2. Seleccionar la opción: **"Instalar desde IP local o URL directa"**.
3. Ingresar la dirección IP de tu computadora o servidor local donde tengas el archivo APK disponible, por ejemplo:
   ```
   http://192.168.1.50:8080/app-debug.apk
   ```
4. Presionar **"Descargar desde URL Personalizada"**.
5. El APK se descargará a máxima velocidad dentro de tu red local y se instalará al instante.

---

## 📄 5. Archivo de respaldo `version.json`

En la raíz del repositorio se encuentra el archivo [`version.json`](./version.json):
```json
{
  "versionName": "v1.0.1",
  "versionCode": 2,
  "apkUrl": "https://github.com/snaiderivera-hub/lelouch-web-player/releases/download/v1.0.1/app-debug.apk",
  "changelog": "• Soporte para adelantar y retroceder en películas y series (VOD).\n• Corrección de categorías ocultas y contador de canales en TV Box.\n• Actualizador automático OTA para TV Box y teléfonos móviles."
}
```
Si en algún momento deseas alojar las actualizaciones en un servidor propio, Google Drive, Vercel o CDN, basta con actualizar este archivo JSON y la app leerá la nueva URL y notas de versión automáticamente.

---

## 🔄 6. Subir cambios al repositorio de GitHub

Para subir esta guía y todo el código nuevo a tu cuenta de GitHub, simplemente ejecuta:
```cmd
"SUBIR A GITHUB.bat"
```
o mediante terminal:
```bash
git add .
git commit -m "docs: add OTA update documentation and version.json"
git push -u origin main
```

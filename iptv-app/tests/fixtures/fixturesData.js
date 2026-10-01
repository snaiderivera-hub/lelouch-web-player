/**
 * @module fixturesData
 * Contenido canónico de los 10 fixtures de pruebas unitarias (FASE 26).
 * Permite ejecución sincrónica determinista en suites de testing y navegadores headless
 * sin latencia ni dependencia de fetch en file:///.
 */

export const TEST_BASIC_LIVE = `#EXTM3U url-tvg="http://epg.fictitious-iptv.test/epg.xml.gz" x-tvg-url="http://epg.fictitious-iptv.test/epg.xml.gz"
#EXTINF:-1 tvg-id="fictitious.es.la1" tvg-name="La 1 HD" tvg-logo="https://fictitious-cdn.test/logos/la1.png" group-title="Nacionales", La 1 HD Ficticio
http://fictitious-iptv.test:8080/live/user_demo/pass_demo/1001.m3u8
#EXTINF:0 tvg-id="fictitious.es.la2" tvg-name="La 2 HD" tvg-logo="https://fictitious-cdn.test/logos/la2.png" group-title="Nacionales", La 2 HD Ficticio
http://fictitious-iptv.test:8080/live/user_demo/pass_demo/1002.m3u8
#EXTINF:-1 tvg-id="fictitious.es.antena3" tvg-name="Antena 3" tvg-logo="https://fictitious-cdn.test/logos/antena3.png" group-title="Generales", Antena 3 HD Ficticio
http://fictitious-iptv.test:8080/live/user_demo/pass_demo/1003.m3u8
#EXTINF:-1 tvg-id="fictitious.radio.rne" tvg-name="Radio Nacional" tvg-logo="https://fictitious-cdn.test/logos/rne.png" radio="true" group-title="Radio", Radio Nacional Ficticia
http://fictitious-iptv.test:8080/live/user_demo/pass_demo/2001.mp3`;

export const TEST_UTF8 = `#EXTM3U name="UTF-8 Multilingual & Special Chars Test"
#EXTINF:-1 tvg-id="cctv1.cn" tvg-name="中央电视台-综合" tvg-logo="https://fictitious-cdn.test/logos/cctv1.png" group-title="中国频道 (Chinese)", 🇨🇳 CCTV-1 综合频道 HD (中文)
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/3001.m3u8?lang=zh_CN&codec=h264&token=eyJhbGciOiJIUzI1NiJ9==
#EXTINF:-1 tvg-id="cctv6.cn" tvg-name="电影频道" tvg-logo="https://fictitious-cdn.test/logos/cctv6.png" group-title="中国频道 (Chinese)", 🎬 电影频道 CCTV-6 旗舰高清
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/3002.m3u8?auth_token=ZGF0YV9leGFtcGxl==&source=shanghai&quality=1080p
#EXTINF:-1 tvg-id="esp.cine.accion" tvg-name="Acción & Emoción HD" tvg-logo="https://fictitious-cdn.test/logos/accion.png" group-title="Películas | Español", 🍿 Películas de Acción: "El Gran Escape" & 'La Venganza' (Edición 2026)
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/4001.m3u8?region=es&subtitles=español&audio=ac3&bitrate=8000
#EXTINF:-1 tvg-id="esp.noticias.vivo" tvg-name="¡Noticias en Vivo! España" tvg-logo="https://fictitious-cdn.test/logos/news.png" group-title="Noticias & Actualidad", 🔴 ¡ÚLTIMA HORA! ¿Qué ocurrió en España? — Señal 24h
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/4002.m3u8?type=live&priority=high&session=demo_sess_123=
#EXTINF:-1 tvg-id="esp.deportes.futbol" tvg-name="Fútbol & Pasión VIP" tvg-logo="https://fictitious-cdn.test/logos/laliga.png" group-title="⚽ Deportes en Vivo", 🏆 Fútbol Total: Niño Maravilla 'El Clásico' 2026 ⚡
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/4003.m3u8?league=la_liga&match=real_barca&token_val=ABC==DEF&p1=1&p2=2
#EXTINF:-1 tvg-id="quote.test.nested" tvg-name="Canal \"Super\" 'Cine'" tvg-logo="https://fictitious-cdn.test/logos/quote.png" group-title="Cine \"Especial\"", Canal "Doble Comilla" y 'Comilla Simple'
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/4004.m3u8?query="test"&quote='single'&and=1&equal=2==`;

export const TEST_LOGO = `#EXTM3U
#EXTINF:-1 tvg-id="logo.query.params" tvg-name="Logo con Params" tvg-logo="https://fictitious-cdn.test/images/icon.png?v=2.5&size=512x512&fmt=webp&token=secret_key==&auth=1" group-title="Test Logos", Canal Logo Con Ampersand y Query
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/5001.m3u8
#EXTINF:-1 tvg-id="logo.encoded" tvg-name="Logo URL Encodificada" tvg-logo="https://fictitious-cdn.test/get_logo.php?channel_name=Espa%C3%B1a%201&category=Noticias%20%26%20M%C3%BAsica" group-title="Test Logos", Canal Logo Encodificado
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/5002.m3u8
#EXTINF:-1 tvg-id="logo.empty" tvg-name="Logo Vacío" tvg-logo="" group-title="Test Logos", Canal con Logo Atributo Vacío
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/5003.m3u8
#EXTINF:-1 tvg-id="logo.none" tvg-name="Sin Atributo Logo" group-title="Test Logos", Canal Sin Atributo Logo
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/5004.m3u8
#EXTINF:-1 tvg-id="logo.spaces" tvg-name="Logo con Espacios" tvg-logo=" https://fictitious-cdn.test/logos/spaced_logo.png " group-title="Test Logos", Canal Logo Con Espacios
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/5005.m3u8`;

export const TEST_GROUPS = `#EXTM3U
#EXTINF:-1 tvg-id="grp.attr.only" tvg-name="Grupo Atributo" group-title="Noticias Internacionales", Canal con group-title Único
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/6001.m3u8
#EXTINF:-1 tvg-id="grp.override" tvg-name="Grupo Override" group-title="Grupo Inicial", Canal con Override #EXTGRP
#EXTGRP:Grupo Primario Sobrescrito
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/6002.m3u8
#EXTINF:-1 tvg-id="grp.extgrp.only" tvg-name="Solo EXTGRP", Canal Sin group-title pero Con #EXTGRP
#EXTGRP:Deportes Exclusivos
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/6003.m3u8
#EXTINF:-1 tvg-id="grp.none" tvg-name="Sin Grupo", Canal Sin Ningún Grupo Definido
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/6004.m3u8
#EXTINF:-1 tvg-id="grp.complex" tvg-name="Grupo Complejo" group-title="Cine & Series, Temporada '2026' (4K UHD)", Canal con Grupo Caracteres Especiales
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/6005.m3u8`;

export const TEST_EXTVLCOPT = `#EXTM3U
#EXTINF:-1 tvg-id="vlc.opt.full" tvg-name="VLC Full Options" group-title="VLC Test", Canal con Headers VLC Completos
#EXTVLCOPT:http-user-agent=LelouchPlayer/2.5.0 (Linux; Android TV 14; Build/UP1A) AppleWebKit/537.36
#EXTVLCOPT:http-referrer=https://secure-stream.fictitious-iptv.test/player?session=abc123xyz==&client=tvbox
#EXTVLCOPT:http-cookie=session_id=fake_cookie_998877; device_token=dev_token_445566==; auth=true
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/7001.m3u8
#EXTINF:-1 tvg-id="vlc.opt.case" tvg-name="VLC Case Insensitive" group-title="VLC Test", Canal con Directivas en Mayúsculas
#EXTVLCOPT:HTTP-USER-AGENT=Mozilla/5.0 (SmartHub; SMART-TV; Linux/SmartTV)
#EXTVLCOPT:HTTP-REFERER=https://portal.fictitious-iptv.test/index.html
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/7002.m3u8
#EXTINF:-1 tvg-id="vlc.opt.short" tvg-name="VLC Short Syntax" group-title="VLC Test", Canal con Sintaxis Corta de VLC
#EXTVLCOPT:user-agent=CustomExoPlayer/2.18
#EXTVLCOPT:referer=https://fictitious-iptv.test/live
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/7003.m3u8`;

export const TEST_KODIPROP = `#EXTM3U
#EXTINF:-1 tvg-id="kodi.dash.widevine" tvg-name="DASH Widevine Channel" group-title="DRM Kodi", Canal DASH con Widevine DRM
#KODIPROP:inputstream=inputstream.adaptive
#KODIPROP:inputstream.adaptive.manifest_type=mpd
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.fictitious-iptv.test/widevine/proxy?session_id=fake_sess==&device=tv
#KODIPROP:inputstream.adaptive.stream_headers=User-Agent=LelouchKodi/1.0&Referer=https://portal.fictitious.test
http://fictitious-iptv.test:8080/drm/live/fake_user/fake_pass/8001.mpd
#EXTINF:-1 tvg-id="kodi.clearkey" tvg-name="ClearKey Channel" group-title="DRM Kodi", Canal HLS con ClearKey
#KODIPROP:inputstream=inputstream.adaptive
#KODIPROP:inputstream.adaptive.manifest_type=hls
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","k":"MDEyMzQ1Njc4OWFiY2RlZg==","kid":"MDEyMzQ1Njc4OWFiY2RlZg=="}]}
http://fictitious-iptv.test:8080/drm/live/fake_user/fake_pass/8002.m3u8`;

export const TEST_CATCHUP = `#EXTM3U
#EXTINF:-1 tvg-id="catchup.default" tvg-name="Catchup Default 7d" catchup="default" catchup-days="7" catchup-source="http://fictitious-iptv.test:8080/streaming/timeshift.php?username=fake_user&password=fake_pass&stream=9001&duration={duration}&start={utc:Y-m-d:H-M}" group-title="Catchup Channels", Canal con Catchup Xtream 7 Días
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/9001.m3u8
#EXTINF:-1 tvg-id="catchup.hours" tvg-name="Catchup 48h" catchup="shift" catchup-hours="48" catchup-source="http://fictitious-iptv.test:8080/timeshift?offset={offset}&stream=9002" group-title="Catchup Channels", Canal con Catchup en Horas
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/9002.m3u8
#EXTINF:-1 tvg-id="catchup.legacy" tvg-name="Legacy TV Archive" tv-archive="1" tv-archive-duration="5" group-title="Catchup Channels", Canal con Archivo TV Legacy
http://fictitious-iptv.test:8080/live/fake_user/fake_pass/9003.m3u8`;

export const TEST_DUPLICATES = `#EXTM3U
#EXTINF:-1 tvg-id="espn.es" tvg-name="ESPN Deportes HD" group-title="Deportes", ESPN Deportes HD (Servidor Alfa)
http://server-alpha.fictitious-iptv.test:8080/live/user1/pass1/100.m3u8
#EXTINF:-1 tvg-id="espn.es" tvg-name="ESPN Deportes HD" group-title="Deportes", ESPN Deportes HD (Servidor Beta)
http://server-beta.fictitious-iptv.test:8080/live/user2/pass2/200.m3u8
#EXTINF:-1 tvg-id="espn.es" tvg-name="ESPN Deportes HD" group-title="Deportes", ESPN Deportes HD (Duplicado Exacto Servidor Alfa)
http://server-alpha.fictitious-iptv.test:8080/live/user1/pass1/100.m3u8
#EXTINF:-1 tvg-id="espn.es" tvg-name="ESPN Deportes HD" group-title="Deportes", ESPN Deportes HD (Duplicado Normalizado con Slash)
http://server-alpha.fictitious-iptv.test:8080/live/user1/pass1/100.m3u8/
#EXTINF:-1 tvg-id="fox.sports" tvg-name="Fox Sports 1" group-title="Deportes", Fox Sports 1 Único
http://server-alpha.fictitious-iptv.test:8080/live/user1/pass1/300.m3u8`;

export const TEST_MOVIES = `#EXTM3U
#EXTINF:7200 tvg-id="movie.inception.2010" tvg-name="Inception (2010)" tvg-logo="https://fictitious-cdn.test/posters/inception.jpg" group-title="Películas | Ciencia Ficción", Origen (Inception) (2010)
http://fictitious-iptv.test:8080/movie/fake_user/fake_pass/1101.mp4
#EXTINF:6480 tvg-id="movie.gladiator.2000" tvg-name="Gladiator (2000)" tvg-logo="https://fictitious-cdn.test/posters/gladiator.jpg" group-title="Películas | Acción", Gladiator (El Gladiador) (2000)
http://fictitious-iptv.test:8080/movie/fake_user/fake_pass/1102.mkv
#EXTINF:5400 tvg-id="movie.coco.2017" tvg-name="Coco (2017)" tvg-logo="https://fictitious-cdn.test/posters/coco.jpg" group-title="Películas | Animación", Coco (2017) Español Latino
http://fictitious-iptv.test:8080/movie/fake_user/fake_pass/1103.mp4`;

export const TEST_SERIES = `#EXTM3U
#EXTINF:2820 tvg-id="series.bb.s01e01" tvg-name="Breaking Bad S01 E01" tvg-logo="https://fictitious-cdn.test/series/bb.jpg" group-title="Series | Drama", Breaking Bad - S01E01 - Pilot
http://fictitious-iptv.test:8080/series/fake_user/fake_pass/2201.mkv
#EXTINF:2880 tvg-id="series.bb.s01e02" tvg-name="Breaking Bad S01 E02" tvg-logo="https://fictitious-cdn.test/series/bb.jpg" group-title="Series | Drama", Breaking Bad - S01E02 - Cat's in the Bag...
http://fictitious-iptv.test:8080/series/fake_user/fake_pass/2202.mkv
#EXTINF:1320 tvg-id="series.friends.s01e01" tvg-name="Friends S01 E01" tvg-logo="https://fictitious-cdn.test/series/friends.jpg" group-title="Series | Comedia", Friends - S01E01 - The One Where Monica Gets a Roommate
http://fictitious-iptv.test:8080/series/fake_user/fake_pass/3301.mp4`;

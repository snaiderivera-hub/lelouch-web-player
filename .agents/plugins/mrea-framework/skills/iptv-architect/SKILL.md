---
name: iptv-architect
description: Specialized IPTV Data Architect skill containing comprehensive specifications for Xtream Codes API, video player integrations with Hls.js, M3U generator, EPG timeline grids, and multi-device streaming patterns.
---

# IPTV Data Architect — Specialized Knowledge Base & Skill

This skill provides expert patterns, API endpoints, video player integration guidelines, and M3U/EPG generator algorithms synthesized from 6 core IPTV repositories:
1. `worldofiptvcom/xtream-codes-api` & `xtream-codes-api-documentation`
2. `ovosimpatico/xtream2m3u`
3. `tellytv/go.xtream-codes`
4. `humolot/Api-PHP-Xtream-Codes`
5. `jvdillon/netv`

---

## 1. Xtream Codes API Specifications

### Core Endpoints
- **Player API**: `/player_api.php?username={user}&password={pass}`
  - Account info & server details
  - `action=get_live_categories`, `action=get_live_streams&category_id={id}`
  - `action=get_vod_categories`, `action=get_vod_streams&category_id={id}`, `action=get_vod_info&vod_id={id}`
  - `action=get_series_categories`, `action=get_series&category_id={id}`, `action=get_series_info&series_id={id}`
  - `action=get_short_epg&stream_id={id}&limit={n}`
  - `action=get_simple_data_table&stream_id={id}`
- **Playlist Generator**: `/get.php?username={user}&password={pass}&type=m3u_plus&output=m3u8`
- **EPG / XMLTV**: `/xmltv.php?username={user}&password={pass}`
- **MAG / Stalker**: `/portal.php`
- **Enigma2**: `/enigma2.php`

### Stream URL Patterns
- **Live TV**: `http://{server}:{port}/live/{user}/{pass}/{stream_id}.m3u8` (or `.ts`)
- **VOD Movies**: `http://{server}:{port}/movie/{user}/{pass}/{stream_id}.{container_extension}`
- **TV Series**: `http://{server}:{port}/series/{user}/{pass}/{stream_id}.{container_extension}`
- **Catchup / Timeshift**: `http://{server}:{port}/timeshift/{user}/{pass}/{duration}/{start_time}/{stream_id}.m3u8`

---

## 2. Integrated Web Video Player Architecture (HLS.js + HTML5)

To stream HLS (`.m3u8`) directly inside the browser:
- Include CDN `https://cdn.jsdelivr.net/npm/hls.js@latest`
- Fallback to native `<video>` playback for Safari/iOS.
- CORS proxy wrapping for stream URL requests when needed.
- Player Controls:
  - Play/Pause, Mute/Volume slider, Fullscreen, Picture-in-Picture.
  - Quality Level Selector (`hls.levels`), Audio Track Selector (`hls.audioTracks`), Subtitles Track Selector (`hls.subtitleTracks`).
  - Resolution badge overlay (`720p`, `1080p`, `4K`).
  - Keyboard Navigation: Space (Play/Pause), F (Fullscreen), M (Mute), Left/Right (Seek 5s), Up/Down (Volume).

---

## 3. M3U Playlist & EPG Generator Engine

- Standard M3U header: `#EXTM3U x-tvg-url="http://{proxy}/xmltv.php"`
- Entry format:
  ```
  #EXTINF:-1 tvg-id="{epg_id}" tvg-name="{name}" tvg-logo="{icon}" group-title="{category}",{name}
  http://{server}/live/{user}/{pass}/{stream_id}.m3u8
  ```
- Catchup tags for timeshift: `catchup="default" catchup-days="{tv_archive_duration}" catchup-source="http://{server}/timeshift/{user}/{pass}/{duration}/{start}/{stream_id}.m3u8"`

---

## 4. EPG Grid & Timeline UI Pattern

- Timeline header showing time slots (e.g., 18:00, 18:30, 19:00, 19:30).
- Channel rows displaying current program, progress bar (elapsed %), and upcoming shows.
- Interactive click to switch channel or play stream directly in the player modal.

---

## 5. VOD & TV Series Navigator

- Detailed VOD modal with backdrop image, plot summary, director, cast, TMDB rating, duration, and direct "Reproducir" button.
- TV Series Modal: Season selector tab, Episode list with thumbnails, episode title, release date, and direct play action per episode.

---

## 6. Detailed Documentation References

For full documentation files, consult:
- `knowledge_base/01_XTREAM_CODES_API_SPEC.md`
- `knowledge_base/02_M3U_CONVERTER_SPECS.md`
- `knowledge_base/03_VIDEO_PLAYER_ARCHITECTURE.md`
- `knowledge_base/04_NETV_UI_PATTERNS.md`
- `knowledge_base/05_EPG_PARSER_SPECS.md`

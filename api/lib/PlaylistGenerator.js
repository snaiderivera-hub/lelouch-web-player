/**
 * @module PlaylistGenerator (Serverless Lib)
 * Generador canónico de M3U y JSON Manifests para /api/playlist.
 */

export function unwrapProxyUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') return '';
  const trimmed = rawUrl.trim();
  if (trimmed.includes('/api/proxy') || trimmed.includes('/proxy?target=')) {
    try {
      const match = trimmed.match(/[?&]target=([^&]+)/);
      if (match && match[1]) {
        return decodeURIComponent(match[1]);
      }
    } catch {
      // Ignorar fallo de decodificación
    }
  }
  return trimmed;
}

export class PlaylistGenerator {
  static convertToParserPlaylist(items, options = {}) {
    const {
      playlistName = 'Mi Lista LELOUCH',
      epgUrl = null
    } = options;

    const validItems = Array.isArray(items) 
      ? items.filter(x => x.isEnabled !== false) 
      : [];

    const headerAttrs = {};
    if (playlistName) headerAttrs['name'] = playlistName;
    if (epgUrl) headerAttrs['x-tvg-url'] = epgUrl;

    return {
      header: {
        attrs: headerAttrs,
        raw: `#EXTM3U name="${playlistName}"`
      },
      items: validItems.map((item, idx) => ({
        name: item.name || 'Canal',
        url: unwrapProxyUrl(item.streamUrl || item.directUrl || item.url || ''),
        tvg: {
          id: item.tvgId || item.epgId || '',
          name: item.tvgName || item.epgName || item.name || '',
          logo: item.logo || item.directLogo || '',
          country: item.country || '',
          language: item.language || '',
          rec: item.extraAttributes?.['tvg-rec'] || '',
          shift: item.extraAttributes?.['tvg-shift'] || ''
        },
        group: {
          title: item.group || item.categoryName || item.category || 'General'
        },
        http: {
          referrer: item.headers?.referrer || item.headers?.Referer || '',
          'user-agent': item.headers?.userAgent || item.headers?.['User-Agent'] || ''
        },
        catchup: item.catchup || null,
        timeshift: item.catchup?.days ? String(item.catchup.days) : '',
        kodiProps: item.kodiProps || {},
        extraAttributes: item.extraAttributes || {},
        line: idx + 1,
        raw: ''
      }))
    };
  }

  static generateM3U(input, options = {}) {
    let playlist = input;
    if (Array.isArray(input)) {
      playlist = this.convertToParserPlaylist(input, options);
    }

    if (!playlist || typeof playlist !== 'object') return '#EXTM3U\n';

    const {
      playlistName = playlist.header?.attrs?.name || 'Mi Lista LELOUCH',
      epgUrl = playlist.header?.attrs?.['x-tvg-url'] || playlist.header?.attrs?.['url-tvg'] || null,
      includeCatchup = true,
      includeVlcOpts = true,
      includeKodiProps = true,
      includeExtGrp = false
    } = options;

    const items = Array.isArray(playlist.items) ? playlist.items : [];
    const blocks = [];

    let header = '#EXTM3U';
    if (playlistName) header += ` name="${this._escapeAttr(playlistName)}"`;
    if (epgUrl) header += ` x-tvg-url="${this._escapeAttr(epgUrl)}"`;
    blocks.push(header);

    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const streamUrl = it.url;
      if (!streamUrl) continue;

      const duration = -1;
      let extinf = `#EXTINF:${duration}`;

      const tvgId = it.tvg?.id;
      if (tvgId) extinf += ` tvg-id="${this._escapeAttr(tvgId)}"`;

      const tvgName = it.tvg?.name || it.name;
      if (tvgName) extinf += ` tvg-name="${this._escapeAttr(tvgName)}"`;

      const logo = it.tvg?.logo;
      if (logo) extinf += ` tvg-logo="${this._escapeAttr(logo)}"`;

      const country = it.tvg?.country;
      if (country) extinf += ` tvg-country="${this._escapeAttr(country)}"`;

      const language = it.tvg?.language;
      if (language) extinf += ` tvg-language="${this._escapeAttr(language)}"`;

      const group = it.group?.title || 'General';
      extinf += ` group-title="${this._escapeAttr(group)}"`;

      if (includeCatchup && it.catchup) {
        const catchupType = it.catchup.type || 'default';
        const catchupDays = it.catchup.days || 7;
        extinf += ` tv-archive="1" tv-archive-duration="${catchupDays}" catchup="${catchupType}"`;
        if (it.catchup.hours) extinf += ` catchup-hours="${it.catchup.hours}"`;
        if (it.catchup.source) extinf += ` catchup-source="${this._escapeAttr(it.catchup.source)}"`;
      }

      if (it.extraAttributes && typeof it.extraAttributes === 'object') {
        for (const [k, v] of Object.entries(it.extraAttributes)) {
          if (v !== undefined && v !== null && !extinf.includes(` ${k}=`)) {
            extinf += ` ${k}="${this._escapeAttr(v)}"`;
          }
        }
      }

      const itemLines = [`${extinf},${it.name || 'Canal'}`];

      if (includeExtGrp && group) {
        itemLines.push(`#EXTGRP:${group}`);
      }

      if (includeKodiProps && it.kodiProps && typeof it.kodiProps === 'object') {
        for (const [k, v] of Object.entries(it.kodiProps)) {
          itemLines.push(`#KODIPROP:${k}=${v}`);
        }
      }

      if (includeVlcOpts) {
        const ua = it.http?.['user-agent'];
        const ref = it.http?.referrer;
        if (ua) itemLines.push(`#EXTVLCOPT:http-user-agent=${ua}`);
        if (ref) itemLines.push(`#EXTVLCOPT:http-referrer=${ref}`);
      }

      itemLines.push(streamUrl);
      blocks.push(itemLines.join('\n'));
    }

    return blocks.join('\n\n') + '\n';
  }

  static generateManifestJson(items, meta = {}) {
    const validItems = Array.isArray(items) ? items.filter(x => x.isEnabled !== false) : [];
    
    const manifest = {
      name: meta.name || 'Mi Lista LELOUCH',
      version: meta.version || 1,
      totalCount: validItems.length,
      updatedAt: meta.updatedAt || new Date().toISOString(),
      items: validItems.map(item => ({
        id: item.id,
        sourceId: item.sourceId || null,
        providerId: item.providerId || item.catalogItemId || null,
        name: item.name,
        originalName: item.originalName || null,
        mediaType: item.mediaType || item.type,
        group: item.group || item.categoryName,
        logo: item.logo || null,
        streamUrl: unwrapProxyUrl(item.streamUrl || item.directUrl || item.url || ''),
        tvgId: item.tvgId || item.epgId || null,
        tvgName: item.tvgName || item.epgName || null,
        containerExtension: item.containerExtension || null,
        providerOrder: item.providerOrder ?? item.sortOrder ?? 0
      }))
    };

    return JSON.stringify(manifest, null, 2);
  }

  static _escapeAttr(val) {
    if (!val) return '';
    return String(val).replace(/"/g, "'").replace(/[\r\n]+/g, ' ').trim();
  }
}

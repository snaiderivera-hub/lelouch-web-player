/**
 * @module ChannelClassifierService
 * Servicio independiente de clasificación e inferencia heurística para canales IPTV.
 * Analiza nombres de canales, categorías y metadatos para extraer:
 * - Calidad de resolución (4K, FHD 1080p, HD 720p, SD, HEVC)
 * - Género temático (Deportes, Noticias, Cine/Películas, Infantil, Documentales, Música, etc.)
 * - País / Idioma inferido (España, México, EE.UU., Argentina, Colombia, Alemania, etc.)
 * - Nombre limpio normalizado (sin prefijos de país ni etiquetas de códec)
 * 
 * Opera 100% en memoria de forma determinista y sin consumo de red.
 */

export class ChannelClassifierService {
  /**
   * Clasifica un canal individual y retorna sus metadatos inferidos.
   * @param {{ name?: string, title?: string, category_name?: string, categoryName?: string }} channel
   */
  classify(channel) {
    if (!channel) return this._getDefaultClassification();

    const rawName = String(channel.name || channel.title || '').trim();
    const rawCategory = String(channel.category_name || channel.categoryName || '').trim();

    const quality = this._detectQuality(rawName);
    const codec = this._detectCodec(rawName);
    const genre = this._detectGenre(rawName, rawCategory);
    const country = this._detectCountry(rawName, rawCategory);
    const cleanName = this._cleanChannelName(rawName);

    const tags = [];
    if (quality !== 'SD') tags.push(quality);
    if (codec !== 'STANDARD') tags.push(codec);
    if (genre !== 'GENERAL') tags.push(genre);
    if (country) tags.push(country.code);

    return {
      cleanName,
      rawName,
      quality,
      codec,
      genre,
      country: country ? country.name : null,
      countryCode: country ? country.code : null,
      isSports: genre === 'SPORTS',
      isNews: genre === 'NEWS',
      isMovies: genre === 'MOVIES',
      isKids: genre === 'KIDS',
      tags
    };
  }

  /**
   * Clasifica una lista completa de canales en memoria.
   * @param {Array} channels
   * @returns {Array} canales con propiedad `classification` adjunta
   */
  classifyBatch(channels = []) {
    return channels.map(ch => ({
      ...ch,
      classification: this.classify(ch)
    }));
  }

  // ── Heurísticas privadas ──

  _detectQuality(name) {
    const n = name.toUpperCase();
    if (/\b(4K|UHD|2160P)\b/.test(n)) return '4K';
    if (/\b(FHD|1080P|1080I|FULL\s*HD)\b/.test(n)) return 'FHD';
    if (/\b(HD|720P)\b/.test(n)) return 'HD';
    return 'SD';
  }

  _detectCodec(name) {
    const n = name.toUpperCase();
    if (/\b(HEVC|H\.?265|X265)\b/.test(n)) return 'HEVC';
    if (/\b(H\.?264|AVC|X264)\b/.test(n)) return 'H264';
    return 'STANDARD';
  }

  _detectGenre(name, category) {
    const combined = `${name} ${category}`.toUpperCase();

    // Deportes
    if (/\b(SPORT|SPORTS|DEPORTE|DEPORTES|FUTBOL|FOOTBALL|SOCCER|ESPN|FOX\s*SPORTS|DAZN|LALIGA|PREMIER|NBA|NFL|MLB|F1|FORMULA\s*1|GOL\s*PLAY|EUROSPORT|TUDN|TYC|DIRECTV\s*SPORTS|WIN\s*SPORTS)\b/.test(combined)) {
      return 'SPORTS';
    }

    // Noticias
    if (/\b(NEWS|NOTICIAS|NOTICIERO|INFORMACION|CNN|BBC|EURONEWS|RT\b|AL\s*JAZEERA|24H|INFO|N-TV|WEATHER)\b/.test(combined)) {
      return 'NEWS';
    }

    // Películas / Cine
    if (/\b(CINEMA|CINE|MOVIES|PELICULAS|HBO|STAR\s*CHANNEL|MAX\b|WARNER|PARAMOUNT|SHOWTIME|CANAL\+|GOLDEN|TCM)\b/.test(combined)) {
      return 'MOVIES';
    }

    // Infantil / Niños
    if (/\b(KIDS|CHILDREN|NINOS|DIBUJOS|CARTOON|DISNEY|NICKELODEON|NICK\s*JR|BOOMERANG|BABY\s*TV|CLAN|TOON)\b/.test(combined)) {
      return 'KIDS';
    }

    // Documentales
    if (/\b(DOC|DOCS|DOCUMENTALES|NAT\s*GEO|NATIONAL\s*GEOGRAPHIC|DISCOVERY|HISTORY|ODISEA|ANIMAL\s*PLANET|INVESTIGATION|CIENCIA)\b/.test(combined)) {
      return 'DOCS';
    }

    // Música
    if (/\b(MUSIC|MUSICA|MTV|VH1|TRACE|KISS|SOL\s*MUSICA|DELUXE\s*MUSIC|RADIO)\b/.test(combined)) {
      return 'MUSIC';
    }

    return 'GENERAL';
  }

  _detectCountry(name, category) {
    const combined = `${name} ${category}`.toUpperCase();

    const countryPatterns = [
      { code: 'ES', name: 'España', regex: /\b(ES\b|ESP\b|ESPA[ÑN]A|SPAIN|MOVISTAR|TVE|ANTENA\s*3|TELECINCO|LA\s*SEXTA|CUATRO\b)/ },
      { code: 'MX', name: 'México', regex: /\b(MX\b|MEX\b|MEXICO|M[EÉ]XICO|TELEVISA|AZTECA|LAS\s*ESTRELLAS|FOROTV)/ },
      { code: 'US', name: 'Estados Unidos', regex: /\b(US\b|USA\b|UNITED\s*STATES|ENGLISH|ABC\b|NBC\b|CBS\b|FOX\b)/ },
      { code: 'AR', name: 'Argentina', regex: /\b(AR\b|ARG\b|ARGENTINA|TELEFE|EL\s*TRECE|TN\b|AMERICA\s*TV)/ },
      { code: 'CO', name: 'Colombia', regex: /\b(CO\b|COL\b|COLOMBIA|CARACOL|RCN)/ },
      { code: 'CL', name: 'Chile', regex: /\b(CL\b|CHL\b|CHILE|MEGA\b|CHV|TVN\b)/ },
      { code: 'DE', name: 'Alemania', regex: /\b(DE\b|GER\b|GERMANY|DEUTSCHLAND|ARD\b|ZDF\b|RTL\b|PRO7)/ },
      { code: 'UK', name: 'Reino Unido', regex: /\b(UK\b|GB\b|BRITAIN|BRITISH|ITV\b|CHANNEL\s*4|SKY\s*UK)/ },
      { code: 'IT', name: 'Italia', regex: /\b(IT\b|ITA\b|ITALIA|ITALY|RAI\b|MEDIASET)/ },
      { code: 'FR', name: 'Francia', regex: /\b(FR\b|FRA\b|FRANCE|CANAL\+|TF1|M6\b)/ }
    ];

    for (const cp of countryPatterns) {
      if (cp.regex.test(combined)) {
        return { code: cp.code, name: cp.name };
      }
    }

    return null;
  }

  _cleanChannelName(rawName) {
    if (!rawName) return '';

    let clean = rawName
      // Eliminar prefijos de país tipo: "ES |", "ES:", "[ES]", "DE |", "USA - ", "|ES|"
      .replace(/^(\[?[A-Z]{2,3}\]?\s*[:|•\-_/]\s*)/i, '')
      .replace(/^(\|\s*[A-Z]{2,3}\s*\|\s*)/i, '')
      // Eliminar sufijos de calidad tipo: "FHD", "HD", "[4K]", "(1080p)", "HEVC", "H.265"
      .replace(/(\s*\[?(4K|UHD|FHD|1080P|1080I|HD|720P|SD|HEVC|H\.?265)\]?)+$/i, '')
      // Eliminar números o tags entre paréntesis al final si es pura resolución
      .replace(/\s*\((4K|UHD|FHD|1080P|HD|720P|SD)\)\s*$/i, '')
      // Limpiar dobles espacios o caracteres colgados
      .replace(/\s+/g, ' ')
      .trim();

    return clean || rawName;
  }

  _getDefaultClassification() {
    return {
      cleanName: '',
      rawName: '',
      quality: 'SD',
      codec: 'STANDARD',
      genre: 'GENERAL',
      country: null,
      countryCode: null,
      isSports: false,
      isNews: false,
      isMovies: false,
      isKids: false,
      tags: []
    };
  }
}

export const channelClassifierService = new ChannelClassifierService();

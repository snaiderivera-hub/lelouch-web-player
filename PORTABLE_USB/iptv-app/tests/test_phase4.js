import { M3uParserAdapter } from '../src/services/playlist/M3uParserAdapter.js';
import { PlaylistNormalizer } from '../src/services/playlist/PlaylistNormalizer.js';
import { PlaylistGenerator } from '../src/services/playlist/PlaylistGenerator.js';

const sampleM3u = `#EXTM3U url-tvg="http://epg.provider.com/guide.xml"
#EXTINF:120 tvg-id="espn.hd" tvg-name="ESPN HD" tvg-logo="http://logos.com/espn.png" tvg-country="US" tvg-language="English" group-title="Sports" catchup="flussonic" catchup-source="http://timeshift.server.com/archive/{utc}.m3u8" catchup-days="7" catchup-hours="168" tvg-chno="105" custom-meta="alpha-omega",ESPN HD Live
#EXTGRP:Sports Extra
#EXTVLCOPT:http-user-agent=LelouchPlayer/3.0
#EXTVLCOPT:http-referrer=https://watch.provider.com/auth
#EXTVLCOPT:http-cookie=auth_token=super_secret_cookie_token
#KODIPROP:inputstream.adaptive.manifest_type=hls
#KODIPROP:inputstream.adaptive.license_type=widevine
http://stream.server.com:8080/live/user123/pass456/99999.m3u8
`;

console.log('--- 1. Testing M3uParserAdapter.parse ---');
const parsed = M3uParserAdapter.parse(sampleM3u);
console.log('Parsed header:', parsed.header);
console.log('Parsed items count:', parsed.items.length);
const item = parsed.items[0];

console.log('Duration:', item.duration);
console.log('TVG:', item.tvg);
console.log('Group:', item.group);
console.log('HTTP:', item.http);
console.log('Catchup:', item.catchup);
console.log('KodiProps:', item.kodiProps);
console.log('ExtraAttributes:', item.extraAttributes);
console.log('URL:', item.url);

console.log('\n--- 2. Testing PlaylistNormalizer.fromExternalItem ---');
const canonical = PlaylistNormalizer.fromExternalItem(item, 0, 'source-test');
console.log('Canonical item ID:', canonical.id);
console.log('Canonical duration:', canonical.duration);
console.log('Canonical headers:', canonical.headers);
console.log('Canonical extraAttributes:', canonical.extraAttributes);
console.log('Canonical catchup:', canonical.catchup);
console.log('Canonical kodiProps:', canonical.kodiProps);
console.log('Canonical streamUrl:', canonical.streamUrl);

console.log('\n--- 3. Testing PlaylistGenerator.generateM3U (Round-trip) ---');
const regeneratedM3u = PlaylistGenerator.generateM3U([canonical], {
  playlistName: 'Test Roundtrip',
  includeCatchup: true,
  includeVlcOpts: true,
  includeKodiProps: true,
  includeExtGrp: true
});

console.log('Generated M3U output:\n' + regeneratedM3u);

// Verificaciones
const checks = [
  ['Duration preserved', canonical.duration === 120 && regeneratedM3u.includes('#EXTINF:120')],
  ['tvg-id preserved', canonical.tvgId === 'espn.hd' && regeneratedM3u.includes('tvg-id="espn.hd"')],
  ['tvg-name preserved', canonical.tvgName === 'ESPN HD' && regeneratedM3u.includes('tvg-name="ESPN HD"')],
  ['tvg-logo preserved', canonical.logo === 'http://logos.com/espn.png' && regeneratedM3u.includes('tvg-logo="http://logos.com/espn.png"')],
  ['tvg-country preserved', canonical.country === 'US' && regeneratedM3u.includes('tvg-country="US"')],
  ['tvg-language preserved', canonical.language === 'English' && regeneratedM3u.includes('tvg-language="English"')],
  ['#EXTGRP preserved', canonical.group === 'Sports Extra' && regeneratedM3u.includes('#EXTGRP:Sports Extra')],
  ['User-Agent preserved', canonical.headers.userAgent === 'LelouchPlayer/3.0' && regeneratedM3u.includes('#EXTVLCOPT:http-user-agent=LelouchPlayer/3.0')],
  ['Referer preserved', canonical.headers.referrer === 'https://watch.provider.com/auth' && regeneratedM3u.includes('#EXTVLCOPT:http-referrer=https://watch.provider.com/auth')],
  ['Cookie preserved', canonical.headers.cookie === 'auth_token=super_secret_cookie_token' && regeneratedM3u.includes('#EXTVLCOPT:http-cookie=auth_token=super_secret_cookie_token')],
  ['KodiProps preserved', canonical.kodiProps['inputstream.adaptive.manifest_type'] === 'hls' && regeneratedM3u.includes('#KODIPROP:inputstream.adaptive.manifest_type=hls')],
  ['Catchup type preserved', canonical.catchup.type === 'flussonic' && regeneratedM3u.includes('catchup="flussonic"')],
  ['Catchup days preserved', canonical.catchup.days === 7 && regeneratedM3u.includes('tv-archive-duration="7"')],
  ['Catchup hours preserved', canonical.catchup.hours === 168 && regeneratedM3u.includes('catchup-hours="168"')],
  ['Catchup source preserved', canonical.catchup.source === 'http://timeshift.server.com/archive/{utc}.m3u8' && regeneratedM3u.includes('catchup-source="http://timeshift.server.com/archive/{utc}.m3u8"')],
  ['Unknown attributes preserved (tvg-chno)', canonical.extraAttributes['tvg-chno'] === '105' && regeneratedM3u.includes('tvg-chno="105"')],
  ['Unknown attributes preserved (custom-meta)', canonical.extraAttributes['custom-meta'] === 'alpha-omega' && regeneratedM3u.includes('custom-meta="alpha-omega"')],
  ['URL original preserved', canonical.streamUrl === 'http://stream.server.com:8080/live/user123/pass456/99999.m3u8' && regeneratedM3u.includes('http://stream.server.com:8080/live/user123/pass456/99999.m3u8')]
];

let allPassed = true;
console.log('\n--- VERIFICATION RESULTS ---');
for (const [desc, passed] of checks) {
  console.log(`${passed ? '✅ PASS' : '❌ FAIL'}: ${desc}`);
  if (!passed) allPassed = false;
}

if (!allPassed) {
  process.exit(1);
} else {
  console.log('\n🎉 ALL FASE 4 ATTRIBUTES PRESERVED ACCURATELY!');
}

// 데이터 수집 스크립트 (B1): 정류장 + 지도를 한 번에 만든다.
//
//   npm run collect            → 정류장 추출 + 지도 그리기 (OSM 데이터는 받아 둔 게 있으면 재사용)
//   npm run collect:refresh    → OSM 지도 데이터를 새로 받아서 다시 그리기
//
// 입력
//   data/area.json                              시범지역 범위, 지도 가로 크기, 시범 정류장 ID (승차 거점 + 주거지)
//   data/raw/서울시버스정류소위치정보(*).xlsx       서울 열린데이터광장 OA-15067 (가장 최근 파일을 자동 선택)
//   data/raw/서울시버스노선별정류소정보(*).xlsx     서울 열린데이터광장 OA-1095 (정류장마다 서는 N버스 노선을 알아냄)
// 출력
//   data/stops-all.json   범위 안의 모든 정류소 + 서는 N버스 노선 (시범 정류장 고를 때 참고용)
//   data/stops.json       시범 정류장만 (GET /stops 로 나가는 데이터, 공통 규약 Stop 형식: kind·nightRoutes 포함). 승차 거점이 앞, 주거지가 뒤
//   data/raw/osm-area.json  Overpass API로 받은 OSM 원본 데이터 (한 번 받으면 재사용)
//   data/map.png          지도 이미지 (GET /map/image)
//   data/map.json         지도 정보 (GET /map, 공통 규약 MapInfo 형식)
//   data/map-check.png    확인용: 지도 위에 정류장 점을 찍은 그림 (회색 = 전체, 노랑 = 시범)
//
// 지도는 OSM 타일 서버에서 그림을 받지 않는다 (타일 이용 정책상 미리 받아 두는 것이 금지).
// 대신 OSM 지도 데이터(도로·철도·공원·물·건물)를 Overpass API로 한 번 받아서 우리가 직접 그린다.
// 그래서 범위가 area.json과 정확히 같고, 공통 규약 5의 식으로 찍은 점이 지도와 딱 맞는다.

const fs = require('fs');
const path = require('path');
const { readFirstSheet } = require('./lib/xlsx');
const { toPixel, heightForWidth, distanceM } = require('./lib/geo');

const DATA = path.join(__dirname, '..', 'data');
const RAW = path.join(DATA, 'raw');
const OSM_CACHE = path.join(RAW, 'osm-area.json');
// 첫 번째가 기본 서버, 나머지는 붐빌 때 쓰는 공용 미러. OVERPASS_URL 환경변수로 직접 지정할 수도 있다
const OVERPASS_URLS = process.env.OVERPASS_URL
  ? [process.env.OVERPASS_URL]
  : [
      'https://overpass-api.de/api/interpreter',
      'https://overpass.private.coffee/api/interpreter',
      'https://maps.mail.ru/osm/tools/overpass/api/interpreter',
    ];
const USER_AGENT = 'DalmuriBus-student-project/0.1 (+https://github.com/hoon7964/Project_MP)';
const MIN_PILOT_GAP_M = 300; // 시범 정류장끼리 이보다 가까우면 경고

const refreshOsm = process.argv.includes('--refresh-osm');

function writeJson(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', 'utf8');
}

// ───────────────────────── 1. 정류장 ─────────────────────────

// data/raw/ 에서 이름에 keyword가 들어간 .xlsx 중 가장 최근 것(이름 안 날짜 YYYYMMDD가 가장 큰 것)을 고른다
function findLatestXlsx(keyword) {
  const files = fs
    .readdirSync(RAW)
    .filter((f) => f.toLowerCase().endsWith('.xlsx') && f.includes(keyword) && !f.startsWith('컬럼정의') && !f.startsWith('~$'));
  if (files.length === 0) return null;
  const dateOf = (f) => (f.match(/(\d{8})/) || [])[1] || '0';
  files.sort((a, b) => dateOf(b).localeCompare(dateOf(a)));
  return path.join(RAW, files[0]);
}

const padArs = (v) => {
  const s = String(v).trim();
  return /^\d+$/.test(s) ? s.padStart(5, '0') : s; // 엑셀이 앞자리 0을 지운 경우
};

// 노선별 정류소 파일에서 정류장(ARS) → 서는 N버스 노선 목록
function readNightRoutes(file) {
  const rows = readFirstSheet(file);
  const header = (rows[0] || []).map((h) => String(h ?? '').trim());
  const routeCol = header.findIndex((h) => ['노선명', 'ROUTE_NM', 'RTE_NM'].includes(h));
  const arsCol = header.findIndex((h) => ['ARS_ID', 'ARS-ID', 'STTN_NO'].includes(h));
  if (routeCol < 0 || arsCol < 0) throw new Error(`노선별 정류소 파일에서 노선명·ARS_ID 열을 찾지 못함 (첫 줄: ${header.join(', ')})`);
  const byStop = new Map();
  for (const row of rows.slice(1)) {
    const route = String(row[routeCol] ?? '').trim();
    if (!/^N\d/i.test(route) || row[arsCol] == null) continue; // 심야버스는 노선명이 N으로 시작 (N13, N26 …)
    const ars = padArs(row[arsCol]);
    if (!byStop.has(ars)) byStop.set(ars, new Set());
    byStop.get(ars).add(route.toUpperCase());
  }
  const sortRoutes = (s) => [...s].sort((a, b) => Number(a.slice(1)) - Number(b.slice(1)));
  return new Map([...byStop].map(([k, v]) => [k, sortRoutes(v)]));
}

// 열 이름이 달마다 조금씩 달라서(ARS_ID / STTN_NO 등) 후보 이름으로 찾는다
const COLUMNS = {
  stopId: ['ARS_ID', 'ARS-ID', 'STTN_NO', '정류소번호'],
  name: ['정류소명', 'STTN_NM'],
  lng: ['X좌표', 'CRDNT_X'],
  lat: ['Y좌표', 'CRDNT_Y'],
  type: ['정류소타입', '정류소 유형', 'STTN_TY'],
};

function readStops(file) {
  const rows = readFirstSheet(file);
  const header = (rows[0] || []).map((h) => String(h ?? '').trim());
  const col = {};
  for (const [key, names] of Object.entries(COLUMNS)) {
    col[key] = header.findIndex((h) => names.includes(h));
    if (col[key] < 0 && key !== 'type') {
      throw new Error(`열을 찾지 못함: ${names.join(' / ')} (첫 줄: ${header.join(', ')})`);
    }
  }

  const stops = [];
  for (const row of rows.slice(1)) {
    let stopId = row[col.stopId];
    const lat = Number(row[col.lat]);
    const lng = Number(row[col.lng]);
    if (stopId == null || !Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    stopId = padArs(stopId);
    stops.push({
      stopId,
      name: String(row[col.name] ?? '').trim(),
      lat: Number(lat.toFixed(7)),
      lng: Number(lng.toFixed(7)),
      type: col.type >= 0 ? String(row[col.type] ?? '').trim() : '',
    });
  }
  return stops;
}

function inArea(area, p) {
  return p.lat <= area.north && p.lat >= area.south && p.lng >= area.west && p.lng <= area.east;
}

function collectStops(area) {
  const file = findLatestXlsx('위치정보');
  if (!file) throw new Error('data/raw/ 에 정류소 위치정보 .xlsx 파일이 없음 (서울 열린데이터광장 OA-15067에서 받기)');
  const all = readStops(file);
  if (all.length === 0) throw new Error('정류소를 하나도 읽지 못함: ' + file);

  // 좌표가 위도·경도(WGS84)인지 간단히 확인 (서울: 위도 37.x, 경도 126~127.x)
  const bad = all.filter((s) => s.lat < 37 || s.lat > 38 || s.lng < 126 || s.lng > 128);
  if (bad.length > all.length * 0.01) {
    throw new Error(`좌표가 위도·경도가 아닌 것 같음 (예: ${JSON.stringify(bad[0])}). 명세서의 좌표계를 확인`);
  }

  const routeFile = findLatestXlsx('노선별');
  const nightRoutes = routeFile ? readNightRoutes(routeFile) : new Map();

  const inside = all
    .filter((s) => inArea(area, s) && s.type !== '한강선착장')
    .map((s) => ({ ...s, nightRoutes: nightRoutes.get(s.stopId) || [] }))
    .sort((a, b) => a.stopId.localeCompare(b.stopId));
  writeJson(path.join(DATA, 'stops-all.json'), inside);

  const hubIds = area.hubStopIds || [];
  const homeIds = area.homeStopIds || [];
  const byId = new Map(inside.map((s) => [s.stopId, s]));
  const missing = [...hubIds, ...homeIds].filter((id) => !byId.has(id));
  if (missing.length) {
    throw new Error(`area.json의 시범 정류장이 범위 안에 없음: ${missing.join(', ')} (stops-all.json에서 다시 고르기)`);
  }
  const hubs = hubIds.map((id) => byId.get(id));
  const homes = homeIds.map((id) => byId.get(id));
  const pilots = [...hubs, ...homes];
  // 공통 규약 Stop 형식 그대로: kind = HUB(승차 거점, 파랑) / HOME(주거지, 주황), nightRoutes = 서는 N버스
  const toStop = (kind) => ({ stopId, name, lat, lng, nightRoutes }) => ({ stopId, name, lat, lng, kind, nightRoutes });
  const stops = [...hubs.map(toStop('HUB')), ...homes.map(toStop('HOME'))];
  writeJson(path.join(DATA, 'stops.json'), stops);

  const warnings = [];
  if (!routeFile) warnings.push('노선별 정류소 파일이 없어 N버스 노선을 확인하지 못함 (서울 열린데이터광장 OA-1095)');
  else hubs.filter((h) => h.nightRoutes.length === 0).forEach((h) => warnings.push(`승차 거점인데 N버스가 안 섬: ${h.stopId} ${h.name}`));
  for (let i = 0; i < pilots.length; i++) {
    for (let j = i + 1; j < pilots.length; j++) {
      const d = distanceM(pilots[i], pilots[j]);
      if (d < MIN_PILOT_GAP_M) {
        warnings.push(`${MIN_PILOT_GAP_M}m보다 가까움: ${pilots[i].stopId} ${pilots[i].name} ↔ ${pilots[j].stopId} ${pilots[j].name}: ${Math.round(d)}m`);
      }
    }
  }
  const areaNightRoutes = [...new Set(inside.flatMap((s) => s.nightRoutes))].sort((a, b) => Number(a.slice(1)) - Number(b.slice(1)));
  return { file, routeFile, totalCount: all.length, inside, hubs, homes, pilots, areaNightRoutes, warnings };
}

// ───────────────────────── 2. 지도 ─────────────────────────

async function loadOsm(area) {
  if (fs.existsSync(OSM_CACHE) && !refreshOsm) {
    return { osm: JSON.parse(fs.readFileSync(OSM_CACHE, 'utf8')), fromCache: true };
  }
  // 가장자리에서 잘리는 도로도 끝까지 그려지게 범위를 조금 넓혀서 받는다
  const m = 0.002;
  const bbox = `${area.south - m},${area.west - m},${area.north + m},${area.east + m}`; // Overpass 순서: 남,서,북,동
  const query = `[out:json][timeout:120];
(
  way["highway"](${bbox});
  way["railway"~"^(rail|subway|light_rail)$"](${bbox});
  way["waterway"](${bbox});
  way["building"](${bbox});
  way["natural"~"^(water|wood|scrub|grassland)$"](${bbox});
  way["leisure"~"^(park|garden|playground|pitch|nature_reserve)$"](${bbox});
  way["landuse"~"^(grass|forest|recreation_ground|meadow|cemetery|village_green|reservoir|basin)$"](${bbox});
  relation["type"="multipolygon"]["natural"~"^(water|wood|scrub|grassland)$"](${bbox});
  relation["type"="multipolygon"]["leisure"~"^(park|garden|nature_reserve)$"](${bbox});
  relation["type"="multipolygon"]["landuse"~"^(grass|forest|recreation_ground|meadow|cemetery|reservoir)$"](${bbox});
  relation["type"="multipolygon"]["building"](${bbox});
  relation["type"="multipolygon"]["waterway"="riverbank"](${bbox});
);
out geom;`;

  console.log('  OSM 지도 데이터 받는 중 (Overpass API, 수십 초 걸릴 수 있음)...');
  // 공용 Overpass 서버는 자주 붐빈다(429·502·503·504). 기다렸다가 다시, 그래도 안 되면 다른 서버로
  const tries = [
    { url: OVERPASS_URLS[0], waitBefore: 0 },
    { url: OVERPASS_URLS[0], waitBefore: 20 },
    ...OVERPASS_URLS.slice(1).map((url) => ({ url, waitBefore: 5 })),
    { url: OVERPASS_URLS[0], waitBefore: 60 },
  ];
  const errors = [];
  for (const { url, waitBefore } of tries) {
    if (waitBefore) {
      console.log(`  ${waitBefore}초 기다렸다가 다시: ${new URL(url).host}`);
      await new Promise((r) => setTimeout(r, waitBefore * 1000));
    }
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': USER_AGENT },
        body: 'data=' + encodeURIComponent(query),
        signal: AbortSignal.timeout(180_000),
      });
      if (!res.ok) {
        const text = (await res.text()).replace(/\s+/g, ' ').slice(0, 80);
        errors.push(`${new URL(url).host} ${res.status} ${text}`);
        console.log(`  ⚠ ${new URL(url).host} 응답 ${res.status} (서버가 바쁨)`);
        continue;
      }
      const osm = await res.json();
      if (!Array.isArray(osm.elements) || osm.elements.length === 0) {
        errors.push(`${new URL(url).host} 빈 결과`);
        continue;
      }
      fs.writeFileSync(OSM_CACHE, JSON.stringify(osm), 'utf8');
      return { osm, fromCache: false, source: new URL(url).host };
    } catch (e) {
      errors.push(`${new URL(url).host} ${e.cause?.code || e.name || e.message}`);
      console.log(`  ⚠ ${new URL(url).host} 접속 실패 (${e.cause?.code || e.name || e.message})`);
    }
  }
  throw new Error(
    'Overpass API 서버가 모두 바쁘거나 접속이 안 됨. 몇 분 뒤 npm run collect 를 다시 실행\n  ' + errors.join('\n  ')
  );
}

// 다크 테마 지도 색 (앱이 다크 테마라서 맞춤. 승차 블루·하차 골드 표시가 잘 보이게 차분한 색만 씀)
const STYLE = {
  background: '#0e1320',
  green: '#13261d',
  water: '#112a49',
  waterway: { color: '#163559', width: 6 },
  building: '#1a2234',
  roads: [
    // 아래부터 위로 그린다 (큰 길이 위)
    { classes: ['service'], color: '#252d3d', width: 2.5 },
    { classes: ['residential', 'unclassified', 'living_street', 'road'], color: '#2f3849', width: 5 },
    { classes: ['tertiary', 'tertiary_link'], color: '#3d4759', width: 7 },
    { classes: ['secondary', 'secondary_link'], color: '#4b566b', width: 9 },
    { classes: ['primary', 'primary_link'], color: '#5a667d', width: 11 },
    { classes: ['trunk', 'trunk_link', 'motorway', 'motorway_link'], color: '#66738c', width: 12 },
  ],
  roadTunnel: { color: '#3a4357', width: 4, dash: '10 8' },
  rail: { color: '#7a6a99', width: 3.5 },
  railTunnel: { color: '#5a5070', width: 2.5, dash: '12 10' },
};

const GREEN = {
  leisure: ['park', 'garden', 'playground', 'pitch', 'nature_reserve'],
  landuse: ['grass', 'forest', 'recreation_ground', 'meadow', 'cemetery', 'village_green'],
  natural: ['wood', 'scrub', 'grassland'],
};

function isTunnel(t) {
  return (t.tunnel && t.tunnel !== 'no') || t.covered === 'yes' || Number(t.layer) < 0;
}

// OSM 태그 → 그릴 층
function classify(t) {
  if (t.natural === 'water' || t.waterway === 'riverbank' || ['reservoir', 'basin'].includes(t.landuse)) return 'water';
  if (Object.entries(GREEN).some(([k, vs]) => vs.includes(t[k]))) return 'green';
  if (t.building && t.building !== 'no') return 'building';
  if (['river', 'stream', 'canal', 'drain', 'ditch'].includes(t.waterway)) return 'waterway';
  if (['rail', 'subway', 'light_rail'].includes(t.railway)) return isTunnel(t) ? 'railTunnel' : 'rail';
  if (t.highway && t.area !== 'yes') {
    const cls = STYLE.roads.find((r) => r.classes.includes(t.highway));
    if (!cls) return null; // 보행로·자전거길 등은 안 그림
    return isTunnel(t) ? 'roadTunnel' : 'road:' + t.highway;
  }
  return null;
}

const samePoint = (a, b) => a.lat === b.lat && a.lon === b.lon;

// 멀티폴리곤 관계의 조각난 선분들을 이어 붙여 닫힌 고리들로 만든다
function assembleRings(segments) {
  const rest = segments.filter((s) => s.length >= 2).map((s) => s.slice());
  const rings = [];
  while (rest.length) {
    let ring = rest.shift();
    let grown = true;
    while (!samePoint(ring[0], ring[ring.length - 1]) && grown) {
      grown = false;
      const end = ring[ring.length - 1];
      for (let i = 0; i < rest.length; i++) {
        const s = rest[i];
        if (samePoint(s[0], end)) ring = ring.concat(s.slice(1));
        else if (samePoint(s[s.length - 1], end)) ring = ring.concat(s.slice().reverse().slice(1));
        else continue;
        rest.splice(i, 1);
        grown = true;
        break;
      }
    }
    if (ring.length >= 3) rings.push(ring); // 끝까지 못 이은 고리도 닫힌 것으로 보고 그린다
  }
  return rings;
}

function buildMapSvg(osm, map) {
  const layers = { green: [], water: [], waterway: [], building: [], roadTunnel: [], rail: [], railTunnel: [] };
  const roads = {};

  const pt = (g) => {
    const { x, y } = toPixel(map, g.lat, g.lon);
    return `${x.toFixed(1)} ${y.toFixed(1)}`;
  };
  const line = (geom) => 'M' + geom.map(pt).join('L');
  const ring = (geom) => line(geom) + 'Z';

  for (const el of osm.elements) {
    const kind = classify(el.tags || {});
    if (!kind) continue;
    const isArea = ['green', 'water', 'building'].includes(kind);

    if (el.type === 'way' && Array.isArray(el.geometry) && el.geometry.length >= 2) {
      const d = isArea ? ring(el.geometry) : line(el.geometry);
      if (kind.startsWith('road:')) (roads[kind.slice(5)] ||= []).push(d);
      else layers[kind].push(d);
    } else if (el.type === 'relation' && isArea && Array.isArray(el.members)) {
      const segs = el.members.filter((m) => m.type === 'way' && Array.isArray(m.geometry)).map((m) => m.geometry);
      const d = assembleRings(segs).map(ring).join('');
      if (d) layers[kind].push(d); // 바깥·안쪽 고리를 한 path에 넣고 evenodd로 구멍을 낸다
    }
  }

  // 아주 긴 path 하나는 SVG 파서가 힘들어해서 1,500개씩 나눠 담는다
  const paths = (ds, attrs) => {
    const out = [];
    for (let i = 0; i < ds.length; i += 1500) out.push(`<path d="${ds.slice(i, i + 1500).join('')}" ${attrs}/>`);
    return out.join('\n');
  };
  const stroke = (s) =>
    `fill="none" stroke="${s.color}" stroke-width="${s.width}" stroke-linecap="round" stroke-linejoin="round"` +
    (s.dash ? ` stroke-dasharray="${s.dash}"` : '');

  const roadSvg = STYLE.roads
    .map((s) => paths(s.classes.flatMap((c) => roads[c] || []), stroke(s)))
    .join('\n');

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${map.widthPx}" height="${map.heightPx}" viewBox="0 0 ${map.widthPx} ${map.heightPx}">
<rect width="100%" height="100%" fill="${STYLE.background}"/>
${paths(layers.green, `fill="${STYLE.green}" fill-rule="evenodd"`)}
${paths(layers.water, `fill="${STYLE.water}" fill-rule="evenodd"`)}
${paths(layers.waterway, stroke(STYLE.waterway))}
${paths(layers.building, `fill="${STYLE.building}" fill-rule="evenodd"`)}
${paths(layers.roadTunnel, stroke(STYLE.roadTunnel))}
${roadSvg}
${paths(layers.railTunnel, stroke(STYLE.railTunnel))}
${paths(layers.rail, stroke(STYLE.rail))}
</svg>`;

  const counts = Object.fromEntries(Object.entries(layers).map(([k, v]) => [k, v.length]));
  counts.road = Object.values(roads).reduce((n, v) => n + v.length, 0);
  return { svg, counts };
}

// 확인용 그림: 지도 위에 정류장 점
//   회색 작은 점 = 범위 안 전체, 하늘색 작은 점 = N버스가 서는 정류장
//   파란 고리 = 승차 거점(시범), 노란 고리 = 주거지(시범), 옆 숫자 = stops.json 순번
function buildCheckOverlay(map, inside, hubs, homes) {
  const dots = inside
    .map((s) => {
      const { x, y } = toPixel(map, s.lat, s.lng);
      const color = s.nightRoutes.length ? '#7cc4ff' : '#8a93a6';
      return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="5" fill="${color}"/>`;
    })
    .join('');
  const mark = (s, i, color) => {
    const { x, y } = toPixel(map, s.lat, s.lng);
    return (
      `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="14" fill="none" stroke="${color}" stroke-width="5"/>` +
      `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="6" fill="${color}"/>` +
      `<text x="${(x + 20).toFixed(1)}" y="${(y + 10).toFixed(1)}" font-family="sans-serif" font-size="30" font-weight="bold" fill="${color}">${i + 1}</text>`
    );
  };
  const pilotDots =
    hubs.map((s, i) => mark(s, i, '#3d8bff')).join('') + homes.map((s, i) => mark(s, hubs.length + i, '#f2c14e')).join('');
  return Buffer.from(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${map.widthPx}" height="${map.heightPx}">${dots}${pilotDots}</svg>`
  );
}

async function collectMap(area, stopsResult) {
  let sharp;
  try {
    sharp = require('sharp');
  } catch {
    throw new Error('sharp가 없음. server 폴더에서 npm install 을 먼저 실행');
  }

  const map = {
    mapId: area.mapId,
    imageUrl: '/map/image',
    widthPx: area.widthPx,
    heightPx: heightForWidth(area, area.widthPx),
    north: area.north,
    south: area.south,
    west: area.west,
    east: area.east,
    attribution: '© OpenStreetMap contributors',
  };

  const { osm, fromCache, source } = await loadOsm(area);
  const { svg, counts } = buildMapSvg(osm, map);
  await sharp(Buffer.from(svg)).png({ compressionLevel: 9 }).toFile(path.join(DATA, 'map.png'));
  writeJson(path.join(DATA, 'map.json'), map); // 공통 규약 MapInfo 형식 그대로

  const overlay = buildCheckOverlay(map, stopsResult.inside, stopsResult.hubs, stopsResult.homes);
  await sharp(path.join(DATA, 'map.png'))
    .composite([{ input: overlay, top: 0, left: 0 }])
    .png()
    .toFile(path.join(DATA, 'map-check.png'));

  return { map, counts, fromCache, source };
}

// ───────────────────────── 실행 ─────────────────────────

async function main() {
  const area = JSON.parse(fs.readFileSync(path.join(DATA, 'area.json'), 'utf8'));
  if (!(area.north > area.south && area.east > area.west)) throw new Error('area.json 범위가 이상함 (north>south, east>west)');

  console.log('1) 정류장');
  const s = collectStops(area);
  console.log(`  원본: ${path.basename(s.file)} (서울 전체 ${s.totalCount}개)`);
  if (s.routeFile) console.log(`  노선: ${path.basename(s.routeFile)} → 범위 안 N버스 ${s.areaNightRoutes.join(', ') || '없음'}`);
  console.log(`  범위 안: ${s.inside.length}개 → data/stops-all.json`);
  console.log(`  시범 정류장: ${s.pilots.length}개 → data/stops.json`);
  let n = 0;
  console.log('   [승차 거점]');
  s.hubs.forEach((p) => console.log(`    ${String(++n).padStart(2)}. ${p.stopId} ${p.name}  (${p.nightRoutes.join(', ') || 'N버스 없음'})`));
  console.log('   [주거지]');
  s.homes.forEach((p) => console.log(`    ${String(++n).padStart(2)}. ${p.stopId} ${p.name}`));
  if (s.hubs.length < 3 || s.homes.length < 5 || s.pilots.length > 25) {
    console.log('  ⚠ 시범 정류장 수 확인: 승차 거점 3개 이상, 주거지 5개 이상, 합쳐서 25개 이하 권장 (지금 정한 값: 거점 6 + 주거지 19)');
  }
  s.warnings.forEach((w) => console.log(`  ⚠ ${w}`));

  console.log('2) 지도');
  const m = await collectMap(area, s);
  console.log(`  OSM 데이터: ${m.fromCache ? '받아 둔 것 재사용 (새로 받으려면 npm run collect:refresh)' : `새로 받음 (${m.source}) → data/raw/osm-area.json`}`);
  console.log(`  그린 것: 도로 ${m.counts.road}, 건물 ${m.counts.building}, 녹지 ${m.counts.green}, 물 ${m.counts.water + m.counts.waterway}, 철도 ${m.counts.rail + m.counts.railTunnel}`);
  console.log(`  data/map.png (${m.map.widthPx}×${m.map.heightPx}px), data/map.json`);
  console.log('  확인용: data/map-check.png 를 열어 파란 고리(승차 거점)·노란 고리(주거지)가 실제 위치(역 앞·아파트 앞 도로)에 있는지 본다');
}

main().catch((e) => {
  console.error('\n실패: ' + e.message);
  process.exit(1);
});

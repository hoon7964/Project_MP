// 도로 경로 만들기 (B6): npm run paths
//   입력  data/raw/osm-area.json (npm run collect가 받아 둔 OSM 데이터), data/area.json, data/stops.json
//   출력  data/paths.json  모든 정류장 쌍의 도로 거리(m)와 좌표 목록 → 엔진이 이동 시간·버스 위치·API path에 씀
// npm run collect 도 마지막에 이걸 같이 돌린다. 정류장이나 범위를 바꾸지 않았으면 다시 돌릴 필요 없음.
// 인터넷·npm 패키지가 필요 없어서 sharp 없이도 돈다.

const fs = require('fs');
const path = require('path');
const { buildPaths } = require('./lib/roads');
const { distanceM } = require('./lib/geo');

const DATA = path.join(__dirname, '..', 'data');

function makePaths({ log = console.log } = {}) {
  const osmFile = path.join(DATA, 'raw', 'osm-area.json');
  if (!fs.existsSync(osmFile)) throw new Error('data/raw/osm-area.json 이 없음 → npm run collect 를 먼저 실행');
  const area = JSON.parse(fs.readFileSync(path.join(DATA, 'area.json'), 'utf8'));
  const stops = JSON.parse(fs.readFileSync(path.join(DATA, 'stops.json'), 'utf8'));
  const osm = JSON.parse(fs.readFileSync(osmFile, 'utf8'));

  const { pairs, report } = buildPaths(osm, area, stops);
  const out = {
    mapId: area.mapId,
    stopIds: stops.map((s) => s.stopId),
    note: 'OSM 도로(차도, 일방통행 반영) 최단 경로. 키 "출발>도착", distM = 도로 거리(m), points = [[위도, 경도], ...] (양 끝은 정류장 좌표)',
    pairs,
  };
  fs.writeFileSync(path.join(DATA, 'paths.json'), JSON.stringify(out), 'utf8');

  const by = Object.fromEntries(stops.map((s) => [s.stopId, s]));
  const kb = Math.round(fs.statSync(path.join(DATA, 'paths.json')).size / 1024);
  log(`  도로 ${report.ways}개 → 서로 오갈 수 있는 도로망 교차점 ${report.graphNodes}개, ${report.ms}ms`);
  log(`  정류장 ${stops.length}개 × ${stops.length - 1} = ${Object.keys(pairs).length}쌍 → data/paths.json (${kb}KB, 좌표 ${report.points}개)`);
  const far = Object.entries(report.snaps).filter(([, s]) => s.snapM > 30);
  log(`  정류장 → 도로 거리: 최대 ${Math.max(...Object.values(report.snaps).map((s) => s.snapM))}m${far.length ? ` (30m 넘는 곳: ${far.map(([id, s]) => `${by[id].name} ${s.snapM}m`).join(', ')})` : ''}`);
  log(`  도로 거리 ÷ 직선 거리: 평균 ${report.meanRatio.toFixed(2)}배 (전에 쓰던 보정값 1.3)`);
  log('  가장 많이 돌아가는 쌍 (일방통행·봉화산 때문):');
  for (const w of report.worst.slice(0, 3)) {
    const [a, b] = w.key.split('>');
    log(`    ${by[a].name} → ${by[b].name}: 직선 ${Math.round(distanceM(by[a], by[b]))}m → 도로 ${Math.round(w.distM)}m (${w.ratio.toFixed(1)}배)`);
  }
  if (report.missing.length) {
    log(`  ⚠ 도로로 못 가는 쌍 ${report.missing.length}개 (서버는 직선×1.3으로 대신함): ${report.missing.slice(0, 5).join(', ')}`);
  }
  return out;
}

module.exports = { makePaths };

if (require.main === module) {
  try {
    console.log('도로 경로');
    makePaths();
  } catch (e) {
    console.error('\n실패: ' + e.message);
    process.exit(1);
  }
}

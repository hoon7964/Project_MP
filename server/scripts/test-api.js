// API 테스트 (B4 완료 기준): 서버를 켜 둔 상태에서 다른 PowerShell 창에서
//   npm run test:api                (기본 http://localhost:3000)
//   npm run test:api -- http://10.0.2.2:3000   (다른 주소)
// 공통 규약 3의 API를 차례로 불러 형식·상태 코드를 확인하고, 호출이 시간이 지나며 바뀌는지 본다.

const BASE = (process.argv[2] || 'http://localhost:3000').replace(/\/$/, '');
let failures = 0;
const check = (ok, msg) => {
  console.log(`  ${ok ? '✓' : '✗'} ${msg}`);
  if (!ok) failures++;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function req(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
    body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
  });
  const type = res.headers.get('content-type') || '';
  const data = type.includes('json') ? await res.json() : Buffer.from(await res.arrayBuffer());
  return { status: res.status, type, data };
}

const hasKeys = (o, keys) => o && keys.every((k) => k in o);

async function main() {
  console.log(`API 테스트: ${BASE}`);
  try {
    await fetch(BASE + '/health');
  } catch {
    console.log(`  ✗ 서버에 접속하지 못함. 다른 창에서 npm start 를 먼저 실행`);
    process.exit(1);
  }

  const stops = await req('GET', '/stops');
  check(stops.status === 200 && Array.isArray(stops.data) && stops.data.length > 0, `GET /stops → ${stops.status}, ${stops.data.length}개`);
  check(hasKeys(stops.data[0], ['stopId', 'name', 'lat', 'lng', 'kind', 'nightRoutes']), '  Stop 필드: stopId, name, lat, lng, kind, nightRoutes');

  const map = await req('GET', '/map');
  check(map.status === 200 && hasKeys(map.data, ['mapId', 'imageUrl', 'widthPx', 'heightPx', 'north', 'south', 'west', 'east', 'attribution']), `GET /map → ${map.status}, ${map.data.mapId} ${map.data.widthPx}×${map.data.heightPx}`);

  const img = await req('GET', '/map/image');
  const isPng = Buffer.isBuffer(img.data) && img.data.subarray(1, 4).toString() === 'PNG';
  check(img.status === 200 && isPng, `GET /map/image → ${img.status}, PNG ${Buffer.isBuffer(img.data) ? Math.round(img.data.length / 1024) : '?'}KB`);

  const buses = await req('GET', '/buses');
  check(buses.status === 200 && buses.data.length >= 1 && hasKeys(buses.data[0], ['busId', 'lat', 'lng', 'load', 'capacity', 'route', 'path']), `GET /buses → ${buses.status}, ${buses.data.length}대`);

  // 관제 전용 (웹 관제만 씀)
  const sim = await req('GET', '/sim');
  check(sim.status === 200 && hasKeys(sim.data, ['simClock', 'speed', 'busCount']), `GET /sim → ${sim.status}, ${sim.data.simClock} · ${sim.data.speed}배속`);
  const badSpeed = await req('POST', '/sim/speed', { speed: 999 });
  check(badSpeed.status === 400, `POST /sim/speed 999 → ${badSpeed.status} (400이어야 함)`);
  const list = await req('GET', '/calls');
  check(list.status === 200 && Array.isArray(list.data), `GET /calls → ${list.status}, ${list.data.length}건`);

  // 잘못된 요청
  const same = await req('POST', '/calls', { originId: '07003', destId: '07003', partySize: 1 });
  check(same.status === 400, `POST /calls 같은 정류장 → ${same.status} (400이어야 함) ${same.data.error || ''}`);
  const four = await req('POST', '/calls', { originId: '07003', destId: '07275', partySize: 4 });
  check(four.status === 400, `POST /calls 4명 → ${four.status} (400이어야 함)`);
  const broken = await req('POST', '/calls', '{"originId": ');
  check(broken.status === 400, `POST /calls 깨진 JSON → ${broken.status} (400이어야 함)`);
  const unknown = await req('GET', '/calls/c-99999');
  check(unknown.status === 404, `GET /calls/c-99999 → ${unknown.status} (404여야 함)`);

  // 페르소나 호출
  const call = await req('POST', '/calls', { originId: '07003', destId: '07275', partySize: 1 });
  check(call.status === 200 && hasKeys(call.data, ['callId', 'status', 'busId']), `POST /calls 상봉역.중랑우체국 → 신내동성3차아파트 → ${call.status} ${JSON.stringify(call.data)}`);
  const id = call.data.callId;

  const d1 = await req('GET', `/calls/${id}`);
  check(
    d1.status === 200 && hasKeys(d1.data, ['callId', 'status', 'partySize', 'originId', 'destId', 'simClock', 'etaPickupMin', 'etaDropoffMin', 'bus', 'route', 'path']),
    `GET /calls/${id} → ${d1.status}, ${d1.data.status} · ${d1.data.simClock} · 탑승까지 ${d1.data.etaPickupMin}분 · 노선 ${d1.data.route.length}곳`
  );

  console.log('  6초 기다렸다가 다시 조회 (시간이 흘러야 함)...');
  await sleep(6000);
  const d2 = await req('GET', `/calls/${id}`);
  // 6배속이면 6초 = 시뮬 36초라 HH:mm은 그대로일 수 있음 → 남은 시간이 줄었거나 상태가 바뀌었는지로 확인
  const moved =
    d2.data.status !== d1.data.status || d2.data.simClock !== d1.data.simClock || (d2.data.etaPickupMin ?? 0) < d1.data.etaPickupMin;
  check(moved, `GET /calls/${id} → ${d2.data.status} · ${d2.data.simClock} · 탑승까지 ${d2.data.etaPickupMin ?? '-'}분 · 하차까지 ${d2.data.etaDropoffMin ?? '-'}분`);

  console.log(failures === 0 ? '\n✓ API 모두 통과' : `\n✗ 실패 ${failures}건`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('테스트 중 오류: ' + e.message);
  process.exit(1);
});

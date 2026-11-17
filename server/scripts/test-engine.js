// 배차 엔진 테스트 (B2 완료 기준): npm run test:engine
//   1) 시연 시나리오: 페르소나 호출 → 같은 방향 합승 → 반대 방향은 다른 버스
//   2) 무작위 호출 수백 개를 넣고 제약(정원·대기 15분·기존 승객 지연 5분)이 한 번도 안 깨지는지 검사
// 서버를 켜지 않고 engine.js만 직접 돌린다.

const fs = require('fs');
const path = require('path');
const config = require('../config');
const { createEngine, formatClock } = require('../engine');
const { distanceM } = require('./lib/geo');

const stops = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'data', 'stops.json'), 'utf8'));
const nameOf = Object.fromEntries(stops.map((s) => [s.stopId, s.name]));
const pathsFile = path.join(__dirname, '..', 'data', 'paths.json');
const paths = fs.existsSync(pathsFile) ? JSON.parse(fs.readFileSync(pathsFile, 'utf8')) : null;
const STEP = 0.1; // 시뮬 6초씩 진행 (서버 기본 tick과 같음)

let failures = 0;
const check = (ok, msg) => {
  if (!ok) {
    failures++;
    console.log('  ✗ ' + msg);
  }
};

function run(engine, minutes) {
  for (let t = 0; t < minutes - 1e-9; t += STEP) engine.tick(STEP);
}

function showCall(engine, callId) {
  const d = engine.getCallDetail(callId);
  const c = engine._state.calls.get(callId);
  const route = d.route.map((r) => {
    const tags = [...r.pickups.map((id) => '+' + id), ...r.dropoffs.map((id) => '-' + id)].join(' ');
    return `${nameOf[r.stopId]}(${tags})`;
  });
  console.log(
    `  ${callId} ${nameOf[c.originId]} → ${nameOf[c.destId]} ${c.partySize}명: ${d.status}` +
      (d.bus ? ` · ${d.bus.busId} · 탑승까지 ${d.etaPickupMin ?? '-'}분 · 하차까지 ${d.etaDropoffMin}분` : '')
  );
  if (route.length) console.log(`     노선: ${route.join(' → ')}`);
}

function showDecision(engine, callId) {
  const dec = engine._state.calls.get(callId).lastDecision;
  for (const c of dec.candidates) {
    console.log(
      `     ${c.busId}: ${c.ok ? `가능 (대기 ${c.waitMin}분, 기존 승객 지연 합 ${c.delayMin}분, 비용 ${c.cost})` : '제외 · ' + c.reason}` +
        (c.busId === dec.chosen ? '  ← 배정' : '')
    );
  }
}

// ───────── 0) 도로 경로 (B6) ─────────
// 점 p에서 선분 a-b까지 거리(m)
function distToSeg(p, a, b) {
  const k = Math.cos((p.lat * Math.PI) / 180);
  const ax = (a.lng - p.lng) * k, ay = a.lat - p.lat, bx = (b.lng - p.lng) * k, by = b.lat - p.lat;
  const dx = bx - ax, dy = by - ay;
  const t = dx * dx + dy * dy === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy)));
  return Math.hypot(ax + dx * t, ay + dy * t) * 111320;
}
const pt = ([lat, lng]) => ({ lat, lng });
console.log('0) 도로 경로');
{
  check(paths, 'data/paths.json 이 없음 → npm run paths');
  const e = createEngine({ stops, config, paths });
  console.log(`  도로로 가는 정류장 쌍 ${e.roadInfo.road}개, 직선으로 대신한 쌍 ${e.roadInfo.straight}개`);
  check(e.roadInfo.straight === 0, '모든 정류장 쌍이 도로 경로를 가져야 함');
  // 봉화산 한가운데(정상 부근) 250m 안으로 지나가는 경로가 없어야 함
  const PEAK = { lat: 37.6094, lng: 127.0879 };
  let closest = Infinity;
  for (const p of Object.values(paths ? paths.pairs : {})) {
    for (let i = 1; i < p.points.length; i++) closest = Math.min(closest, distToSeg(PEAK, pt(p.points[i - 1]), pt(p.points[i])));
  }
  console.log(`  봉화산 정상 부근에 가장 가까이 지나가는 경로: ${Math.round(closest)}m`);
  check(closest > 250, '봉화산을 가로지르는 경로가 있음');
  const straight = distanceM(stops.find((s) => s.stopId === '07003'), stops.find((s) => s.stopId === '07275'));
  console.log(`  상봉역.중랑우체국 → 신내동성3차아파트: 직선 ${Math.round(straight)}m, 도로 ${paths && paths.pairs['07003>07275'].distM}m, 버스 ${e.travelMin('07003', '07275').toFixed(1)}분`);
}

// ───────── 1) 시연 시나리오 ─────────
console.log('\n1) 시연 시나리오');
{
  const e = createEngine({ stops, config, paths });
  console.log(`  ${e.simClock()} 버스 3대 대기: ${e.getBuses().map((b) => b.busId + ' ' + nameOf[e._state.buses.find((x) => x.busId === b.busId).atStop]).join(', ')}`);

  console.log(`\n  [${e.simClock()}] 페르소나: 상봉역.중랑우체국 → 신내동성3차아파트, 1명`);
  const r1 = e.requestCall({ originId: '07003', destId: '07275', partySize: 1 });
  showDecision(e, r1.response.callId);
  showCall(e, r1.response.callId);
  check(r1.ok && r1.response.status === 'ASSIGNED', '페르소나가 바로 배정돼야 함');

  run(e, 0.5);
  console.log(`\n  [${e.simClock()}] 같은 방향 승객 2명 (웹 관제에서 호출): 상봉역.중랑우체국 → 신내8단지아파트.서울의료원`);
  const r2 = e.requestCall({ originId: '07003', destId: '07279', partySize: 2 });
  showDecision(e, r2.response.callId);
  showCall(e, r2.response.callId);
  showCall(e, r1.response.callId);
  check(r2.response.busId === r1.response.busId, '같은 방향 승객은 페르소나 버스에 합승해야 함');

  // API path: 버스 위치에서 시작해 내 하차 정류장에서 끝나야 함
  const d1 = e.getCallDetail(r1.response.callId);
  const dest = stops.find((s) => s.stopId === '07275');
  const last = d1.path[d1.path.length - 1];
  console.log(`  ${r1.response.callId} path: 도로 좌표 ${d1.path.length}개 (노선 정류장 ${d1.route.length}곳)`);
  check(d1.path.length > d1.route.length, 'path에 도로 좌표가 채워져야 함');
  check(distanceM(d1.path[0], d1.bus) < 1, 'path는 버스 위치에서 시작해야 함');
  check(distanceM(last, dest) < 1, 'path는 내 하차 정류장에서 끝나야 함');

  run(e, 2.5); // 2호차가 두 승객을 태우고 신내 쪽(동쪽)으로 출발한 뒤
  console.log(`\n  [${e.simClock()}] 반대 방향 승객 1명 (2호차가 떠난 직후): 상봉역.중랑우체국 → 태릉고등학교.원묵초등학교`);
  const r3 = e.requestCall({ originId: '07003', destId: '07430', partySize: 1 });
  showDecision(e, r3.response.callId);
  showCall(e, r3.response.callId);
  check(r3.response.busId && r3.response.busId !== r1.response.busId, '반대 방향 승객은 다른 버스로 가야 함');

  // 버스가 움직이는 동안 매번 위치가 도로 좌표 위에 있어야 함 (직선으로 질러가지 않음)
  let off = 0;
  for (let k = 0; k < 100; k++) {
    run(e, 0.2);
    for (const b of e._state.buses.filter((x) => x.status === 'moving')) {
      const g = paths.pairs[b.from + '>' + b.to].points.map(pt);
      const pos = e.getBuses().find((x) => x.busId === b.busId);
      let dmin = Infinity;
      for (let i = 1; i < g.length; i++) dmin = Math.min(dmin, distToSeg(pos, g[i - 1], g[i]));
      off = Math.max(off, dmin);
    }
  }
  console.log(`\n  20분 동안 버스 위치와 도로 경로 사이 최대 거리: ${off.toFixed(2)}m`);
  check(off < 1, '버스가 도로 경로를 벗어남');

  run(e, 20);
  console.log(`\n  [${e.simClock()}] 40분 뒤`);
  for (const r of [r1, r2, r3]) {
    const c = e._state.calls.get(r.response.callId);
    console.log(
      `  ${c.callId}: ${c.status} · 승차 ${formatClock(c.pickedUpAt)} (약속 ${formatClock(c.firstPickup)}) · 하차 ${formatClock(c.droppedAt)} (약속 ${formatClock(c.firstDropoff)})`
    );
    check(c.status === 'COMPLETED', `${c.callId}가 하차 완료돼야 함`);
  }

  // 잘못된 요청은 400용 에러
  check(!e.requestCall({ originId: '07003', destId: '07003', partySize: 1 }).ok, '같은 정류장 호출은 거절');
  check(!e.requestCall({ originId: '07003', destId: '07275', partySize: 4 }).ok, '4명 호출은 거절');
  check(!e.requestCall({ originId: 'xxxxx', destId: '07275', partySize: 1 }).ok, '없는 정류장은 거절');
  check(e.getCallDetail('c-999') === null, '없는 callId는 null(404)');
}

// ───────── 1-1) 운행 시간 (05:00 끝) ─────────
console.log('\n1-1) 운행 시간');
{
  const e = createEngine({ stops, config, paths });
  run(e, 209.5); // 01:30 + 209.5분 = 04:59:30
  const last = e.requestCall({ originId: '07003', destId: '07275', partySize: 1 });
  check(last.ok && e.inService(), `${e.simClock()} 호출은 받아야 함`);
  run(e, 1);
  const late = e.requestCall({ originId: '07003', destId: '07275', partySize: 1 });
  console.log(`  ${e.simClock()} 호출 → ${late.ok ? '받음' : '거절: ' + late.error}`);
  check(!late.ok && !e.inService(), '05:00이 지나면 새 호출은 거절해야 함');
  run(e, 20);
  const c = e._state.calls.get(last.response.callId);
  console.log(`  04:59 호출 승객: ${c.status}, ${formatClock(c.droppedAt)} 하차`);
  check(c.status === 'COMPLETED', '운행 끝 전에 잡은 승객은 끝까지 태워 줘야 함');
  e.reset();
  check(e.inService() && e.requestCall({ originId: '07003', destId: '07275', partySize: 1 }).ok, '처음부터 다시 → 01:30부터 다시 호출 가능');
}

// ───────── 1-2) 호출 취소·이용 내역 (추가 기능) ─────────
console.log('\n1-2) 호출 취소·이용 내역');
{
  const e = createEngine({ stops, config, paths });
  const me = 'uid-jaeyeol';
  // 페르소나(앱, userId) + 같은 방향 합승 2명(관제) → 둘 다 2호차
  const a = e.requestCall({ originId: '07003', destId: '07275', partySize: 1, userId: me }).response;
  const b = e.requestCall({ originId: '07003', destId: '07279', partySize: 2 }).response;
  check(a.busId === b.busId, '취소 시험용 합승이 같은 버스여야 함');
  const etaBefore = e.getCallDetail(b.callId).etaDropoffMin;
  const bus = e._state.buses.find((x) => x.busId === a.busId);
  const r = e.cancelCall(a.callId);
  const d = r.detail;
  console.log(`  ${a.callId} 취소 → ${d.status}, bus ${d.bus}, route ${d.route.length}, path ${d.path.length}, ETA ${d.etaPickupMin}/${d.etaDropoffMin}`);
  check(r.ok && d.status === 'CANCELED' && d.bus === null && d.route.length === 0 && d.path.length === 0 && d.etaPickupMin === null && d.etaDropoffMin === null, '취소 응답은 CANCELED + bus null, route·path [], ETA null');
  const left = bus.waypoints.flatMap((w) => [...w.pickups, ...w.dropoffs]);
  check(!left.includes(a.callId), '취소한 승객은 버스 경유지에서 빠져야 함');
  const etaAfter = e.getCallDetail(b.callId).etaDropoffMin;
  console.log(`  남은 승객 ${b.callId} 하차까지: ${etaBefore}분 → ${etaAfter}분`);
  check(etaAfter <= etaBefore + 1e-9, '취소 뒤 남은 승객 ETA가 늦어지면 안 됨');
  check(e.cancelCall(a.callId).ok, '이미 취소한 걸 다시 보내도 성공(같은 결과)');
  check(e.cancelCall('c-999').status === 404, '모르는 callId 취소는 404');
  run(e, 3); // c-2 탑승
  const onboard = e.cancelCall(b.callId);
  check(!onboard.ok && onboard.status === 400, `탑승 중 취소는 400 (${e.getCallDetail(b.callId).status}: ${onboard.error})`);

  // 버스를 다 막아서 REQUESTED로 남는 호출 → 취소하면 대기열에서 빠짐
  const e2 = createEngine({ stops, config: { ...config, BUS_COUNT: 1 }, paths });
  for (let i = 0; i < 2; i++) e2.requestCall({ originId: '07440', destId: '07390', partySize: 3 });
  const p = e2.requestCall({ originId: '07003', destId: '07275', partySize: 3, userId: me }).response;
  console.log(`  버스 1대가 꽉 찬 뒤 호출 ${p.callId}: ${p.status}`);
  check(p.status === 'REQUESTED' && e2.cancelCall(p.callId).ok && !e2._state.pending.includes(p.callId), 'REQUESTED 취소는 대기열에서 빠져야 함');
  run(e2, 20);
  check(e2._state.calls.get(p.callId).status === 'CANCELED', '취소한 호출은 시간이 지나도 REJECTED로 안 바뀜');

  // 이용 내역
  const c = e.requestCall({ originId: '07440', destId: '07385', partySize: 2, userId: me }).response;
  run(e, 30);
  const h = e.userCalls(me);
  console.log('  이용 내역: ' + h.map((x) => `${x.callId} ${x.status} 요청 ${x.requestClock} 탑승 ${x.pickupClock} 하차 ${x.dropoffClock} 취소 ${x.canceledClock}`).join(' / '));
  check(h.length === 2 && h[0].callId === c.callId && h[1].callId === a.callId, '내 호출만, 최근 것부터');
  check(h[0].status === 'COMPLETED' && h[0].pickupClock && h[0].dropoffClock && h[0].canceledClock === null, '하차 완료 호출은 탑승·하차 시각이 있음');
  check(h[1].status === 'CANCELED' && h[1].pickupClock === null && h[1].canceledClock === '01:30' && h[1].busId === a.busId, '취소 호출은 취소 시각과 배정됐던 버스');
  check(Array.isArray(e.userCalls('nobody')) && e.userCalls('nobody').length === 0, '호출한 적 없는 userId는 []');
  e.reset();
  check(e.userCalls(me).length === 0, '처음부터 다시 → 이용 내역도 비워짐');
}

// ───────── 2) 무작위 부하 테스트 ─────────
console.log('\n2) 무작위 호출 테스트 (제약 검사)');
{
  let seed = 7;
  const rand = () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);
  const pick = (arr) => arr[Math.floor(rand() * arr.length)];
  const hubs = stops.filter((s) => s.kind === 'HUB').map((s) => s.stopId);
  const homes = stops.filter((s) => s.kind === 'HOME').map((s) => s.stopId);

  const e = createEngine({ stops, config, paths });
  const MINUTES = 180;
  let maxLoadSeen = 0;
  for (let t = 0; t < MINUTES; t += STEP) {
    // 평균 1분에 0.4건 (승차 거점→주거지 80%, 아무 데나 20%)
    if (rand() < 0.04) {
      const fromHub = rand() < 0.8;
      const originId = fromHub ? pick(hubs) : pick(homes);
      let destId = fromHub ? pick(homes) : pick([...hubs, ...homes]);
      if (destId === originId) destId = pick(homes.filter((h) => h !== originId));
      const partySize = 1 + Math.floor(rand() * 3);
      check(e.requestCall({ originId, destId, partySize }).ok, '정상 호출이 거절됨');
    }
    e.tick(STEP);
    for (const b of e.getBuses()) {
      maxLoadSeen = Math.max(maxLoadSeen, b.load);
      check(b.load <= config.CAPACITY, `${b.busId} 정원 초과: ${b.load}명`);
    }
  }
  run(e, 60); // 남은 승객 정리

  const calls = [...e._state.calls.values()];
  const by = (s) => calls.filter((c) => c.status === s);
  const done = by('COMPLETED');
  const maxWait = Math.max(...done.map((c) => c.pickedUpAt - c.requestTime));
  const maxPickDelay = Math.max(...done.map((c) => c.pickedUpAt - c.firstPickup));
  const maxDropDelay = Math.max(...done.map((c) => c.droppedAt - c.firstDropoff));
  const avgWait = done.reduce((n, c) => n + c.pickedUpAt - c.requestTime, 0) / done.length;
  const shared = done.filter((c) => calls.some((o) => o !== c && o.busId === c.busId && o.pickedUpAt < c.droppedAt && o.droppedAt > c.pickedUpAt)).length;

  console.log(`  호출 ${calls.length}건 → 하차 완료 ${done.length}, 배차 불가 ${by('REJECTED').length}, 남음 ${calls.length - done.length - by('REJECTED').length}`);
  console.log(`  평균 대기 ${avgWait.toFixed(1)}분, 최대 대기 ${maxWait.toFixed(2)}분 (한도 ${config.MAX_WAIT_MIN})`);
  console.log(`  기존 승객 최대 지연: 승차 ${maxPickDelay.toFixed(2)}분, 하차 ${maxDropDelay.toFixed(2)}분 (한도 ${config.MAX_DELAY_MIN})`);
  console.log(`  버스 최대 탑승 ${maxLoadSeen}명 (정원 ${config.CAPACITY}), 합승한 승객 ${shared}명 (${Math.round((shared / done.length) * 100)}%)`);
  check(maxWait <= config.MAX_WAIT_MIN + 1e-6, '대기 15분 초과 발생');
  check(maxPickDelay <= config.MAX_DELAY_MIN + 1e-6 && maxDropDelay <= config.MAX_DELAY_MIN + 1e-6, '지연 5분 초과 발생');
  check(calls.length - done.length - by('REJECTED').length === 0, '끝나지 않은 승객이 남음');
}

console.log(failures === 0 ? '\n✓ 모두 통과' : `\n✗ 실패 ${failures}건`);
process.exit(failures === 0 ? 0 : 1);

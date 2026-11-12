/* ================= 달무리버스 웹 관제 (B5): 서버 연결판 =================
 * 배차·이동 계산은 하지 않는다. 서버(같은 주소)의 API를 1초마다 받아서 그리기만 한다.
 *   공통 규약: GET /stops, GET /map, GET /map/image, POST /calls, GET /buses
 *   관제 전용: GET /calls(호출 목록 + 배차 판정), GET /sim, POST /sim/speed, POST /sim/reset
 * 노선선 규칙은 앱과 같다: path(도로 좌표)가 있으면 그대로, 비어 있으면 route 정류장을 직선으로.
 * 지도 칸은 /map의 widthPx:heightPx 비율로 잡고, 지도 그림이 칸에 꼭 맞게(여백 없이) 깐다.
 */

const POLL_MS = 1000;

// 버스 색 (BUS1~3). 4대 이상이면 회색
const BUS_STYLE = {
  BUS1: { name: '1호차', color: '#5eead4' },
  BUS2: { name: '2호차', color: '#f472b6' },
  BUS3: { name: '3호차', color: '#b39dfa' },
};
const busStyle = (id) => BUS_STYLE[id] || { name: id.replace('BUS', '') + '호차', color: '#94a3b8' };

const STATUS_TEXT = {
  REQUESTED: '버스 찾는 중',
  ASSIGNED: '버스 오는 중',
  ONBOARD: '타고 가는 중',
  COMPLETED: '하차 완료',
  REJECTED: '배차 못 함',
};

let STOPS = [];
const stopById = (id) => STOPS.find((s) => s.stopId === id);
const stopName = (id) => (stopById(id) || { name: id }).name;
const fmtMin = (m) => (m == null ? '-' : m < 1 ? '1분 안' : Math.round(m) + '분');
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const $ = (id) => document.getElementById(id);

const labels = {}; // 이 관제 화면에서 붙인 이름 (예: 페르소나). 없으면 callId
const lastStatus = {}; // 상태가 바뀔 때만 방송 문구를 띄우려고
let map;
let mapBounds;
let partySize = 1;
let simClock = '--:--';
const busLayers = {}; // busId → { marker, line, track, cum, t0, rest }

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `${method} ${path} → ${res.status}`);
  return data;
}

// 지도 왼쪽 위 방송 한 줄
function broadcast(msg, flash = true) {
  $('eventMsg').textContent = msg;
  $('eventTime').textContent = simClock === '--:--' ? '관제 방송' : `${simClock} 관제 방송`;
  const box = $('eventBox');
  if (flash) {
    box.classList.remove('flash');
    void box.offsetWidth; // 애니메이션 다시 시작
    box.classList.add('flash');
  }
}

function formMsg(msg, isErr = false) {
  const el = $('formMsg');
  el.textContent = msg;
  el.classList.toggle('err', isErr);
}

/* ================= 지도 ================= */
function initMap(mapInfo) {
  // 지도 칸 비율 = 지도 그림 비율 → 위아래·옆에 빈 띠가 안 생김
  document.documentElement.style.setProperty('--map-ratio', `${mapInfo.widthPx} / ${mapInfo.heightPx}`);
  mapBounds = L.latLngBounds([mapInfo.south, mapInfo.west], [mapInfo.north, mapInfo.east]);

  map = L.map('map', {
    zoomControl: false,
    zoomSnap: 0, // 칸에 꼭 맞는 배율(소수)을 쓰려고
    zoomDelta: 0.5,
    wheelPxPerZoomLevel: 120,
    maxBounds: mapBounds, // 그림 밖으로 끌려 나가지 않게
    maxBoundsViscosity: 1,
  });
  L.control.zoom({ position: 'bottomright', zoomInTitle: '확대', zoomOutTitle: '축소' }).addTo(map);
  map.attributionControl.setPrefix(false);
  // 서버가 만든 지도 그림을 그대로 깐다 (앱과 같은 그림·같은 좌표, 외부 타일 없음)
  L.imageOverlay(mapInfo.imageUrl, mapBounds, { attribution: mapInfo.attribution }).addTo(map);
  fitMap(true);
  new ResizeObserver(() => fitMap(false)).observe($('mapWrap'));

  for (const s of STOPS) {
    const hub = s.kind === 'HUB';
    // 지도 가장자리 정류장은 이름이 잘리지 않게 안쪽으로 붙인다
    const fx = (s.lng - mapInfo.west) / (mapInfo.east - mapInfo.west);
    const edge = fx < 0.1 ? ' edge-l' : fx > 0.9 ? ' edge-r' : '';
    const html = `<div class="stop ${hub ? 'hub' : 'home'}${edge}"><i></i>${hub ? `<span class="name">${esc(s.name)}</span>` : ''}<span class="wait" id="badge-${s.stopId}"></span></div>`;
    const icon = L.divIcon({ className: '', html, iconSize: [26, 26], iconAnchor: [13, 13], popupAnchor: [0, -10] });
    L.marker([s.lat, s.lng], { icon, title: s.name, riseOnHover: true, keyboard: true })
      .addTo(map)
      .bindPopup(
        `<div class="pop"><h3>${esc(s.name)}</h3>` +
          `<div class="meta"><span class="dot ${hub ? 'board' : 'home'}"></span>` +
          (hub ? `승차 거점, 서는 심야버스 ${s.nightRoutes.map(esc).join(', ')}` : '주거지, 집 근처에서 내리는 곳') +
          `</div><div class="meta" style="margin-top:2px">정류장 번호 ${s.stopId}</div>` +
          `<div class="acts"><button class="o" onclick="pickStop('${s.stopId}','o')">여기서 출발</button>` +
          `<button class="d" onclick="pickStop('${s.stopId}','d')">여기로 도착</button></div></div>`
      );
  }
}

// 칸 크기가 바뀌면 그림이 칸을 꼭 채우는 배율을 최소 배율로 다시 잡는다
function fitMap(first) {
  if (!map) return;
  map.invalidateSize({ animate: false });
  const fit = map.getBoundsZoom(mapBounds, false);
  const atFit = first || map.getZoom() <= map.getMinZoom() + 0.01;
  map.setMinZoom(fit);
  map.setMaxZoom(fit + 3);
  if (atFit || map.getZoom() < fit) map.fitBounds(mapBounds, { animate: false });
}

// 팝업의 "여기서 출발 / 여기로 도착" → 호출 칸에 채움
window.pickStop = (id, which) => {
  $(which === 'o' ? 'originSel' : 'destSel').value = id;
  map.closePopup();
  formMsg(`${which === 'o' ? '출발' : '도착'}: ${stopName(id)}`);
};

function ensureBusLayer(b) {
  if (busLayers[b.busId]) return busLayers[b.busId];
  const st = busStyle(b.busId);
  const html = `<div class="bus" id="busMk-${b.busId}" style="--c:${st.color}; --glow:${st.color}88"><span class="halo"></span><span class="core" id="busCount-${b.busId}">0</span><span class="tag">${st.name}</span></div>`;
  const icon = L.divIcon({ className: '', html, iconSize: [44, 44], iconAnchor: [22, 22] });
  const line = L.polyline([], { color: st.color, weight: 4, opacity: 0.9, dashArray: '2 9', lineCap: 'round', interactive: false }).addTo(map);
  const marker = L.marker([b.lat, b.lng], { icon, zIndexOffset: 1000, interactive: false, keyboard: false }).addTo(map);
  return (busLayers[b.busId] = { marker, line, track: [[b.lat, b.lng]], cum: [0], t0: 0, rest: [], ahead: [] });
}

// ── 1초마다 받은 위치 사이를 부드럽게 (앱 F7과 같은 방식) ──
// 직선으로 이으면 배속이 클 때 모퉁이를 질러가서, 지난번에 받은 도로 좌표(path)를 따라 새 위치까지 옮긴다.
const flat = (a, b) => {
  const k = Math.cos((a[0] * Math.PI) / 180);
  return Math.hypot((b[1] - a[1]) * k, b[0] - a[0]) * 111320; // m
};
function cumOf(pts) {
  const cum = [0];
  for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + flat(pts[i - 1], pts[i]));
  return cum;
}
// 화면에 있는 위치 from에서, 지난 path(old)를 따라 새 위치 to까지 가는 좌표들
function trackAlong(from, to, old) {
  if (old.length < 2) return [from, to];
  let best = { d: Infinity, i: 0 };
  for (let i = 1; i < old.length; i++) {
    const a = old[i - 1];
    const b = old[i];
    const k = Math.cos((a[0] * Math.PI) / 180);
    const dx = (b[1] - a[1]) * k;
    const dy = b[0] - a[0];
    const len2 = dx * dx + dy * dy;
    const t = len2 ? Math.max(0, Math.min(1, ((to[1] - a[1]) * k * dx + (to[0] - a[0]) * dy) / len2)) : 0;
    const d = flat([a[0] + dy * t, a[1] + (b[1] - a[1]) * t], to);
    if (d < best.d) best = { d, i };
  }
  if (best.d > 25) return [from, to]; // 노선이 바뀌었으면 그냥 직선
  return [from, ...old.slice(1, best.i), to];
}
function animate(now) {
  for (const layer of Object.values(busLayers)) {
    const r = Math.min(1, (now - layer.t0) / POLL_MS);
    const { track, cum } = layer;
    const d = r * cum[cum.length - 1];
    let i = 1;
    while (i < track.length - 1 && cum[i] < d) i++;
    const span = cum[i] - cum[i - 1] || 1;
    const f = track.length > 1 ? Math.min(1, Math.max(0, (d - cum[i - 1]) / span)) : 0;
    const a = track[Math.max(0, i - 1)];
    const b = track[Math.min(i, track.length - 1)];
    const pos = [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f];
    layer.marker.setLatLng(pos);
    // 노선선: 지금 위치 → 이번 1초 동안 남은 길 → 서버가 준 남은 노선
    layer.line.setLatLngs(layer.rest.length ? [pos, ...track.slice(i), ...layer.rest] : []);
  }
  requestAnimationFrame(animate);
}

/* ================= 그리기 ================= */
// inService = 서버 GET /sim 값. 05:00이 지나 시각이 한 바퀴 돌아도(서버를 오래 켜 둔 경우) "운행 끝"으로 보이게 서버 값을 따른다
function renderClock(clock, inService = true) {
  simClock = clock;
  $('clockVal').textContent = clock;
  const [h, m] = clock.split(':').map(Number);
  const min = h >= 12 ? 0 : h * 60 + m; // 자정 전은 0으로
  const progress = inService ? Math.min(1, min / 300) : 1; // 00:00~05:00
  $('nightArc').setAttribute('stroke-dashoffset', (119.38 * (1 - progress)).toFixed(2));
  const left = Math.max(0, 300 - min);
  $('nightLeft').textContent = inService && left ? `운행 끝(05:00)까지 ${Math.floor(left / 60)}시간 ${left % 60}분` : '운행 끝, 새 호출 안 받음';
}

function renderBuses(buses) {
  let onboard = 0;
  let active = 0;
  const rows = [];
  for (const b of buses) {
    const st = busStyle(b.busId);
    const layer = ensureBusLayer(b);
    const shown = layer.marker.getLatLng();
    const to = [b.lat, b.lng];
    layer.track = trackAlong([shown.lat, shown.lng], to, layer.ahead);
    layer.cum = cumOf(layer.track);
    layer.t0 = performance.now();
    // 노선선: path(도로 좌표, 첫 점 = 버스 위치)가 있으면 그대로, 없으면 route 정류장을 직선으로
    const ahead = b.path.length ? b.path.map((p) => [p.lat, p.lng]) : [to, ...b.route.map((r) => [r.lat, r.lng])];
    layer.ahead = ahead;
    layer.rest = ahead.length > 1 ? ahead.slice(1) : [];

    const moving = b.route.length > 0;
    onboard += b.load;
    if (moving || b.load) active++;
    const countEl = $(`busCount-${b.busId}`);
    if (countEl) countEl.textContent = b.load;
    $(`busMk-${b.busId}`)?.classList.toggle('moving', moving);

    let where = '대기 중';
    if (moving) {
      const next = b.route[0];
      const what = [next.dropoffs.length && `${next.dropoffs.length}건 하차`, next.pickups.length && `${next.pickups.length}건 승차`].filter(Boolean).join(', ');
      where = `<b>${esc(stopName(next.stopId))}</b>${what ? ` ${what}` : ''}${b.route.length > 1 ? `, 그다음 ${b.route.length - 1}곳` : ''}`;
    }
    const seats = Array.from({ length: b.capacity }, (_, i) => `<i class="${i < b.load ? 'on' : ''}"></i>`).join('');
    rows.push(
      `<div class="fbus" style="--c:${st.color}; --cap:${b.capacity}">` +
        `<span class="who">${st.name}</span><span class="where">${where}</span>` +
        `<span class="load"><b>${b.load}</b>/${b.capacity}명</span>` +
        `<div class="bar" aria-label="${b.capacity}석 중 ${b.load}명">${seats}</div></div>`
    );
  }
  $('fleet').innerHTML = rows.join('');
  $('statActive').textContent = `${active}/${buses.length}`;
  $('statOnboard').textContent = onboard;
}

function renderCalls(calls) {
  // 정류장별 기다리는 인원 배지
  const waiting = {};
  let waitingTotal = 0;
  for (const c of calls) {
    if (c.status === 'REQUESTED' || c.status === 'ASSIGNED') {
      waiting[c.originId] = (waiting[c.originId] || 0) + c.partySize;
      waitingTotal += c.partySize;
    }
  }
  for (const s of STOPS) {
    const el = $(`badge-${s.stopId}`);
    if (!el) continue;
    el.textContent = waiting[s.stopId] || '';
    el.classList.toggle('on', !!waiting[s.stopId]);
  }
  $('statWaiting').textContent = waitingTotal;

  // 승객 목록: 진행 중인 호출 먼저, 그다음 최근에 끝난 것 (최대 12개)
  const live = calls.filter((c) => c.status === 'REQUESTED' || c.status === 'ASSIGNED' || c.status === 'ONBOARD');
  const done = calls.filter((c) => c.status === 'COMPLETED' || c.status === 'REJECTED');
  const items = [...live, ...done].slice(0, 12);
  $('paxEmpty').hidden = items.length > 0;
  $('paxCount').textContent = calls.length ? `진행 중 ${live.length}건, 전체 ${calls.length}건` : '';
  $('paxList').innerHTML = items
    .map((c) => {
      let text = STATUS_TEXT[c.status];
      if (c.status === 'ASSIGNED') text = `${fmtMin(c.etaPickupMin)} 뒤 탑승`;
      if (c.status === 'ONBOARD') text = `${fmtMin(c.etaDropoffMin)} 뒤 하차`;
      const st = c.busId && busStyle(c.busId);
      return (
        `<li class="${c.status === 'COMPLETED' || c.status === 'REJECTED' ? 'done' : ''}" style="--c:${st ? st.color : 'inherit'}">` +
        `<span class="nm">${esc(labels[c.callId] || c.callId)}${st ? `<em>${st.name}</em>` : ''}</span>` +
        `<span class="st ${c.status}">${text}</span>` +
        `<span class="rt">${c.requestClock} ${esc(stopName(c.originId))} → ${esc(stopName(c.destId))}, ${c.partySize}명</span></li>`
      );
    })
    .join('');

  renderJudge(calls);
}

const JUDGE_EMPTY = '<p class="empty">호출하면 버스마다 기다리는 시간과 이미 탄 승객이 늦어지는 시간을 비교해서 여기에 보여 줘요.</p>';

// 가장 최근 호출의 배차 판정: 버스마다 최선의 끼워넣기 결과와 제외 이유
function renderJudge(calls) {
  const c = calls.find((x) => x.decision);
  if (!c) {
    $('judge').innerHTML = JUDGE_EMPTY;
    return;
  }
  const d = c.decision;
  const rows = d.candidates
    .map((x) => {
      const st = busStyle(x.busId);
      const chosen = x.busId === d.chosen;
      if (!x.ok) {
        return `<div class="cand out" style="--c:${st.color}"><span class="who">${st.name}</span><span class="why">${esc(x.reason)}</span><span class="res">제외</span></div>`;
      }
      const why = `대기 ${x.waitMin}분, 이미 탄 승객 +${x.delayMin}분`;
      return `<div class="cand${chosen ? ' chosen' : ''}" style="--c:${st.color}"><span class="who">${st.name}</span><span class="why">${why}</span><span class="res">${chosen ? '배정' : ''} ${x.cost}점</span></div>`;
    })
    .join('');
  const result = d.chosen ? '' : `<p class="judge-note" style="color:var(--warn)">조건에 맞는 버스가 없어 15분까지 매초 다시 찾아요.</p>`;
  $('judge').innerHTML =
    `<p class="judge-title"><b>${esc(labels[c.callId] || c.callId)}</b> ${esc(stopName(c.originId))} → ${esc(stopName(c.destId))}, ${c.partySize}명<span>${d.clock} 판정</span></p>` +
    `<div class="cands">${rows}</div>${result}` +
    `<p class="judge-note">점수 = 새 승객이 내릴 때까지 걸리는 분 + 이미 탄 승객이 늦어지는 분. 가장 낮은 버스에 배정. 대기 15분, 지연 5분을 넘으면 제외.</p>`;
}

// 상태가 바뀐 호출이 있으면 관제 방송 한 줄
function announce(calls) {
  for (const c of [...calls].reverse()) {
    if (lastStatus[c.callId] === c.status) continue;
    const who = labels[c.callId] || c.callId;
    const bus = c.busId ? busStyle(c.busId).name : '';
    if (c.status === 'ASSIGNED') broadcast(`${who}: ${bus} 배정. ${stopName(c.originId)}에서 약 ${fmtMin(c.etaPickupMin)} 뒤 탑승`);
    else if (c.status === 'REQUESTED') broadcast(`${who}: 지금 조건에 맞는 버스가 없어 다시 찾는 중 (최대 15분)`);
    else if (c.status === 'ONBOARD') broadcast(`${who}: ${stopName(c.originId)}에서 ${bus}에 탑승`);
    else if (c.status === 'COMPLETED') broadcast(`${who}: ${stopName(c.destId)}에서 내림. 귀가 완료`);
    else if (c.status === 'REJECTED') broadcast(`${who}: 15분 안에 탈 수 있는 버스가 없어 배차하지 못함`);
    lastStatus[c.callId] = c.status;
  }
}

/* ================= 서버에서 받기 ================= */
let offline = false;
let polling = false;
let lastCallCount = 0;
// 서버가 초기화되면(다른 관제 창에서 해도) 호출 목록이 비워진다 → 이 화면이 붙인 이름·상태 기억도 지움
// (서버를 껐다 켜면 callId가 c-1부터 다시 시작해서, 지우지 않으면 예전 이름이 새 호출에 붙을 수 있음)
function forgetCalls() {
  for (const k of Object.keys(labels)) delete labels[k];
  for (const k of Object.keys(lastStatus)) delete lastStatus[k];
}
async function poll() {
  if (polling) return; // 응답이 1초보다 늦으면 겹쳐 부르지 않음
  polling = true;
  try {
    const [simInfo, buses, calls] = await Promise.all([api('GET', '/sim'), api('GET', '/buses'), api('GET', '/calls')]);
    if (calls.length < lastCallCount) forgetCalls();
    lastCallCount = calls.length;
    renderClock(simInfo.simClock, simInfo.inService !== false);
    markSpeed(simInfo.speed);
    renderService(simInfo.inService !== false);
    renderBuses(buses);
    renderCalls(calls);
    if (offline) broadcast('서버에 다시 연결됨');
    offline = false;
    announce(calls);
  } catch (e) {
    if (!offline) broadcast(`서버 응답 없음 (${e.message}). 서버 창에서 npm start가 켜져 있는지 확인`);
    offline = true;
  } finally {
    polling = false;
  }
}

// 05:00 운행이 끝나면 호출 버튼을 잠그고 안내 (서버도 새 호출을 400으로 거절). 처음부터 다시 → 다시 열림
let serviceOn = true;
function renderService(on) {
  if (on === serviceOn) return;
  serviceOn = on;
  for (const id of ['callBtn', 'personaBtn', 'demoBtn']) $(id).disabled = !on;
  if (on) formMsg('01:30부터 다시 운행해요. 호출할 수 있어요.');
  else {
    formMsg('05:00에 운행이 끝나서 새 호출은 받지 않아요. 이미 탄 승객은 끝까지 내려 줘요. "처음부터 다시"를 누르면 01:30부터 다시 시작해요.', true);
    broadcast('05:00 운행 끝. 남은 승객만 내려 주는 중');
  }
}

async function call(originId, destId, size, label) {
  if (originId === destId) {
    formMsg('출발과 도착이 같아요. 다른 정류장을 골라 주세요.', true);
    return null;
  }
  try {
    const r = await api('POST', '/calls', { originId, destId, partySize: size });
    if (label) labels[r.callId] = label;
    formMsg(`${label || r.callId} 호출함: ${stopName(originId)} → ${stopName(destId)}, ${size}명`);
    await poll();
    return r;
  } catch (e) {
    formMsg(`호출 실패: ${e.message}`, true);
    return null;
  }
}

/* ================= 화면 조작 ================= */
const SPEEDS = [6, 12, 30];
function setupSpeedButtons() {
  const wrap = $('speedGroup');
  wrap.innerHTML = '';
  for (const speed of SPEEDS) {
    const b = document.createElement('button');
    b.textContent = `${speed}배`;
    b.title = `실제 1초 = 시뮬레이션 ${speed}초`;
    b.dataset.speed = speed;
    b.onclick = () => api('POST', '/sim/speed', { speed }).then(poll);
    wrap.appendChild(b);
  }
}
function markSpeed(speed) {
  for (const b of document.querySelectorAll('#speedGroup button')) {
    const on = Number(b.dataset.speed) === speed;
    b.classList.toggle('active', on);
    b.setAttribute('aria-pressed', on);
  }
}

function setupParty() {
  for (const b of document.querySelectorAll('#party button')) {
    b.onclick = () => {
      partySize = Number(b.dataset.n);
      for (const x of document.querySelectorAll('#party button')) x.setAttribute('aria-pressed', x === b);
    };
  }
}

function populateSelects() {
  const group = (label, list) => {
    const g = document.createElement('optgroup');
    g.label = label;
    list.forEach((s) => g.appendChild(new Option(s.name, s.stopId)));
    return g;
  };
  const hubs = STOPS.filter((s) => s.kind === 'HUB');
  const homes = STOPS.filter((s) => s.kind === 'HOME');
  for (const sel of [$('originSel'), $('destSel')]) {
    sel.innerHTML = '';
    sel.appendChild(group('승차 거점 (N버스)', hubs));
    sel.appendChild(group('주거지', homes));
  }
  $('originSel').value = '07003'; // 기본 출발: 상봉역.중랑우체국 (페르소나 N26 하차)
  $('destSel').value = '07275'; // 기본 도착: 신내동성3차아파트 (페르소나 집 근처)
}

function renderLegendBuses(n) {
  $('legendBuses').innerHTML = Array.from({ length: n }, (_, i) => {
    const st = busStyle(`BUS${i + 1}`);
    return `<span style="--c:${st.color}"><span class="dot ring"></span>${st.name}</span>`;
  }).join('');
}

async function start() {
  if (location.protocol === 'file:') {
    broadcast('파일로 열면 서버에 연결할 수 없어요. 서버를 켜고(npm start) http://localhost:3000/ 으로 여세요.');
    return;
  }
  if (!window.L) {
    broadcast('지도 라이브러리(Leaflet)를 불러오지 못했어요. 서버를 끄고 server 폴더에서 npm install 한 뒤 다시 켜세요.');
    return;
  }
  let simInfo;
  try {
    const [stops, mapInfo, sim] = await Promise.all([api('GET', '/stops'), api('GET', '/map'), api('GET', '/sim')]);
    STOPS = stops;
    simInfo = sim;
    initMap(mapInfo);
  } catch (e) {
    broadcast(`서버에서 정류장·지도를 받지 못함 (${e.message}). npm start 확인`);
    return;
  }
  populateSelects();
  setupSpeedButtons();
  setupParty();
  renderLegendBuses(simInfo.busCount);
  // 기본 설정(버스 3대)일 때만 시작 정류장 이름을 말한다 (BUS_COUNT를 바꾸면 일반 문구)
  const startMsg =
    simInfo.busCount === 3 ? '버스 3대가 태릉입구역, 망우역, 신내역에서 기다리는 중' : `버스 ${simInfo.busCount}대가 시작 정류장에서 기다리는 중`;

  $('callBtn').onclick = async () => {
    const btn = $('callBtn');
    btn.disabled = true;
    await call($('originSel').value, $('destSel').value, partySize);
    btn.disabled = !serviceOn;
  };
  $('personaBtn').onclick = () => call('07003', '07275', 1, '페르소나 (신내동 23세)');
  // 승차 거점(N버스 하차) → 주거지로 가는 호출 3건을 무작위로 (1~3명씩)
  $('demoBtn').onclick = async () => {
    const hubs = STOPS.filter((s) => s.kind === 'HUB');
    const homes = STOPS.filter((s) => s.kind === 'HOME');
    const pick = (a) => a[Math.floor(Math.random() * a.length)];
    for (let i = 0; i < 3; i++) await call(pick(hubs).stopId, pick(homes).stopId, 1 + Math.floor(Math.random() * 3));
    formMsg('무작위 호출 3건을 보냈어요.');
  };
  $('resetBtn').onclick = async () => {
    if (!confirm('서버 시뮬레이션을 01:30으로 되돌리고 모든 호출을 지울까요? (앱으로 한 호출도 지워져요)')) return;
    try {
      await api('POST', '/sim/reset');
    } catch (e) {
      broadcast(`되돌리지 못함 (${e.message}). 서버 창에서 npm start가 켜져 있는지 확인`);
      return;
    }
    forgetCalls();
    $('judge').innerHTML = JUDGE_EMPTY;
    await poll();
    broadcast(`처음으로 되돌림. ${startMsg}`);
  };

  await poll();
  broadcast(`서버에 연결됨. ${startMsg}`, false);
  setInterval(poll, POLL_MS);
  requestAnimationFrame(animate);
}

document.addEventListener('DOMContentLoaded', start);

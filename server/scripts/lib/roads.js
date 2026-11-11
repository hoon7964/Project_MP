// 도로 경로 (B6): 지도를 그린 OSM 데이터(osm-area.json)로 정류장 사이 최단 도로 경로를 계산한다.
// 카카오 길찾기 같은 외부 API를 쓰지 않아서 키가 필요 없고, 지도 그림의 도로와 경로가 정확히 겹친다.
//
//   1. 도로 그래프: 차가 다니는 길(highway)만, 일방통행(oneway·회전교차로) 방향을 지킨다
//      - 시범지역 사각형 밖으로 나가는 도로 조각은 뺀다 (버스가 지도 밖으로 나갔다 오지 않게)
//      - 서로 오갈 수 있는 가장 큰 도로망(강한 연결 요소)만 남긴다 (끊긴 조각에 정류장이 붙지 않게)
//   2. 정류장 붙이기: 정류장에서 가장 가까운 도로 위 지점(수선의 발)에 가상 노드를 만들어 도로를 나눈다
//   3. 모든 정류장 쌍의 최단 경로(다익스트라, 거리 기준) → 거리 m + 좌표 목록
//      좌표는 [정류장, 도로 위 지점, …, 도로 위 지점, 정류장]. 버스가 정류장 점까지 가서 서도록 양 끝에 정류장 좌표를 붙인다.

// 버스가 다니는 길. 고속·간선도로(동부간선·북부간선), 단지 안 길(service), 보행로는 뺀다
const DRIVE = new Set([
  'primary', 'primary_link', 'secondary', 'secondary_link', 'tertiary', 'tertiary_link',
  'unclassified', 'residential', 'living_street', 'road', 'busway',
]);
const NO_ACCESS = new Set(['no', 'private']);

const R = 6371000;
const RAD = Math.PI / 180;
const dist = (a, b) => {
  const lat = ((a.lat + b.lat) / 2) * RAD;
  return Math.hypot((b.lng - a.lng) * RAD * Math.cos(lat) * R, (b.lat - a.lat) * RAD * R);
};

function isOneway(tags) {
  const o = tags.oneway;
  if (o === '-1') return -1;
  if (o === 'yes' || o === '1' || o === 'true') return 1;
  if (o === 'no') return 0;
  if (tags.junction === 'roundabout' || tags.junction === 'circular') return 1;
  if (tags.highway === 'motorway' || tags.highway === 'motorway_link') return 1;
  return 0;
}

// segments: { a, b, ow } — ow=true면 a→b만, false면 양방향. pos: 노드 → { lat, lng }
function buildSegments(osm, area) {
  const inside = (p) => p.lat <= area.north && p.lat >= area.south && p.lon >= area.west && p.lon <= area.east;
  const pos = new Map();
  const segments = [];
  let ways = 0;
  for (const e of osm.elements) {
    if (e.type !== 'way' || !e.tags || !DRIVE.has(e.tags.highway) || !e.nodes || !e.geometry) continue;
    if (NO_ACCESS.has(e.tags.access) || NO_ACCESS.has(e.tags.motor_vehicle) || e.tags.area === 'yes') continue;
    const dir = isOneway(e.tags);
    let used = false;
    for (let i = 0; i + 1 < e.nodes.length; i++) {
      const ga = e.geometry[i];
      const gb = e.geometry[i + 1];
      if (!ga || !gb || !inside(ga) || !inside(gb)) continue;
      let a = e.nodes[i];
      let b = e.nodes[i + 1];
      pos.set(a, { lat: ga.lat, lng: ga.lon });
      pos.set(b, { lat: gb.lat, lng: gb.lon });
      if (dir === -1) [a, b] = [b, a];
      segments.push({ a, b, ow: dir !== 0 });
      used = true;
    }
    if (used) ways++;
  }
  return { pos, segments, ways };
}

function adjacency(segments, pos) {
  const out = new Map();
  const inn = new Map();
  const add = (m, u, v, w) => {
    if (!m.has(u)) m.set(u, []);
    m.get(u).push([v, w]);
  };
  for (const s of segments) {
    const w = dist(pos.get(s.a), pos.get(s.b));
    add(out, s.a, s.b, w);
    add(inn, s.b, s.a, w);
    if (!s.ow) {
      add(out, s.b, s.a, w);
      add(inn, s.a, s.b, w);
    }
  }
  return { out, inn };
}

// 가장 큰 강한 연결 요소 (코사라주, 재귀 없이)
function largestScc(nodes, out, inn) {
  const order = [];
  const seen = new Set();
  for (const s of nodes) {
    if (seen.has(s)) continue;
    seen.add(s);
    const stack = [[s, 0]];
    while (stack.length) {
      const top = stack[stack.length - 1];
      const nb = out.get(top[0]) || [];
      if (top[1] < nb.length) {
        const v = nb[top[1]++][0];
        if (!seen.has(v)) {
          seen.add(v);
          stack.push([v, 0]);
        }
      } else {
        order.push(top[0]);
        stack.pop();
      }
    }
  }
  const comp = new Map();
  let best = null;
  let bestSize = 0;
  for (let i = order.length - 1; i >= 0; i--) {
    const s = order[i];
    if (comp.has(s)) continue;
    const id = s;
    let size = 0;
    const stack = [s];
    comp.set(s, id);
    while (stack.length) {
      const u = stack.pop();
      size++;
      for (const [v] of inn.get(u) || []) {
        if (!comp.has(v)) {
          comp.set(v, id);
          stack.push(v);
        }
      }
    }
    if (size > bestSize) {
      bestSize = size;
      best = id;
    }
  }
  return new Set([...comp].filter(([, c]) => c === best).map(([n]) => n));
}

// 정류장에서 가장 가까운 도로 조각 위 지점에 가상 노드를 끼운다
function snapStops(stops, segments, pos, keep) {
  const snaps = {};
  for (const stop of stops) {
    const lat0 = stop.lat * RAD;
    const toXY = (p) => [(p.lng - stop.lng) * RAD * Math.cos(lat0) * R, (p.lat - stop.lat) * RAD * R];
    let best = null;
    for (let i = 0; i < segments.length; i++) {
      const s = segments[i];
      if (!keep.has(s.a) || !keep.has(s.b)) continue;
      const [ax, ay] = toXY(pos.get(s.a));
      const [bx, by] = toXY(pos.get(s.b));
      const dx = bx - ax;
      const dy = by - ay;
      const len2 = dx * dx + dy * dy;
      const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
      const d = Math.hypot(ax + dx * t, ay + dy * t);
      if (!best || d < best.d) best = { i, t, d };
    }
    if (!best) throw new Error(`정류장 ${stop.stopId}를 붙일 도로가 없음`);
    const s = segments[best.i];
    const pa = pos.get(s.a);
    const pb = pos.get(s.b);
    const node = 'S:' + stop.stopId;
    pos.set(node, { lat: pa.lat + (pb.lat - pa.lat) * best.t, lng: pa.lng + (pb.lng - pa.lng) * best.t });
    keep.add(node);
    // a—b 를 a—S—b 로 나눈다 (방향 그대로)
    segments.splice(best.i, 1, { a: s.a, b: node, ow: s.ow }, { a: node, b: s.b, ow: s.ow });
    snaps[stop.stopId] = { node, snapM: best.d, oneway: s.ow };
  }
  return snaps;
}

// 이진 힙 다익스트라: src에서 모든 노드까지
function dijkstra(out, src) {
  const d = new Map([[src, 0]]);
  const prev = new Map();
  const heap = [[0, src]];
  const push = (x) => {
    heap.push(x);
    let i = heap.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (heap[p][0] <= heap[i][0]) break;
      [heap[p], heap[i]] = [heap[i], heap[p]];
      i = p;
    }
  };
  const pop = () => {
    const top = heap[0];
    const last = heap.pop();
    if (heap.length) {
      heap[0] = last;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1;
        const r = l + 1;
        let m = i;
        if (l < heap.length && heap[l][0] < heap[m][0]) m = l;
        if (r < heap.length && heap[r][0] < heap[m][0]) m = r;
        if (m === i) break;
        [heap[m], heap[i]] = [heap[i], heap[m]];
        i = m;
      }
    }
    return top;
  };
  while (heap.length) {
    const [du, u] = pop();
    if (du > d.get(u)) continue;
    for (const [v, w] of out.get(u) || []) {
      const nd = du + w;
      if (nd < (d.get(v) ?? Infinity)) {
        d.set(v, nd);
        prev.set(v, u);
        push([nd, v]);
      }
    }
  }
  return { d, prev };
}

// 더글러스-포커: 거의 일직선인 중간 점을 빼서 파일 크기를 줄인다 (tolM 미터 안쪽 오차)
function simplify(points, tolM) {
  if (points.length < 3) return points;
  const lat0 = points[0].lat * RAD;
  const xy = points.map((p) => [p.lng * RAD * Math.cos(lat0) * R, p.lat * RAD * R]);
  const keep = new Uint8Array(points.length);
  keep[0] = keep[points.length - 1] = 1;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [i, j] = stack.pop();
    const [ax, ay] = xy[i];
    const [bx, by] = xy[j];
    const dx = bx - ax;
    const dy = by - ay;
    const len = Math.hypot(dx, dy) || 1e-9;
    let far = -1;
    let farD = tolM;
    for (let k = i + 1; k < j; k++) {
      const dd = Math.abs((xy[k][0] - ax) * dy - (xy[k][1] - ay) * dx) / len;
      if (dd > farD) {
        farD = dd;
        far = k;
      }
    }
    if (far >= 0) {
      keep[far] = 1;
      stack.push([i, far], [far, j]);
    }
  }
  return points.filter((_, k) => keep[k]);
}

const r6 = (x) => Math.round(x * 1e6) / 1e6;

// 결과: { pairs: { "A>B": { distM, points: [[lat,lng], ...] } }, report }
function buildPaths(osm, area, stops, { tolM = 2 } = {}) {
  const t0 = Date.now();
  const { pos, segments, ways } = buildSegments(osm, area);
  const before = adjacency(segments, pos);
  const keep = largestScc([...before.out.keys()], before.out, before.inn);
  const snaps = snapStops(stops, segments, pos, keep);
  const { out } = adjacency(segments, pos);

  const pairs = {};
  const missing = [];
  const ratios = [];
  for (const a of stops) {
    const { d, prev } = dijkstra(out, snaps[a.stopId].node);
    for (const b of stops) {
      if (a === b) continue;
      const target = snaps[b.stopId].node;
      if (!d.has(target)) {
        missing.push(`${a.stopId}>${b.stopId}`);
        continue;
      }
      const nodes = [];
      for (let u = target; u !== undefined; u = prev.get(u)) nodes.push(u);
      nodes.reverse();
      const road = [{ lat: a.lat, lng: a.lng }, ...nodes.map((n) => pos.get(n)), { lat: b.lat, lng: b.lng }];
      const distM = d.get(target) + snaps[a.stopId].snapM + snaps[b.stopId].snapM;
      const points = simplify(road, tolM).map((p) => [r6(p.lat), r6(p.lng)]);
      pairs[`${a.stopId}>${b.stopId}`] = { distM: Math.round(distM), points };
      ratios.push({ key: `${a.stopId}>${b.stopId}`, ratio: distM / dist(a, b), distM });
    }
  }
  ratios.sort((x, y) => y.ratio - x.ratio);
  return {
    pairs,
    report: {
      ways,
      segments: segments.length,
      graphNodes: keep.size,
      snaps: Object.fromEntries(Object.entries(snaps).map(([id, s]) => [id, { snapM: Math.round(s.snapM), oneway: s.oneway }])),
      missing,
      meanRatio: ratios.reduce((n, r) => n + r.ratio, 0) / (ratios.length || 1),
      worst: ratios.slice(0, 5),
      points: Object.values(pairs).reduce((n, p) => n + p.points.length, 0),
      ms: Date.now() - t0,
    },
  };
}

module.exports = { buildPaths, DRIVE };

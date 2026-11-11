// 배차·시뮬레이션 엔진 (B2·B3). HTTP나 타이머와 무관하게 만들어서 테스트 스크립트로 바로 검증할 수 있다.
// 설계와 "웹 시뮬레이터와 다르게 한 점"은 server/DESIGN.md 참고.
//
// 시간 단위는 모두 "시뮬레이션 분" (simTime 90 = 01:30).
// 경유지(waypoint) = { stopId, pickups: [callId], dropoffs: [callId] }  — 같은 정류장에서는 하차 먼저, 승차 나중.

const { distanceM } = require('./scripts/lib/geo');

const EPS = 1e-9;

function parseClock(hhmm) {
  const [h, m] = hhmm.split(':').map(Number);
  return h * 60 + m;
}

function formatClock(min) {
  const m = Math.floor(min + EPS);
  const h = Math.floor(m / 60) % 24;
  return String(h).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
}

const round1 = (x) => Math.round(x * 10) / 10;

function createEngine({ stops, config }) {
  const stopById = new Map(stops.map((s) => [s.stopId, s]));

  // 정류장 쌍 이동 시간(분) 표를 미리 만들어 둔다 (25개 → 625칸)
  const speedMPerMin = (config.SPEED_KMH * 1000) / 60;
  const travelTable = new Map();
  for (const a of stops) {
    for (const b of stops) {
      travelTable.set(a.stopId + '>' + b.stopId, a === b ? 0 : (distanceM(a, b) * config.ROAD_FACTOR) / speedMPerMin);
    }
  }
  const travelMin = (a, b) => travelTable.get(a + '>' + b);

  // ───────── 상태 (서버 메모리) ─────────
  function initialState() {
    const s = {
      simTime: parseClock(config.SIM_START),
      buses: [],
      calls: new Map(), // callId → call
      pending: [], // 아직 버스를 못 잡은 callId (REQUESTED)
      nextId: 1,
    };
    for (let i = 0; i < config.BUS_COUNT; i++) {
      const start = config.BUS_START_STOPS[i % config.BUS_START_STOPS.length];
      if (!stopById.has(start)) throw new Error(`config.BUS_START_STOPS의 정류장이 stops에 없음: ${start}`);
      s.buses.push({
        busId: 'BUS' + (i + 1),
        status: 'idle', // idle = 정류장에 서 있음, moving = 다음 경유지로 가는 중
        atStop: start,
        from: null,
        to: null,
        segStart: 0,
        segEnd: 0,
        onboard: new Set(), // 타고 있는 callId
        waypoints: [],
      });
    }
    return s;
  }
  const state = initialState();
  const reset = () => Object.assign(state, initialState()); // 관제 "초기화": 01:30, 버스 제자리, 호출 모두 지움

  const loadOf = (callIds) => [...callIds].reduce((n, id) => n + state.calls.get(id).partySize, 0);

  // ───────── 노선 평가 (웹 evaluateBusRoute) ─────────
  // 버스가 waypoints 순서대로 돌 때 각 승객의 승차·하차 예정 시각과 정원 초과 여부.
  // 움직이는 중이면 "지금 구간의 출발 정류장·출발 시각"부터 계산한다 (waypoints[0] = 지금 가는 곳).
  function evaluate(bus, waypoints) {
    let loc = bus.status === 'moving' ? bus.from : bus.atStop;
    let t = bus.status === 'moving' ? bus.segStart : state.simTime;
    let load = loadOf(bus.onboard);
    const pickupTimes = {};
    const dropoffTimes = {};
    let feasible = true;
    for (const wp of waypoints) {
      t += travelMin(loc, wp.stopId);
      loc = wp.stopId;
      for (const id of wp.dropoffs) {
        dropoffTimes[id] = t;
        load -= state.calls.get(id).partySize;
      }
      for (const id of wp.pickups) {
        pickupTimes[id] = t;
        load += state.calls.get(id).partySize;
      }
      if (load > config.CAPACITY) feasible = false; // 정원 = 인원 합 (웹은 호출 수였음)
    }
    return { feasible, pickupTimes, dropoffTimes, endTime: t };
  }

  function buildCandidate(original, p, d, call) {
    const arr = original.map((wp) => ({ stopId: wp.stopId, pickups: [...wp.pickups], dropoffs: [...wp.dropoffs] }));
    arr.splice(p, 0, { stopId: call.originId, pickups: [call.callId], dropoffs: [] });
    arr.splice(d + 1, 0, { stopId: call.destId, pickups: [], dropoffs: [call.callId] });
    return arr;
  }

  // ───────── 배차: 끼워넣기 (웹 attemptInsertGlobal) ─────────
  // 모든 버스 × 승차 자리 p × 하차 자리 d(≥p)를 시도한다.
  // 제약: 정원(인원 합), 새 승객 대기 ≤ 15분, 기존 승객은 "처음 약속" 대비 승차·하차 지연 ≤ 5분.
  // 비용: 새 승객 하차 시각 + 기존 승객들이 늦어지는 시간 합 (= 전체 하차 시각 합의 증가량, 제안서 ΔC).
  function findBestInsertion(call) {
    let best = null;
    const candidates = []; // 관제 화면·발표용: 버스별 최선 후보와 제외 이유
    for (const bus of state.buses) {
      const original = bus.waypoints;
      const baseline = evaluate(bus, original);
      const minP = bus.status === 'moving' ? 1 : 0; // 지금 가는 경유지 앞에는 끼우지 않음
      let busBest = null;
      let reason = null;
      for (let p = minP; p <= original.length; p++) {
        for (let d = p; d <= original.length; d++) {
          const cand = buildCandidate(original, p, d, call);
          const res = evaluate(bus, cand);
          if (!res.feasible) {
            reason ||= '정원 초과';
            continue;
          }
          const wait = res.pickupTimes[call.callId] - call.requestTime;
          if (wait > config.MAX_WAIT_MIN + EPS) {
            reason ||= '대기 15분 초과';
            continue;
          }
          let delaySum = 0;
          let delayOk = true;
          for (const id of Object.keys(res.dropoffTimes)) {
            if (id === call.callId) continue;
            const other = state.calls.get(id);
            if (res.dropoffTimes[id] - other.firstDropoff > config.MAX_DELAY_MIN + EPS) delayOk = false;
            if (id in res.pickupTimes && res.pickupTimes[id] - other.firstPickup > config.MAX_DELAY_MIN + EPS) delayOk = false;
            delaySum += res.dropoffTimes[id] - baseline.dropoffTimes[id];
          }
          if (!delayOk) {
            reason = '기존 승객 지연 5분 초과';
            continue;
          }
          const cost = res.dropoffTimes[call.callId] + delaySum;
          if (!busBest || cost < busBest.cost - EPS || (Math.abs(cost - busBest.cost) <= EPS && wait < busBest.wait)) {
            busBest = { bus, waypoints: cand, res, cost, wait, delaySum };
          }
        }
      }
      candidates.push(
        busBest
          ? { busId: bus.busId, ok: true, waitMin: round1(busBest.wait), delayMin: round1(busBest.delaySum), cost: round1(busBest.cost - state.simTime) }
          : { busId: bus.busId, ok: false, reason: reason || '불가' }
      );
      if (busBest && (!best || busBest.cost < best.cost - EPS || (Math.abs(busBest.cost - best.cost) <= EPS && busBest.wait < best.wait))) {
        best = busBest;
      }
    }
    return { best, candidates };
  }

  function startNextSegment(bus, at) {
    const wp = bus.waypoints[0];
    bus.status = 'moving';
    bus.from = bus.atStop;
    bus.to = wp.stopId;
    bus.segStart = at;
    bus.segEnd = at + travelMin(bus.from, bus.to);
  }

  function commit(call, best) {
    const { bus, waypoints, res } = best;
    bus.waypoints = waypoints;
    call.status = 'ASSIGNED';
    call.busId = bus.busId;
    call.firstPickup = res.pickupTimes[call.callId]; // 처음 약속 (지연 제약 기준, 이후 바꾸지 않음)
    call.firstDropoff = res.dropoffTimes[call.callId];
    // 화면에 보여 줄 예상 시각은 이 버스의 모든 승객 것을 새로 적는다
    for (const [id, t] of Object.entries(res.pickupTimes)) state.calls.get(id).etaPickup = t;
    for (const [id, t] of Object.entries(res.dropoffTimes)) state.calls.get(id).etaDropoff = t;
    if (bus.status === 'idle') startNextSegment(bus, state.simTime); // 서 있던 버스는 바로 출발
  }

  // 배정을 시도하고 결과를 call에 남긴다. 성공하면 true
  function tryAssign(call) {
    const { best, candidates } = findBestInsertion(call);
    call.lastDecision = { at: state.simTime, candidates, chosen: best ? best.bus.busId : null };
    if (!best) return false;
    commit(call, best);
    return true;
  }

  // ───────── 호출 (웹 requestRide) ─────────
  // 성공: { ok: true, response: CallResponse }   잘못된 요청: { ok: false, error }
  function requestCall({ originId, destId, partySize }) {
    if (!stopById.has(originId)) return { ok: false, error: `없는 출발 정류장: ${originId}` };
    if (!stopById.has(destId)) return { ok: false, error: `없는 도착 정류장: ${destId}` };
    if (originId === destId) return { ok: false, error: '출발과 도착이 같은 정류장' };
    if (!Number.isInteger(partySize) || partySize < config.PARTY_MIN || partySize > config.PARTY_MAX) {
      return { ok: false, error: `탑승 인원은 ${config.PARTY_MIN}~${config.PARTY_MAX}명` };
    }
    const call = {
      callId: 'c-' + state.nextId++,
      originId,
      destId,
      partySize,
      requestTime: state.simTime,
      status: 'REQUESTED',
      busId: null,
      firstPickup: null,
      firstDropoff: null,
      etaPickup: null,
      etaDropoff: null,
      pickedUpAt: null,
      droppedAt: null,
      lastDecision: null,
    };
    state.calls.set(call.callId, call);
    if (!tryAssign(call)) state.pending.push(call.callId); // 지금은 못 잡음 → 매 tick 다시 시도
    return { ok: true, response: { callId: call.callId, status: call.status, busId: call.busId } };
  }

  // ───────── 이동 (웹 tick · arriveBusAtStop) ─────────
  function arrive(bus) {
    const wp = bus.waypoints.shift();
    const t = bus.segEnd; // 도착 시각은 tick 시각이 아니라 정확한 구간 끝 시각
    bus.atStop = wp.stopId;
    bus.status = 'idle';
    bus.from = null;
    bus.to = null;
    for (const id of wp.dropoffs) {
      bus.onboard.delete(id);
      const c = state.calls.get(id);
      c.status = 'COMPLETED';
      c.droppedAt = t;
    }
    for (const id of wp.pickups) {
      bus.onboard.add(id);
      const c = state.calls.get(id);
      c.status = 'ONBOARD';
      c.pickedUpAt = t;
    }
    if (bus.waypoints.length) startNextSegment(bus, t); // 정차 시간 없이 바로 다음 경유지로
  }

  function tick(dtMin) {
    state.simTime += dtMin;
    for (const bus of state.buses) {
      // 한 tick 안에 정류장 여러 개를 지날 수도 있어서 while
      while (bus.status === 'moving' && bus.segEnd <= state.simTime + EPS) arrive(bus);
    }
    // 대기열 재시도 (웹은 정류장 도착 때만, 서버는 매 tick)
    const still = [];
    for (const id of state.pending) {
      const call = state.calls.get(id);
      if (state.simTime - call.requestTime > config.MAX_WAIT_MIN + EPS) {
        call.status = 'REJECTED';
        continue;
      }
      if (!tryAssign(call)) still.push(id);
    }
    state.pending = still;
  }

  // ───────── 조회 (공통 규약 형식) ─────────
  function busPosition(bus) {
    if (bus.status === 'moving') {
      const f = stopById.get(bus.from);
      const to = stopById.get(bus.to);
      const span = bus.segEnd - bus.segStart;
      const r = span <= EPS ? 1 : Math.min(1, Math.max(0, (state.simTime - bus.segStart) / span));
      return { lat: f.lat + (to.lat - f.lat) * r, lng: f.lng + (to.lng - f.lng) * r };
    }
    const s = stopById.get(bus.atStop);
    return { lat: s.lat, lng: s.lng };
  }

  const busInfo = (bus) => {
    const p = busPosition(bus);
    return { busId: bus.busId, lat: round7(p.lat), lng: round7(p.lng), load: loadOf(bus.onboard), capacity: config.CAPACITY };
  };
  const round7 = (x) => Math.round(x * 1e7) / 1e7;
  const routeStop = (wp) => {
    const s = stopById.get(wp.stopId);
    return { stopId: wp.stopId, lat: s.lat, lng: s.lng, pickups: [...wp.pickups], dropoffs: [...wp.dropoffs] };
  };

  // GET /calls/{callId} → CallDetail (없으면 null → 404)
  function getCallDetail(callId) {
    const c = state.calls.get(callId);
    if (!c) return null;
    const active = c.status === 'ASSIGNED' || c.status === 'ONBOARD';
    let route = [];
    if (active) {
      const bus = state.buses.find((b) => b.busId === c.busId);
      // 이 승객이 내리는 경유지까지만 (그 사이 다른 승객 callId가 있으면 합승)
      const end = bus.waypoints.findIndex((wp) => wp.dropoffs.includes(c.callId));
      route = bus.waypoints.slice(0, end + 1).map(routeStop);
    }
    return {
      callId: c.callId,
      status: c.status,
      partySize: c.partySize,
      originId: c.originId,
      destId: c.destId,
      simClock: formatClock(state.simTime),
      etaPickupMin: c.status === 'ASSIGNED' ? round1(Math.max(0, c.etaPickup - state.simTime)) : null,
      etaDropoffMin: active ? round1(Math.max(0, c.etaDropoff - state.simTime)) : null,
      bus: active ? busInfo(state.buses.find((b) => b.busId === c.busId)) : null,
      route,
      path: [], // B6 도로 좌표 전까지 빈 배열 → 앱이 직선으로 이음
    };
  }

  // GET /buses → BusState 배열 (웹 관제용)
  function getBuses() {
    return state.buses.map((bus) => ({ ...busInfo(bus), route: bus.waypoints.map(routeStop), path: [] }));
  }

  // 관제 전용: 호출 목록 (최근 것이 앞). 배차 판정 근거(decision)도 같이 준다
  function listCalls(limit = 50) {
    return [...state.calls.values()]
      .sort((a, b) => b.requestTime - a.requestTime || Number(b.callId.slice(2)) - Number(a.callId.slice(2)))
      .slice(0, limit)
      .map((c) => {
        const d = getCallDetail(c.callId);
        return {
          callId: c.callId,
          status: c.status,
          originId: c.originId,
          destId: c.destId,
          partySize: c.partySize,
          busId: c.busId,
          requestClock: formatClock(c.requestTime),
          etaPickupMin: d.etaPickupMin,
          etaDropoffMin: d.etaDropoffMin,
          decision: c.lastDecision && { clock: formatClock(c.lastDecision.at), chosen: c.lastDecision.chosen, candidates: c.lastDecision.candidates },
        };
      });
  }

  return {
    requestCall,
    tick,
    getCallDetail,
    getBuses,
    listCalls,
    reset,
    // 테스트·관제용
    get simTime() {
      return state.simTime;
    },
    simClock: () => formatClock(state.simTime),
    travelMin,
    _state: state,
  };
}

module.exports = { createEngine, parseClock, formatClock };

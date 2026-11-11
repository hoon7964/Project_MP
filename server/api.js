// API (B4) — 공통 규약 3 그대로.
// Express와 분리해 두어서 테스트하기 쉽다: 각 함수는 { status, body } 또는 { status, file }을 돌려주고,
// index.js가 이걸 Express 응답으로 바꾼다.
//
//   GET  /stops            → Stop 배열 (승차 거점 먼저)
//   GET  /map              → MapInfo
//   GET  /map/image        → PNG 파일
//   POST /calls            → CallResponse      (잘못된 요청 400)
//   GET  /calls/{callId}   → CallDetail        (모르는 callId 404)
//   GET  /buses            → BusState 배열 (웹 관제용)
//
// 관제 전용 (공통 규약 밖, 앱은 안 씀): GET /calls, GET /sim, POST /sim/speed, POST /sim/reset

function createApi({ db, engine, sim }) {
  const stops = db.getStops(); // DB는 서버 켤 때 만들어지고 실행 중엔 안 바뀌어서 한 번만 읽는다
  const mapInfo = db.getMapInfo();

  return {
    getStops: () => ({ status: 200, body: stops }),
    getMap: () => ({ status: 200, body: mapInfo }),
    getMapImage: () => ({ status: 200, file: db.mapImagePath }),

    postCall(body) {
      const { originId, destId, partySize } = body || {};
      if (typeof originId !== 'string' || typeof destId !== 'string' || typeof partySize !== 'number') {
        return { status: 400, body: { error: '본문은 { "originId": "07003", "destId": "07275", "partySize": 1 } 형식' } };
      }
      const r = engine.requestCall({ originId, destId, partySize });
      return r.ok ? { status: 200, body: r.response } : { status: 400, body: { error: r.error } };
    },

    getCall(callId) {
      const d = engine.getCallDetail(callId);
      return d ? { status: 200, body: d } : { status: 404, body: { error: `모르는 callId: ${callId}` } };
    },

    getBuses: () => ({ status: 200, body: engine.getBuses() }),

    // ── 관제 전용 (웹 관제만 씀, 앱은 안 씀. 공통 규약 밖) ──
    listCalls: () => ({ status: 200, body: engine.listCalls() }),
    getSim: () => ({ status: 200, body: { simClock: engine.simClock(), speed: sim.speed, busCount: engine.getBuses().length } }),
    postSpeed(body) {
      const speed = Number((body || {}).speed);
      if (!Number.isInteger(speed) || speed < 1 || speed > 60) return { status: 400, body: { error: 'speed는 1~60 정수' } };
      sim.speed = speed;
      return { status: 200, body: { speed } };
    },
    postReset() {
      engine.reset();
      return { status: 200, body: { simClock: engine.simClock() } };
    },
  };
}

module.exports = { createApi };

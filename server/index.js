// 달무리버스 서버
//   npm start → 웹 관제 http://localhost:3000/ , 에뮬레이터에서는 http://10.0.2.2:3000
//   API는 api.js (공통 규약 3), 배차·이동은 engine.js, DB는 db.js

const path = require('path');
const express = require('express');
const config = require('./config');
const { openDb } = require('./db');
const { createEngine } = require('./engine');
const { createApi } = require('./api');

const db = openDb();
const engine = createEngine({ stops: db.getStops(), config });
const sim = { speed: config.SIM_SPEED }; // 배속 (관제에서 바꿀 수 있음)
const api = createApi({ db, engine, sim });

const app = express();

// 요청 한 줄 로그 (앱·관제가 계속 조회하는 GET은 빼고: /calls, /calls/{id}, /buses, /sim)
const QUIET = /^\/(calls(\/.*)?|buses|sim)$/;
app.use((req, res, next) => {
  if (!(req.method === 'GET' && QUIET.test(req.path))) {
    res.on('finish', () => console.log(`[${engine.simClock()}] ${req.method} ${req.originalUrl} → ${res.statusCode}`));
  }
  next();
});
app.use(express.json());

// api.js의 { status, body | file } → Express 응답
const send = (res, r) => (r.file ? res.status(r.status).sendFile(r.file) : res.status(r.status).json(r.body));

app.get('/stops', (req, res) => send(res, api.getStops()));
app.get('/map', (req, res) => send(res, api.getMap()));
app.get('/map/image', (req, res) => send(res, api.getMapImage()));
app.post('/calls', (req, res) => send(res, api.postCall(req.body)));
app.get('/calls/:callId', (req, res) => send(res, api.getCall(req.params.callId)));
app.get('/buses', (req, res) => send(res, api.getBuses()));
// 관제 전용
app.get('/calls', (req, res) => send(res, api.listCalls()));
app.get('/sim', (req, res) => send(res, api.getSim()));
app.post('/sim/speed', (req, res) => send(res, api.postSpeed(req.body)));
app.post('/sim/reset', (req, res) => send(res, api.postReset()));
app.get('/health', (req, res) => res.json({ ok: true, simClock: engine.simClock(), stops: db.getStops().length, buses: config.BUS_COUNT }));

app.use(express.static(path.join(__dirname, '..', 'web'))); // 웹 관제 (web/index.html)

// JSON 형식이 깨진 요청 등 → 400, 그 밖의 오류 → 500
app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON 형식이 잘못됨' });
  console.error(err);
  res.status(500).json({ error: '서버 오류' });
});

// 시뮬레이션 시계: 실제 1초마다 시뮬 (배속 / 60)분 진행
const timer = setInterval(() => engine.tick((config.TICK_MS / 1000) * (sim.speed / 60)), config.TICK_MS);

const server = app.listen(config.PORT, config.HOST, () => {
  console.log(`달무리버스 서버: http://localhost:${config.PORT}/  (에뮬레이터: http://10.0.2.2:${config.PORT})`);
  console.log(`시뮬 시각 ${engine.simClock()}부터 ${sim.speed}배속, 버스 ${config.BUS_COUNT}대, 정류장 ${db.getStops().length}개`);
});

function shutdown() {
  clearInterval(timer);
  server.close(() => {
    db.close();
    process.exit(0);
  });
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

module.exports = { app, engine };

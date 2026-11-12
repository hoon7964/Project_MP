// 달무리버스 서버
//   npm start → 웹 관제 http://localhost:3000/ , 에뮬레이터에서는 http://10.0.2.2:3000
//   API는 api.js (공통 규약 3), 배차·이동은 engine.js, DB는 db.js

const fs = require('fs');
const path = require('path');
const express = require('express');
const config = require('./config');
const { openDb, loadPaths } = require('./db');
const { createEngine } = require('./engine');
const { createApi } = require('./api');

const db = openDb();
const engine = createEngine({ stops: db.getStops(), config, paths: loadPaths() });
const sim = { speed: config.SIM_SPEED }; // 배속 (관제에서 바꿀 수 있음)
const api = createApi({ db, engine, sim });

const app = express();

// 요청 한 줄 로그 (앱·관제가 계속 조회하는 GET과 글꼴·라이브러리 파일은 빼고: /calls, /calls/{id}, /buses, /sim, /vendor/…)
const QUIET = /^\/(calls(\/.*)?|buses|sim|vendor\/.*)$/;
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

// 웹 관제가 쓰는 지도 라이브러리·글꼴: npm install로 받아 둔 것을 /vendor/… 로 내준다 → 시연장에 인터넷이 없어도 됨
// (없으면 web/index.html이 인터넷(CDN)에서 대신 받고, 글꼴은 컴퓨터 기본 글꼴로)
const VENDOR = {
  leaflet: 'leaflet/dist',
  'fonts/gowun-batang': '@fontsource/gowun-batang',
  'fonts/ibm-plex-sans-kr': '@fontsource/ibm-plex-sans-kr',
};
const vendorMissing = [];
for (const [url, dir] of Object.entries(VENDOR)) {
  const full = path.join(__dirname, 'node_modules', dir);
  if (fs.existsSync(full)) app.use('/vendor/' + url, express.static(full, { maxAge: '7d' }));
  else vendorMissing.push(dir.split('/dist')[0]);
}

// 없는 주소 → 404도 JSON으로 (앱이 주소를 잘못 불렀을 때 Gson이 HTML을 읽다 죽지 않게)
app.use((req, res) => res.status(404).json({ error: `없는 주소: ${req.method} ${req.path}` }));

// JSON 형식이 깨진 요청 등 → 400, 그 밖의 오류 → 500
app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON 형식이 잘못됨' });
  console.error(err);
  res.status(500).json({ error: '서버 오류' });
});

// 시뮬레이션 시계: 실제 1초마다 시뮬 (배속 / 60)분 진행
const timer = setInterval(() => engine.tick((config.TICK_MS / 1000) * (sim.speed / 60)), config.TICK_MS);

// Express 5는 포트를 못 열었을 때도 이 콜백을 오류와 함께 부른다 → 그때는 아래 server.on('error')가 안내
const server = app.listen(config.PORT, config.HOST, (err) => {
  if (err) return;
  console.log(`달무리버스 서버: http://localhost:${config.PORT}/  (에뮬레이터: http://10.0.2.2:${config.PORT})`);
  console.log(`시뮬 시각 ${engine.simClock()}부터 ${sim.speed}배속, 버스 ${config.BUS_COUNT}대, 정류장 ${db.getStops().length}개`);
  const { road, straight } = engine.roadInfo;
  console.log(
    straight === 0
      ? `도로 경로: 정류장 ${road}쌍 모두 도로를 따라 이동 (data/paths.json)`
      : `⚠ 도로 경로: ${road}쌍만 도로, ${straight}쌍은 직선×${config.ROAD_FACTOR}로 대신함 → npm run paths 실행`
  );
  if (vendorMissing.length) {
    console.log(`⚠ 웹 관제용 ${vendorMissing.join(', ')} 없음 → 서버를 끄고 npm install (그 전까지는 인터넷에서 받음)`);
  }
});

server.on('error', (e) => {
  if (e.code === 'EADDRINUSE') {
    console.error(`\n${config.PORT}번 포트를 이미 쓰고 있음: 서버가 다른 창에서 켜져 있으면 그 창에서 Ctrl+C, 아니면 다른 포트로 (README 5. 안 될 때)`);
  } else {
    console.error(e);
  }
  process.exit(1);
});

// Ctrl+C: 웹 관제·앱이 계속 조회 중이어도 바로 꺼지게 연결을 끊고, 1초 안에 안 꺼지면 그냥 끝낸다
let closing = false;
function shutdown() {
  if (closing) return;
  closing = true;
  clearInterval(timer);
  server.close(() => {
    db.close();
    process.exit(0);
  });
  server.closeAllConnections();
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

module.exports = { app, engine };

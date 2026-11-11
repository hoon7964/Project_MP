// 달무리버스 서버 (B2 뼈대). API는 B4에서 이 파일에 붙인다.
//   npm start → http://localhost:3000/ (웹 관제), 에뮬레이터에서는 http://10.0.2.2:3000

const path = require('path');
const express = require('express');
const config = require('./config');
const { openDb } = require('./db');
const { createEngine } = require('./engine');

const db = openDb();
const engine = createEngine({ stops: db.getStops(), config });

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, '..', 'web'))); // 웹 관제 (web/index.html)

// 살아 있는지 확인용 (B4에서 공통 규약 API를 여기에 더함)
app.get('/health', (req, res) => {
  res.json({ ok: true, simClock: engine.simClock(), stops: db.getStops().length, buses: config.BUS_COUNT });
});

// 시뮬레이션 시계: 실제 1초마다 시뮬 (SIM_SPEED / 60)분 진행
const dtMin = (config.TICK_MS / 1000) * (config.SIM_SPEED / 60);
const timer = setInterval(() => engine.tick(dtMin), config.TICK_MS);

const server = app.listen(config.PORT, config.HOST, () => {
  console.log(`달무리버스 서버: http://localhost:${config.PORT}/  (에뮬레이터: http://10.0.2.2:${config.PORT})`);
  console.log(`시뮬 시각 ${engine.simClock()}부터 ${config.SIM_SPEED}배속, 버스 ${config.BUS_COUNT}대, 정류장 ${db.getStops().length}개`);
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

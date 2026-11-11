// SQLite (B2). 교수님 말씀 "데이터는 긁어와서 서버에 DB화".
// Node 내장 node:sqlite 를 써서 따로 설치할 게 없다 (Node 22.13 이상).
// DB 파일(data/dalmuri.db)은 수집 결과(stops.json, map.json)로 서버를 켤 때마다 다시 만든다.
// 그래서 커밋하지 않고(.gitignore *.db), JSON만 바꾸면 다음 실행에 바로 반영된다.

const fs = require('fs');
const path = require('path');

let DatabaseSync;
try {
  ({ DatabaseSync } = require('node:sqlite'));
} catch {
  throw new Error(`이 Node(${process.version})에는 내장 SQLite가 없음. Node 22.13 이상(권장: 24 LTS)으로 업데이트`);
}

const DATA = path.join(__dirname, 'data');

function openDb(file = path.join(DATA, 'dalmuri.db')) {
  const stops = JSON.parse(fs.readFileSync(path.join(DATA, 'stops.json'), 'utf8'));
  const map = JSON.parse(fs.readFileSync(path.join(DATA, 'map.json'), 'utf8'));

  const db = new DatabaseSync(file);
  db.exec(`
    DROP TABLE IF EXISTS stops;
    DROP TABLE IF EXISTS map_info;
    CREATE TABLE stops (
      stopId TEXT PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL,
      kind TEXT NOT NULL CHECK (kind IN ('HUB', 'HOME')),
      nightRoutes TEXT NOT NULL,        -- "N26,N72" (앱 Room과 같은 방식)
      ord INTEGER NOT NULL              -- stops.json 순서 (승차 거점이 앞)
    );
    CREATE TABLE map_info (
      mapId TEXT PRIMARY KEY, imageUrl TEXT NOT NULL, widthPx INTEGER NOT NULL, heightPx INTEGER NOT NULL,
      north REAL NOT NULL, south REAL NOT NULL, west REAL NOT NULL, east REAL NOT NULL, attribution TEXT NOT NULL
    );
  `);

  const insStop = db.prepare('INSERT INTO stops VALUES (?, ?, ?, ?, ?, ?, ?)');
  const insMap = db.prepare('INSERT INTO map_info VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)');
  db.exec('BEGIN');
  stops.forEach((s, i) => insStop.run(s.stopId, s.name, s.lat, s.lng, s.kind, (s.nightRoutes || []).join(','), i));
  insMap.run(map.mapId, map.imageUrl, map.widthPx, map.heightPx, map.north, map.south, map.west, map.east, map.attribution);
  db.exec('COMMIT');

  return {
    // GET /stops 형식 (공통 규약 Stop)
    getStops() {
      return db
        .prepare('SELECT stopId, name, lat, lng, kind, nightRoutes FROM stops ORDER BY ord')
        .all()
        .map((r) => ({ ...r, nightRoutes: r.nightRoutes ? r.nightRoutes.split(',') : [] }));
    },
    // GET /map 형식 (공통 규약 MapInfo)
    getMapInfo() {
      return { ...db.prepare('SELECT * FROM map_info LIMIT 1').get() };
    },
    mapImagePath: path.join(DATA, 'map.png'),
    close: () => db.close(),
  };
}

module.exports = { openDb };

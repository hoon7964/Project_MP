# 달무리버스 서버

합승 배차·버스 이동 시뮬레이션·정류장/지도 데이터를 맡는 서버다. 앱과 웹 관제는 이 서버의 API만 부른다.
API 이름·필드는 루트 `CLAUDE.md`의 공통 규약 3·4가 기준이다. 설계와 "웹 시뮬레이터와 다르게 한 점"은 `DESIGN.md`에 있다.

## 1. 켜기

**필요한 것:** Node.js **22.13 이상** (권장 24 LTS, https://nodejs.org). 확인: `node -v`

```powershell
cd C:\dev\Project_MP\server
npm install          # 처음 한 번 (express, sharp)
npm start            # 서버 켜기. 끄려면 Ctrl+C
```

켜지면 이렇게 나온다:

```
달무리버스 서버: http://localhost:3000/  (에뮬레이터: http://10.0.2.2:3000)
시뮬 시각 01:30부터 6배속, 버스 3대, 정류장 25개
도로 경로: 정류장 600쌍 모두 도로를 따라 이동 (data/paths.json)
```

| 어디서 | 주소 |
| --- | --- |
| 노트북 브라우저 (웹 관제) | http://localhost:3000/ (서버를 켠 뒤 이 주소로 열어야 함. `web/index.html`을 파일로 열면 연결 안 됨) |
| 안드로이드 에뮬레이터 | `http://10.0.2.2:3000/` (`AppConfig.BASE_URL`) |
| 같은 와이파이의 실제 폰 | `http://<노트북 IP>:3000/` (PowerShell `ipconfig`의 IPv4 주소, 방화벽에서 Node 허용 필요) |
| 살아 있는지 확인 | http://localhost:3000/health → `simClock`이 계속 오르면 정상 |

데이터(정류장·지도·도로 경로)는 레포에 커밋돼 있어서 **수집 없이 바로 켜진다.** 서버는 켤 때마다 `data/stops.json`, `data/map.json`으로 SQLite DB(`data/dalmuri.db`, 커밋 안 함)를 새로 만들고, 버스·호출 상태는 메모리에 둔다. **서버를 껐다 켜면 호출과 버스 위치는 처음(01:30)으로 돌아간다.**

### 시연용 설정 바꾸기

`config.js`가 기준이다. 실행할 때 잠깐 바꾸려면 PowerShell에서:

```powershell
$env:SIM_SPEED=12; npm start      # 12배속 (기본 6배속: 실제 1초 = 시뮬 6초)
$env:BUS_COUNT=4;  npm start      # 버스 4대
$env:PORT=3001;    npm start      # 3000번이 이미 쓰이고 있을 때
```

다시 기본값으로: 새 PowerShell 창을 열거나 `Remove-Item Env:SIM_SPEED`.

## 2. API

모든 응답은 JSON(지도 이미지만 PNG). 시간 값(`etaPickupMin` 등)은 **시뮬레이션 분**이다.

| API | 하는 일 | 보내는 것 | 받는 것 |
| --- | --- | --- | --- |
| `GET /stops` | 정류장 25개 (승차 거점 6개가 앞) | — | `Stop` 배열 |
| `GET /map` | 지도 정보 | — | `MapInfo` |
| `GET /map/image` | 지도 그림 2400×2306 | — | PNG |
| `POST /calls` | 호출 | `CallRequest` | `CallResponse` |
| `GET /calls/{callId}` | 내 배차 상태 (앱이 2초마다) | — | `CallDetail` |
| `GET /buses` | 버스 전체 상태 (웹 관제용) | — | `BusState` 배열 |

**오류:** 같은 정류장·없는 정류장·인원 1~3명 밖·깨진 JSON → `400 { "error": "..." }`, 모르는 `callId` → `404 { "error": "..." }`.

### 예시

```jsonc
// POST /calls
{ "originId": "07003", "destId": "07275", "partySize": 1 }
// → 200
{ "callId": "c-1", "status": "ASSIGNED", "busId": "BUS2" }

// GET /calls/c-1   (바로 이어서 같은 상봉역에서 같은 방향 c-2가 호출해 합승, 망우역의 2호차가 막 출발)
{
  "callId": "c-1", "status": "ASSIGNED", "partySize": 1,
  "originId": "07003", "destId": "07275",
  "simClock": "01:30", "etaPickupMin": 1.6, "etaDropoffMin": 7.4,
  "bus": { "busId": "BUS2", "lat": 37.5978284, "lng": 127.0911386, "load": 0, "capacity": 6 },
  "route": [
    { "stopId": "07003", "lat": 37.5959591, "lng": 127.0837436, "pickups": ["c-1"], "dropoffs": [] },
    { "stopId": "07003", "lat": 37.5959591, "lng": 127.0837436, "pickups": ["c-2"], "dropoffs": [] },
    { "stopId": "07275", "lat": 37.6076432, "lng": 127.0952481, "pickups": [], "dropoffs": ["c-1"] }
  ],
  "path": [
    { "lat": 37.597828, "lng": 127.091139 },   // 첫 점 = 버스 지금 위치 (망우역)
    { "lat": 37.597864, "lng": 127.091125 },
    ...                                          // 도로를 따라 모두 32개
    { "lat": 37.607643, "lng": 127.095248 }     // 끝 점 = 내 하차 정류장
  ]
}
```

### 앱이 알아 둘 것

- **상태 흐름:** `REQUESTED`(빈 버스 찾는 중, `busId` null) → `ASSIGNED`(버스 오는 중) → `ONBOARD`(탑승 중) → `COMPLETED`(하차 완료). 15분 안에 못 잡으면 `REJECTED`. `COMPLETED`·`REJECTED`가 오면 조회를 멈추면 된다 (끝난 호출도 서버에 남아 있어서 404가 나지 않는다).
- **`route`는 그 승객이 내리는 정류장까지만** 온다. 사이에 다른 `callId`가 있으면 합승이다. 같은 정류장이 연달아 나올 수 있다(같은 곳에서 여러 명 승차).
- **`path` = 버스 지금 위치부터 내 하차 정류장까지의 도로 좌표** (B6). 그대로 이어 그리면 버스가 봉화산을 가로지르지 않고 도로를 따라간다. `REQUESTED`·`COMPLETED`·`REJECTED`일 때는 `[]`. 앱은 `path`가 비어 있으면 `route`를 직선으로 잇는 코드도 그대로 두면 된다 (서버에 `paths.json`이 없을 때 대비).
- `etaPickupMin`은 `ONBOARD`부터 `null`, `etaDropoffMin`은 `ASSIGNED`·`ONBOARD`일 때만 있다.
- 버스 위치는 시뮬레이션 값이다. 2초마다 받은 위치 사이를 부드럽게 옮기면 실시간처럼 보인다 (공통 규약 6).

### 관제 전용 API (웹 관제만 씀, 앱은 안 씀)

| API | 하는 일 |
| --- | --- |
| `GET /calls` | 호출 목록 (최근 50건, 최근 것이 앞). 각 호출의 배차 판정 근거 `decision.candidates`(버스별 대기·기존 승객 지연·제외 이유) 포함 |
| `GET /sim` | `{ simClock, speed, busCount }` |
| `POST /sim/speed` | `{ "speed": 12 }` 배속 바꾸기 (1~60) |
| `POST /sim/reset` | 01:30으로 되돌리고 호출 모두 지우기 (**앱으로 한 호출도 지워짐**) |

### PowerShell로 직접 해 보기

```powershell
Invoke-RestMethod http://localhost:3000/stops | Select-Object -First 3
$r = Invoke-RestMethod -Method Post -Uri http://localhost:3000/calls -ContentType "application/json" `
       -Body '{"originId":"07003","destId":"07275","partySize":1}'
Invoke-RestMethod http://localhost:3000/calls/$($r.callId) | ConvertTo-Json -Depth 5
Invoke-RestMethod http://localhost:3000/buses | ConvertTo-Json -Depth 5
```

## 3. 합승 배차는 어떻게 도나 (발표용)

**끼워넣기(삽입 휴리스틱).** 새 호출이 오면 버스 3대 각각의 "앞으로 들를 정류장 목록"에 이 승객의 승차·하차 정류장을 **넣을 수 있는 모든 자리**에 넣어 본다(승차가 하차보다 앞). 그중 제약을 지키면서 **비용이 가장 작은** 버스·자리에 배정한다.

- **제약 (하나라도 어기면 그 후보는 제외)**
  - 정원: 어느 구간에서도 탑승 **인원 합** ≤ 6명 (한 호출이 1~3명)
  - 대기: 새 승객도, 아직 안 탄 기존 승객도 호출 후 **15분 안에** 탑승
  - 기존 승객: **처음 안내받은** 탑승·하차 시각보다 **5분 넘게 늦어지지 않음**
- **비용** = 새 승객의 하차 시각 + 기존 승객들이 늦어지는 시간의 합 (= 모든 승객 하차 시각 합이 늘어나는 양, 제안서 ΔC)
- 지금 못 잡으면 대기열에 두고 매초 다시 시도, 15분이 지나면 `REJECTED`
- 버스는 정류장 사이를 **OSM 도로 최단 경로**로 일정 속도(26km/h) 이동하고, 정류장에 닿으면 하차 → 승차 순서로 처리. 도로 경로는 지도를 그린 같은 OSM 데이터로 미리 계산해 둔다 (`DESIGN.md` 6절)

**예 (`npm run test:engine`의 시연 시나리오)**

1. 01:30 페르소나가 상봉역.중랑우체국 → 신내동성3차아파트 호출 → 망우역에 있던 **2호차**가 비용이 가장 작아 배정 (약 2분 뒤 탑승)
2. 같은 상봉역에서 같은 방향(신내8단지) 2명 호출 → 2호차 노선에 끼우는 비용이 가장 작음 → **합승**, 노선에 경유 정류장이 하나 늘어남
3. 2호차가 신내 쪽으로 떠난 뒤 상봉역에서 반대 방향(태릉고) 호출 → 2호차는 다 내려 주고 돌아와야 해서 대기 12분 → **3호차** 배정

무작위 호출 80건 시험에서 최대 대기 14.7분(한도 15), 기존 승객 최대 지연 5.0분(한도 5), 최대 탑승 6명(정원 6), 승객의 59%가 합승했다. 같은 시험에서 봉화산 정상 부근 470m 안으로 지나가는 경로가 없고, 버스 위치가 늘 도로 경로 위(오차 0.01m)에 있는지도 확인한다.

## 4. 파일

```
server/
├─ index.js        Express: API 연결, 웹 관제(../web: index.html + control.js) 제공, 1초마다 tick, 요청 로그
├─ api.js          API 처리 (공통 규약 3)
├─ engine.js       배차·이동 엔진 (HTTP와 무관)
├─ db.js           SQLite (Node 내장 node:sqlite)
├─ config.js       설정값 (공통 규약 6)
├─ DESIGN.md       설계, 웹 시뮬레이터와 다르게 한 점
├─ scripts/
│  ├─ collect.js       데이터 수집: 정류장 + 지도 + 도로 경로 (npm run collect)
│  ├─ paths.js         도로 경로만 다시 (npm run paths) ← lib/roads.js
│  ├─ test-engine.js   배차 엔진 테스트 (npm run test:engine)
│  └─ test-api.js      API 테스트, 서버 켜 둔 채로 (npm run test:api)
└─ data/
   ├─ area.json         시범지역 범위, 시범 정류장 ID (승차 거점 / 주거지)
   ├─ stops.json        GET /stops 데이터
   ├─ stops-all.json    범위 안 전체 정류장 225개 + 서는 N버스 (참고용)
   ├─ map.json, map.png GET /map, /map/image
   ├─ map-check.png     지도 위 정류장 확인용 그림
   ├─ paths.json        정류장 600쌍의 도로 거리·좌표 (엔진이 씀)
   └─ raw/              원본 (열린데이터광장 정류소·노선 파일, OSM 데이터)
```

| 명령 | 하는 일 |
| --- | --- |
| `npm start` | 서버 켜기 |
| `npm run test:engine` | 배차 엔진 테스트 (서버 안 켜도 됨) |
| `npm run test:api` | API 테스트 (다른 창에서 서버를 켜 둔 채) |
| `npm run collect` | 정류장·지도·도로 경로 다시 만들기 (`area.json`을 바꿨을 때만) |
| `npm run paths` | 도로 경로만 다시 만들기 (1초, 인터넷·sharp 필요 없음) |

## 5. 안 될 때

| 증상 | 확인 |
| --- | --- |
| `내장 SQLite가 없음` | `node -v`가 22.13보다 낮음 → Node 24 LTS 설치 |
| 켤 때 `⚠ 도로 경로: …직선×1.3으로 대신함` | `data/paths.json`이 없거나 정류장이 바뀜 → `npm run paths` |
| `EADDRINUSE` | 서버가 이미 켜져 있음 → 그 창에서 Ctrl+C, 또는 `$env:PORT=3001` |
| 에뮬레이터에서 접속 안 됨 | 주소가 `http://10.0.2.2:3000`인지, 앱 Manifest에 평문(http) 통신 허용이 있는지, 노트북에서 http://localhost:3000/health 가 열리는지 |
| 실제 폰에서 접속 안 됨 | 같은 와이파이인지, 주소가 노트북 IPv4인지, Windows 방화벽에서 Node.js 허용 |
| 호출이 계속 `REQUESTED` | 버스가 모두 꽉 찼거나 15분 안에 올 버스가 없음 → 15분 지나면 `REJECTED` |

서버 쪽 문제 같으면 "무엇을 했더니 → 무엇이 나왔다"와 서버 창의 로그를 카톡으로 백(정훈)에게.

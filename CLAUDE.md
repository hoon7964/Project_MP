# 달무리버스 — Claude Code 규칙

> 기준은 팀 계획 문서(Claude Docs)의 **"프로젝트 설정" 탭**이다: https://claude.ai/artifact/3vf4SLdECUgTiCgwUr8xPh
> 이 파일은 문서의 "Claude 사용법"(CLAUDE.md 규칙)과 "공통 규약" 탭을 옮긴 것이다 (11/11 기준). 문서가 바뀌면 이 파일도 맞춰 고친다.
> 역할별 할 일: 프론트(윤재열) → `docs/ROLE_FRONT.md`, 백(장정훈) → `docs/ROLE_BACK.md`
> 시작할 때 "나는 프론트야, ROLE_FRONT.md의 F3 하자"처럼 역할과 작업을 말한다.

## 작업 규칙

- 역할 파일의 할 일을 위에서부터 하나씩. 끝나면 확인 방법을 알려주고 체크박스를 `[x]`로 바꾼다.
- 시작 전에 `git pull`이 필요한지 확인한다.
- 코드는 직접 수정하되 무엇을 왜 바꿨는지 짧게 설명한다.
- commit·push는 사용자가 확인한 뒤에만. 커밋 앞머리 `[프론트]`, `[백]` (둘 다 해당하는 루트 파일은 `[공통]`).
- 안드로이드는 `gradlew.bat assembleDebug`로 빌드해 봐도 되지만 화면 확인은 사용자가. 서버는 띄워서 요청을 보내 확인해도 되고, 끝나면 끈다.
- 라이브러리·외부 API 사용법은 추측하지 않고 공식 문서로 확인한다.
- 자기 역할 영역 밖의 파일은 고치지 않는다 (프론트 `DalmuriBus/`, 백 `server/`·`web/`). 필요하면 "상대에게 카톡으로 보낼 내용"으로 정리한다.
- 공통 규약의 이름을 바꾸지 않는다. 바꾸거나 더할 게 있으면 `[규약 변경 제안]`으로 표시한다. 앱은 실행 중 외부 API·지도 SDK를 부르지 않는다.
- `local.properties`, `.env`, API 키, DB 파일, `node_modules/`, `.idea/`는 커밋하지 않는다.
- Hilt, Firestore·Realtime Database, 지도 SDK, GPS는 추가하지 않는다.
- 교수님 말씀(11/11 미팅)과 다른 판단을 넣었으면 그렇다고 밝힌다.
- 답은 한국어 반말로, 짧고 명확하게.

---

# 공통 규약

프론트와 백이 똑같이 따르는 규칙이다. 바꿔야 하면 카톡으로 합의하고 문서의 "공통 규약" 탭부터 고친 뒤 이 파일을 맞춘다.

## 1. 프로젝트 요약

- **달무리버스**: 심야 합승형 자율주행버스 호출·배차 앱. 모바일프로그래밍 2인 팀 프로젝트.
- 시범지역: **서울 중랑구 봉화산 일대** (묵1·신내1·2·중화1·상봉1·망우본동). 북서 태릉입구역 · 북동 봉화산역·신내역 · 남서 상봉역 · 남동 망우역·우림시장까지의 약 3.0km × 2.8km 사각형 (정확한 범위는 `server/data/area.json`). 늦은 밤 지하철·N버스에서 내린 뒤 집 근처 정류장까지 이어주는 서비스.
- **교수님 미팅(11/11)에서 정한 구조**
  - **안드로이드 앱 (프론트)**: 출발·도착 정류장 + 탑승 인원을 골라 호출 → 받은 배차 ID로 서버에 주기적으로 상태를 물어서 지도에 버스 위치·노선을 그린다. 노선이 바뀌면 받은 대로 다시 그린다. GPS(내 위치) 안 씀.
  - **서버 (백)**: 합승·최적 배차, 버스 이동 시뮬레이션, 데이터 수집, API. 친구가 만든 웹 시뮬레이터는 **참고용**이고, 서버는 우리 구조에 맞게 새로 만든다.
  - **웹 관제**: 친구 웹을 참고한 관제 화면. 서버 상태를 받아서 그린다. 서버가 같은 주소로 띄워준다.
- **정류장과 지도는 미리 받아 서버 DB에 저장한다.** 서버의 수집 스크립트(`npm run collect`)가 개발할 때 한 번, 서울 열린데이터광장 "서울시 버스정류소 위치정보" 파일(인증키 불필요)에서 시범지역 사각형 안의 정류소만 뽑고, 같은 사각형의 OpenStreetMap 지도 데이터(Overpass API)로 지도 이미지를 직접 그린다 (OSM 타일 서버의 그림을 미리 받아 두는 건 이용 정책상 금지라서 쓰지 않음). 결과(`stops.json`, `map.json`, `map.png`)는 커밋하고, 서버는 시작할 때 이것으로 DB를 만든다.
- **시범 정류장 = 승차 거점 + 주거지.** 승차 거점은 심야 N버스(N13·N26·N61·N72)가 서는 역 앞 정류장 6개, 주거지는 N버스가 안 서는 아파트·학교·주택가 정류장 19개다 (`server/data/area.json`). 서는 N버스는 "서울시 버스노선별 정류소 정보" 파일로 확인한다. API의 `Stop`은 그대로이고(구분 필드 없음), `stops.json`에 승차 거점이 앞에 온다. 앱이 출발지를 거점으로 제한하지는 않는다.
- 앱은 실행 중에 우리 서버(와 Firebase 로그인)만 부르고, **처음 실행할 때 서버에서 받아 같은 구조로 Room에 저장**한다. 이미 있으면 다시 받지 않는다.
- **지도 화면 = 레이어 두 장**: 아래는 서버에서 받아 둔 지도 이미지, 위는 정류장·버스(🚌)·노선을 찍는 그리기 레이어.
- 버스 위치는 "가짜"가 아니라 **"시뮬레이션"** 데이터다 (일정 속도로 이동한다고 가정).
- 로그인(Firebase 이메일)은 범위가 명확하니 **먼저 끝내 둔다** (교수님 말씀).
- 범위에서 뺀 것: 내 위치·가까운 정류장 추천, Firestore·RTDB 실시간 동기화, 앱 안 관제 모드, 실행 중 지도 SDK·외부 API 호출, Hilt.

## 2. 레포와 환경

| 항목 | 값 |
| --- | --- |
| 레포 | https://github.com/hoon7964/Project_MP |
| 안드로이드 프로젝트 | `DalmuriBus/` (패키지 `com.dalmuri.bus`) |
| 서버 | `server/` (Node.js + Express, 포트 3000, DB는 SQLite) |
| 웹 관제 | `web/index.html` (서버가 정적 파일로 띄움 → 브라우저에서 `http://localhost:3000/`) |
| 로컬 경로 예시 | `C:\dev\Project_MP` (한글·공백·OneDrive 경로 금지) |

**안드로이드 빌드 환경 (바꾸지 않음)**

- AGP 9.4.1(built-in Kotlin → `org.jetbrains.kotlin.android` 플러그인 추가 금지), Kotlin 2.4.0, Gradle 9.6.0, compileSdk 37, minSdk 26, Java 11
- 의존성은 버전 카탈로그 `gradle/libs.versions.toml`로만 추가한다.
- UI는 Jetpack Compose + Material3. XML 레이아웃 안 씀. Hilt 안 씀.
- Firebase BoM 35.1.0 (`-ktx` 모듈 없음). 로그인에 Auth만 쓴다. `google-services.json`은 재열이 11/13에 `DalmuriBus/`와 함께 push한다 (11/11 기준 레포 main에는 아직 없음).
- 지도 SDK(네이버 등)는 쓰지 않는다. 지도는 서버에서 받은 이미지로 그린다.
- 에뮬레이터에서 노트북 서버 주소는 `http://10.0.2.2:3000`. http라서 앱에 평문 통신 허용 설정이 필요하다.

## 3. API 규약

앱과 서버는 아래 API로만 대화한다. JSON 필드 이름 = 서버 코드 = 앱 데이터 클래스 = Room 컬럼.

| API | 하는 일 | 보내는 것 | 받는 것 |
| --- | --- | --- | --- |
| `GET /stops` | 정류장 목록 (앱 첫 실행 때 한 번) | 없음 | `Stop` 배열 |
| `GET /map` | 지도 정보 (앱 첫 실행 때 한 번) | 없음 | `MapInfo` |
| `GET /map/image` | 지도 이미지 파일 (PNG, 첫 실행 때 한 번) | 없음 | 이미지 |
| `POST /calls` | 호출 | `CallRequest` | `CallResponse` |
| `GET /calls/{callId}` | 내 배차 상태 (앱이 주기적으로) | 없음 | `CallDetail` |
| `GET /buses` | 전체 버스 상태 (웹 관제용) | 없음 | `BusState` 배열 |

```json
// GET /stops 의 원소 (값은 예시)
{ "stopId": "07101", "name": "신내역", "lat": 37.6128, "lng": 127.1032 }

// GET /map (server/data/map.json 실제 값. 범위는 area.json에서 옴)
{ "mapId": "bonghwasan-v1", "imageUrl": "/map/image", "widthPx": 2400, "heightPx": 2306,
  "north": 37.62, "south": 37.5945, "west": 127.0725, "east": 127.106,
  "attribution": "© OpenStreetMap contributors" }

// POST /calls
요청: { "originId": "07101", "destId": "07215", "partySize": 2 }
응답: { "callId": "c-17", "status": "ASSIGNED", "busId": "BUS2" }   // 바로 못 잡으면 "REQUESTED", busId null

// GET /calls/c-17
{
  "callId": "c-17", "status": "ASSIGNED", "partySize": 2,
  "originId": "07101", "destId": "07215",
  "simClock": "01:42", "etaPickupMin": 4.5, "etaDropoffMin": 12.0,
  "bus": { "busId": "BUS2", "lat": 37.6110, "lng": 127.0990, "load": 3, "capacity": 6 },
  "route": [
    { "stopId": "07101", "lat": 37.6128, "lng": 127.1032, "pickups": ["c-17"], "dropoffs": [] },
    { "stopId": "07215", "lat": 37.6075, "lng": 127.0960, "pickups": [], "dropoffs": ["c-17", "c-12"] }
  ],
  "path": []
}

// GET /buses 의 원소
{ "busId": "BUS2", "lat": 37.6110, "lng": 127.0990, "load": 3, "capacity": 6, "route": [ ...RouteStop ], "path": [] }
```

**규칙**

- `callId`가 교수님이 말한 "배차 ID"다.
- `status`는 5개: `REQUESTED`(빈 버스 찾는 중) → `ASSIGNED`(버스 오는 중) → `ONBOARD`(탑승 중) → `COMPLETED`(하차 완료). 최대 대기 15분 안에 못 잡으면 `REJECTED`.
- `REQUESTED`·`REJECTED`·`COMPLETED`일 때 `bus`는 `null`, `route`·`path`는 `[]`, ETA는 `null`. `ONBOARD`일 때 `etaPickupMin`은 `null`.
- ETA는 "지금부터 몇 분 뒤"(시뮬레이션 분). `simClock`은 서버 시뮬레이션 시각 `"HH:mm"`.
- `route`는 버스가 앞으로 들를 정류장 순서. 다른 사람의 `callId`가 섞여 있으면 그게 합승이다.
- `path`는 버스 현재 위치부터 남은 노선을 따라가는 **도로 좌표** 목록(`MapPoint` 배열). 준비 전에는 `[]` → 앱은 버스 위치와 `route` 정류장들을 직선으로 잇는다. (앱 코드는 처음부터 두 경우를 다 처리)
- `load`는 지금 타고 있는 **인원 수 합**(호출 수가 아님).
- 지도 좌표 범위(`north`/`south`/`west`/`east`)는 이미지의 위·아래·왼쪽·오른쪽 끝의 위도·경도다.
- 모르는 `callId` → HTTP 404. 잘못된 요청(같은 정류장, 인원 범위 밖) → HTTP 400.

## 4. 앱 데이터 클래스 (`com.dalmuri.bus.data.model`)

프론트가 1주차 첫 작업(F0)에서 이대로 만든다. 서버 JSON과 이름이 같아야 Gson이 그대로 읽는다.

```kotlin
data class Stop(val stopId: String, val name: String, val lat: Double, val lng: Double)

data class MapInfo(
    val mapId: String, val imageUrl: String, val widthPx: Int, val heightPx: Int,
    val north: Double, val south: Double, val west: Double, val east: Double,
    val attribution: String,
)

data class MapPoint(val lat: Double, val lng: Double)

data class BusInfo(val busId: String, val lat: Double, val lng: Double, val load: Int, val capacity: Int)

data class RouteStop(
    val stopId: String, val lat: Double, val lng: Double,
    val pickups: List<String>, val dropoffs: List<String>,
)

enum class CallStatus { REQUESTED, ASSIGNED, ONBOARD, COMPLETED, REJECTED }

data class CallRequest(val originId: String, val destId: String, val partySize: Int)

data class CallResponse(val callId: String, val status: CallStatus, val busId: String?)

data class CallDetail(
    val callId: String, val status: CallStatus, val partySize: Int,
    val originId: String, val destId: String,
    val simClock: String, val etaPickupMin: Double?, val etaDropoffMin: Double?,
    val bus: BusInfo?, val route: List<RouteStop>, val path: List<MapPoint>,
)
```

Room 테이블: `stops`(`Stop`과 같은 컬럼, `stopId` 기본키), `map_info`(`MapInfo`와 같은 컬럼, `mapId` 기본키). 지도 이미지는 앱 내부 저장소 파일 `map_<mapId>.png`.

## 5. 지도 좌표 → 화면 위치

지도 이미지는 웹 지도와 같은 메르카토르 투영이다. 위도·경도를 이미지 픽셀로 바꾸는 식 (앱·웹 공통):

```
x = (lng - west) / (east - west) * widthPx
m(lat) = ln(tan(π/4 + lat·π/360))
y = (m(north) - m(lat)) / (m(north) - m(south)) * heightPx
```

시범지역이 4km 안쪽이라 위도를 직선 비례로 계산해도 오차는 몇 픽셀이지만, 위 식을 쓴다.

## 6. 정해둔 값

| 이름 | 값 | 어디서 | 출처 |
| --- | --- | --- | --- |
| 앱 조회 간격 | 2초 (`POLL_MS = 2_000`), 받은 위치 사이는 부드럽게 이어 그림 | 앱 | 팀 결정 (교수님 예시는 10초) |
| 버스 | 3대 (`BUS1`, `BUS2`, `BUS3`), 설정값으로 늘릴 수 있게 | 서버 | 친구 웹 참고 |
| 정원 | 6명 | 서버 | 친구 웹 |
| 최대 대기 | 15분 | 서버 | 제안서·친구 웹 |
| 기존 승객 최대 지연 | 5분 | 서버 | 제안서 (친구 웹은 8분) |
| 속도 | 26km/h, 일정 속도 | 서버 | 친구 웹 |
| 배속 | 기본 6배 (실제 1초 = 시뮬 6초) | 서버 | 시연용 |
| 탑승 인원 | 1~3명 | 앱·서버 | 팀 결정 |
| 시범 정류장 수 | 25개 = 승차 거점 6 + 주거지 19 (제안서는 20~30개) | 서버 | 팀 결정 |
| 시범지역 | 서울 중랑구 봉화산 일대 (태릉입구역·봉화산역·신내역·상봉역·망우역 사이) | 앱·서버 | 팀 결정 |

## 7. 협업 규칙

**프론트(윤재열)는 앱 전부, 백(장정훈)은 서버·데이터·관제 전부.** 완전히 나눠서 각자 노트북에서 한다. 정해진 날 만나는 일정은 없고, **막히면 카톡 → 그래도 안 풀리면 그때 만난다.**

1. 파일마다 주인이 한 명이다. 남의 영역은 읽기만 하고, 바꿔야 하면 카톡으로 부탁한다.
   - **프론트**: `DalmuriBus/` 전체 (gradle, `AndroidManifest.xml`, `data/model/` 포함)
   - **백**: `server/`, `web/`
2. 앱과 서버의 경계는 이 API 규약 하나다. 프론트는 서버 없이 가짜 서버 응답(Fake)으로, 백은 앱 없이 브라우저·PowerShell·웹 관제로 각자 끝까지 만든다.
3. 연결 확인도 각자 한다. 백이 `server/README.md`에 서버 실행법을 적어 두면, 프론트가 자기 노트북에서 pull 받아 서버를 직접 켜고 앱을 붙인다.
4. 상대 쪽 버그를 찾으면 카톡(또는 GitHub 이슈)으로 "무엇을 했더니 → 무엇이 나왔다"를 남긴다.
5. 작업 시작 전 pull, 작업 하나 끝나면 commit·push. 커밋 메시지 앞머리: `[프론트]`, `[백]`.
6. `local.properties`, `.env`(카카오 키 등), `.idea/`, `node_modules/`, DB 파일은 커밋하지 않는다.

## 8. 시연 시나리오 (11/29 완료 기준, 각자 자기 노트북에서 끝까지 돌려 봄)

1. 노트북에서 서버를 켜고 웹 관제를 연다. 버스 3대가 대기 중.
2. 앱 로그인 → 정류장과 지도를 서버에서 받아 저장하고 지도에 정류장 표시 (두 번째 실행부터는 저장된 것 사용).
3. N버스에서 내린 승차 거점(예: 상봉역.중랑우체국, N26) → 집 근처 주거지 정류장(예: 신내동성3차아파트), 1명 호출 → "2호차 배정, 약 4분 후 탑승".
4. 주기적 조회로 버스(🚌)가 다가오고 노선선이 줄어든다.
5. 웹 관제에서 같은 방향 승객 2명 호출 → 같은 버스에 합승, 앱 노선에 경유 정류장이 하나 늘어남.
6. 반대 방향 호출 → 다른 버스로 배정.
7. 탑승 → 하차 완료.

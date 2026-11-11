# 서버 설계 — 배차·시뮬레이션 (B0 분석 → B2·B3·B4 구현 기준)

> `web/index.html`(친구 웹 시뮬레이터, 관악구 신림 기준)을 읽고, 우리 서버에서 **그대로 쓸 것**과 **바꿀 것**을 정리했다.
> 웹은 참고용이고 서버는 새로 작성한다 (공통 규약 1). 값은 공통 규약 6을 따른다.

## 1. 웹 시뮬레이터가 하는 일 (요약)

| 부분 | 웹 코드 | 하는 일 |
| --- | --- | --- |
| 이동 시간 | `geoDistance` · `travelTime(a, b)` | 두 정류장 직선 거리(하버사인) ÷ 26km/h |
| 노선 평가 | `evaluateBusRoute(bus, waypoints, baseLoc, baseTime)` | 경유지를 차례로 지나며 각 승객의 승차·하차 예정 시각과 정원 초과 여부를 계산 |
| 후보 만들기 | `buildCandidate(original, p, d, pax)` | 기존 경유지 목록의 p 자리에 승차, d 자리에 하차를 끼워 넣은 새 목록 |
| 배차 | `attemptInsertGlobal(pax)` | **버스 3대 × 모든 (승차 자리, 하차 자리)** 조합을 다 해 보고, 제약(정원·최대 대기·기존 승객 지연)을 지키는 것 중 새 승객 하차 시각이 가장 이른 것을 고름 |
| 확정 | `commitInsert` | 그 버스의 경유지를 바꾸고, 관련 승객 모두의 약속 시각(promised)을 새로 적음 |
| 대기열 | `requestRide` → 실패 시 `pending`, `retryPending` | 바로 못 잡으면 대기열에 두고 버스가 정류장에 설 때마다 다시 시도, 15분 넘으면 `missed` |
| 이동 | `tick` · `startBusNextSegment` · `arriveBusAtStop` | 버스가 다음 경유지까지 일정 속도로 직선 이동, 도착하면 하차 → 승차 처리 |
| 위치 | `currentBusGeo` | 구간 출발·도착 정류장 사이를 시간 비율로 직선 보간 |

경유지(waypoint)는 `{ stopId, pickups: [승객], dropoffs: [승객] }`이고, 버스가 이동 중이면 첫 경유지(지금 가는 곳) 앞에는 끼워 넣지 않는다(`minP = 1`). 이 구조가 공통 규약의 `route`(`RouteStop` 배열)와 그대로 맞는다.

## 2. 그대로 가져올 것

- 끼워넣기(삽입 휴리스틱) 배차: 버스 × 승차 자리 × 하차 자리 전부 시도. 버스 3대, 경유지 10개여도 조합은 수백 개라 매 호출마다 다 해도 1ms 안쪽
- 경유지 구조 `{ stopId, pickups, dropoffs }`, 같은 정류장에서는 **하차 먼저, 승차 나중**
- 이동 중인 버스는 지금 가는 경유지 앞에 끼워 넣지 않음. 평가는 구간 출발 정류장·출발 시각 기준
- 대기열과 재시도, 최대 대기 15분 넘으면 실패
- 버스 위치 = 구간 사이 직선 보간 (B6 도로 좌표 전까지)
- 상수: 버스 3대, 속도 26km/h, 정원 6명, 최대 대기 15분

## 3. 바꿀 것 (발표 때 "웹과 다르게 한 점"으로 설명)

| # | 웹 | 서버 | 이유 |
| --- | --- | --- | --- |
| 1 | 정원 = 탑승 **호출 수** (`onboard.size`) | 정원 = 탑승 **인원 합** (`partySize` 합) | 호출 한 번에 1~3명 (공통 규약 `load`) |
| 2 | 기존 승객 최대 지연 8분 | **5분** | 제안서 기준 (공통 규약 6) |
| 3 | 지연을 **직전 약속 시각** 기준으로 비교, 끼울 때마다 약속을 새로 적음 → 여러 번 끼우면 처음보다 5분 넘게 늦어질 수 있음 | 지연은 **처음 배정됐을 때 약속한 시각** 기준. 화면에 보여 주는 ETA만 새로 계산 | "기존 승객은 최대 5분까지만 늦어진다"는 약속을 실제로 지키기 위해 |
| 4 | 고르는 기준 = 새 승객 하차 시각만 | 비용 = 새 승객 하차 시각 + **기존 승객들이 늦어지는 시간 합** | 새 승객만 빨라지고 다른 승객이 손해 보는 배정을 피함 ("합승 최적") |
| 5 | 대기열 재시도는 버스가 정류장에 설 때만 | **매 tick** 재시도 (가벼움) | 빈 버스가 생기는 순간 바로 배정 |
| 6 | 끝난 승객은 8분 뒤 삭제 | 지우지 않고 보관 (`COMPLETED`·`REJECTED`) | 앱이 2초마다 조회하다 404가 나지 않게 (B3) |
| 7 | 정류장 8개 하드코딩 (관악구) | `stops.json`(25개, HUB/HOME)에서 읽음 | 시범지역 변경 |
| 8 | 브라우저에서 200ms마다 tick, 기본 약 40배속 | 서버에서 1초마다 tick, 기본 6배속(설정값) | 공통 규약 6, 앱 2초 조회와 맞춤 |
| 9 | 직선 거리 그대로 | 직선 거리 × **도로 보정 1.3** (설정값, B6에서 도로 좌표가 생기면 실제 거리) | 직선은 실제 도로보다 짧아 ETA가 너무 낙관적임 |
| 10 | 웹이 배차·이동을 직접 계산 | 웹은 `GET /buses`로 받아서 그리기만 (B5) | 배차는 서버 한 곳에서만 (교수님) |
| 11 | 지도 = CARTO 타일 실시간 | 지도 = 우리 `map.png`를 Leaflet `imageOverlay`로 (B5) | 앱과 같은 그림·같은 좌표, 외부 타일 의존 없음 |

## 4. 서버 구조 (B2)

```
server/
├─ index.js        Express: API(B4), express.static('../web'), 1초 tick 시작
├─ engine.js       상태 + 배차 + 이동 (화면·HTTP와 무관, 테스트하기 쉽게)
├─ db.js           SQLite: 시작할 때 stops.json·map.json → stops·map_info 테이블 (없으면 만듦)
├─ config.js       상수 (버스 대수, 정원, 속도, 대기·지연 한도, 배속, 도로 보정, tick 간격)
└─ scripts/test-engine.js   호출 몇 개 넣고 배정·노선 출력 (B2 완료 기준)
```

**engine.js 상태 (서버 메모리)**

```js
state = {
  simTime,            // 시뮬 분 (01:30 = 90에서 시작)
  buses: [{ busId, atStop, status: 'idle'|'moving', from, to, segStart, segEnd, onboard: Set<callId>, waypoints: [{ stopId, pickups, dropoffs }] }],
  calls: Map<callId, { callId, originId, destId, partySize, requestTime, status, busId,
                       firstPickup, firstDropoff,   // 처음 약속 (지연 제약 기준, 바꾸지 않음)
                       etaPickup, etaDropoff,       // 지금 예상 (매 배정마다 갱신, 화면용)
                       pickedUpAt, droppedAt }>,
  pending: [callId],
  nextId,
}
```

**engine.js 함수**

| 함수 | 웹의 대응 | 하는 일 |
| --- | --- | --- |
| `travelMin(a, b)` | `travelTime` | 직선 거리 × 도로 보정 ÷ 속도 |
| `evaluate(bus, waypoints)` | `evaluateBusRoute` | 예정 시각들 + 인원 합 정원 검사 |
| `findBestInsertion(call)` | `attemptInsertGlobal` | 바꿀 것 1·3·4 반영 |
| `requestCall({ originId, destId, partySize })` | `requestRide` | 검증(400) → 배정 또는 대기열 → `CallResponse` |
| `tick(dtMin)` | `tick` + `retryPending` | 시계 진행, 버스 이동·도착 처리, 대기열 재시도·15분 실패 |
| `busPosition(bus)` | `currentBusGeo` | 위도·경도 보간 |
| `getCallDetail(callId)` | — | 공통 규약 `CallDetail` (없으면 null → 404) |
| `getBuses()` | — | 공통 규약 `GET /buses` 배열 |

**상태 대응 (웹 → 공통 규약 `status`)**

| 웹 | 서버 |
| --- | --- |
| `requested`, `pending` | `REQUESTED` (busId null) |
| `waiting` | `ASSIGNED` |
| `onboard` | `ONBOARD` |
| `dropped` | `COMPLETED` |
| `missed` | `REJECTED` |

`CallDetail`의 `route`는 그 승객이 탄(탈) 버스의 남은 경유지 중 **그 승객이 내리는 곳까지**(사이에 다른 승객 승하차가 있으면 합승이 보임. 버스 전체 노선은 `GET /buses`), `etaPickupMin` = `etaPickup - simTime`, `bus.load` = 탑승 중 호출들의 `partySize` 합.

## 5. 버스 시작 위치

웹은 거점 1곳 + 역 2곳에 나눠 둠. 우리도 승차 거점(HUB) 중 지역 서·남·동에 하나씩 둔다: BUS1 태릉입구역1번출구(11105), BUS2 망우역(07001), BUS3 신내역4번출구(07440) (`config.js`).
처음엔 BUS1을 상봉역에 뒀더니 페르소나를 대기 0분에 바로 태우고 떠나서, 같은 방향 승객이 합승할 틈이 없었다. 지금 배치면 페르소나(상봉역)에는 망우역의 2호차가 약 2분 뒤 오고, 그 사이 같은 방향 호출이 같은 버스에 합승한다 (`npm run test:engine`의 시연 시나리오).

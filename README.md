🌙 달무리버스 (DalmuriBus)

심야 합승형 자율주행버스 호출·배차 앱 — 2026 모바일프로그래밍 팀 프로젝트

늦은 밤 지하철·N버스에서 내린 뒤 집 근처 정류장까지 이어 주는 합승 자율주행버스 서비스입니다. 
승객이 앱에서 출발·도착 정류장과 인원을 고르면, 서버가 운행 중인 버스 3대의 경로에 승객을 끼워 넣어(합승 배차) 승차·하차 예정 시각을 알려 줍니다.

시범 지역: 서울 중랑구 봉화산 일대 (태릉입구역 · 봉화산역 · 신내역 · 상봉역 · 망우역, 약 3.0km × 2.8km)
운행 시간: 심야 01:30 ~ 05:00 (시뮬레이션 시각)
진행 상황: 🚧 개발 중 — 서버(배차 엔진·도로 경로·웹 관제) 구현 완료, 안드로이드 앱 개발 완료
기획 단계: Project_Urban-Issue-with-ORACLE에서 제안한 달무리버스를 실제 앱·서버로 구현

주요 기능
구분	기능
앱 (개발 중)	회원가입·로그인(Firebase Auth), 정류장 선택 후 호출, 배차 상태·ETA 실시간 조회(2초 주기), 지도 위 버스·노선 표시, 이용 내역
서버	삽입 휴리스틱 기반 합승 배차 엔진, 대기열 재시도(최대 대기 15분), 기존 승객 지연 5분 제한, 도로 최단경로를 따라 버스 이동 시뮬레이션
웹 관제	버스 3대 위치·경로, 호출 목록, 배차 판정 근거, 배속(6·12·30배) 조절
기술 스택
Android: Kotlin, Jetpack Compose, Retrofit, Room, Navigation-Compose, Firebase Auth
Server: Node.js 22.13+, Express, SQLite, sharp
데이터: 서울시 버스정류소 위치·노선 공공데이터, OpenStreetMap 도로망
웹 관제: Leaflet (서버가 직접 제공해 오프라인 시연 가능)

폴더 구조

text

Project_MP/
├── DalmuriBus/   # 안드로이드 앱 (프론트)
├── server/       # 배차·시뮬레이션 서버 + 웹 관제 (백)
│   ├── README.md   # 서버 실행 방법과 API 명세
│   ├── DESIGN.md   # 배차 알고리즘 설계와 원본 시뮬레이터 대비 변경점
│   └── data/       # 정류장·지도·도로 경로 데이터 (커밋됨)
└── docs/         # 역할별 작업 목록

실행 방법
bash
# 1) 서버
cd server
npm install
npm start          # http://localhost:3000  (웹 관제도 이 주소)

# 2) 앱
Android Studio로 DalmuriBus/ 열기 → 에뮬레이터 실행
에뮬레이터는 http://10.0.2.2:3000 으로 서버에 접속

자세한 설정(배속·버스 수·포트)과 API는 server/README.md를 참고하세요.

API 요약
API	설명
GET /stops	정류장 25개
GET /map, GET /map/image	지도 정보 / 지도 이미지
POST /calls	버스 호출
GET /calls/{callId}	내 배차 상태·ETA·경로
GET /buses	전체 버스 상태 (웹 관제)
팀
역할	이름	담당
프론트	윤재열	안드로이드 앱 전체
백	장정훈	배차 엔진, 시뮬레이션 서버, 데이터 수집, 웹 관제

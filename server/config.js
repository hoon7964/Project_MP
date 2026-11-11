// 서버 설정값 (공통 규약 6 "정해둔 값"). 바꿀 때는 여기 한 곳만 고친다.
// 시연 때 일부는 환경변수로 덮어쓸 수 있다. 예) PowerShell: $env:SIM_SPEED=10; npm start

const num = (name, fallback) => (process.env[name] != null && process.env[name] !== '' ? Number(process.env[name]) : fallback);

module.exports = {
  PORT: num('PORT', 3000),
  HOST: '0.0.0.0', // 에뮬레이터(10.0.2.2)와 같은 와이파이의 폰에서도 붙을 수 있게

  // 버스: 대수만큼 BUS1, BUS2 … 를 만들고, 시작 정류장은 아래 목록을 차례로 쓴다 (모자라면 처음부터 다시)
  BUS_COUNT: num('BUS_COUNT', 3),
  BUS_START_STOPS: ['11105', '07001', '07440'], // 태릉입구역1번출구, 망우역, 신내역4번출구 (승차 거점, 지역 서·남·동에 하나씩)
  CAPACITY: 6, // 정원 (명). 탑승 인원 합으로 비교

  // 이동
  SPEED_KMH: 26, // 일정 속도
  ROAD_FACTOR: 1.3, // 직선 거리 × 1.3 = 도로 거리 추정 (B6 도로 좌표 전까지)

  // 배차 제약 (제안서 기준)
  MAX_WAIT_MIN: 15, // 호출 후 15분 안에 못 태우면 배차 불가(REJECTED)
  MAX_DELAY_MIN: 5, // 기존 승객은 처음 약속한 승차·하차 시각보다 5분 넘게 늦어지면 안 됨

  // 탑승 인원
  PARTY_MIN: 1,
  PARTY_MAX: 3,

  // 시뮬레이션 시계
  SIM_START: '01:30', // 서버를 켜면 이 시각부터
  SIM_SPEED: num('SIM_SPEED', 6), // 배속: 실제 1초 = 시뮬 6초
  TICK_MS: 1000, // 1초마다 tick
};

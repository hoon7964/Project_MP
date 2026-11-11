// 위도·경도 ↔ 지도 이미지 픽셀 변환 (공통 규약 5와 같은 식).
// 앱(Kotlin)과 웹 관제도 이 식을 그대로 쓴다. 서버 배차에서 쓰는 거리 계산도 여기 둔다.

const merc = (lat) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360));

// map: { north, south, west, east, widthPx, heightPx }
function toPixel(map, lat, lng) {
  const x = ((lng - map.west) / (map.east - map.west)) * map.widthPx;
  const y = ((merc(map.north) - merc(lat)) / (merc(map.north) - merc(map.south))) * map.heightPx;
  return { x, y };
}

// 가로 픽셀 수가 정해지면, 같은 축척(메르카토르)이 되도록 세로 픽셀 수를 구한다
function heightForWidth(area, widthPx) {
  const lngSpanRad = ((area.east - area.west) * Math.PI) / 180;
  return Math.round((widthPx * (merc(area.north) - merc(area.south))) / lngSpanRad);
}

// 두 지점 사이 거리(m). 시범지역은 작아서 평면 근사로 충분하다
function distanceM(a, b) {
  const R = 6371000;
  const lat = (((a.lat + b.lat) / 2) * Math.PI) / 180;
  const dx = (((b.lng - a.lng) * Math.PI) / 180) * Math.cos(lat) * R;
  const dy = (((b.lat - a.lat) * Math.PI) / 180) * R;
  return Math.hypot(dx, dy);
}

module.exports = { merc, toPixel, heightForWidth, distanceM };

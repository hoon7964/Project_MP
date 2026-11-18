package com.dalmuri.bus.ui.theme

import androidx.compose.ui.graphics.Color

// 웹 관제(web/index.html) 밤 테마와 같은 색. 앱과 관제가 같은 색으로 보이게 한다.
val Night = Color(0xFF0C1424)      // 배경
val Night2 = Color(0xFF121C31)     // 시트·카드
val Night3 = Color(0xFF1A2640)     // 한 단계 밝은 면 (지도 자리 등)
val Line = Color(0xFF25324E)       // 테두리
val Ink = Color(0xFFE9EDF6)        // 글자
val Ink2 = Color(0xFF98A3BD)       // 보조 글자

val BoardBlue = Color(0xFF5B9BFF)  // 승차 = 블루 (승차 거점 HUB 정류장도 이 색)
val MoonGold = Color(0xFFF2C46D)   // 하차 = 달빛 골드 (주거지 HOME 정류장도 이 색)
val Ok = Color(0xFF7FD1A4)         // 완료·정상
val Warn = Color(0xFFF08A7E)       // 에러·배차 불가

// 호차 색: 웹 관제 web/control.js BUS_STYLE과 같은 색 (앱 버스 마커, 11/18)
val Bus1 = Color(0xFF5EEAD4)       // 1호차 민트
val Bus2 = Color(0xFFF472B6)       // 2호차 핑크
val Bus3 = Color(0xFFB39DFA)       // 3호차 보라
val BusOther = Color(0xFF94A3B8)   // 4호차부터 회색

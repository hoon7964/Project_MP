package com.dalmuri.bus.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dalmuri.bus.ui.theme.Bus1
import com.dalmuri.bus.ui.theme.Bus2
import com.dalmuri.bus.ui.theme.Bus3
import com.dalmuri.bus.ui.theme.BusOther
import com.dalmuri.bus.ui.theme.Ink
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.theme.Night

/** 버스 ID → 호차 색 (웹 관제와 같은 색) */
fun busColor(busId: String): Color = when (busId) {
    "BUS1" -> Bus1
    "BUS2" -> Bus2
    "BUS3" -> Bus3
    else -> BusOther
}

/**
 * 지도 위 버스 마커 (추가 기능, 11/18). 이모지 🚌 대신 직접 그린 앞모습 버스.
 *  - 몸통 = 웹 관제와 같은 호차 색, 앞유리에 호차 번호
 *  - 헤드라이트 눈 + 작은 미소, 지붕에 초승달 (달무리 버스)
 * 크기는 줌과 상관없이 화면 기준 약 38×46dp. c = 버스 위치(몸통 가운데).
 */
fun DrawScope.drawBus(textMeasurer: TextMeasurer, c: Offset, busId: String) {
    val u = 1.dp.toPx()
    val body = busColor(busId)
    fun at(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)

    // 바퀴 (몸통 아래로 살짝 나옴)
    drawRoundRect(Night, topLeft = at(-13f, 13f), size = sz(8f, 8f), cornerRadius = CornerRadius(3f * u))
    drawRoundRect(Night, topLeft = at(5f, 13f), size = sz(8f, 8f), cornerRadius = CornerRadius(3f * u))

    // 몸통: 어두운 테두리 + 호차 색
    drawRoundRect(Night, topLeft = at(-19f, -17f), size = sz(38f, 36f), cornerRadius = CornerRadius(11f * u))
    drawRoundRect(body, topLeft = at(-17f, -15f), size = sz(34f, 32f), cornerRadius = CornerRadius(9f * u))

    // 앞유리 + 호차 번호
    drawRoundRect(Night.copy(alpha = 0.9f), topLeft = at(-13f, -11f), size = sz(26f, 13f), cornerRadius = CornerRadius(4f * u))
    val number = busId.filter { it.isDigit() }.ifEmpty { "?" }
    val text = textMeasurer.measure(number, TextStyle(color = body, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    drawText(text, topLeft = at(0f, -4.5f) - Offset(text.size.width / 2f, text.size.height / 2f))

    // 헤드라이트 눈 (달빛 골드 + 반짝)
    drawCircle(MoonGold, radius = 3.2f * u, center = at(-9.5f, 7.5f))
    drawCircle(MoonGold, radius = 3.2f * u, center = at(9.5f, 7.5f))
    drawCircle(Ink, radius = 1.1f * u, center = at(-10.4f, 6.6f))
    drawCircle(Ink, radius = 1.1f * u, center = at(8.6f, 6.6f))

    // 작은 미소
    drawArc(
        Night, startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = at(-4f, 4f), size = sz(8f, 6f),
        style = Stroke(1.6f * u, cap = StrokeCap.Round),
    )

    // 지붕 초승달: 원에서 살짝 비킨 원을 빼서 만든다
    val full = Path().apply { addOval(Rect(center = at(0f, -20f), radius = 4.5f * u)) }
    val cut = Path().apply { addOval(Rect(center = at(2.2f, -21.5f), radius = 3.8f * u)) }
    drawPath(Path.combine(PathOperation.Difference, full, cut), MoonGold)
}

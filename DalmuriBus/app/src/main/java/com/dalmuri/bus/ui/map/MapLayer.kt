package com.dalmuri.bus.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dalmuri.bus.data.model.BusInfo
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.MapPoint
import com.dalmuri.bus.data.model.RouteStop
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.model.StopKind
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.Ink
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.theme.Night
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 지도 레이어 두 장 (교수님: "지도 레이아웃 하나 + 그 위에 겹쳐서 점을 찍는다").
 *  - 아래 장: 서버에서 받아 저장해 둔 지도 이미지
 *  - 위 장: 정류장 점, 선택·출발·도착 강조, 노선선, 버스 마커(BusMarker.kt)
 * 두 장을 같은 Canvas에 같은 식(toScreen)으로 그려서, 핀치 줌·드래그를 해도 점이 지도와 어긋나지 않는다.
 * 점 크기는 줌해도 그대로 (화면 기준 크기).
 */
@Composable
fun MapLayer(
    mapInfo: MapInfo,
    mapImage: ImageBitmap,
    stops: List<Stop>,
    selectedStopId: String?,
    originId: String?,
    destId: String?,
    bus: BusInfo?,
    route: List<RouteStop>,
    path: List<MapPoint>,
    onStopTap: (Stop?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }        // 1 = 지도가 화면을 꽉 채움
    var pan by remember { mutableStateOf(Offset.Zero) }    // 화면 중앙에서 얼마나 옮겼는지 (px)
    val currentOnStopTap by rememberUpdatedState(onStopTap)
    val textMeasurer = rememberTextMeasurer()
    val extraPanY = with(LocalDensity.current) { 160.dp.toPx() }  // 하단 시트에 가린 부분도 끌어올려 볼 수 있게

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // 줌 1일 때 이미지 1px → 화면 몇 px. max = 지도가 화면을 꽉 채움(남는 쪽은 잘리고 드래그로 봄). min이면 전체가 들어가고 위아래가 빔
        val fit = max(w / mapInfo.widthPx, h / mapInfo.heightPx)

        /** 위도·경도 → 화면 좌표. 이미지 중심이 (화면 중심 + pan)에 오고, fit × zoom 배로 커진다 */
        fun toScreen(lat: Double, lng: Double): Offset {
            val p = MapProjection.toPixel(lat, lng, mapInfo)
            val k = fit * zoom
            return Offset(
                (w / 2 + pan.x + (p.x - mapInfo.widthPx / 2.0) * k).toFloat(),
                (h / 2 + pan.y + (p.y - mapInfo.heightPx / 2.0) * k).toFloat(),
            )
        }

        /** 지도가 화면 밖으로 다 나가지 않게 이동 범위 제한 */
        fun clampPan(p: Offset, z: Float): Offset {
            val k = fit * z
            val maxX = max(0f, (mapInfo.widthPx * k - w) / 2)
            val maxY = max(0f, (mapInfo.heightPx * k - h) / 2) + extraPanY
            return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        }

        // 지도 이미지 + 점·선·버스를 한 Canvas에. 손가락 입력도 여기서 받음
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fit, w, h) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                        // 두 손가락 가운데 지점이 줌 전후에 같은 지도 위치에 있도록 pan을 보정
                        val c = centroid - Offset(w / 2, h / 2)
                        val newPan = c - (c - pan) * (newZoom / zoom) + panChange
                        zoom = newZoom
                        pan = clampPan(newPan, newZoom)
                    }
                }
                .pointerInput(stops, fit, w, h) {
                    detectTapGestures { tap ->
                        // 누른 곳에서 가장 가까운 정류장 (28dp 안쪽만)
                        val nearest = stops.minByOrNull { (toScreen(it.lat, it.lng) - tap).getDistance() }
                        val hit = nearest?.takeIf { (toScreen(it.lat, it.lng) - tap).getDistance() <= 28.dp.toPx() }
                        currentOnStopTap(hit)
                    }
                },
        ) {
            // 0) 아래 장: 지도 이미지. 이미지 왼쪽 위 = toScreen식에서 픽셀 (0, 0), 크기 = 이미지 × fit × zoom
            val k = fit * zoom
            drawImage(
                image = mapImage,
                dstOffset = IntOffset(
                    (w / 2 + pan.x - mapInfo.widthPx / 2f * k).roundToInt(),
                    (h / 2 + pan.y - mapInfo.heightPx / 2f * k).roundToInt(),
                ),
                dstSize = IntSize((mapInfo.widthPx * k).roundToInt(), (mapInfo.heightPx * k).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )

            // 1) 노선선: path(도로 좌표)가 있으면 그대로, 없으면 버스 → route 정류장 직선
            val line: List<Offset> = if (path.isNotEmpty()) {
                path.map { toScreen(it.lat, it.lng) }
            } else {
                buildList {
                    bus?.let { add(toScreen(it.lat, it.lng)) }
                    route.forEach { add(toScreen(it.lat, it.lng)) }
                }
            }
            if (line.size >= 2) {
                val p = Path().apply {
                    moveTo(line[0].x, line[0].y)
                    line.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(p, Night.copy(alpha = 0.7f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(p, BoardBlue, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // 2) 정류장 점: 승차 거점(HUB) 파랑, 주거지(HOME) 골드. 노선에 들어간 정류장은 흰 테두리
            val routeIds = route.map { it.stopId }.toSet()
            val dot = 6.dp.toPx()
            stops.forEach { s ->
                val c = toScreen(s.lat, s.lng)
                drawCircle(Night, radius = dot + 2.dp.toPx(), center = c)
                drawCircle(if (s.kind == StopKind.HUB) BoardBlue else MoonGold, radius = dot, center = c)
                if (s.stopId in routeIds) drawCircle(Ink, radius = dot + 4.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
            }

            // 3) 누른 정류장, 출발·도착 강조
            stops.find { it.stopId == selectedStopId }?.let {
                drawCircle(Ink, radius = 13.dp.toPx(), center = toScreen(it.lat, it.lng), style = Stroke(2.5f.dp.toPx()))
            }
            stops.find { it.stopId == originId }?.let { markStop(textMeasurer, toScreen(it.lat, it.lng), BoardBlue, "출발") }
            stops.find { it.stopId == destId }?.let { markStop(textMeasurer, toScreen(it.lat, it.lng), MoonGold, "도착") }

            // 4) 버스: 직접 그린 버스 마커 (호차 색 + 번호, BusMarker.kt)
            bus?.let { drawBus(textMeasurer, toScreen(it.lat, it.lng), it.busId) }
        }
    }
}

/** 출발·도착 정류장: 굵은 고리 + 위에 이름표 */
private fun DrawScope.markStop(textMeasurer: TextMeasurer, c: Offset, color: Color, label: String) {
    drawCircle(color, radius = 12.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
    val text = textMeasurer.measure(label, TextStyle(color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    val padX = 6.dp.toPx()
    val padY = 2.dp.toPx()
    val boxW = text.size.width + padX * 2
    val boxH = text.size.height + padY * 2
    val topLeft = Offset(c.x - boxW / 2, c.y - 16.dp.toPx() - boxH)
    drawRoundRect(color, topLeft = topLeft, size = Size(boxW, boxH), cornerRadius = CornerRadius(boxH / 2))
    drawText(text, topLeft = topLeft + Offset(padX, padY))
}

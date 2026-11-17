package com.dalmuri.bus.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dalmuri.bus.data.model.BusInfo

/**
 * 버스를 부드럽게 옮긴다 (F7). 서버 위치는 2초마다 바뀌어서 그대로 그리면 뚝뚝 끊기니까,
 * 새 위치를 받으면 지금 그리고 있던 위치 → 새 위치로 durationMs(=조회 간격) 동안 직선으로 이어 그린다.
 * 서버가 일정 속도로 시뮬레이션하므로 실제 이동과 거의 같다.
 */
@Composable
fun rememberSmoothBus(bus: BusInfo?, durationMs: Int): BusInfo? {
    var from by remember { mutableStateOf<BusInfo?>(null) }
    var to by remember { mutableStateOf<BusInfo?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(bus) {
        if (bus == null) {
            from = null
            to = null
            return@LaunchedEffect
        }
        // 다른 버스로 바뀌었거나 처음이면 바로 그 자리에서 시작
        val drawnNow = lerpBus(from, to, progress.value)
        from = if (drawnNow != null && drawnNow.busId == bus.busId) drawnNow else bus
        to = bus
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return lerpBus(from, to, progress.value)
}

/** from과 to 사이 f 비율 위치 (인원 등 나머지 값은 to 그대로) */
private fun lerpBus(from: BusInfo?, to: BusInfo?, f: Float): BusInfo? {
    if (to == null) return null
    if (from == null) return to
    return to.copy(
        lat = from.lat + (to.lat - from.lat) * f,
        lng = from.lng + (to.lng - from.lng) * f,
    )
}

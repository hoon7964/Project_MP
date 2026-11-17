package com.dalmuri.bus.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalmuri.bus.data.model.StopKind
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold

/**
 * 메인 화면 = 지도 영역 + 하단 시트.
 *  - 지도 영역: MapLayer (지도 이미지 + 정류장·노선·버스, 핀치 줌·드래그, 정류장 누르기) + 범례 + 출처
 *  - 하단 시트: F6(호출)·F7(상태 5종)에서 내용을 채운다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel = viewModel(factory = MapViewModel.Factory)) {
    val state by vm.state.collectAsState()

    BottomSheetScaffold(
        sheetPeekHeight = 170.dp,  // 시트가 접혀 있을 때 보이는 높이
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = { SheetPlaceholder(state, onDemoTrip = vm::startDemoTrip) },
    ) { _ ->
        // 지도는 시트 뒤까지 화면 전체를 채운다
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = state.mapImage
            val info = state.mapInfo
            when {
                image != null && info != null -> {
                    val trip = state.demoTrip
                    MapLayer(
                        mapInfo = info,
                        mapImage = image,
                        stops = state.stops,
                        selectedStopId = state.selectedStopId,
                        originId = trip?.originId,
                        destId = trip?.destId,
                        bus = trip?.bus,
                        route = trip?.route.orEmpty(),
                        path = trip?.path.orEmpty(),
                        onStopTap = vm::onStopTap,
                    )
                    StopLegend(
                        Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp),
                    )
                    // OSM 이용 조건: 지도 출처 표시
                    Text(
                        info.attribution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp),
                    )
                }
                state.loading -> CircularProgressIndicator()
                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("다시 시도") }
                }
            }
        }
    }
}

/** 지도 구석 범례 카드 (ROLE_FRONT.md F5) */
@Composable
private fun StopLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            LegendRow(BoardBlue, "승차 거점", "심야 N버스에서 내려 타는 곳")
            Spacer(Modifier.height(6.dp))
            LegendRow(MoonGold, "주거지", "집 근처 하차")
        }
    }
}

@Composable
private fun LegendRow(color: Color, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetPlaceholder(state: MapUiState, onDemoTrip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text("어디서 타고 어디서 내릴까요?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // 지도에서 누른 정류장 (F6에서 정보 카드 + [여기서 출발] [여기로 도착]으로 바뀜)
        val selected = state.stops.find { it.stopId == state.selectedStopId }
        Text(
            if (selected == null) {
                "지도에서 정류장 점을 눌러 보세요"
            } else {
                val kind = if (selected.kind == StopKind.HUB) "승차 거점" else "주거지"
                "선택: ${selected.name} (${selected.stopId}) · $kind"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "정류장 ${state.stops.size}개 · 지도 ${state.mapInfo?.mapId ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── F5 확인용 임시 (F7에서 지움): 상봉역 → 신내동성3차 호출을 Fake로 돌려서 버스·노선 그리기 확인 ──
        Spacer(Modifier.height(12.dp))
        val trip = state.demoTrip
        if (trip != null) {
            Text(
                "임시 호출 ${trip.callId} · ${trip.simClock} ${trip.status} · 버스 ${trip.bus?.busId ?: "-"} · 남은 정류장 ${trip.route.size}곳",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onDemoTrip) {
            Text("버스·노선 그리기 확인 (임시)")
        }
    }
}

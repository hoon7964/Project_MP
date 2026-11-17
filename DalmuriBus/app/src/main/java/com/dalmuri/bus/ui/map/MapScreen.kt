package com.dalmuri.bus.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalmuri.bus.AppConfig
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.trip.TripSheet
import com.dalmuri.bus.ui.trip.TripViewModel

/**
 * 메인 화면 = 지도 영역 + 하단 시트.
 *  - 지도 영역: MapLayer (지도 이미지 + 정류장·노선·버스, 핀치 줌·드래그, 정류장 누르기) + 범례 + 출처
 *  - 하단 시트: 호출 전에는 CallSheet (F6), 호출 뒤에는 TripSheet 상태 5종 (F7)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    vm: MapViewModel = viewModel(factory = MapViewModel.Factory),
    tripVm: TripViewModel = viewModel(factory = TripViewModel.Factory),
) {
    val state by vm.state.collectAsState()
    val trip by tripVm.state.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    // 호출해서 배차 ID를 받으면 추적 시작 + 시트를 접어서 지도가 잘 보이게
    LaunchedEffect(state.callId) {
        val id = state.callId ?: return@LaunchedEffect
        tripVm.start(id)
        scaffoldState.bottomSheetState.partialExpand()
    }

    // 정류장을 누르면 시트를 펼쳐서 정보 카드와 버튼이 보이게
    LaunchedEffect(state.selectedStopId) {
        if (state.selectedStopId != null) scaffoldState.bottomSheetState.expand()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 170.dp,  // 시트가 접혀 있을 때 보이는 높이
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            if (state.callId != null) {
                TripSheet(
                    trip = trip,
                    stops = state.stops,
                    onHome = {
                        tripVm.clear()
                        vm.resetRoute()
                    },
                    onRetry = {
                        tripVm.clear()
                        vm.clearCall()
                    },
                )
            } else {
                CallSheet(
                    state = state,
                    onStopTap = vm::onStopTap,
                    onSetOrigin = vm::setOrigin,
                    onSetDest = vm::setDest,
                    onReset = vm::resetRoute,
                    onPartySize = vm::setPartySize,
                    onCall = vm::requestCall,
                )
            }
        },
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
                    // 호출 중이면 받은 상태로, 아니면 고르고 있는 출발·도착으로 그림
                    // 호출이 서버에서 사라지면(lost) 예전 버스·노선은 지우고 출발·도착 표시만 남김
                    val detail = if (state.callId != null && !trip.lost) trip.detail else null
                    val bus = rememberSmoothBus(detail?.bus, AppConfig.POLL_MS.toInt())
                    MapLayer(
                        mapInfo = info,
                        mapImage = image,
                        stops = state.stops,
                        selectedStopId = state.selectedStopId,
                        originId = detail?.originId ?: state.originId,
                        destId = detail?.destId ?: state.destId,
                        bus = bus,
                        route = detail?.route.orEmpty(),
                        path = detail?.path.orEmpty(),
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

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
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold

/**
 * 메인 화면 = 지도 영역 + 하단 시트.
 *  - 지도 영역: MapLayer (지도 이미지 + 정류장·노선·버스, 핀치 줌·드래그, 정류장 누르기) + 범례 + 출처
 *  - 하단 시트: CallSheet (F6 호출). F7에서 호출 뒤 상태 시트 5종으로 바뀐다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel = viewModel(factory = MapViewModel.Factory)) {
    val state by vm.state.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    // 정류장을 누르면 시트를 펼쳐서 정보 카드와 버튼이 보이게
    LaunchedEffect(state.selectedStopId) {
        if (state.selectedStopId != null) scaffoldState.bottomSheetState.expand()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 170.dp,  // 시트가 접혀 있을 때 보이는 높이
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            CallSheet(
                state = state,
                onStopTap = vm::onStopTap,
                onSetOrigin = vm::setOrigin,
                onSetDest = vm::setDest,
                onReset = vm::resetRoute,
                onPartySize = vm::setPartySize,
                onCall = vm::requestCall,
            )
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
                    val trip = state.trip
                    MapLayer(
                        mapInfo = info,
                        mapImage = image,
                        stops = state.stops,
                        selectedStopId = state.selectedStopId,
                        originId = state.originId,
                        destId = state.destId,
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

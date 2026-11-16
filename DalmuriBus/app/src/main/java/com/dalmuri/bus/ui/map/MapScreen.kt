package com.dalmuri.bus.ui.map

import androidx.compose.foundation.Image
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
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalmuri.bus.DalmuriApp
import com.dalmuri.bus.data.model.StopKind
import com.dalmuri.bus.debug.ApiCheck
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold
import kotlinx.coroutines.launch

/**
 * 메인 화면 = 지도 영역 + 하단 시트.
 *  - 지도 영역: F4는 저장된 지도 이미지만 보여 준다. F5에서 그 위에 정류장·버스·노선을 겹쳐 그리고 줌·이동을 넣는다.
 *  - 하단 시트: F6(호출)·F7(상태 5종)에서 내용을 채운다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel = viewModel(factory = MapViewModel.Factory)) {
    val state by vm.state.collectAsState()

    BottomSheetScaffold(
        sheetPeekHeight = 170.dp,  // 시트가 접혀 있을 때 보이는 높이
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = { SheetPlaceholder(state) },
    ) { _ ->
        // 지도는 시트 뒤까지 화면 전체를 채운다
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = state.mapImage
            when {
                image != null -> {
                    Image(
                        bitmap = image,
                        contentDescription = "봉화산 일대 지도",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // OSM 이용 조건: 지도 출처 표시
                    Text(
                        state.mapInfo?.attribution ?: "",
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

@Composable
private fun SheetPlaceholder(state: MapUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text("어디서 타고 어디서 내릴까요?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        LegendRow(color = BoardBlue, text = "출발(승차) 정류장")
        Spacer(Modifier.height(6.dp))
        LegendRow(color = MoonGold, text = "도착(하차) 정류장")
        Spacer(Modifier.height(12.dp))

        // F4 확인용: 저장소에서 꺼낸 정류장·지도 정보
        if (state.stops.isNotEmpty()) {
            Text(
                "정류장 ${state.stops.size}개 (승차 거점 ${state.stops.count { it.kind == StopKind.HUB }}) · 지도 ${state.mapInfo?.mapId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── F3 확인용 임시 버튼 (F6에서 지움): 누르면 API를 불러 Logcat "ApiCheck"에 찍음 ──
        val container = (LocalContext.current.applicationContext as DalmuriApp).container
        val scope = rememberCoroutineScope()
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { scope.launch { ApiCheck.run(container.api) } }) {
            Text("API 확인 (임시)")
        }
    }
}

/** 색 점 + 글자 한 줄 */
@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

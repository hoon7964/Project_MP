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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold

/**
 * 메인 화면 = 지도 영역 + 하단 시트.
 *  - 지도 영역: 지금은 회색 박스. F5에서 지도 이미지 + 정류장·버스·노선 그리기로 바꾼다.
 *  - 하단 시트: 지금은 안내 문구. F6(호출)·F7(상태 5종)에서 내용을 채운다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    BottomSheetScaffold(
        sheetPeekHeight = 170.dp,  // 시트가 접혀 있을 때 보이는 높이
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = { SheetPlaceholder() },
    ) { _ ->
        // 지도는 시트 뒤까지 화면 전체를 채운다
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("지도 자리 (F5)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetPlaceholder() {
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
        Text(
            "정류장 선택·인원·호출은 F6에서",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

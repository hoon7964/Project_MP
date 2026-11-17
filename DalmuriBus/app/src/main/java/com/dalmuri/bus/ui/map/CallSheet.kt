package com.dalmuri.bus.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.model.StopKind
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.theme.Night

/** 정류장 종류 → 점 색, 이름 */
private fun Stop.color() = if (kind == StopKind.HUB) BoardBlue else MoonGold
private fun Stop.kindLabel() = if (kind == StopKind.HUB) "승차 거점" else "주거지"

/**
 * 호출 시트 (F6): 정류장 정보 카드 → 출발·도착 → 탑승 인원 → [호출하기].
 * 정류장은 지도 점을 누르거나 아래 목록에서 고른다.
 */
@Composable
fun CallSheet(
    state: MapUiState,
    onStopTap: (Stop?) -> Unit,
    onSetOrigin: (Stop) -> Unit,
    onSetDest: (Stop) -> Unit,
    onReset: () -> Unit,
    onPartySize: (Int) -> Unit,
    onCall: () -> Unit,
) {
    val selected = state.stops.find { it.stopId == state.selectedStopId }
    val origin = state.stops.find { it.stopId == state.originId }
    val dest = state.stops.find { it.stopId == state.destId }
    var showList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text("어디서 타고 어디서 내릴까요?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        // 1) 누른 정류장 정보 카드
        if (selected != null) {
            StopInfoCard(selected, onSetOrigin = { onSetOrigin(selected) }, onSetDest = { onSetDest(selected) })
            Spacer(Modifier.height(12.dp))
        }

        // 2) 고른 출발·도착
        RouteRow("출발", BoardBlue, origin?.name)
        Spacer(Modifier.height(6.dp))
        RouteRow("도착", MoonGold, dest?.name)
        if (origin != null || dest != null) {
            TextButton(onClick = onReset, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("다시 고르기") }
        }

        // 3) 탑승 인원 1~3명
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("탑승 인원", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
            (1..3).forEach { n ->
                FilterChip(
                    selected = state.partySize == n,
                    onClick = { onPartySize(n) },
                    label = { Text("${n}명") },
                )
            }
        }

        // 4) 안내·에러 문구 (같은 정류장, 운행 시간 끝남 등)
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // 5) 호출
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCall,
            enabled = origin != null && dest != null && !state.calling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.calling) "호출 중…" else "호출하기")
        }

        // 받은 배차 ID + 임시 상태 줄 (F7에서 상태 시트 5종으로 바뀜)
        state.callId?.let { id ->
            Spacer(Modifier.height(8.dp))
            val t = state.trip
            Text(
                "배차 ID $id" + if (t != null) " · ${t.simClock} ${t.status} · 버스 ${t.bus?.busId ?: "-"}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 6) 목록에서 고르기 (지도 점이 작아 누르기 어려울 때)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { showList = !showList }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(if (showList) "정류장 목록 닫기" else "정류장 목록에서 고르기")
        }
        if (showList) {
            Column(
                Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.stops.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStopTap(s) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(s.color(), CircleShape))
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(s.stopId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 정류장 정보 카드: 이름, 번호, 구분, 서는 N버스(승차 거점만), [여기서 출발] [여기로 도착] */
@Composable
private fun StopInfoCard(stop: Stop, onSetOrigin: () -> Unit, onSetDest: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(stop.color(), CircleShape))
                Text(stop.name, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "정류장 번호 ${stop.stopId} · ${stop.kindLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stop.kind == StopKind.HUB && stop.nightRoutes.isNotEmpty()) {
                Text(
                    "서는 심야버스 ${stop.nightRoutes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSetOrigin,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BoardBlue, contentColor = Night),
                ) { Text("여기서 출발") }
                Button(
                    onClick = onSetDest,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MoonGold, contentColor = Night),
                ) { Text("여기로 도착") }
            }
        }
    }
}

/** "출발 ● 상봉역.중랑우체국" 한 줄 */
@Composable
private fun RouteRow(label: String, color: Color, name: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            name ?: "지도에서 정류장을 눌러 고르세요",
            style = MaterialTheme.typography.bodyMedium,
            color = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

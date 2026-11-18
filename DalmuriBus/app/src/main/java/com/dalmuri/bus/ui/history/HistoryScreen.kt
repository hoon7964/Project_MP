package com.dalmuri.bus.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dalmuri.bus.data.model.CallHistory
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.theme.Ok
import com.dalmuri.bus.ui.theme.Warn
import com.dalmuri.bus.ui.trip.minutesBetween
import com.google.firebase.auth.FirebaseAuth

/** 이용 내역 화면 (추가 기능): 최근 호출부터 카드 목록. 위에 [← 지도] [새로고침], 아래에 계정 + [로그아웃] */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,  // [로그아웃] → 확인 → 로그인 화면
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 위쪽 줄
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 지도") }
            Text(
                "이용 내역",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = vm::load, enabled = !state.loading) { Text("새로고침") }
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()

                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("다시 시도") }
                }

                state.items.isEmpty() -> Text(
                    "아직 이용 내역이 없어요.\n서버를 다시 켜거나 관제에서 '처음부터 다시'를 누르면 내역이 비워져요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.callId }) { h ->
                        HistoryCard(h, nameOf = { id -> state.stopNames[id] ?: id })
                    }
                }
            }
        }

        // 아래: 로그인한 계정 + [로그아웃] (시연 때 로그인 화면을 다시 보여 줄 수 있게, 11/18)
        LogoutRow(onLogout)
    }
}

@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val email = remember { FirebaseAuth.getInstance().currentUser?.email ?: "" }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { asking = true }) { Text("로그아웃") }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("로그아웃할까요?") },
            text = { Text("진행 중인 호출이 있으면 앱에서 더 이상 따라가지 않아요. 저장된 정류장·지도는 그대로 남아요.") },
            confirmButton = { TextButton(onClick = { asking = false; onLogout() }) { Text("로그아웃") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("취소") } },
        )
    }
}

/** 호출 한 건 카드: 상태 · 출발→도착 · 인원·버스 · 시각 · (하차했으면) 대기·탄 시간 */
@Composable
private fun HistoryCard(h: CallHistory, nameOf: (String) -> String) {
    val (label, color) = statusLabel(h.status)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(h.callId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            StopRow("출발", BoardBlue, nameOf(h.originId))
            Spacer(Modifier.height(2.dp))
            StopRow("도착", MoonGold, nameOf(h.destId))
            Spacer(Modifier.height(8.dp))
            Small("${h.partySize}명 · ${busNumber(h.busId)}")
            Small(clocksText(h))
            durationsText(h)?.let { Small(it) }
        }
    }
}

@Composable
private fun StopRow(label: String, color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 상태 → 이름, 색 (상태 시트와 같은 색) */
@Composable
private fun statusLabel(s: CallStatus): Pair<String, Color> = when (s) {
    CallStatus.REQUESTED -> "배차 중" to BoardBlue
    CallStatus.ASSIGNED -> "버스 오는 중" to BoardBlue
    CallStatus.ONBOARD -> "탑승 중" to MoonGold
    CallStatus.COMPLETED -> "하차 완료" to Ok
    CallStatus.REJECTED -> "배차 불가" to Warn
    CallStatus.CANCELED -> "취소함" to MaterialTheme.colorScheme.onSurfaceVariant
}

/** "BUS2" → "2호차", 배정 안 됐으면 "배정 전" */
private fun busNumber(busId: String?): String =
    busId?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { "${it}호차" } ?: (busId ?: "배정 전")

/** "호출 01:36 · 탑승 01:38 · 하차 01:44" (안 일어난 건 뺌, 취소했으면 취소 시각) */
private fun clocksText(h: CallHistory): String = listOfNotNull(
    "호출 ${h.requestClock}",
    h.pickupClock?.let { "탑승 $it" },
    h.dropoffClock?.let { "하차 $it" },
    h.canceledClock?.let { "취소 $it" },
).joinToString(" · ")

/** 하차까지 했으면 "대기 약 2분 · 탄 시간 약 6분" (시뮬레이션 시각 기준) */
private fun durationsText(h: CallHistory): String? {
    val wait = minutesBetween(h.requestClock, h.pickupClock) ?: return null
    val ride = minutesBetween(h.pickupClock, h.dropoffClock) ?: return null
    fun m(x: Int) = if (x == 0) "1분 미만" else "약 ${x}분"
    return "대기 ${m(wait)} · 탄 시간 ${m(ride)}"
}

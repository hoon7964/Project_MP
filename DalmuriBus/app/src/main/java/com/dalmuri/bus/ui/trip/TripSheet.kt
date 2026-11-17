package com.dalmuri.bus.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.ui.theme.BoardBlue
import com.dalmuri.bus.ui.theme.MoonGold
import com.dalmuri.bus.ui.theme.Ok
import com.dalmuri.bus.ui.theme.Warn
import java.util.Locale

/**
 * 호출 뒤 상태 시트 5종 (F7): 배차 중 / 버스 오는 중 / 탑승 중 / 하차 완료 / 배차 불가.
 * 위쪽 몇 줄만으로 상태를 알 수 있게 (시트를 접어도 보이게) 중요한 것부터 둔다.
 */
@Composable
fun TripSheet(
    trip: TripUiState,
    stops: List<Stop>,
    onHome: () -> Unit,   // 하차 완료 → 처음으로
    onRetry: () -> Unit,  // 배차 불가 / 호출 사라짐 → 다시 호출
) {
    val d = trip.detail
    fun nameOf(id: String?) = stops.find { it.stopId == id }?.name ?: id ?: "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        // 호출이 서버에서 사라짐 (404)
        if (trip.lost) {
            Title("호출 정보가 없어요", Warn)
            Line("서버가 초기화되거나 다시 켜져서 이전 호출이 지워졌어요. 다시 호출해 주세요.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("다시 호출하기") }
            return@Column
        }

        when (d?.status) {
            // 첫 응답 전 + REQUESTED: 빈 버스 찾는 중
            null, CallStatus.REQUESTED -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Title("배차 중", BoardBlue)
                }
                Line("태울 수 있는 버스를 찾고 있어요")
            }

            // ASSIGNED: 버스 오는 중
            CallStatus.ASSIGNED -> {
                Title("${busNumber(d?.bus?.busId)} 배정 · ${minutes(d?.etaPickupMin)} 후 탑승", BoardBlue)
                Line("현재 탑승 인원 ${d?.bus?.load ?: 0}/${d?.bus?.capacity ?: 0}명")
            }

            // ONBOARD: 탑승 중
            CallStatus.ONBOARD -> {
                Title("탑승 중 · ${minutes(d?.etaDropoffMin)} 후 하차", MoonGold)
                // route 마지막이 내 하차 정류장이라 그 앞까지가 경유 정류장. load는 나까지 포함한 버스 전체 인원
                Line("남은 경유 정류장 ${((d?.route?.size ?: 0) - 1).coerceAtLeast(0)}곳 · 버스 탑승 인원 ${d?.bus?.load ?: 0}명")
            }

            // COMPLETED: 하차 완료
            CallStatus.COMPLETED -> {
                Title("하차 완료", Ok)
                Line("${nameOf(d?.destId)}에 내렸어요. 조심히 들어가세요!")
                Spacer(Modifier.height(12.dp))
                TripSummaryCard(trip.record)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("처음으로") }
            }

            // REJECTED: 최대 대기 15분 안에 배차 불가
            CallStatus.REJECTED -> {
                Title("배차 불가", Warn)
                Line("15분 안에 태울 수 있는 버스가 없었어요. 다시 호출해 주세요.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("다시 호출") }
            }
        }

        // 공통: 경로와 시뮬레이션 시각
        Spacer(Modifier.height(14.dp))
        RouteRow("출발", BoardBlue, nameOf(d?.originId))
        Spacer(Modifier.height(4.dp))
        RouteRow("도착", MoonGold, nameOf(d?.destId))
        Spacer(Modifier.height(8.dp))
        Line("배차 ID ${trip.callId ?: "-"} · ${d?.partySize ?: "-"}명 · 시뮬 시각 ${d?.simClock ?: "-"}")

        // 연결이 잠깐 끊긴 경우 안내 (조회는 계속함)
        trip.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "BUS2" → "2호차" */
private fun busNumber(busId: String?): String =
    busId?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { "${it}호차" } ?: (busId ?: "버스")

/** ETA(시뮬레이션 분) → "약 4.5분" */
private fun minutes(m: Double?): String = if (m == null) "-" else "약 ${String.format(Locale.US, "%.1f", m)}분"

@Composable
private fun Title(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun Line(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RouteRow(label: String, color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 하차 요약 카드: 기다린 시간, 탄 시간, 함께 탄 사람 (시뮬레이션 시각 기준) */
@Composable
private fun TripSummaryCard(r: TripRecord) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("이번 이용 요약", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SummaryRow("기다린 시간", durationText(r.waitMin), "${r.requestClock ?: "-"} 호출 → ${r.pickupClock ?: "-"} 탑승")
            SummaryRow("탄 시간", durationText(r.rideMin), "${r.pickupClock ?: "-"} 탑승 → ${r.dropoffClock ?: "-"} 하차")
            SummaryRow(
                "함께 탄 사람",
                if (r.maxOthers == 0 && r.sharedCallIds.isEmpty()) "혼자 탔어요" else "최대 ${r.maxOthers}명",
                if (r.sharedCallIds.isEmpty()) null else "합승 호출 ${r.sharedCallIds.size}건",
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "시뮬레이션 시각 기준이에요",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, sub: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 분 → "약 7분" (시각이 분 단위라 0이면 "1분 미만") */
private fun durationText(min: Int?): String = when {
    min == null -> "-"
    min == 0 -> "1분 미만"
    else -> "약 ${min}분"
}

package com.dalmuri.bus.ui.trip

import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallStatus

/**
 * 하차 요약용 기록 (추가 기능). 서버는 지금 상태만 알려 주므로, 2초마다 받은 응답을 보며 앱이 직접 모은다.
 *  - 시각은 서버 시뮬레이션 시각(simClock "HH:mm")이라 분 단위, 조회 간격 때문에 실제보다 최대 2초(시뮬 약 12초) 늦게 잡힐 수 있다
 *    → 하차 완료 때 TripViewModel이 서버 이용 내역의 정확한 시각으로 바꿔 넣는다 (11/18)
 *  - 함께 탄 사람: 내가 타 있는 동안(ONBOARD) 버스 인원(load)에서 우리 일행을 뺀 값의 최댓값
 *  - 합승 호출: 내가 타 있는 동안 route에 나온 다른 callId (그 사이 타거나 내린 다른 손님)
 */
data class TripRecord(
    val requestClock: String? = null,  // 처음 조회한 시각 (호출 직후)
    val pickupClock: String? = null,   // 처음 ONBOARD를 본 시각
    val dropoffClock: String? = null,  // 처음 COMPLETED를 본 시각
    val maxOthers: Int = 0,
    val sharedCallIds: Set<String> = emptySet(),
) {
    /** 새 응답 하나를 반영한 기록 */
    fun update(d: CallDetail): TripRecord {
        var r = this
        if (r.requestClock == null) r = r.copy(requestClock = d.simClock)
        when (d.status) {
            CallStatus.ONBOARD -> {
                if (r.pickupClock == null) r = r.copy(pickupClock = d.simClock)
                val others = ((d.bus?.load ?: 0) - d.partySize).coerceAtLeast(0)
                val ids = d.route.flatMap { it.pickups + it.dropoffs }.filter { it != d.callId }
                r = r.copy(maxOthers = maxOf(r.maxOthers, others), sharedCallIds = r.sharedCallIds + ids)
            }
            CallStatus.COMPLETED -> {
                if (r.dropoffClock == null) r = r.copy(dropoffClock = d.simClock)
            }
            else -> Unit
        }
        return r
    }

    /** 기다린 시간(분): 호출 → 탑승 */
    val waitMin: Int? get() = minutesBetween(requestClock, pickupClock)

    /** 탄 시간(분): 탑승 → 하차 */
    val rideMin: Int? get() = minutesBetween(pickupClock, dropoffClock)
}

/** "01:36" → "01:44" 사이 분. 자정을 넘기면 하루를 더함 */
internal fun minutesBetween(from: String?, to: String?): Int? {
    val a = toMinutes(from) ?: return null
    val b = toMinutes(to) ?: return null
    val diff = b - a
    return if (diff < 0) diff + 24 * 60 else diff
}

private fun toMinutes(clock: String?): Int? {
    val parts = clock?.split(":") ?: return null
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

package com.dalmuri.bus.debug

import android.util.Log
import com.dalmuri.bus.data.model.CallRequest
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.toUserMessage
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * F3 확인용 (임시, F6·F7에서 실제 화면이 생기면 지운다).
 * API를 차례로 불러 보고 결과를 Logcat 태그 "ApiCheck"로 찍는다.
 * 시연 기본 경로: 07003 상봉역.중랑우체국(HUB) → 07275 신내동성3차아파트(HOME), 1명
 */
object ApiCheck {
    private const val TAG = "ApiCheck"

    suspend fun run(api: DalmuriApi) {
        try {
            val stops = api.getStops()
            Log.d(TAG, "정류장 ${stops.size}개 (HUB ${stops.count { it.kind.name == "HUB" }}) 첫 번째: ${stops.first()}")

            val map = api.getMap()
            Log.d(TAG, "지도: $map")

            val imageBytes = api.getMapImage().use { it.bytes().size }
            Log.d(TAG, "지도 이미지: ${imageBytes / 1024} KB")

            // 에러 문구 확인: 같은 정류장으로 호출하면 400이어야 함
            runCatching { api.createCall(CallRequest("07003", "07003", 1)) }
                .onFailure { Log.d(TAG, "같은 정류장 호출 → (정상: 에러) ${it.toUserMessage()}") }

            val res = api.createCall(CallRequest("07003", "07275", 1))
            Log.d(TAG, "호출 → $res")

            while (true) {
                val d = api.getCall(res.callId)
                val bus = d.bus?.let { "${it.busId} (${fmt(it.lat)}, ${fmt(it.lng)}) ${it.load}/${it.capacity}명" } ?: "없음"
                Log.d(
                    TAG,
                    "${d.simClock} ${d.status} 버스=$bus 남은정류장=${d.route.map { it.stopId }} " +
                        "탑승까지=${d.etaPickupMin}분 하차까지=${d.etaDropoffMin}분 path=${d.path.size}점",
                )
                if (d.status == CallStatus.COMPLETED || d.status == CallStatus.REJECTED) break
                delay(2_000)
            }
            Log.d(TAG, "확인 끝")
        } catch (e: Exception) {
            Log.e(TAG, "실패: ${e.toUserMessage()}", e)
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.5f", v)
}

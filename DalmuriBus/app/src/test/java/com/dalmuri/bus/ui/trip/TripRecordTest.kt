package com.dalmuri.bus.ui.trip

import com.dalmuri.bus.data.model.BusInfo
import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.model.RouteStop
import org.junit.Assert.assertEquals
import org.junit.Test

/** 하차 요약 계산이 맞는지 (추가 기능) */
class TripRecordTest {

    private fun detail(status: CallStatus, clock: String, load: Int = 0, route: List<RouteStop> = emptyList()) = CallDetail(
        callId = "c-12", status = status, partySize = 1,
        originId = "07003", destId = "07275",
        simClock = clock, etaPickupMin = null, etaDropoffMin = null,
        bus = if (status == CallStatus.ASSIGNED || status == CallStatus.ONBOARD) BusInfo("BUS2", 37.6, 127.09, load, 6) else null,
        route = route, path = emptyList(),
    )

    private fun stop(id: String, pickups: List<String> = emptyList(), dropoffs: List<String> = emptyList()) =
        RouteStop(id, 37.6, 127.09, pickups, dropoffs)

    @Test
    fun 혼자_탄_경우_대기_탑승_시간() {
        val r = listOf(
            detail(CallStatus.ASSIGNED, "01:36"),
            detail(CallStatus.ASSIGNED, "01:37"),
            detail(CallStatus.ONBOARD, "01:38", load = 1, route = listOf(stop("07275", dropoffs = listOf("c-12")))),
            detail(CallStatus.ONBOARD, "01:41", load = 1, route = listOf(stop("07275", dropoffs = listOf("c-12")))),
            detail(CallStatus.COMPLETED, "01:44"),
            detail(CallStatus.COMPLETED, "01:45"),  // 같은 상태가 또 와도 처음 시각 유지
        ).fold(TripRecord()) { acc, d -> acc.update(d) }

        assertEquals("01:36", r.requestClock)
        assertEquals("01:38", r.pickupClock)
        assertEquals("01:44", r.dropoffClock)
        assertEquals(2, r.waitMin)
        assertEquals(6, r.rideMin)
        assertEquals(0, r.maxOthers)
        assertEquals(emptySet<String>(), r.sharedCallIds)
    }

    @Test
    fun 합승하면_함께_탄_사람과_호출을_모은다() {
        val r = listOf(
            detail(CallStatus.ONBOARD, "01:38", load = 1, route = listOf(
                stop("07101", pickups = listOf("c-13")),
                stop("07275", dropoffs = listOf("c-12", "c-13")),
            )),
            detail(CallStatus.ONBOARD, "01:40", load = 3, route = listOf(
                stop("07275", dropoffs = listOf("c-12", "c-13")),
            )),
        ).fold(TripRecord()) { acc, d -> acc.update(d) }

        assertEquals(2, r.maxOthers)              // 3명 중 우리 일행 1명 빼고
        assertEquals(setOf("c-13"), r.sharedCallIds)
    }

    @Test
    fun 자정을_넘겨도_분이_맞다() {
        assertEquals(10, minutesBetween("23:55", "00:05"))
        assertEquals(null, minutesBetween(null, "01:00"))
    }
}

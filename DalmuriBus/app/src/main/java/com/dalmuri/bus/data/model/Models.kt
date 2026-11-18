package com.dalmuri.bus.data.model

// 공통 규약 4 (루트 CLAUDE.md) 그대로.
// 이름이 서버 JSON 필드와 같아야 Gson이 그대로 읽는다. 바꾸지 말 것.

/** HUB = 승차 거점(파랑, N버스 서는 역 앞), HOME = 주거지(주황, 집 근처 하차) */
enum class StopKind { HUB, HOME }

data class Stop(
    val stopId: String, val name: String, val lat: Double, val lng: Double,
    val kind: StopKind, val nightRoutes: List<String>,
)

data class MapInfo(
    val mapId: String, val imageUrl: String, val widthPx: Int, val heightPx: Int,
    val north: Double, val south: Double, val west: Double, val east: Double,
    val attribution: String,
)

data class MapPoint(val lat: Double, val lng: Double)

data class BusInfo(val busId: String, val lat: Double, val lng: Double, val load: Int, val capacity: Int)

data class RouteStop(
    val stopId: String, val lat: Double, val lng: Double,
    val pickups: List<String>, val dropoffs: List<String>,
)

enum class CallStatus { REQUESTED, ASSIGNED, ONBOARD, COMPLETED, REJECTED, CANCELED }

data class CallRequest(val originId: String, val destId: String, val partySize: Int, val userId: String? = null)

data class CallResponse(val callId: String, val status: CallStatus, val busId: String?)

data class CallDetail(
    val callId: String, val status: CallStatus, val partySize: Int,
    val originId: String, val destId: String,
    val simClock: String, val etaPickupMin: Double?, val etaDropoffMin: Double?,
    val bus: BusInfo?, val route: List<RouteStop>, val path: List<MapPoint>,
)

data class CallHistory(   // GET /users/{userId}/calls 의 원소 (11/18)
    val callId: String, val status: CallStatus, val originId: String, val destId: String,
    val partySize: Int, val busId: String?,
    val requestClock: String, val pickupClock: String?, val dropoffClock: String?, val canceledClock: String?,
)

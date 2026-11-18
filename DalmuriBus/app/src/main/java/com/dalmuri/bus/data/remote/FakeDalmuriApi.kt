package com.dalmuri.bus.data.remote

import android.content.Context
import android.util.Log
import com.dalmuri.bus.data.model.BusInfo
import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallHistory
import com.dalmuri.bus.data.model.CallRequest
import com.dalmuri.bus.data.model.CallResponse
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.MapPoint
import com.dalmuri.bus.data.model.RouteStop
import com.dalmuri.bus.data.model.Stop
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 서버 없이 화면을 만들기 위한 가짜 서버. DalmuriApi와 똑같이 동작한다.
 *  - 정류장·지도 정보·지도 이미지는 진짜 서버 데이터(server/data)를 복사한 assets/fake/ 파일을 그대로 쓴다.
 *  - 호출하면 실제 시간이 지나며 REQUESTED(3초) → ASSIGNED(20초) → ONBOARD(30초) → COMPLETED.
 *    시뮬 시각·ETA는 진짜 서버처럼 6배속으로 계산한다 (실제 1초 = 시뮬 6초).
 *  - 짝수 번째 호출은 합승 확인용으로 다른 승객(c-99)이 같이 타서 route에 경유 정류장이 하나 늘어난다.
 *  - path는 항상 [] → 앱은 버스 위치와 route 정류장을 직선으로 잇는다. 도로 좌표는 진짜 서버(F8)에서 확인.
 */
class FakeDalmuriApi(private val context: Context) : DalmuriApi {

    private val gson = Gson()
    private val stops: List<Stop> by lazy { readAsset("fake/stops.json") { gson.fromJson(it, Array<Stop>::class.java).toList() } }
    private val mapInfo: MapInfo by lazy { readAsset("fake/map.json") { gson.fromJson(it, MapInfo::class.java) } }

    private val calls = ConcurrentHashMap<String, FakeCall>()
    private val nextNumber = AtomicInteger(1)

    override suspend fun getStops(): List<Stop> = withContext(Dispatchers.IO) { stops }

    override suspend fun getMap(): MapInfo = withContext(Dispatchers.IO) { mapInfo }

    override suspend fun getMapImage(): ResponseBody = withContext(Dispatchers.IO) {
        val bytes = context.assets.open("fake/map.png").use { it.readBytes() }
        bytes.toResponseBody("image/png".toMediaType())
    }

    override suspend fun createCall(body: CallRequest): CallResponse = withContext(Dispatchers.IO) {
        // 진짜 서버와 같은 경우에 400을 낸다
        if (body.originId == body.destId) throw httpError(400, "출발과 도착 정류장이 같아요")
        val origin = stops.find { it.stopId == body.originId } ?: throw httpError(400, "없는 정류장이에요: ${body.originId}")
        val dest = stops.find { it.stopId == body.destId } ?: throw httpError(400, "없는 정류장이에요: ${body.destId}")
        if (body.partySize !in 1..3) throw httpError(400, "탑승 인원은 1~3명이에요")

        val number = nextNumber.getAndIncrement()
        val callId = "c-$number"
        val busStart = stops.find { it.stopId == if (origin.stopId == "07001") "07440" else "07001" } ?: stops.first()
        val shareStop = if (number % 2 == 0) bestDetour(origin, dest) else null
        calls[callId] = FakeCall(callId, origin, dest, body.partySize, busStart, shareStop, System.currentTimeMillis(), body.userId)

        Log.d(TAG, "POST /calls ${origin.name} → ${dest.name} ${body.partySize}명 → $callId (합승 손님 ${if (shareStop != null) "있음" else "없음"})")
        CallResponse(callId, CallStatus.REQUESTED, null)
    }

    override suspend fun getCall(callId: String): CallDetail = withContext(Dispatchers.IO) {
        val call = calls[callId] ?: throw httpError(404, "호출 정보가 없어요. 다시 호출해 주세요")
        call.detailAt(System.currentTimeMillis())
    }

    override suspend fun cancelCall(callId: String): CallDetail = withContext(Dispatchers.IO) {
        val call = calls[callId] ?: throw httpError(404, "호출 정보가 없어요. 다시 호출해 주세요")
        val now = System.currentTimeMillis()
        when (call.detailAt(now).status) {
            CallStatus.REQUESTED, CallStatus.ASSIGNED -> {
                val canceled = call.copy(canceledMs = now)
                calls[callId] = canceled
                canceled.detailAt(now)
            }
            CallStatus.CANCELED -> call.detailAt(now)  // 이미 취소한 걸 또 보내도 같은 결과 (진짜 서버와 같음)
            else -> throw httpError(400, "탑승했거나 끝난 호출은 취소할 수 없어요")
        }
    }

    override suspend fun getHistory(userId: String): List<CallHistory> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        calls.values
            .filter { it.userId == userId }
            .sortedByDescending { it.startMs }
            .take(50)
            .map { it.historyAt(now) }
    }

    // ───────── 아래는 가짜 시뮬레이션 계산 ─────────

    private data class FakeCall(
        val callId: String,
        val origin: Stop,
        val dest: Stop,
        val partySize: Int,
        val busStart: Stop,
        val shareStop: Stop?,   // 합승 손님(c-99)이 내리는 정류장. null이면 합승 없음
        val startMs: Long,
        val userId: String?,
        val canceledMs: Long? = null,  // 취소한 시각. null이면 취소 안 함
    ) {
        fun detailAt(nowMs: Long): CallDetail {
            val sec = (nowMs - startMs) / 1000.0
            val clock = simClock(sec)
            val pickupAt = REQUESTED_SEC + ASSIGNED_SEC   // 이 시각(초)에 탑승
            val dropAt = pickupAt + ONBOARD_SEC            // 이 시각(초)에 하차

            // 타는 구간의 정류장들: 출발 → (합승 손님 하차) → 도착
            val rideStops = listOfNotNull(origin, shareStop, dest)
            val sharedId = if (shareStop != null) SHARED_CALL_ID else null

            return when {
                canceledMs != null -> ended(CallStatus.CANCELED, clock)

                sec < REQUESTED_SEC -> ended(CallStatus.REQUESTED, clock)

                sec < pickupAt -> {
                    // 버스가 시작 위치에서 출발 정류장으로 다가옴
                    val f = (sec - REQUESTED_SEC) / ASSIGNED_SEC
                    val pos = lerp(busStart.point(), origin.point(), f)
                    CallDetail(
                        callId, CallStatus.ASSIGNED, partySize, origin.stopId, dest.stopId, clock,
                        etaPickupMin = simMinutes(pickupAt - sec),
                        etaDropoffMin = simMinutes(dropAt - sec),
                        bus = BusInfo(BUS_ID, pos.lat, pos.lng, load = 0, capacity = CAPACITY),
                        route = routeOf(rideStops, sharedId, fromIndex = 0),
                        path = emptyList(),
                    )
                }

                sec < dropAt -> {
                    // 탑승 중: 정류장들을 차례로 지나 도착 정류장으로
                    val f = (sec - pickupAt) / ONBOARD_SEC
                    val (pos, passed) = alongStops(rideStops, f)
                    val sharedAboard = shareStop != null && passed < 1  // 합승 손님은 첫 경유지에서 내림
                    CallDetail(
                        callId, CallStatus.ONBOARD, partySize, origin.stopId, dest.stopId, clock,
                        etaPickupMin = null,
                        etaDropoffMin = simMinutes(dropAt - sec),
                        bus = BusInfo(BUS_ID, pos.lat, pos.lng, load = partySize + if (sharedAboard) SHARED_SIZE else 0, capacity = CAPACITY),
                        route = routeOf(rideStops, sharedId, fromIndex = passed + 1),
                        path = emptyList(),
                    )
                }

                else -> ended(CallStatus.COMPLETED, clock)
            }
        }

        /** 이용 내역 한 줄. 가짜 서버는 호출마다 01:30부터 시각을 센다 */
        fun historyAt(nowMs: Long): CallHistory {
            val sec = (nowMs - startMs) / 1000.0
            val pickupAt = REQUESTED_SEC + ASSIGNED_SEC
            val dropAt = pickupAt + ONBOARD_SEC
            val cancelSec = canceledMs?.let { (it - startMs) / 1000.0 }
            val lastSec = cancelSec ?: sec  // 취소했으면 그 뒤 일은 안 일어남
            return CallHistory(
                callId = callId,
                status = detailAt(nowMs).status,
                originId = origin.stopId,
                destId = dest.stopId,
                partySize = partySize,
                busId = if (lastSec >= REQUESTED_SEC) BUS_ID else null,
                requestClock = simClock(0.0),
                pickupClock = if (lastSec >= pickupAt) simClock(pickupAt) else null,
                dropoffClock = if (lastSec >= dropAt) simClock(dropAt) else null,
                canceledClock = cancelSec?.let { simClock(it) },
            )
        }

        /** REQUESTED·COMPLETED: bus null, route·path 빈 배열, ETA null (공통 규약 3) */
        private fun ended(status: CallStatus, clock: String) = CallDetail(
            callId, status, partySize, origin.stopId, dest.stopId, clock,
            etaPickupMin = null, etaDropoffMin = null, bus = null, route = emptyList(), path = emptyList(),
        )

        /** 앞으로 들를 정류장 목록. 출발에서 나(+합승 손님) 승차, 경유지에서 합승 손님 하차, 도착에서 나 하차 */
        private fun routeOf(rideStops: List<Stop>, sharedId: String?, fromIndex: Int): List<RouteStop> =
            rideStops.mapIndexed { i, s ->
                val pickups = if (i == 0) listOfNotNull(callId, sharedId) else emptyList()
                val dropoffs = when {
                    i == rideStops.lastIndex -> listOf(callId)
                    i > 0 && sharedId != null -> listOf(sharedId)
                    else -> emptyList()
                }
                RouteStop(s.stopId, s.lat, s.lng, pickups, dropoffs)
            }.drop(fromIndex)
    }

    private fun httpError(code: Int, message: String): HttpException {
        val json = gson.toJson(mapOf("error" to message))
        return HttpException(Response.error<Any>(code, json.toResponseBody("application/json".toMediaType())))
    }

    /** 출발→도착 사이에 들러도 가장 덜 돌아가는 정류장 (합승 손님 하차 지점) */
    private fun bestDetour(origin: Stop, dest: Stop): Stop? =
        stops.filter { it.stopId != origin.stopId && it.stopId != dest.stopId }
            .minByOrNull { meters(origin.point(), it.point()) + meters(it.point(), dest.point()) }

    private fun <T> readAsset(path: String, parse: (java.io.Reader) -> T): T =
        context.assets.open(path).bufferedReader().use(parse)

    companion object {
        private const val TAG = "FakeDalmuriApi"
        private const val REQUESTED_SEC = 3.0
        private const val ASSIGNED_SEC = 20.0
        private const val ONBOARD_SEC = 30.0
        private const val SIM_SPEED = 6.0          // 진짜 서버 기본 배속
        private const val SIM_START_MIN = 90.0     // 01:30
        private const val BUS_ID = "BUS2"
        private const val CAPACITY = 6
        private const val SHARED_CALL_ID = "c-99"
        private const val SHARED_SIZE = 2

        private fun Stop.point() = MapPoint(lat, lng)

        private fun lerp(a: MapPoint, b: MapPoint, f: Double): MapPoint {
            val t = f.coerceIn(0.0, 1.0)
            return MapPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
        }

        /** 두 좌표 사이 거리(m). 4km 안쪽이라 평면 근사로 충분 */
        private fun meters(a: MapPoint, b: MapPoint): Double {
            val dy = (b.lat - a.lat) * 111_320.0
            val dx = (b.lng - a.lng) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
            return hypot(dx, dy)
        }

        /** 정류장들을 잇는 선 위에서 전체 거리의 f 비율만큼 간 위치 + 지나간 구간 수 */
        private fun alongStops(stops: List<Stop>, f: Double): Pair<MapPoint, Int> {
            val pts = stops.map { it.point() }
            val segs = pts.zipWithNext { a, b -> meters(a, b) }
            var left = segs.sum() * f.coerceIn(0.0, 1.0)
            segs.forEachIndexed { i, len ->
                if (left <= len || i == segs.lastIndex) {
                    return lerp(pts[i], pts[i + 1], if (len == 0.0) 1.0 else left / len) to i
                }
                left -= len
            }
            return pts.last() to segs.lastIndex
        }

        /** 실제 초 → 시뮬레이션 분 (소수 한 자리) */
        private fun simMinutes(realSec: Double): Double = (realSec * SIM_SPEED / 60.0 * 10).roundToInt() / 10.0

        /** 실제 경과 초 → 시뮬 시각 "HH:mm" (01:30부터) */
        private fun simClock(realSec: Double): String {
            val total = (SIM_START_MIN + realSec * SIM_SPEED / 60.0).toInt()
            return String.format(Locale.US, "%02d:%02d", total / 60 % 24, total % 60)
        }
    }
}

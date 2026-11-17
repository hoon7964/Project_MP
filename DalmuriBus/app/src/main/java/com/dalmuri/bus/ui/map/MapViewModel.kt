package com.dalmuri.bus.ui.map

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dalmuri.bus.DalmuriApp
import com.dalmuri.bus.data.model.CallRequest
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.toUserMessage
import com.dalmuri.bus.data.repository.MapRepository
import com.dalmuri.bus.data.repository.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** 지도 화면이 그리는 값 */
data class MapUiState(
    val loading: Boolean = true,
    val stops: List<Stop> = emptyList(),
    val mapInfo: MapInfo? = null,
    val mapImage: ImageBitmap? = null,  // 2400×2306 이미지를 한 번만 풀어서 들고 있음 (약 22MB)
    val error: String? = null,
    // ── F6 호출 ──
    val selectedStopId: String? = null, // 지도·목록에서 누른 정류장 (정보 카드에 보임)
    val originId: String? = null,       // 출발(승차)
    val destId: String? = null,         // 도착(하차)
    val partySize: Int = 1,             // 탑승 인원 1~3명
    val calling: Boolean = false,       // POST /calls 기다리는 중
    val callId: String? = null,         // 받은 배차 ID
    val message: String? = null,        // 사용자에게 보여 줄 안내·에러 문구
)

/**
 * 지도 화면 ViewModel.
 *  - F4: 화면이 처음 뜰 때 정류장·지도를 저장소에서 꺼낸다
 *  - F6: 출발·도착·인원을 고르고 호출해서 배차 ID(callId)를 받는다
 *    (받은 뒤 추적은 TripViewModel이 한다, F7)
 */
class MapViewModel(
    private val stopRepository: StopRepository,
    private val mapRepository: MapRepository,
    private val api: DalmuriApi,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val stops = stopRepository.getStops()
                val map = mapRepository.getMap()
                // 큰 이미지라 화면 스레드가 아닌 곳에서 한 번만 디코드
                val image = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(map.imageFile.path)?.asImageBitmap()
                } ?: error("지도 이미지를 열 수 없어요")
                _state.update { it.copy(loading = false, stops = stops, mapInfo = map.info, mapImage = image) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    // ───────── F6: 정류장 고르기 ─────────

    /** 지도 점이나 목록에서 정류장을 누름 → 정보 카드. 빈 곳을 누르면 null → 카드 닫힘 */
    fun onStopTap(stop: Stop?) {
        _state.update { it.copy(selectedStopId = stop?.stopId, message = null) }
    }

    /** [여기서 출발] */
    fun setOrigin(stop: Stop) {
        if (stop.stopId == _state.value.destId) {
            _state.update { it.copy(message = "도착과 같은 정류장은 출발로 고를 수 없어요") }
            return
        }
        _state.update { it.copy(originId = stop.stopId, selectedStopId = null, message = null) }
    }

    /** [여기로 도착] */
    fun setDest(stop: Stop) {
        if (stop.stopId == _state.value.originId) {
            _state.update { it.copy(message = "출발과 같은 정류장은 도착으로 고를 수 없어요") }
            return
        }
        _state.update { it.copy(destId = stop.stopId, selectedStopId = null, message = null) }
    }

    /** [다시 고르기]: 출발·도착을 비움 */
    fun resetRoute() {
        _state.update { it.copy(originId = null, destId = null, selectedStopId = null, callId = null, message = null) }
    }

    /** 배차 불가·호출 사라짐 → 다시 호출: 출발·도착·인원은 그대로 두고 호출만 비움 */
    fun clearCall() {
        _state.update { it.copy(callId = null, message = null) }
    }

    fun setPartySize(n: Int) {
        _state.update { it.copy(partySize = n.coerceIn(1, 3)) }
    }

    // ───────── F6: 호출 ─────────

    /** [호출하기] → POST /calls → 배차 ID 저장 */
    fun requestCall() {
        val s = _state.value
        val origin = s.originId ?: return
        val dest = s.destId ?: return
        if (s.calling) return

        _state.update { it.copy(calling = true, message = null) }
        viewModelScope.launch {
            try {
                val res = api.createCall(CallRequest(originId = origin, destId = dest, partySize = s.partySize))
                _state.update { it.copy(calling = false, callId = res.callId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 400이면 서버가 준 이유(예: 운행 시간 끝남)를 그대로 보여 줌
                _state.update { it.copy(calling = false, message = e.toUserMessage()) }
            }
        }
    }

    companion object {
        /** ViewModel을 만들 때 AppContainer에서 저장소·API를 꺼내 넣어 준다 (Hilt 대신) */
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DalmuriApp).container
                MapViewModel(container.stopRepository, container.mapRepository, container.api)
            }
        }
    }
}

package com.dalmuri.bus.ui.map

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dalmuri.bus.DalmuriApp
import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallRequest
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.toUserMessage
import com.dalmuri.bus.data.repository.MapRepository
import com.dalmuri.bus.data.repository.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val selectedStopId: String? = null, // 지도에서 누른 정류장
    val demoTrip: CallDetail? = null,   // F5 확인용 임시 호출 (F7의 TripViewModel로 바뀜)
)

/**
 * 지도 화면 ViewModel. 화면이 처음 뜰 때 정류장·지도를 저장소에서 꺼낸다
 * (첫 실행이면 서버에서 받아 Room에 저장, 두 번째부터는 Room에서만).
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

    /** 지도에서 정류장을 누름 (빈 곳을 누르면 null → 선택 해제) */
    fun onStopTap(stop: Stop?) {
        _state.update { it.copy(selectedStopId = stop?.stopId) }
    }

    // ── F5 확인용 임시: 시연 기본 경로로 호출하고 2초마다 상태를 받아 지도에 그림. F7에서 TripViewModel로 바꾼다 ──
    private var demoJob: Job? = null

    fun startDemoTrip() {
        demoJob?.cancel()
        demoJob = viewModelScope.launch {
            try {
                val res = api.createCall(CallRequest(originId = "07003", destId = "07275", partySize = 1))
                while (true) {
                    val detail = api.getCall(res.callId)
                    _state.update { it.copy(demoTrip = detail) }
                    if (detail.status == CallStatus.COMPLETED || detail.status == CallStatus.REJECTED) break
                    delay(2_000)
                }
            } catch (e: CancellationException) {
                throw e  // 버튼을 다시 눌러 이전 호출을 멈춘 경우: 에러 아님
            } catch (e: Exception) {
                Log.e("MapViewModel", "임시 호출 실패: ${e.toUserMessage()}", e)
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

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
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.Stop
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

/** 지도 화면이 그리는 값 */
data class MapUiState(
    val loading: Boolean = true,
    val stops: List<Stop> = emptyList(),
    val mapInfo: MapInfo? = null,
    val mapImage: ImageBitmap? = null,  // 2400×2306 이미지를 한 번만 풀어서 들고 있음 (약 22MB)
    val error: String? = null,
)

/**
 * 지도 화면 ViewModel. 화면이 처음 뜰 때 정류장·지도를 저장소에서 꺼낸다
 * (첫 실행이면 서버에서 받아 Room에 저장, 두 번째부터는 Room에서만).
 */
class MapViewModel(
    private val stopRepository: StopRepository,
    private val mapRepository: MapRepository,
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
                _state.value = MapUiState(loading = false, stops = stops, mapInfo = map.info, mapImage = image)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    companion object {
        /** ViewModel을 만들 때 AppContainer에서 저장소를 꺼내 넣어 준다 (Hilt 대신) */
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DalmuriApp).container
                MapViewModel(container.stopRepository, container.mapRepository)
            }
        }
    }
}

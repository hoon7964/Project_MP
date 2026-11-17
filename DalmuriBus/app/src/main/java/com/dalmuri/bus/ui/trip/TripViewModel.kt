package com.dalmuri.bus.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dalmuri.bus.AppConfig
import com.dalmuri.bus.DalmuriApp
import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallStatus
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.isNotFound
import com.dalmuri.bus.data.remote.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** 호출 뒤 상태 시트가 그리는 값 */
data class TripUiState(
    val callId: String? = null,     // 지금 추적 중인 배차 ID
    val detail: CallDetail? = null, // 마지막으로 받은 GET /calls/{callId} 응답
    val message: String? = null,    // 연결 실패 등 안내 (조회는 계속함)
    val lost: Boolean = false,      // 404: 서버 초기화·재시작으로 호출이 사라짐 → 다시 호출해야 함
)

/**
 * 배차 추적 (F7). 교수님: "배차 ID로 주기적으로 요청해서 지도에 출력" (팀 결정 2초).
 * callId가 생기면 POLL_MS마다 GET /calls/{callId}를 부르고,
 * COMPLETED·REJECTED면 멈추고, 앱 화면이 안 보이면 다시 보일 때까지 쉰다.
 */
class TripViewModel(
    private val api: DalmuriApi,
    private val appVisible: StateFlow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** 호출로 받은 배차 ID로 추적 시작 */
    fun start(callId: String) {
        if (_state.value.callId == callId && pollJob?.isActive == true) return
        pollJob?.cancel()
        _state.value = TripUiState(callId = callId)
        pollJob = viewModelScope.launch { poll(callId) }
    }

    /** 추적 끝 (처음으로 / 다시 호출) */
    fun clear() {
        pollJob?.cancel()
        _state.value = TripUiState()
    }

    private suspend fun poll(callId: String) {
        while (true) {
            appVisible.first { it }  // 앱이 안 보이면 다시 보일 때까지 기다림
            try {
                val detail = api.getCall(callId)
                _state.update { it.copy(detail = detail, message = null) }
                if (detail.status == CallStatus.COMPLETED || detail.status == CallStatus.REJECTED) return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isNotFound()) {
                    // 관제 "처음부터 다시"나 서버 재시작으로 호출이 지워짐 → 조회 멈추고 다시 호출 안내
                    _state.update { it.copy(lost = true, message = "호출 정보가 없어요, 다시 호출해 주세요") }
                    return
                }
                // 잠깐 연결이 끊긴 경우: 문구만 보여 주고 계속 조회
                _state.update { it.copy(message = e.toUserMessage()) }
            }
            delay(AppConfig.POLL_MS)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DalmuriApp).container
                TripViewModel(container.api, container.appVisible)
            }
        }
    }
}

package com.dalmuri.bus.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dalmuri.bus.DalmuriApp
import com.dalmuri.bus.data.model.CallHistory
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.toUserMessage
import com.dalmuri.bus.data.repository.StopRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** 이용 내역 화면이 그리는 값 */
data class HistoryUiState(
    val loading: Boolean = true,
    val items: List<CallHistory> = emptyList(),
    val stopNames: Map<String, String> = emptyMap(),  // stopId → 정류장 이름 (Room에 저장된 것)
    val error: String? = null,
)

/**
 * 이용 내역 (추가 기능, 11/18): GET /users/{내 Firebase uid}/calls.
 * 서버 메모리에 있어서 서버를 다시 켜거나 관제 "처음부터 다시"를 누르면 비워진다 (공통 규약 3).
 */
class HistoryViewModel(
    private val api: DalmuriApi,
    private val stopRepository: StopRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            _state.update { it.copy(loading = false, error = "로그인 정보가 없어요. 다시 로그인해 주세요") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val names = stopRepository.getStops().associate { it.stopId to it.name }
                val items = api.getHistory(uid)
                _state.update { it.copy(loading = false, items = items, stopNames = names) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DalmuriApp).container
                HistoryViewModel(container.api, container.stopRepository)
            }
        }
    }
}

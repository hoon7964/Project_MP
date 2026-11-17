package com.dalmuri.bus

import android.content.Context
import com.dalmuri.bus.data.local.AppDatabase
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.FakeDalmuriApi
import com.dalmuri.bus.data.repository.MapRepository
import com.dalmuri.bus.data.repository.StopRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 앱 전체가 같이 쓰는 객체를 한 곳에서 만들어 두는 상자 (Hilt 대신 손으로 하는 의존성 연결).
 * 화면(ViewModel)은 여기서 꺼내 쓴다. by lazy = 처음 쓸 때 한 번만 만든다.
 */
class AppContainer(val context: Context) {

    /** 서버 API (AppConfig.USE_FAKE_SERVER로 진짜/Fake 전환) */
    val api: DalmuriApi by lazy {
        if (AppConfig.USE_FAKE_SERVER) FakeDalmuriApi(context) else DalmuriApi.create(AppConfig.BASE_URL)
    }

    /** 앱 안 DB (Room) */
    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    /** 정류장·지도 저장소: Room에 있으면 그것만, 없으면 서버에서 받아 저장 */
    val stopRepository: StopRepository by lazy { StopRepository(api, database.stopDao()) }
    val mapRepository: MapRepository by lazy { MapRepository(context, api, database.mapInfoDao()) }

    /** 앱 화면이 지금 보이는지 (MainActivity onStart/onStop이 바꿈). 안 보이면 상태 조회를 쉰다 */
    val appVisible = MutableStateFlow(true)
}

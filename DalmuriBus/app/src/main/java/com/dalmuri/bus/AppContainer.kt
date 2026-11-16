package com.dalmuri.bus

import android.content.Context
import com.dalmuri.bus.data.remote.DalmuriApi
import com.dalmuri.bus.data.remote.FakeDalmuriApi

/**
 * 앱 전체가 같이 쓰는 객체를 한 곳에서 만들어 두는 상자 (Hilt 대신 손으로 하는 의존성 연결).
 * 화면(ViewModel)은 여기서 꺼내 쓴다.
 *  - F3: 서버 API (AppConfig.USE_FAKE_SERVER로 진짜/Fake 전환)
 *  - F4: 정류장·지도 저장소(Room)를 여기에 추가
 */
class AppContainer(val context: Context) {

    val api: DalmuriApi by lazy {
        if (AppConfig.USE_FAKE_SERVER) FakeDalmuriApi(context) else DalmuriApi.create(AppConfig.BASE_URL)
    }
}

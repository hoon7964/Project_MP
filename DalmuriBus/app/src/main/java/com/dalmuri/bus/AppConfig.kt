package com.dalmuri.bus

/** 앱 설정값. 서버 전환은 USE_FAKE_SERVER 한 줄로 한다. */
object AppConfig {
    /** true = 서버 없이 앱 안의 FakeDalmuriApi 사용, false = 노트북에서 켠 진짜 서버 사용 */
    const val USE_FAKE_SERVER = false

    /** 에뮬레이터에서 본 노트북 주소 (10.0.2.2 = 에뮬레이터를 띄운 PC). 끝에 / 꼭 붙임 */
    const val BASE_URL = "http://10.0.2.2:3000/"

    /** 내 배차 상태 조회 간격 (공통 규약 6: 2초, 받은 위치 사이는 부드럽게 이어 그림) */
    const val POLL_MS = 2_000L
}

package com.dalmuri.bus

import android.app.Application

/**
 * 앱 전체에서 하나만 있는 Application.
 * 앱이 켜질 때 AppContainer를 한 번 만들어 둔다. Hilt는 안 쓴다.
 */
class DalmuriApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

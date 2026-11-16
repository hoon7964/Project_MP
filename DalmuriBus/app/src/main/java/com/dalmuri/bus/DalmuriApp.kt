package com.dalmuri.bus

import android.app.Application

/**
 * 앱 전체에서 하나만 있는 Application.
 * F2에서 여기에 간단한 컨테이너(API, 저장소)를 붙인다. Hilt는 안 쓴다.
 */
class DalmuriApp : Application()

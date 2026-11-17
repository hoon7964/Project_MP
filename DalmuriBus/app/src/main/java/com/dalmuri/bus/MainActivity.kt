package com.dalmuri.bus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dalmuri.bus.ui.DalmuriNavHost
import com.dalmuri.bus.ui.theme.DalmuriBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 앱은 항상 다크 테마라 상태바·내비게이션바 아이콘도 항상 밝은색으로
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DalmuriBusTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DalmuriNavHost()
                }
            }
        }
    }

    // 화면이 보이는 동안만 배차 상태를 조회한다 (ROLE_FRONT.md F7 "화면 꺼지면 멈춤")
    override fun onStart() {
        super.onStart()
        (application as DalmuriApp).container.appVisible.value = true
    }

    override fun onStop() {
        super.onStop()
        (application as DalmuriApp).container.appVisible.value = false
    }
}

package com.dalmuri.bus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dalmuri.bus.ui.login.LoginScreen
import com.dalmuri.bus.ui.map.MapScreen
import com.google.firebase.auth.FirebaseAuth

/** 화면 주소 (ROLE_FRONT.md F2: "login" → "map", "history"는 자리만) */
object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
    const val HISTORY = "history"
}

/** 앱의 화면 이동 전체. 로그인돼 있으면 지도(메인)부터, 아니면 로그인부터 시작한다. */
@Composable
fun DalmuriNavHost() {
    val nav = rememberNavController()
    val start = remember {
        if (FirebaseAuth.getInstance().currentUser != null) Routes.MAP else Routes.LOGIN
    }

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    // 메인으로 가면서 로그인 화면은 뒤로가기 목록에서 지움 (뒤로가기로 다시 안 나오게)
                    nav.navigate(Routes.MAP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                modifier = Modifier.safeDrawingPadding(),
            )
        }

        composable(Routes.MAP) {
            MapScreen()
        }

        composable(Routes.HISTORY) {
            // 추가 기능(이용 내역) 자리만. 11/30 이후에 채운다.
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                Text("이용 내역 (추가 기능)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

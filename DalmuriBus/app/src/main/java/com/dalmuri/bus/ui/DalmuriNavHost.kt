package com.dalmuri.bus.ui

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dalmuri.bus.ui.history.HistoryScreen
import com.dalmuri.bus.ui.login.LoginScreen
import com.dalmuri.bus.ui.map.MapScreen
import com.google.firebase.auth.FirebaseAuth

/** 화면 주소 (ROLE_FRONT.md F2: "login" → "map", "history" = 이용 내역(추가 기능)) */
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
            MapScreen(onOpenHistory = { nav.navigate(Routes.HISTORY) })
        }

        // 이용 내역 (추가 기능, 11/18). 뒤로가기 → 지도 (호출 중이던 상태 그대로)
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
    }
}

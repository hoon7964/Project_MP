package com.dalmuri.bus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dalmuri.bus.ui.login.LoginScreen
import com.dalmuri.bus.ui.theme.DalmuriBusTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DalmuriBusTheme {
                // 앱이 켜질 때 이미 로그인돼 있으면 바로 메인 (Firebase가 로그인 상태를 기기에 저장해 둠)
                var loggedIn by rememberSaveable {
                    mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (loggedIn) {
                        MainPlaceholder(Modifier.padding(innerPadding))
                    } else {
                        LoginScreen(
                            onLoggedIn = { loggedIn = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/** 임시 메인 화면. F2에서 Navigation("login" → "map")과 지도 화면 구조로 바꾼다. */
@Composable
fun MainPlaceholder(modifier: Modifier = Modifier) {
    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("메인 화면 (F2에서 지도)", style = MaterialTheme.typography.titleMedium)
        Text("로그인됨: $email", style = MaterialTheme.typography.bodyMedium)
    }
}

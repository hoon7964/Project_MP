package com.dalmuri.bus.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dalmuri.bus.AppConfig

/**
 * Android 17(API 37)부터 targetSdk 37 앱은 로컬 네트워크(10.0.2.2, 같은 와이파이의 노트북 등)에
 * 접속하려면 ACCESS_LOCAL_NETWORK 런타임 권한이 있어야 한다. 없으면 연결이 타임아웃으로 막힌다.
 * (https://developer.android.com/about/versions/17/behavior-changes-17)
 * 권한('주변 기기' 그룹)을 받은 뒤에만 앱 화면을 띄워서, 지도 화면이 처음부터 서버에 붙게 한다.
 */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val API_ANDROID_17 = 37

private fun hasLocalNetwork(context: Context): Boolean =
    AppConfig.USE_FAKE_SERVER ||                // 가짜 서버는 네트워크를 안 씀
        Build.VERSION.SDK_INT < API_ANDROID_17 || // Android 16 이하는 권한 필요 없음
        ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/**
 * @param recheckKey 화면이 다시 보일 때마다 바뀌는 값 (설정에서 허용하고 돌아오면 다시 확인)
 */
@Composable
fun LocalNetworkGate(recheckKey: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocalNetwork(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }

    // 설정 화면에서 돌아왔을 때 등
    LaunchedEffect(recheckKey) { granted = hasLocalNetwork(context) }

    // 처음 한 번은 바로 권한 창을 띄움
    LaunchedEffect(Unit) {
        if (!granted && !askedOnce) {
            askedOnce = true
            launcher.launch(LOCAL_NETWORK)
        }
    }

    if (granted) {
        content()
        return
    }

    // 거부했을 때 안내
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("서버 연결 권한이 필요해요", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "달무리버스는 노트북에서 켠 서버와 통신해요. Android 17부터는 이때 '주변 기기' 권한이 필요해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { launcher.launch(LOCAL_NETWORK) }, modifier = Modifier.fillMaxWidth()) {
            Text("권한 허용하기")
        }
        // 두 번 거부하면 시스템이 창을 더 안 띄우므로 설정으로 보냄
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text("설정에서 허용하기") }
    }
}

package com.dalmuri.bus.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 로그인·회원가입 화면 하나.
 * 성공하면 onLoggedIn()을 불러서 바깥(MainActivity)이 메인 화면으로 바꾼다.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LoginViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    // 로그인·가입이 성공한 순간 한 번만 메인으로 넘김
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("달무리버스", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = { Text("이메일") },
            singleLine = true,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text("비밀번호 (6자 이상)") },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation(),  // ●●● 로 가림
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // 에러 문구 (없으면 안 보임)
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        } else {
            Button(onClick = vm::login, modifier = Modifier.fillMaxWidth()) {
                Text("로그인")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::signUp, modifier = Modifier.fillMaxWidth()) {
                Text("회원가입")
            }
        }
    }
}

package com.dalmuri.bus.ui.login

import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 로그인 화면이 그리는 값 전부 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,   // Firebase 응답 기다리는 중
    val error: String? = null,      // 화면에 보여 줄 에러 문구
    val loggedIn: Boolean = false,  // 성공하면 true → 화면이 메인으로 넘어감
)

/**
 * Firebase 이메일 로그인·회원가입.
 * 화면은 state만 보고 그리고, 버튼은 login()/signUp()만 부른다.
 */
class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() = submit { email, pw -> auth.signInWithEmailAndPassword(email, pw) }

    fun signUp() = submit { email, pw -> auth.createUserWithEmailAndPassword(email, pw) }

    /** 로그인·회원가입 공통: 입력 확인 → Firebase 요청 → 결과를 state에 반영 */
    private fun submit(request: (String, String) -> Task<AuthResult>) {
        val now = _state.value
        if (now.loading) return  // 버튼 연타 막기

        val email = now.email.trim()
        if (email.isEmpty() || now.password.isEmpty()) {
            _state.update { it.copy(error = "이메일과 비밀번호를 입력해 주세요") }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        request(email, now.password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _state.update { it.copy(loading = false, loggedIn = true) }
            } else {
                _state.update { it.copy(loading = false, error = errorMessage(task.exception)) }
            }
        }
    }

    /** Firebase 예외 → 한국어 문구. 구체적인 것부터 검사한다. */
    private fun errorMessage(e: Exception?): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "비밀번호는 6자 이상이어야 해요"
        is FirebaseAuthUserCollisionException -> "이미 가입된 이메일이에요. 로그인해 주세요"
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "이메일 형식이나 비밀번호가 맞지 않아요"
        is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요"
        is FirebaseTooManyRequestsException -> "시도가 너무 많아요. 잠시 후 다시 해 주세요"
        else -> e?.localizedMessage ?: "알 수 없는 오류가 났어요"
    }
}

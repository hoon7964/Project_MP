package com.dalmuri.bus.data.remote

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/** 서버의 400·404 본문: { "error": "이유(한국어)" } (공통 규약 3) */
private data class ApiErrorBody(val error: String?)

/** API 예외 → 화면에 보여 줄 한국어 문구 */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val body = response()?.errorBody()?.string()
        val message = body?.let { runCatching { Gson().fromJson(it, ApiErrorBody::class.java)?.error }.getOrNull() }
        message ?: "서버 오류가 났어요 (${code()})"
    }
    is IOException -> "서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요"
    else -> localizedMessage ?: "알 수 없는 오류가 났어요"
}

/** 조회 중 404 = 서버가 초기화·재시작돼서 이 callId가 없어짐 → 다시 호출해야 함 */
fun Throwable.isNotFound(): Boolean = this is HttpException && code() == 404

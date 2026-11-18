package com.dalmuri.bus.data.remote

import com.dalmuri.bus.data.model.CallDetail
import com.dalmuri.bus.data.model.CallHistory
import com.dalmuri.bus.data.model.CallRequest
import com.dalmuri.bus.data.model.CallResponse
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.Stop
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * 우리 서버 API (공통 규약 3). 진짜 서버(Retrofit)와 FakeDalmuriApi가 같은 모양을 따른다.
 * 실패하면 예외를 던진다: 400·404 → retrofit2.HttpException, 서버가 꺼짐 → IOException.
 * 사용자에게 보여 줄 문구는 Throwable.toUserMessage() (ApiErrors.kt).
 */
interface DalmuriApi {

    /** 정류장 25개 (앱 첫 실행 때 한 번) */
    @GET("stops")
    suspend fun getStops(): List<Stop>

    /** 지도 정보: 이미지 크기와 좌표 범위 (첫 실행 때 한 번) */
    @GET("map")
    suspend fun getMap(): MapInfo

    /** 지도 이미지 PNG 파일 (약 1.9MB, 첫 실행 때 한 번). @Streaming = 메모리에 한꺼번에 안 올림 */
    @Streaming
    @GET("map/image")
    suspend fun getMapImage(): ResponseBody

    /** 호출 → 배차 ID(callId)를 받는다 */
    @POST("calls")
    suspend fun createCall(@Body body: CallRequest): CallResponse

    /** 내 배차 상태 (F7에서 2초마다) */
    @GET("calls/{callId}")
    suspend fun getCall(@Path("callId") callId: String): CallDetail

    /** 호출 취소 (추가 기능). REQUESTED·ASSIGNED만 되고 나머지는 400 */
    @POST("calls/{callId}/cancel")
    suspend fun cancelCall(@Path("callId") callId: String): CallDetail

    /** 내 이용 내역, 최근 것부터 최대 50건 (추가 기능). 서버 메모리라 재시작·초기화 때 비워짐 */
    @GET("users/{userId}/calls")
    suspend fun getHistory(@Path("userId") userId: String): List<CallHistory>

    companion object {
        /** 진짜 서버용 Retrofit 객체를 만든다 */
        fun create(baseUrl: String): DalmuriApi {
            // 요청 줄과 응답 코드만 Logcat(태그 okhttp.OkHttpClient)에 찍음. BODY로 하면 지도 이미지까지 찍혀서 BASIC
            val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            val client = OkHttpClient.Builder().addInterceptor(logging).build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DalmuriApi::class.java)
        }
    }
}

package com.dalmuri.bus.data.repository

import android.util.Log
import com.dalmuri.bus.data.local.StopDao
import com.dalmuri.bus.data.local.toEntity
import com.dalmuri.bus.data.local.toModel
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.model.StopKind
import com.dalmuri.bus.data.remote.DalmuriApi

/**
 * 정류장 저장소. 교수님: "처음 뜰 때 받고, 있으면 안 받음".
 * Room에 있으면 그것만 쓰고, 없을 때만 서버(GET /stops)에서 받아 Room에 저장한다.
 */
class StopRepository(private val api: DalmuriApi, private val dao: StopDao) {

    suspend fun getStops(): List<Stop> {
        val saved = dao.getAll()
        if (saved.isNotEmpty()) {
            Log.d(TAG, "정류장: Room에 저장된 ${saved.size}개 사용 (서버 안 부름)")
            return saved.map { it.toModel() }.hubFirst()
        }
        val fresh = api.getStops()
        dao.insertAll(fresh.map { it.toEntity() })
        Log.d(TAG, "정류장: 서버에서 ${fresh.size}개 받아 Room에 저장")
        return fresh.hubFirst()
    }

    /** 서버처럼 승차 거점(HUB)이 앞에 오게 (같은 종류 안의 순서는 그대로) */
    private fun List<Stop>.hubFirst() = sortedBy { it.kind != StopKind.HUB }

    private companion object {
        const val TAG = "Repo"
    }
}

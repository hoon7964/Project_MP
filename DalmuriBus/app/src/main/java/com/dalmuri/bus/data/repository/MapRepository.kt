package com.dalmuri.bus.data.repository

import android.content.Context
import android.util.Log
import com.dalmuri.bus.data.local.MapInfoDao
import com.dalmuri.bus.data.local.toEntity
import com.dalmuri.bus.data.local.toModel
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.remote.DalmuriApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 저장된 지도: 좌표 범위 정보 + 앱 안 이미지 파일 */
data class SavedMap(val info: MapInfo, val imageFile: File)

/**
 * 지도 저장소. Room(map_info)에 정보가 있고 이미지 파일도 있으면 그것만 쓰고,
 * 없으면 서버에서 GET /map + GET /map/image를 받아 map_<mapId>.png 파일과 Room에 저장한다.
 */
class MapRepository(
    private val context: Context,
    private val api: DalmuriApi,
    private val dao: MapInfoDao,
) {

    suspend fun getMap(): SavedMap {
        val saved = dao.get()
        if (saved != null) {
            val file = imageFile(saved.mapId)
            if (file.exists()) {
                Log.d(TAG, "지도: Room에 저장된 ${saved.mapId} + ${file.name} 사용 (서버 안 부름)")
                return SavedMap(saved.toModel(), file)
            }
        }

        val info = api.getMap()
        val file = imageFile(info.mapId)
        downloadImage(file)
        // 이미지를 다 받은 다음에 Room에 적는다 → 중간에 끊기면 다음 실행 때 다시 받음
        dao.insert(info.toEntity())
        Log.d(TAG, "지도: 서버에서 ${info.mapId} + 이미지 ${file.length() / 1024}KB 받아 저장")
        return SavedMap(info, file)
    }

    /** 이미지를 임시 파일에 다 받은 뒤 이름을 바꿔서, 반쯤 받은 파일이 남지 않게 한다 */
    private suspend fun downloadImage(target: File) {
        val body = api.getMapImage()
        withContext(Dispatchers.IO) {
            val tmp = File(target.parentFile, target.name + ".part")
            body.use { b ->
                b.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "지도 이미지 저장 실패" }
        }
    }

    private fun imageFile(mapId: String) = File(context.filesDir, "map_$mapId.png")

    private companion object {
        const val TAG = "Repo"
    }
}

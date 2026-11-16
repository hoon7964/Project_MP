package com.dalmuri.bus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.dalmuri.bus.data.model.MapInfo
import com.dalmuri.bus.data.model.Stop
import com.dalmuri.bus.data.model.StopKind

/**
 * Room 테이블. 교수님: "서버와 같은 구조로 Room에 저장".
 * 컬럼 이름 = 서버 JSON 필드 이름 = 데이터 클래스 이름 (공통 규약 4).
 */

/** stops 테이블 한 행 = GET /stops 의 정류장 하나 */
@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey val stopId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val kind: String,               // "HUB" / "HOME" (enum 이름 그대로)
    val nightRoutes: List<String>,  // 저장할 땐 "N26,N72" 문자열 (아래 Converters)
)

/** map_info 테이블 한 행 = GET /map 응답. 이미지는 앱 안 파일 map_<mapId>.png */
@Entity(tableName = "map_info")
data class MapInfoEntity(
    @PrimaryKey val mapId: String,
    val imageUrl: String,
    val widthPx: Int,
    val heightPx: Int,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val attribution: String,
)

/** Room은 List를 바로 못 넣어서 문자열로 바꿔 저장한다: ["N26","N72"] ↔ "N26,N72", [] ↔ "" */
class Converters {
    @TypeConverter
    fun routesToString(routes: List<String>): String = routes.joinToString(",")

    @TypeConverter
    fun stringToRoutes(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")
}

// ── 데이터 클래스 ↔ Room 행 변환 ──

fun Stop.toEntity() = StopEntity(stopId, name, lat, lng, kind.name, nightRoutes)

fun StopEntity.toModel() = Stop(stopId, name, lat, lng, StopKind.valueOf(kind), nightRoutes)

fun MapInfo.toEntity() = MapInfoEntity(mapId, imageUrl, widthPx, heightPx, north, south, west, east, attribution)

fun MapInfoEntity.toModel() = MapInfo(mapId, imageUrl, widthPx, heightPx, north, south, west, east, attribution)

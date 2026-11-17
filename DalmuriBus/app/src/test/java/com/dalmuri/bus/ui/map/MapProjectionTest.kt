package com.dalmuri.bus.ui.map

import com.dalmuri.bus.data.model.MapInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 좌표 변환식이 맞는지: 지도 모서리 좌표가 이미지 모서리로 가야 한다 (ROLE_FRONT.md F5) */
class MapProjectionTest {

    // server/data/map.json 실제 값
    private val map = MapInfo(
        mapId = "bonghwasan-v1", imageUrl = "/map/image", widthPx = 2400, heightPx = 2306,
        north = 37.62, south = 37.5945, west = 127.0725, east = 127.106,
        attribution = "© OpenStreetMap contributors",
    )

    @Test
    fun 북서쪽_모서리는_이미지_왼쪽_위() {
        val p = MapProjection.toPixel(map.north, map.west, map)
        assertEquals(0.0, p.x, 1e-6)
        assertEquals(0.0, p.y, 1e-6)
    }

    @Test
    fun 남동쪽_모서리는_이미지_오른쪽_아래() {
        val p = MapProjection.toPixel(map.south, map.east, map)
        assertEquals(2400.0, p.x, 1e-6)
        assertEquals(2306.0, p.y, 1e-6)
    }

    @Test
    fun 정류장은_이미지_안에_찍힌다() {
        // 07003 상봉역.중랑우체국
        val p = MapProjection.toPixel(37.5959591, 127.0837436, map)
        assertTrue(p.x in 0.0..2400.0)
        assertTrue(p.y in 0.0..2306.0)
    }
}

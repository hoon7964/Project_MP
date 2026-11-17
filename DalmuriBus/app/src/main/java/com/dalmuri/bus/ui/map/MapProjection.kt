package com.dalmuri.bus.ui.map

import com.dalmuri.bus.data.model.MapInfo
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * 위도·경도 → 지도 이미지 픽셀 (공통 규약 5, 웹 지도와 같은 메르카토르 투영).
 *   x = (lng - west) / (east - west) * widthPx
 *   m(lat) = ln(tan(π/4 + lat·π/360))
 *   y = (m(north) - m(lat)) / (m(north) - m(south)) * heightPx
 * 안드로이드 코드를 안 써서 단위 테스트(MapProjectionTest)로 바로 확인할 수 있다.
 */
object MapProjection {

    data class Pixel(val x: Double, val y: Double)

    fun toPixel(lat: Double, lng: Double, map: MapInfo): Pixel {
        val x = (lng - map.west) / (map.east - map.west) * map.widthPx
        val y = (mercator(map.north) - mercator(lat)) / (mercator(map.north) - mercator(map.south)) * map.heightPx
        return Pixel(x, y)
    }

    private fun mercator(lat: Double): Double = ln(tan(PI / 4 + lat * PI / 360))
}

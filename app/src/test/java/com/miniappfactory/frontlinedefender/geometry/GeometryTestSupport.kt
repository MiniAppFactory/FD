package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Saf geometri yardimcilari — testler icin.
 *
 * TASARIM NOTU: bu dosya `src/main` icindeki HICBIR imzaya bagli degil;
 * yalnizca `PointF` veri sinifini ve `GameConfig.REFERENCE_*` sabitlerini okur.
 * Boylece motor/UI refactor edildiginde bu testler kirilmaz.
 *
 * Tum mesafeler **1920x1080 referans tuvalinde** (DECISIONS §B3) hesaplanir:
 * normalize koordinat * REFERENCE_WIDTH / REFERENCE_HEIGHT.
 */
internal object GeometryTestSupport {

    val refW: Float get() = GameConfig.REFERENCE_WIDTH
    val refH: Float get() = GameConfig.REFERENCE_HEIGHT

    /** Normalize noktayi referans tuval piksellerine tasir. */
    fun toRef(p: PointF): PointF = PointF(p.x * refW, p.y * refH)

    /** Bir noktanin [a,b] dogru PARCASINA (sonsuz dogruya degil) uzakligi. */
    fun pointToSegment(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = min(1f, max(0f, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2))
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Bir noktanin polylinenin en yakin yerine uzakligi (referans px). */
    fun pointToPolyline(pRef: PointF, routeNorm: List<PointF>): Float {
        require(routeNorm.size >= 2) { "rota en az 2 nokta icermeli" }
        val r = routeNorm.map { toRef(it) }
        var best = Float.MAX_VALUE
        for (i in 0 until r.size - 1) {
            val d = pointToSegment(pRef, r[i], r[i + 1])
            if (d < best) best = d
        }
        return best
    }

    /** Bir pad'in, o haritadaki TUM rotalar arasinda en yakin olanina uzakligi. */
    fun padToNearestRoute(padNormX: Float, padNormY: Float, routes: List<List<PointF>>): Float {
        val p = PointF(padNormX * refW, padNormY * refH)
        return routes.minOf { pointToPolyline(p, it) }
    }

    /** Polylinenin referans px cinsinden toplam uzunlugu. */
    fun polylineLength(routeNorm: List<PointF>): Float {
        val r = routeNorm.map { toRef(it) }
        var sum = 0f
        for (i in 0 until r.size - 1) sum += hypot(r[i + 1].x - r[i].x, r[i + 1].y - r[i].y)
        return sum
    }

    /** Oyundaki EN UZUN kule menzili (kademe 2 dahil), referans px. */
    fun maxTowerRange(): Float =
        GameConfig.TOWER_SPECS.values.maxOf { max(it.level1Range, it.level2Range) }

    /** En kisa L1 menzili — "en erisilebilir olmasi gereken" kule. */
    fun minLevel1Range(): Float = GameConfig.TOWER_SPECS.values.minOf { it.level1Range }
}

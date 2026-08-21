package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * ARAC (assert YOK) — cime basan rota noktalarini YOLA oturtur ve duzeltilmis
 * `PointF` satirlarini Kotlin kaynagi olarak basar.
 *
 * ## Neden gerekti
 * Cihaz raporu: *"haritada hala yolu takip etmeyen rotalar var, bu level 3
 * ornegin."* Olcum dogruladi: 16 rotanin BESI cime basiyor (harita 3 A %1,48 /
 * B %0,90 · harita 4 A %1,42 / B %1,15 · harita 10 %0,35).
 *
 * ## CLASS_OTHER kusur DEGIL
 * Maske ucuncu sinifi kopru, su, kaya, us rampasi ve spawn platformunu birlikte
 * tasiyor. Harita 4'un golu ustunden IKI AHSAP KOPRU geciyor ve rota oradan
 * gecmek ZORUNDA; harita 6 ve 10'daki "diger" payi da rampalardan geliyor
 * (ikisinde cim payi zaten sifir). Bu yuzden hedef "diger'i sifirlamak" degil,
 * **CIMI sifirlamak**.
 *
 * ## Yontem
 * Rota ince orneklenir; cime dusen ornekler bulunur. Uc noktalar (bunker yol
 * agzi / us rampasi) SABITTIR — olculerek konmuslardi ve oynatilirsa dusman
 * yine kapidan girmemis gorunur. Aradaki her nokta icin kucuk bir disk
 * taranir, komsu iki segmentteki cim ornegini en aza indiren kaydirma secilir,
 * esitlikte EN KUCUK kaydirma kazanir — rota sekli korunsun diye.
 */
class RouteSnapToRoadTool {

    private companion object {
        const val REF_W = 1920f
        const val REF_H = 1080f

        /** Ornekleme adimi, ref-px. Zemin denetimiyle ayni mertebe. */
        // ⚠ TESTIN ORNEKLEMESIYLE AYNI OLMAK ZORUNDA (RouteStaysOnRoadTest
        // sampleStepRefPx = 4f, ornekler `1..steps`). Farkli orneklerse arac
        // "cim yok" der, test "cim var" der ve ikisi de kendince hakli olur —
        // ilk kosuda tam bu oldu.
        const val SAMPLE_STEP_REF = 4f

        /** Tarama yaricapi ve adimi, ref-px. */
        const val SEARCH_RADIUS_REF = 34f
        const val SEARCH_STEP_REF = 2f

        const val PASSES = 6

        /**
         * Segment tavani, ref-px. `RouteStaysOnRoadTest` 35'i kilitliyor:
         * kiris ne kadar uzunsa yaydan o kadar sapar ve rota yolun disina
         * TASAR — duzeltmeye calistigimiz hatanin ta kendisi. Yola oturtmak
         * noktalari kaydirdigi icin segmentler uzayabiliyor; o yuzden
         * oturtmadan SONRA yeniden yogunlastirmak sart.
         */
        const val MAX_SEGMENT_REF = 32f
    }

    private fun sampleCount(a: PointF, b: PointF): Int {
        val dx = (b.x - a.x) * REF_W
        val dy = (b.y - a.y) * REF_H
        return maxOf(1, (hypot(dx, dy) / SAMPLE_STEP_REF).roundToInt())
    }

    /** Bir segmentteki CIM ornegi sayisi. */
    private fun grassOn(mask: MapMaskFixture.Mask, a: PointF, b: PointF): Int {
        val n = sampleCount(a, b)
        var g = 0
        for (i in 1..n) {
            val t = i.toFloat() / n
            val x = a.x + (b.x - a.x) * t
            val y = a.y + (b.y - a.y) * t
            if (mask.classAt(x, y) == MapMaskFixture.CLASS_VEGETATION) g++
        }
        return g
    }

    private fun grassTotal(mask: MapMaskFixture.Mask, route: List<PointF>): Int =
        (1 until route.size).sumOf { grassOn(mask, route[it - 1], route[it]) }

    private fun neighbourGrass(mask: MapMaskFixture.Mask, r: List<PointF>, i: Int): Int {
        var g = 0
        if (i > 0) g += grassOn(mask, r[i - 1], r[i])
        if (i < r.size - 1) g += grassOn(mask, r[i], r[i + 1])
        return g
    }

    /** 32 ref-px'i asan her segmente orta nokta ekler (tek gecis). */
    private fun densify(route: List<PointF>): MutableList<PointF> {
        val out = mutableListOf(route.first())
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            val len = hypot((b.x - a.x) * REF_W, (b.y - a.y) * REF_H)
            val cuts = Math.ceil((len / MAX_SEGMENT_REF).toDouble()).toInt()
            for (k in 1 until cuts) {
                val t = k.toFloat() / cuts
                out += PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            out += b
        }
        return out
    }

    /**
     * UC NOKTA CIMDEYSE en kucuk kaydirmayla kurtarilir.
     *
     * Uc noktalar kural olarak SABIT (olculmus kapi agzi) ve bu dogru bir
     * kural — oynatilirsa dusman bunkerden/rampadan girmemis gorunur, ki o
     * hata bu depoda zaten yasandi. AMA harita 10'un us ucu MASKEDE CIM
     * pikseline dusuyor ve test uc noktayi da orneklediginden hicbir ara nokta
     * bunu duzeltemez: kalan tek cim ornegi tam oradaydi.
     *
     * Bu yuzden istisna DAR: en fazla 8 ref-px, yalnizca nokta GERCEKTEN
     * cimdeyse, ve kapi agzi bandini (x 0,05-0,30 / 0,70-0,95) koruyacak
     * kadar kucuk. Rampanin genisligi bunun kat kat ustunde.
     */
    private fun rescueEndpoint(mask: MapMaskFixture.Mask, p: PointF): PointF {
        if (mask.classAt(p.x, p.y) != MapMaskFixture.CLASS_VEGETATION) return p
        var off = 1f
        while (off <= 8f) {
            for (k in 0 until 24) {
                val ang = 2.0 * Math.PI * k / 24
                val c = PointF(
                    p.x + (Math.cos(ang).toFloat() * off) / REF_W,
                    p.y + (Math.sin(ang).toFloat() * off) / REF_H
                )
                if (c.x in 0f..1f && c.y in 0f..1f &&
                    mask.classAt(c.x, c.y) != MapMaskFixture.CLASS_VEGETATION
                ) return c
            }
            off += 1f
        }
        return p
    }

    private fun snap(mapId: Int, route: List<PointF>): List<PointF> {
        val mask = MapMaskFixture.maskFor(mapId)
        var work = route.toMutableList()
        work[0] = rescueEndpoint(mask, work[0])
        work[work.size - 1] = rescueEndpoint(mask, work[work.size - 1])
        // OTURT -> YOGUNLASTIR -> TEKRAR OTURT. Yogunlastirma hem segment
        // tavanini geri getirir hem de inatci noktalara YENI komsu vererek
        // arama alani acar (harita 10'da tek kalan cim ornegi boyle cozuluyor).
        // Her gecis YOGUNLASTIR sonra OTURT sirasiyla ilerler ve OTURTMAYLA
        // biter. Onceki hali son yogunlastirmayi oturtmadan birakiyordu: yeni
        // eklenen orta noktalar cime dusebiliyordu (harita 10'da tek kalan
        // ornek buydu).
        repeat(PASSES) { pass ->
            if (pass > 0) work = densify(work)
            for (i in 1 until work.size - 1) {
                var bestG = neighbourGrass(mask, work, i)
                if (bestG == 0) continue
                var bestP = work[i]
                var bestD = 0f
                var off = SEARCH_STEP_REF
                while (off <= SEARCH_RADIUS_REF) {
                    val steps = maxOf(8, (off * 2).toInt())
                    for (k in 0 until steps) {
                        val ang = 2.0 * Math.PI * k / steps
                        val cand = PointF(
                            work[i].x + (Math.cos(ang).toFloat() * off) / REF_W,
                            work[i].y + (Math.sin(ang).toFloat() * off) / REF_H
                        )
                        if (cand.x !in 0f..1f || cand.y !in 0f..1f) continue
                        if (mask.classAt(cand.x, cand.y) == MapMaskFixture.CLASS_VEGETATION) continue
                        val old = work[i]
                        work[i] = cand
                        val g = neighbourGrass(mask, work, i)
                        work[i] = old
                        if (g < bestG || (g == bestG && bestD > 0f && off < bestD)) {
                            bestG = g; bestP = cand; bestD = off
                        }
                    }
                    if (bestG == 0) break
                    off += SEARCH_STEP_REF
                }
                work[i] = bestP
            }
        }
        return densify(work)
    }

    /** Noktadan polyline'a en kisa uzaklik, ref-px. */
    private fun distToPolyline(p: PointF, poly: List<PointF>): Float {
        var best = Float.MAX_VALUE
        for (i in 1 until poly.size) {
            val ax = poly[i - 1].x * REF_W; val ay = poly[i - 1].y * REF_H
            val bx = poly[i].x * REF_W; val by = poly[i].y * REF_H
            val px = p.x * REF_W; val py = p.y * REF_H
            val vx = bx - ax; val vy = by - ay
            val len2 = vx * vx + vy * vy
            val t = if (len2 == 0f) 0f else
                (((px - ax) * vx + (py - ay) * vy) / len2).coerceIn(0f, 1f)
            val d = hypot(ax + t * vx - px, ay + t * vy - py)
            if (d < best) best = d
        }
        return best
    }

    @Test
    fun emitSnappedRoutesForGrassTouchingMaps() {
        val targets = listOf(
            Triple("MAP_03.waypoints", 3, LevelGeometry.ALL_MAPS.first { it.levelId == 3 }.waypoints),
            Triple("ALT_ROUTES[3]", 3, LevelGeometry.ALT_ROUTES.getValue(3)),
            Triple("MAP_04.waypoints", 4, LevelGeometry.ALL_MAPS.first { it.levelId == 4 }.waypoints),
            Triple("ALT_ROUTES[4]", 4, LevelGeometry.ALT_ROUTES.getValue(4)),
            Triple("MAP_10.waypoints", 10, LevelGeometry.ALL_MAPS.first { it.levelId == 10 }.waypoints),
        )
        println()
        targets.forEach { (label, mapId, route) ->
            val mask = MapMaskFixture.maskFor(mapId)
            val before = grassTotal(mask, route)
            val snapped = snap(mapId, route)
            val after = grassTotal(mask, snapped)
            // KAYDIRMA, INDEKS ESLESTIRMESIYLE OLCULEMEZ: yogunlastirma yeni
            // noktalar ekliyor, yani iki dizi ayni uzunlukta degil. Dogru olcu,
            // her ESKI noktanin YENI POLILINE olan uzakligi — rotanin sekil
            // olarak ne kadar kaydiginin gercek karsiligi.
            val maxShift = route.maxOf { old -> distToPolyline(old, snapped) }
            val maxSeg = (1 until snapped.size).maxOf {
                hypot(
                    (snapped[it].x - snapped[it - 1].x) * REF_W,
                    (snapped[it].y - snapped[it - 1].y) * REF_H
                )
            }
            println("### $label  cim ornegi $before -> $after  |  en buyuk kaydirma ${"%.1f".format(maxShift)} ref-px  |  en uzun segment ${"%.1f".format(maxSeg)} ref-px")
            // KALAN CIM ORNEKLERINI ISARETLE — nerede takildigini gormeden
            // "kabul edilebilir" demek, hatayi kaydetmekten farksiz olur.
            for (i in 1 until snapped.size) {
                if (grassOn(mask, snapped[i - 1], snapped[i]) > 0) {
                    println("            // ⚠ KALAN CIM: segment ${i - 1}-$i  " +
                        "(${"%.4f".format(snapped[i - 1].x)}, ${"%.4f".format(snapped[i - 1].y)}) -> " +
                        "(${"%.4f".format(snapped[i].x)}, ${"%.4f".format(snapped[i].y)})  " +
                        "uc nokta mi: ${i - 1 == 0 || i == snapped.size - 1}")
                }
            }
            println("            // nokta sayisi ${route.size} -> ${snapped.size}")
            snapped.forEach { p ->
                println("            PointF(${"%.4f".format(p.x)}f, ${"%.4f".format(p.y)}f),")
            }
            println()
        }
    }
}

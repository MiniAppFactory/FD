package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Saf geometri yardimcilari — testler icin.
 *
 * TASARIM NOTU: bu dosya motor/UI'a bagli DEGILDIR; yalnizca veri katmanini
 * okur (`PointF`, `LevelData.routesForMapId`, `GameConfig` sabit/tablolari).
 * Boylece motor/UI refactor edildiginde bu testler kirilmaz. Rota secim
 * KURALI ise bilincli olarak `GameEngine.loadLevel` ile aynidir (bkz.
 * [activeRoutesFor]) — testin oynanistan farkli bir dunya olcmesini onler.
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

    /**
     * Bir pad'in, VERILEN rota kumesindeki en yakin rotaya uzakligi.
     *
     * DIKKAT — bu fonksiyon "harita geometrisi" sorusunu cevaplar, "oyuncu bu
     * pad'i kullanabilir mi" sorusunu DEGIL. Cagiran, rota kumesini kendisi
     * secmek zorundadir. Oynanis sorusu icin [padToActiveRoute] kullan.
     */
    fun padToNearestOfRoutes(padNormX: Float, padNormY: Float, routes: List<List<PointF>>): Float {
        val p = PointF(padNormX * refW, padNormY * refH)
        return routes.minOf { pointToPolyline(p, it) }
    }

    /**
     * O BOLUMDE motorun gercekten kullandigi rotalar.
     *
     * `GameEngine.loadLevel` ile birebir ayni kural:
     *   `routes = if (usesAlternateRoutes(levelId)) allRoutes else listOf(allRoutes.first())`
     * `LevelData.routesForMapId` zaten harita 3'te kanonik kolu (`ALT_ROUTES[3]`)
     * ilk sirada dondurur, harita 1/2/4/11'de ise [birincil, ikinci] dondurur.
     */
    fun activeRoutesFor(mapId: Int, levelId: Int): List<List<PointF>> {
        val all = LevelData.routesForMapId(mapId)
        return if (GameConfig.usesAlternateRoutes(levelId)) all else listOf(all.first())
    }

    /**
     * Bir pad'in, o BOLUMDE AKTIF olan rotalardan en yakinina uzakligi.
     *
     * NEDEN AYRI BIR FONKSIYON: bolum < `ALT_ROUTE_FIRST_LEVEL` iken ikinci kol
     * motorda HIC kullanilmaz. Tum rotalarin minimumuna bakmak pad'i olmadigi
     * kadar iyi gosterir — harita 1 pad 3 icin ikinci kolun 116'sini alir,
     * bolum 1'de gecerli olan 444'u hic gormez. "Olu build pad" hatasinin 22
     * bolum boyunca testten kacmasinin sebebi tam olarak buydu
     * (docs/PAD_COVERAGE_REPORT.md 5. bolum).
     */
    fun padToActiveRoute(padNormX: Float, padNormY: Float, mapId: Int, levelId: Int): Float =
        padToNearestOfRoutes(padNormX, padNormY, activeRoutesFor(mapId, levelId))

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

    /**
     * Bu BOLUMDE oyuncunun erisebildigi EN UZUN menzil, ref-px.
     *
     * Kademe 1, cunku oyuncu kuleyi once KURAR sonra yukseltir: kademe-2
     * menziliyle olcmek, henuz odenmemis bir yukseltmeyi bugunun gecerliligi
     * saymaktir. `GameConfig`ten TURETILIR — sabit yazilmaz.
     */
    fun maxUnlockedLevel1Range(levelId: Int): Float =
        GameConfig.unlockedTowers(levelId)
            .mapNotNull { GameConfig.TOWER_SPECS[it]?.level1Range }
            .max()

    /**
     * Bu bolumde HASAR VEREN (destek disi) kulelerin en uzun kademe-1 menzili.
     * SLOW/Frost Field haric: o kule yalnizca 3 hasar verir, yani yalniz ona
     * erisen bir pad "calisiyor" sayilmaz, "yalniz-destek" sayilir.
     */
    fun maxUnlockedDamagingLevel1Range(levelId: Int): Float =
        GameConfig.unlockedTowers(levelId)
            .filter { it != GameConfig.TowerType.SLOW }
            .mapNotNull { GameConfig.TOWER_SPECS[it]?.level1Range }
            .max()
}

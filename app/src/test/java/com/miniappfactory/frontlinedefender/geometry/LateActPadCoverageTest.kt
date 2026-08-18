package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * ===========================================================================
 * PERDE III/IV/V KRATER KISITI — "kapattigimiz pad'ler yolu savunmasiz birakti mi?"
 * ===========================================================================
 *
 * `GameConfig.LATE_ACT_DISABLED_PADS` iki isi birden yapiyor: **olu** pad'leri
 * gizliyor (K-6, tuzagi kaldirir) ve calisan pad'leri bilerek kapatarak her
 * geciste farkli bir bulmaca kuruyor (krater). Ikincisi tehlikeli tarafi:
 * yanlis uc pad kapatildiginda yolun bir bolumu **hicbir kuleden erisilemez**
 * hale gelir ve bolum, oyuncunun gorebilecegi bir sebep olmadan gecilemez olur.
 *
 * Bu test `LEVEL_DESIGN.md` F.3'un D4 kisitini olcer: **900 ref-px'den uzun,
 * kapsamasiz yol parcasi olamaz.** Olcum kaynak veriden (LevelGeometry +
 * TOWER_SPECS) CANLI turetilir; sabit menzil ya da sabit mesafe yazili degildir.
 *
 * KAPSAM: yalnizca L23..L55. Act I'in gizlemesi menzil disi pad'lerle sinirli
 * (`OUT_OF_RANGE_PADS`), Act II'nin krater listesi ise kendi D1-D6 denetiminden
 * gecmis ve DONDURULMUS durumda (`CampaignTableTest`); onlari burada yeniden
 * yargilamak yayinlanmis icerigi riske atmaktan baska bir sey yapmaz.
 */
class LateActPadCoverageTest {

    /** ~10 ref-px adimlarla ornekli aktif rota; her ornek yay uzunlugu ile. */
    private fun sampleActiveRoutes(mapId: Int, levelId: Int): List<List<Pair<Float, PointF>>> =
        GeometryTestSupport.activeRoutesFor(mapId, levelId).map { route ->
            val out = ArrayList<Pair<Float, PointF>>()
            val ref = route.map { GeometryTestSupport.toRef(it) }
            var acc = 0f
            out.add(0f to ref.first())
            for (i in 0 until ref.size - 1) {
                val seg = hypot(ref[i + 1].x - ref[i].x, ref[i + 1].y - ref[i].y)
                val steps = maxOf(1, (seg / 10f).toInt())
                for (k in 1..steps) {
                    val t = k.toFloat() / steps
                    out.add(
                        acc + seg * t to PointF(
                            ref[i].x + (ref[i + 1].x - ref[i].x) * t,
                            ref[i].y + (ref[i + 1].y - ref[i].y) * t,
                        )
                    )
                }
                acc += seg
            }
            out
        }

    /**
     * D4 — kapsamasiz yol parcasi <= 900 ref-px.
     *
     * "Kapsama" = o bolumde GORUNUR bir pad'den, o bolumde kilidi acik HASAR
     * VEREN kulelerin en buyuk kademe-1 menzili icinde olmak. Kademe 1, cunku
     * oyuncu kuleyi once kurar sonra yukseltir; kademe-2 menziliyle olcmek
     * henuz odenmemis bir yukseltmeyi bugunun gecerliligi saymaktir. Frost
     * Field HARIC, cunku 3 hasar veren bir kule yolu "savunuyor" sayilmaz.
     */
    @Test
    fun noStretchOfRoadIsLeftUncoveredByTheCraterConstraint() {
        val failures = mutableListOf<String>()
        for (level in 23..55) {
            val spec = GameConfig.levelSpec(level)
            val range = GeometryTestSupport.maxUnlockedDamagingLevel1Range(level)
            val pads = LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id !in spec.disabledPadIds }
                .map {
                    PointF(
                        it.normX * GameConfig.REFERENCE_WIDTH,
                        it.normY * GameConfig.REFERENCE_HEIGHT,
                    )
                }
            sampleActiveRoutes(spec.mapId, level).forEachIndexed { routeIdx, samples ->
                var gapStart: Float? = null
                var worstGap = 0f
                var worstAt = 0f
                samples.forEach { (arc, p) ->
                    val covered = pads.any { hypot(p.x - it.x, p.y - it.y) <= range }
                    if (covered) {
                        gapStart?.let { start ->
                            if (arc - start > worstGap) { worstGap = arc - start; worstAt = start }
                        }
                        gapStart = null
                    } else if (gapStart == null) {
                        gapStart = arc
                    }
                }
                gapStart?.let { start ->
                    val end = samples.last().first
                    if (end - start > worstGap) { worstGap = end - start; worstAt = start }
                }
                if (worstGap > 900f) {
                    failures += "L$level (harita ${spec.mapId}, rota $routeIdx): " +
                        "${worstGap.toInt()} ref-px kapsamasiz parca, yay ${worstAt.toInt()}'den " +
                        "itibaren (menzil ${range.toInt()})"
                }
            }
        }
        assertTrue(
            "KRATER KISITI YOLU SAVUNMASIZ BIRAKTI (D4: kapsamasiz parca <= 900 ref-px).\n" +
                "Duzeltme: GameConfig.LATE_ACT_DISABLED_PADS icinde o bolgeye bakan bir " +
                "pad'i geri ac; acik pad SAYISI degisirse CampaignTableTest de guncellenmeli.\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /**
     * Kapatilan pad'lerin en az bir kismi GERCEKTEN calisan pad olmali.
     *
     * Aksi halde "krater kisiti" diye bir sey yok, yalnizca olu pad temizligi
     * var demektir — ve o zaman ayni harita bes geciste bes kez AYNI tahta
     * olur. CAMPAIGN_55.md 6.1'in 3. ekseni (kullanilabilir pad kumesi) tam
     * olarak bunu istiyor.
     */
    @Test
    fun theCraterConstraintActuallyClosesWorkingPadsNotJustDeadOnes() {
        var levelsWithRealCraters = 0
        for (level in 23..55) {
            val spec = GameConfig.levelSpec(level)
            val range = GeometryTestSupport.maxUnlockedLevel1Range(level)
            val workingClosed = LevelData.forMapId(spec.mapId).buildSpots
                .filter { it.id in spec.disabledPadIds }
                .count {
                    GeometryTestSupport.padToActiveRoute(
                        it.normX, it.normY, spec.mapId, level,
                    ) <= range
                }
            if (workingClosed > 0) levelsWithRealCraters++
        }
        assertTrue(
            "33 bolumun yalnizca $levelsWithRealCraters'inde gercek krater var — " +
                "tahta bes geciste degismiyorsa tekrar hissi kacinilmaz",
            levelsWithRealCraters >= 20,
        )
    }

    /**
     * AYNI HARITANIN IKI GECISI AYNI TAHTAYI VERMEMELI.
     *
     * Zorunlu istisna: harita 08'in olu pad kumesi {6, 8, 11} ve o haritanin
     * her geciste 8 acik pad'i var, yani gizlenecek kume TAM OLARAK olu kume.
     * Ayni sey harita 06/07 icin Act IV-V'te gecerli. Bunlar geometriden gelen
     * zorunluluklardir; testin isi onlarin SAYISINI pinlemek.
     */
    @Test
    fun repeatedMapsMostlyPresentADifferentBoardEachTime() {
        val identicalPairs = mutableListOf<String>()
        val byMap = (23..55).groupBy { GameConfig.levelSpec(it).mapId }
        byMap.forEach { (mapId, levels) ->
            for (i in levels.indices) {
                for (j in i + 1 until levels.size) {
                    val a = GameConfig.levelSpec(levels[i]).disabledPadIds.toSet()
                    val b = GameConfig.levelSpec(levels[j]).disabledPadIds.toSet()
                    if (a == b) identicalPairs += "harita $mapId: L${levels[i]} == L${levels[j]} ($a)"
                }
            }
        }
        assertEquals(
            "ayni tahtayi tekrar eden gecis sayisi degisti — geometrinin zorladigi " +
                "durumlar disinda her gecis farkli bir bulmaca olmali:\n" +
                identicalPairs.joinToString("\n"),
            5, identicalPairs.size,
        )
    }
}

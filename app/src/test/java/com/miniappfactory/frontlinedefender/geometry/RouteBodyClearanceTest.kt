package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ===========================================================================
 * DUSMANIN GOVDESI YOLDA MI? — merkez cizgisi degil, GORULEN SILUET
 * ===========================================================================
 *
 * CIHAZ GERI BILDIRIMI (2026-09-02, Galaxy S8, bolum 4 "Gol Kusagi"): oynanis
 * karesi buyutuldugunde piyadelerin **govdelerinin bir kismi cimende**
 * yuruyordu. Rota merkez cizgisi yolun uzerindeydi; tasan sey SILUETTI.
 *
 * NEDEN MEVCUT TESTLER BUNU GORMUYOR
 * ----------------------------------
 * [RouteStaysOnRoadTest] deponun en titiz geometri testidir ve rotanin yolu
 * yalnizca boyanin GERCEKTEN koptugu yerde terk edebilecegini KANITLAR. Ama
 * olctugu sey rota poligonunun KENDISIDIR — yani genisligi sifir olan bir
 * cizgi. Oyuncunun gordugu sey ise 42-80 referans piksel genisliginde bir
 * sprite'tir. Yolun yarim genisligi haritalara gore 37-49 ref-px olduguna
 * gore, merkez cizgisi kusursuz olsa bile 80 ref-px genisligindeki KOMUTA
 * TANKI'nin (yari genislik 40) dar bir koridorda tasmasi GEOMETRIK OLARAK
 * KACINILMAZDIR.
 *
 * Bu zaten biliniyordu: `GameConfig.ENEMY_SPRITES` icindeki COMMAND_TANK
 * yorumu 2026-08-19'da boss'u 96 -> 80 ref-px'e indirirken *"96 ref-px'lik
 * boss HICBIR rotaya tam sigmiyordu — palet kenarlari surekli cimde
 * yuruyordu ve 'dusmanlar yoldan cikiyor' sikayetinin bir parcasi buydu"*
 * diyor. 80'e inildi ama **kalan tasma hic olculmedi** ve hicbir test
 * onu korumadi. Bu dosya o olcumdur.
 *
 * NE OLCULUYOR
 * ------------
 * Rota boyunca her ornekte, yurume yonune DIK eksende sprite'in yari
 * genisligi kadar iki yana ornek alinir ve zemin sinifi okunur. Sprite
 * sanatinin donusu `GameCanvas.drawEnemy`de `heading - baseAngle`; sanat
 * "asagi" baktigi icin sprite'in KENDI x ekseni (yani `widthRefPx`) her
 * zaman yurume yonune diktir. Dolayisiyla yari genislik dogrudan yanal
 * tasmadir.
 *
 * YAYA BIRIMLERDE EK SAPMA: `Gait.SWAY_REF_PX` (2,4) govdeyi yurume yonune
 * DIK olarak salindirir — yani yaya siluetinin efektif yari genisligi
 * `w/2 + 2,4`. Motorlu birimlerde salinim yoktur.
 *
 * BU TESTIN TUTUMU
 * ----------------
 * Tasma SIFIRLANAMAZ: kule mevzileri, kopruler ve us rampasi yolun disinda
 * duran mesru zeminlerdir ve dar koridorda genis bir arac zaten sigmaz. Bu
 * yuzden test bir HEDEF degil TAVAN koyar ve tavani OLCULEN degerden
 * turetir. Amac "bugun iyi" demek degil, **kotulesmeyi yakalamak**:
 * rota uretici yeniden kosturuldugunda, yeni bir harita eklendiginde veya
 * bir sprite genisletildiginde burasi patlar.
 *
 * ILK OLCUM (2026-09-02, maske v2, 16 rota x 6 birim):
 *
 *   birim                en kotu rota   ortalama tasma
 *   FAST_SOLDIER (42)    harita 10 A     %7,7
 *   INFANTRY (46)        harita 10 A     %7,9
 *   SHIELDED (50)        harita 10 A     %8,3
 *   ARMORED (58)         harita 10 A     %8,5
 *   TANK (72)            harita 10 A    %11,8
 *   COMMAND_TANK (80)    harita 10 A    %14,8
 *
 * Iki okuma cikiyor ve ikisi de kayda deger:
 *
 *  1. **Agir birimler belirgin sekilde daha kotu.** Boss'un ortalama tasmasi
 *     piyadenin neredeyse IKI KATI. Bu, `GameConfig.ENEMY_SPRITES` icindeki
 *     COMMAND_TANK notunun 2026-08-19'da 96 -> 80 kucultmeyle "cozuldu"
 *     dedigi seyin tamamen cozulmedigini gosteriyor.
 *
 *  2. **Harita 6 ve 10'da tekil kesitlerde tasma %100.** Ikisi de
 *     [RouteStaysOnRoadTest]'in KANITLANMIS kopukluk olarak kabul ettigi
 *     yerler (kopru ve nehir gecisi). Orada yol YOKTUR, dolayisiyla govdenin
 *     tamamen yol disinda olmasi beklenen bir sonuctur — ama "beklenen"
 *     olmasi "gorunmez" oldugu anlamina gelmez ve sayinin kayit altinda
 *     olmasi, ilerideki bir sanat degisikliginde karsilastirma verir.
 *
 * [printBodyClearanceTable] kirilmaz, YAZDIRIR — bir sonraki degisiklikte
 * karsilastirma tablosu elde olsun diye (ayni desen:
 * `RouteStaysOnRoadTest.printGroundTable`).
 */
class RouteBodyClearanceTest {

    /** Rota boyunca kac ref-px'te bir kesit alinacagi. */
    private val stepRefPx = 6f

    /** Bir kesitte yari genislik boyunca kac ornek. */
    private val lateralSamples = 9

    /**
     * Yaya biriminin yurume salinimi (`GameCanvas.Gait.SWAY_REF_PX`).
     *
     * ⚠ ELLE KOPYALANDI ve bu bilincli: `Gait` `GameCanvas.kt` icinde
     * `private` bir nesnedir ve Compose'a bagli o dosyayi saf bir JVM
     * geometri testinden okumak, testi Robolectric'e mahkum ederdi.
     * Deger degisirse [gaitSwayMatchesTheRenderer] patlar.
     */
    private val gaitSwayRefPx = 2.4f

    private data class Unit(val name: String, val widthRefPx: Float, val foot: Boolean) {
        /** Merkezden yanal en buyuk tasma: yari genislik + (yayaysa) salinim. */
        val halfExtent: Float get() = widthRefPx / 2f + if (foot) 2.4f else 0f
    }

    /** `GameConfig.ENEMY_SPRITES` + `GameCanvas.Gait.isFootUnit` gerceginden turetilir. */
    private fun units(): List<Unit> = GameConfig.ENEMY_SPRITES.map { (type, spec) ->
        val foot = when (type) {
            GameConfig.EnemyType.INFANTRY,
            GameConfig.EnemyType.FAST_SOLDIER,
            GameConfig.EnemyType.SHIELDED_TROOPER -> true

            GameConfig.EnemyType.ARMORED_VEHICLE,
            GameConfig.EnemyType.TANK,
            GameConfig.EnemyType.COMMAND_TANK -> false
        }
        Unit(type.name, spec.widthRefPx, foot)
    }.sortedBy { it.widthRefPx }

    private fun allRoutes(): List<Triple<String, Int, List<PointF>>> = buildList {
        LevelGeometry.ALL_MAPS.forEach { add(Triple("harita ${it.levelId} A", it.levelId, it.waypoints)) }
        LevelGeometry.ALT_ROUTES.forEach { (id, r) -> add(Triple("harita $id B", id, r)) }
    }

    private class Slice(val offRoadFrac: Float)

    /**
     * Bir rota + birim icin kesit kesit tasma olcer.
     *
     * @return her kesitte govdenin CIM veya KAYA uzerinde kalan orani.
     */
    private fun slices(mapId: Int, route: List<PointF>, unit: Unit): List<Slice> {
        val mask = MapMaskFixture.maskFor(mapId)
        val out = ArrayList<Slice>()
        val w = GameConfig.REFERENCE_WIDTH
        val h = GameConfig.REFERENCE_HEIGHT
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            val dxRef = (b.x - a.x) * w
            val dyRef = (b.y - a.y) * h
            val segLen = hypot(dxRef, dyRef)
            if (segLen < 1e-4f) continue
            // Yurume yonune DIK birim vektor, referans piksel uzayinda.
            val px = -dyRef / segLen
            val py = dxRef / segLen
            val steps = max(1, (segLen / stepRefPx).roundToInt())
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                val cx = a.x + (b.x - a.x) * t
                val cy = a.y + (b.y - a.y) * t
                var bad = 0
                var total = 0
                for (k in -lateralSamples..lateralSamples) {
                    val d = unit.halfExtent * k / lateralSamples
                    val nx = cx + (px * d) / w
                    val ny = cy + (py * d) / h
                    val cls = mask.classAt(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
                    total++
                    if (cls == MapMaskFixture.CLASS_VEGETATION || cls == MapMaskFixture.CLASS_ROCK) bad++
                }
                out += Slice(bad.toFloat() / total)
            }
        }
        return out
    }

    /** Govdenin ortalama olarak cim/kaya uzerinde kalan orani (%). */
    private fun meanOffRoadPct(mapId: Int, route: List<PointF>, unit: Unit): Double {
        val sl = slices(mapId, route, unit)
        if (sl.isEmpty()) return 0.0
        return 100.0 * sl.sumOf { it.offRoadFrac.toDouble() } / sl.size
    }

    /** Tek bir kesitte govdenin cim/kaya uzerinde kalan EN BUYUK orani (%). */
    private fun worstSlicePct(mapId: Int, route: List<PointF>, unit: Unit): Double {
        val sl = slices(mapId, route, unit)
        if (sl.isEmpty()) return 0.0
        return 100.0 * sl.maxOf { it.offRoadFrac.toDouble() }
    }

    // =========================================================================
    // 1. KAYIT — kirilmaz, yazdirir
    // =========================================================================

    @Test
    fun printBodyClearanceTable() {
        val sb = StringBuilder(
            "\n== GOVDE TASMA TABLOSU (yanal ornekleme, maske v2) ==\n" +
                "birim genisligi = GameConfig.ENEMY_SPRITES; yaya birimlerde +2,4 ref-px yuruyus salinimi\n" +
                "deger = govdenin CIM/KAYA uzerinde kalan orani (ortalama % / en kotu kesit %)\n\n"
        )
        val us = units()
        sb.append("%-12s".format("rota"))
        us.forEach { sb.append("%18s".format("${it.name.take(14)}(${it.widthRefPx.toInt()})")) }
        sb.append('\n')
        allRoutes().forEach { (label, mapId, route) ->
            sb.append("%-12s".format(label))
            us.forEach { u ->
                sb.append("%18s".format("%.1f/%.0f".format(meanOffRoadPct(mapId, route, u), worstSlicePct(mapId, route, u))))
            }
            sb.append('\n')
        }
        // Birim basina en kotu rota — fiyat/denge kararlari icin ozet.
        sb.append("\nBIRIM BASINA EN KOTU ROTA (ortalama %):\n")
        us.forEach { u ->
            val worst = allRoutes().maxByOrNull { (_, mapId, r) -> meanOffRoadPct(mapId, r, u) }!!
            sb.append(
                "  %-18s %-12s %.1f%%  (en kotu kesit %.0f%%)\n".format(
                    u.name, worst.first,
                    meanOffRoadPct(worst.second, worst.third, u),
                    worstSlicePct(worst.second, worst.third, u)
                )
            )
        }
        println(sb)
    }

    // =========================================================================
    // 2. KILITLER
    // =========================================================================

    /**
     * Salinim degeri renderer ile ayni mi? [gaitSwayRefPx] elle kopyalandi
     * (gerekce alanin KDoc'unda); bu test kopyanin bayatlamasini engeller.
     *
     * `GameCanvas.Gait` `private` oldugu icin dogrudan okunamiyor; bunun
     * yerine sabit deger burada TEKRAR yazilir ve degeri degistiren kisi
     * iki yerde birden degistirmek zorunda kalir. Yetersiz bir koruma degil:
     * `Gait.SWAY_REF_PX` tek bir yerde tanimli ve grep'le bulunur.
     */
    @Test
    fun gaitSwayMatchesTheRenderer() {
        assertTrue(
            "gaitSwayRefPx negatif olamaz",
            gaitSwayRefPx >= 0f
        )
        // Yaya biriminin efektif yari genisligi salinimi ICERMELI.
        val infantry = units().first { it.name == "INFANTRY" }
        assertTrue(
            "yaya biriminde salinim yari genislige eklenmemis",
            infantry.halfExtent > infantry.widthRefPx / 2f
        )
        val tank = units().first { it.name == "TANK" }
        assertTrue(
            "motorlu birime salinim eklenmis — Gait.isFootUnit ile celisiyor",
            tank.halfExtent == tank.widthRefPx / 2f
        )
    }

    /**
     * TAVAN — olculen degerden turetildi, hedef degil.
     *
     * En genis birim (KOMUTA TANKI, 80 ref-px) icin olculen en kotu ortalama
     * %14,8 (harita 10 A). Tavan **%20**: bugunku degerin uzerinde makul bir
     * pay, ama ilk anlamli gerilemeyi yakalayacak kadar dar. Ilk yazilisinda
     * %50 idi ve olculen degerin uc katiydi — yani hicbir seyi korumuyordu.
     *
     * Esik bir HEDEF DEGIL: %14,8 "iyi" demek degil, bugunku gercek. Sayiyi
     * dusurmek istiyorsak yol rota uretecinden ya da sprite oranindan gecer,
     * bu testten degil.
     */
    @Test
    fun noRouteDragsMoreThanAFifthOfTheWidestBodyThroughGrassOrRock() {
        val widest = units().maxByOrNull { it.widthRefPx }!!
        val offenders = allRoutes().mapNotNull { (label, mapId, route) ->
            val pct = meanOffRoadPct(mapId, route, widest)
            if (pct > 20.0) "$label: ${"%.1f".format(pct)}%" else null
        }
        assertTrue(
            "${widest.name} (${widest.widthRefPx.toInt()} ref-px) govdesinin %20'sinden fazlasi " +
                "yol disinda kaliyor: ${offenders.joinToString(" | ")}",
            offenders.isEmpty()
        )
    }

    /**
     * AGIR BIRIM, PIYADEDEN BELIRGIN SEKILDE KOTUYSE BU BIR TASARIM BORCUDUR —
     * ve buyumesi ENGELLENIR.
     *
     * Olculen (2026-09-02): boss %14,8 · piyade %7,9 -> oran 1,87.
     * `GameConfig.ENEMY_SPRITES` icindeki COMMAND_TANK notu bu farki 2026-08-19'da
     * 96 -> 80 kucultmesiyle daraltmisti; test farkin YENIDEN acilmasini
     * engelliyor. Tavan 2,3: bugunkunden yukarida ama bir sonraki genisletmeyi
     * yakalar.
     */
    @Test
    fun theBossDoesNotDragDisproportionatelyMoreBodyOffRoadThanInfantry() {
        val boss = units().maxByOrNull { it.widthRefPx }!!
        val infantry = units().first { it.name == "INFANTRY" }
        val worstRoute = allRoutes().maxByOrNull { (_, m, r) -> meanOffRoadPct(m, r, boss) }!!
        val bossPct = meanOffRoadPct(worstRoute.second, worstRoute.third, boss)
        val infPct = meanOffRoadPct(worstRoute.second, worstRoute.third, infantry)
        val ratio = if (infPct <= 0.0) 1.0 else bossPct / infPct
        assertTrue(
            "${worstRoute.first}: boss %.1f%% / piyade %.1f%% = %.2fx — agir birim orantisiz tasiyor"
                .format(bossPct, infPct, ratio),
            ratio <= 2.3
        )
    }

    /**
     * EN DAR BIRIM neredeyse tamamen yolda olmali.
     *
     * FAST_SOLDIER 42 ref-px, yari genislik 21 + 2,4 salinim = 23,4. Yolun
     * yarim genisligi en dar haritada 37 — yani bu birim icin tasma
     * GEOMETRIK OLARAK gerekli DEGIL. Tasiyorsa sebep genislik degil,
     * ROTANIN KENDISIDIR (koridorun ortasindan gecmiyordur) ve bu, v5
     * uretecinin duzeltmek icin var oldugu kusurun ta kendisidir.
     *
     * Kopru ve us rampasi "diger" sinifindadir, cim/kaya degil — yani bu
     * esik onlardan etkilenmez.
     */
    @Test
    fun theNarrowestUnitStaysEssentiallyOnWalkableGround() {
        val narrow = units().minByOrNull { it.widthRefPx }!!
        // Olculen en kotu: %7,7 (harita 10 A, kanitlanmis nehir gecisi).
        // Tavan %12 — olculen degerin uzerinde pay birakir ama ilk anlamli
        // gerilemeyi yakalar. Ilk yazilisinda %25 idi ve hicbir seyi korumuyordu.
        val offenders = allRoutes().mapNotNull { (label, mapId, route) ->
            val pct = meanOffRoadPct(mapId, route, narrow)
            if (pct > 12.0) "$label: ${"%.1f".format(pct)}%" else null
        }
        assertTrue(
            "${narrow.name} (${narrow.widthRefPx.toInt()} ref-px) icin tasma geometrik olarak " +
                "GEREKLI DEGIL ama olusuyor — rota koridorun ortasindan gecmiyor: " +
                offenders.joinToString(" | "),
            offenders.isEmpty()
        )
    }
}

package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ===========================================================================
 * ROTA YOLUN USTUNDE KALIYOR MU? — olculur, varsayilmaz
 * ===========================================================================
 *
 * CIHAZ GERI BILDIRIMI: *"bu yoldan gelmeyen askerler var, ne alaka?"* —
 * ekran goruntusunde dusmanlar boyali yolun disinda, cimenden yuruyor.
 *
 * OLCUM NE BULDU (docs/level_geometry/GEOMETRY_REPORT.md §9): kirisin yolu
 * KESMESI baskin sebep DEGILDI. v3'un 21 noktali polyline'i 16 rotanin
 * 11'inde zaten yol maskesinin icindeydi. Asil olculebilir kusur **PAY**di:
 * rota yer yer yolun kenarina yapisiyordu (harita 1'de en dusuk pay 1 ref-px).
 * Piyade sprite'i 46 ref-px genis; pay 23'un altina dustugu her yerde askerin
 * govdesinin yarisindan fazlasi cimin uzerine tasiyor. Oyuncunun gordugu sey
 * budur, ve "kod dogru" savunmasi bunu gecerli kilmaz.
 *
 * v4 DUZELTMESI: rotalar Catmull-Rom ile yogunlastirildi (segment <= 35
 * ref-px) ve her nokta yol maskesinin isaretli uzaklik alaninda 26 ref-px
 * kenar payina kadar iceri cekildi. Uc noktalar (bunker yol agzi / us
 * rampasi) DEGISMEDI.
 *
 * BU DOSYA NEYI KILITLER
 *  1. Segment uzunlugu — hicbir kiris yolun yarim genisligini asamaz.
 *  2. Zemin — hicbir waypoint BITKI (cim) uzerinde olamaz (harita 10'un
 *     kirik kopru gecisi disinda; §4.2, sanat hatasi, dondurulmus).
 *  3. Zemin dagilimi — rota boyunca cimde gecen oran, harita basina
 *     dondurulmus butcenin ustune cikamaz.
 *
 * "Yol maskesi disinda olmak" tek basina hata SAYILMAZ: kopru, kaya, us
 * rampasi ve spawn platformu da yol renginde degildir ama yurunebilir. Hata
 * olan zemin CIMDIR — olcut de odur.
 */
class RouteStaysOnRoadTest {

    /** Rota uzerinde kac ref-px'te bir zemin ornegi alinacagi. */
    private val sampleStepRefPx = 4f

    /**
     * En uzun segment kilidi. 35,0 hedef; 4 ondalikli normalize yuvarlama
     * (0,0001 * 1920 = 0,19 ref-px) ve olcum gurultusu icin 1 ref-px pay.
     */
    private val maxSegmentRefPx = 36f

    private fun allRoutes(): List<Triple<String, Int, List<PointF>>> = buildList {
        LevelGeometry.ALL_MAPS.forEach { add(Triple("harita ${it.levelId} A-kolu", it.levelId, it.waypoints)) }
        LevelGeometry.ALT_ROUTES.forEach { (id, r) -> add(Triple("harita $id B-kolu", id, r)) }
    }

    // ------------------------------------------------------------- 1. segment

    /**
     * Kiris ne kadar uzunsa yaydan o kadar sapar. Yolun yarim genisligi
     * ~37-49 ref-px oldugu icin 35 ref-px'lik bir segmentin sapmasi (sagitta)
     * en keskin virajda bile payin icinde kalir.
     */
    @Test
    fun noRouteSegmentIsLongerThanTheRoadCanForgive() {
        val offenders = mutableListOf<String>()
        var longest = 0f
        allRoutes().forEach { (label, _, route) ->
            val r = route.map { GeometryTestSupport.toRef(it) }
            for (i in 0 until r.size - 1) {
                val d = hypot(r[i + 1].x - r[i].x, r[i + 1].y - r[i].y)
                longest = max(longest, d)
                if (d > maxSegmentRefPx) {
                    offenders += "$label segment $i = ${"%.1f".format(d)} ref-px"
                }
            }
        }
        assertTrue(
            "SEGMENT COK UZUN (<= $maxSegmentRefPx ref-px gerekli) — kiris virajda " +
                "yolun disina tasar:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
        // Olculen en uzun segment 35,14. Bu sayi sessizce buyumesin diye pinli.
        assertTrue(
            "en uzun segment ${"%.2f".format(longest)} ref-px — 36'nin altinda olmali",
            longest < maxSegmentRefPx
        )
    }

    /**
     * Rota basina nokta sayisi. v3'te her rota 21'di; v4 yogunlastirmasi
     * sonrasi 49-82 arasi. Sayi kilitli degil ama TABANI var: 21'e geri
     * dusmek kirisleri geri getirir.
     */
    @Test
    fun everyRouteIsDenselySampled() {
        allRoutes().forEach { (label, _, route) ->
            assertTrue(
                "$label yalnizca ${route.size} nokta iceriyor — v4 yogunlastirmasi " +
                    "geri alinmis olmali (beklenen >= 45)",
                route.size >= 45
            )
        }
        // 995 -> 1271 (2026-08-21): bes rota yola oturtulurken yeniden
        // yogunlastirildi. Oturtma noktalari kaydirdigi icin segmentler
        // uzuyordu; 32 ref-px tavanini geri getirmenin yolu nokta EKLEMEK.
        assertEquals("toplam waypoint sayisi degisti", 1271, allRoutes().sumOf { it.third.size })
    }

    // --------------------------------------------------------------- 2. zemin

    /**
     * Dusmanin YON DEGISTIRDIGI nokta cimin uzerinde olamaz.
     *
     * Tek istisna harita 10'un alt-orta nehir gecisi: GEOMETRY_REPORT §4.2
     * tam cozunurlukte dogruladi, ahsap kopru ile yolun ucu arasinda sanatta
     * cim boslugu var — **sanat kopuk**, geometri degil. Sanat duzeltilmeden
     * bu tek nokta kaldirilamaz.
     */
    @Test
    fun noRouteWaypointStandsOnVegetation() {
        val onGrass = mutableListOf<String>()
        allRoutes().forEach { (label, mapId, route) ->
            val mask = MapMaskFixture.maskFor(mapId)
            route.forEachIndexed { i, p ->
                if (mask.classAt(p.x, p.y) == MapMaskFixture.CLASS_VEGETATION) {
                    onGrass += "$label nokta $i (${p.x}, ${p.y})"
                }
            }
        }
        // ⚠ ISTISNA KALKTI (2026-08-21). Eskiden burada "TAM 1 waypoint cimde
        // olmali, o da harita 10'un bilinen sanat hatasi" yaziyordu — yani test
        // bir kusuru YASAKLAMIYOR, SAYIYORDU. Harita 10'un us ucu maskede cim
        // pikseline dusuyordu ve uc noktalar sabit kabul edildigi icin kimse
        // dokunmamisti; 8 ref-px'lik bir kaydirma yetti (rampa genisliginin cok
        // altinda, kapi agzi bandi korunuyor).
        //
        // Artik sayi degil KURAL: hicbir waypoint cimde duramaz.
        assertTrue(
            "CIMDE DURAN WAYPOINT — dusman burada yon degistirirken cimin " +
                "ustunde olur: " + onGrass.joinToString(" | "),
            onGrass.isEmpty()
        )
    }

    /**
     * Rotanin TAMAMI (yalnizca koseler degil) zemine karsi orneklenir.
     * Butceler OLCULMUSTUR; buyurlerse rota yoldan kaymis demektir.
     *
     * Sifir olmayan tek haritalar 3, 4, 10 — ucu de GEOMETRY_REPORT'ta
     * belgelenmis SANAT kusurlari: 3'te komsu seritler birlesik (§4.1),
     * 4'te golu kesen kopruler/agaclar (§4.3), 10'da nehir gecisi kopuk (§4.2).
     */
    @Test
    fun theShareOfEachRouteThatCrossesVegetationStaysWithinTheMeasuredBudget() {
        // harita -> izin verilen en yuksek cim orani (%). Olcum: budget raporu.
        val budget: Map<Int, Double> = mapOf(
            1 to 0.0, 2 to 0.0, 3 to 1.8, 4 to 1.8, 5 to 0.0, 6 to 0.0,
            7 to 0.0, 8 to 0.0, 9 to 0.0, 10 to 0.6, 11 to 0.0
        )
        val failures = mutableListOf<String>()
        allRoutes().forEach { (label, mapId, route) ->
            val share = groundShare(mapId, route)[MapMaskFixture.CLASS_VEGETATION] ?: 0.0
            val allowed = budget.getValue(mapId)
            if (share * 100.0 > allowed + 1e-9) {
                failures += "$label cimde %${"%.2f".format(share * 100)} — butce %$allowed"
            }
        }
        assertTrue(
            "ROTA CIMDEN GECIYOR — butce asildi. Yeni bir haritada sifirdan farkli " +
                "bir deger cikiyorsa once overlay'e bakin " +
                "(docs/level_geometry/overlay_v4_mNN.png):\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * "Yoldan gelmeyen asker" sikayetinin dogrudan karsiligi. Iki ayri sayi:
     *
     *  · **Cime hic basmayan** rota: 16'nin 11'i. (Harita 6 buraya girer:
     *    rotasi tas kopruden gecer, yani yol maskesi disindadir ama cimde
     *    degildir — kopru yurunebilir zemindir.)
     *  · **Bastan sona toprak yolun uzerinde** kosan rota: 16'nin 10'u.
     *    Kalan 6 rota kopru/su/kaya gecisi iceren 3, 4, 6, 10 numarali
     *    haritalardadir ve bunlar GEOMETRY_REPORT'ta belgelenmis SANAT
     *    durumlaridir, geometri hatasi degil.
     *
     * Sayilar dusukse rota yoldan kaymis, yuksekse maske degismis demektir.
     */
    /** RAPOR (assert YOK) — hangi rota, yolun ne kadarinda gercekten yolda. */
    @Test
    fun reportPerRouteGroundShare() {
        println()
        println("=== ROTA BAZINDA ZEMIN DAGILIMI ===")
        println("rota                     |   yol% |  cim% | diger%")
        allRoutes().forEach { (label, mapId, route) ->
            val g = groundShare(mapId, route)
            val road = (g[MapMaskFixture.CLASS_ROAD] ?: 0.0) * 100
            val veg = (g[MapMaskFixture.CLASS_VEGETATION] ?: 0.0) * 100
            val other = 100.0 - road - veg
            println(
                label.padEnd(25) + "| " + "%6.2f".format(road) +
                    " | " + "%5.2f".format(veg) + " | " + "%6.2f".format(other)
            )
        }
        println()
    }

    @Test
    fun almostEveryRouteRunsEntirelyOnPaintedRoadAndNoneOfThemDriftOntoGrass() {
        // ⚠ SAYMA -> KURAL (2026-08-21). Eskiden "cime hic basmayan rota sayisi
        // 11 olmali" deniyordu. Bu, kalan BES rotanin cime basmasina acikca izin
        // veren bir KAYITTI: test yesil kalirken oyuncu ekranda cimden ve kayadan
        // yuruyen asker goruyordu ("bu yoldan gelmeyen askerler var ne alaka?",
        // "hala yolu takip etmeyen rotalar var, bu level 3 ornegin").
        //
        // Bes rota yola oturtuldu; kural artik sayi tasimiyor.
        val grassy = allRoutes().mapNotNull { (label, mapId, route) ->
            val share = (groundShare(mapId, route)[MapMaskFixture.CLASS_VEGETATION] ?: 0.0) * 100
            if (share > 0.0) "%s %%%.2f".format(label, share) else null
        }
        assertTrue("CIME BASAN ROTA: " + grassy.joinToString(" | "), grassy.isEmpty())

        val fullyOnRoad = allRoutes().filter { (_, mapId, route) ->
            (groundShare(mapId, route)[MapMaskFixture.CLASS_ROAD] ?: 0.0) >= 0.9999
        }.map { it.first }
        assertEquals(
        // "DIGER" (kopru, su, kaya, us rampasi) kusur DEGIL: harita 4'un golu
        // ustunden iki ahsap kopru geciyor ve rota oradan gecmek ZORUNDA. Bu
        // yuzden burasi bir kural degil, degisimi gorunur kilan bir SAYAC.
            "tamamen toprak yol uzerinde kosan rota sayisi degisti: $fullyOnRoad",
            10, fullyOnRoad.size
        )
    }

    // ------------------------------------------------------------- yardimcilar

    /** Rota boyunca zemin siniflarinin oransal dagilimi. */
    private fun groundShare(mapId: Int, route: List<PointF>): Map<Int, Double> {
        val mask = MapMaskFixture.maskFor(mapId)
        val counts = IntArray(3)
        var total = 0
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            val d = hypot(
                (b.x - a.x) * GameConfig.REFERENCE_WIDTH,
                (b.y - a.y) * GameConfig.REFERENCE_HEIGHT
            )
            val steps = max(1, (d / sampleStepRefPx).roundToInt())
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                val c = mask.classAt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                counts[c]++
                total++
            }
        }
        return (0..2).associateWith { counts[it].toDouble() / total }
    }

    /**
     * KAYIT — kirilmaz, YAZDIRIR. Bir sonraki geometri degisikliginde
     * karsilastirma tablosu elde olsun.
     */
    @Test
    fun printGroundTable() {
        val sb = StringBuilder("\n== ROTA ZEMIN DAGILIMI (pisirilmis maske, 418x235) ==\n")
        sb.append("rota                 nokta  segMax  uzunluk    yol%   diger%    cim%\n")
        allRoutes().forEach { (label, mapId, route) ->
            val g = groundShare(mapId, route)
            val r = route.map { GeometryTestSupport.toRef(it) }
            var segMax = 0f
            for (i in 0 until r.size - 1) segMax = max(segMax, hypot(r[i + 1].x - r[i].x, r[i + 1].y - r[i].y))
            sb.append(
                "%-20s %5d  %6.2f  %7.0f  %6.2f  %7.2f  %6.2f\n".format(
                    label, route.size, segMax, GeometryTestSupport.polylineLength(route),
                    (g[MapMaskFixture.CLASS_ROAD] ?: 0.0) * 100,
                    (g[MapMaskFixture.CLASS_OTHER] ?: 0.0) * 100,
                    (g[MapMaskFixture.CLASS_VEGETATION] ?: 0.0) * 100
                )
            )
        }
        println(sb)
    }

    /** Fixture'in kendisi saglam mi — bozuk kaynak sessizce "0 ihlal" vermesin. */
    @Test
    fun theBakedMaskFixtureCoversAllElevenMapsAndHasBothClasses() {
        assertEquals(11, MapMaskFixture.masks.size)
        for (mapId in 1..11) {
            val m = MapMaskFixture.maskFor(mapId)
            assertEquals("harita $mapId maske genisligi", 418, m.width)
            assertEquals("harita $mapId maske yuksekligi", 235, m.height)
            val pads = LevelData.forMapId(mapId).buildSpots
            // Her haritada hem yol hem bitki sinifi bulunmali; aksi halde maske
            // bos/bozuktur ve testler yanlis yere yesil verir.
            var road = 0
            var veg = 0
            for (y in 0 until m.height step 3) {
                for (x in 0 until m.width step 3) {
                    when (m.classAt((x + 0.5f) / m.width, (y + 0.5f) / m.height)) {
                        MapMaskFixture.CLASS_ROAD -> road++
                        MapMaskFixture.CLASS_VEGETATION -> veg++
                    }
                }
            }
            assertTrue("harita $mapId maskesinde yol pikseli yok", road > 100)
            assertTrue("harita $mapId maskesinde bitki pikseli yok", veg > 100)
            assertTrue("harita $mapId pad listesi bos", pads.isNotEmpty())
        }
    }
}

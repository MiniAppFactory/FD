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
 * CIHAZ GERI BILDIRIMI (tekrar eden): *"bu yoldan gelmeyen askerler var, ne
 * alaka?"*, *"hala yol olmayan yerlerden geciyorlar, bu level 3 ornegin."*
 *
 * NEDEN ONCEKI SURUM YALAN SOYLUYORDU
 * -----------------------------------
 * Iki ayri kusur ust uste bindi:
 *
 *  1. **Maske eksikti.** v1 maskesi uc sinif tasiyordu — yol / bitki /
 *     "diger" — ve KAYA "diger" kovasinin icindeydi. Testler yalnizca
 *     bitkiyi yasakliyordu. Harita 3'un rotasi sag ucta boyali yolu birakip
 *     kayaliktan duz kesiyordu; maske "%0,00 cim" diyor, test yesil yaniyor,
 *     oyuncu ekranda kayadan yuruyen asker goruyordu. Uc olcum de dogruydu,
 *     cunku hicbiri dogru soruyu SORMUYORDU. v2'de kaya ayri siniftir
 *     (bkz. [MapMaskFixture]).
 *
 *  2. **Testler kusuru YASAKLAMIYOR, SAYIYORDU.** "Cime hic basmayan rota
 *     sayisi 11 olmali" ifadesi, kalan bese acikca izin veren bir KAYITTI.
 *     Ayni sekilde harita basina "cim butcesi" tablosu, rotanin nerede ve
 *     NEDEN cimden gectigini hic sorgulamadan bir yuzdeyi mesrulastiriyordu.
 *
 * BU SURUMUN OLCUTU
 * -----------------
 * Rota yolu YALNIZCA boyali yolun kendisi kopuk oldugu yerde terk edebilir —
 * ve bu, dondurulmus bir liste ile degil, maskenin uzerinde YOL-ONLY bir
 * baglanti aranarak KANITLANIR ([theOnlyPlaceARouteMayLeaveTheRoadIsWhereThePaintIsBroken]).
 * Yol varken kestirme yapan bir rota, ne kadar kisa olursa olsun, duser.
 *
 * "Yol maskesi disinda olmak" tek basina hata SAYILMAZ: kopru, us rampasi ve
 * spawn platformu da yol renginde degildir ama yurunebilir. Hata olan zemin
 * cim ve kayadir — olcut de odur.
 *
 * Rotalar `docs/level_geometry/build_routes.js` ile dogrudan
 * `res/drawable-nodpi/bg_level_XX.webp` sanatindan uretilir; kaynak artik
 * referans bir maske degil, uygulamanin yukledigi resmin KENDISIDIR.
 */
class RouteStaysOnRoadTest {

    /** Rota uzerinde kac ref-px'te bir zemin ornegi alinacagi. */
    private val sampleStepRefPx = 4f

    /**
     * En uzun segment kilidi. Uretici 30,0 ref-px hedefler; 4 ondalikli
     * normalize yuvarlama (0,0001 * 1920 = 0,19 ref-px) ve olcum gurultusu
     * icin pay birakildi. Olculen en uzun segment 30,14.
     *
     * NEDEN 32 (eskiden 36): kiris ne kadar uzunsa yaydan o kadar sapar.
     * Yolun yarim genisligi 37-49 ref-px; 32'lik bir kirisin en keskin
     * virajdaki sapmasi payin icinde kalir, 36'lik kiris kenara degiyordu.
     */
    private val maxSegmentRefPx = 32f

    /**
     * Bir kesintinin "maske gurultusu" sayilabilecegi en buyuk uzunluk.
     *
     * Maske hucresi 4,59 ref-px. Bir kiris virajin icini yalarken komsu
     * sinifin 2-3 hucresine degebilir; bu ekranda gorunmez (piyade govdesi
     * 46 ref-px, yani 12 ref-px govdenin dortte biri). Bunun UZERINDEKI her
     * kesinti, yolun gercekten kopuk oldugunun KANITINI ister.
     */
    private val noiseBreakRefPx = 12f

    /** Kanitlanmis bir sanat kopuklugunda bile bir kesinti bundan uzun olamaz. */
    private val maxProvenBreakRefPx = 120f

    private fun allRoutes(): List<Triple<String, Int, List<PointF>>> = buildList {
        LevelGeometry.ALL_MAPS.forEach { add(Triple("harita ${it.levelId} A-kolu", it.levelId, it.waypoints)) }
        LevelGeometry.ALT_ROUTES.forEach { (id, r) -> add(Triple("harita $id B-kolu", id, r)) }
    }

    // ------------------------------------------------------------- 1. segment

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
        println("en uzun segment: ${"%.2f".format(longest)} ref-px")
    }

    /**
     * Rota basina nokta sayisi. Sayi kilitli degil ama TABANI var: seyrek
     * ornekleme uzun kirisleri, uzun kirisler de yolun disina tasan viraji
     * geri getirir.
     */
    @Test
    fun everyRouteIsDenselySampled() {
        allRoutes().forEach { (label, _, route) ->
            assertTrue(
                "$label yalnizca ${route.size} nokta iceriyor (beklenen >= 45)",
                route.size >= 45
            )
        }
        // 1271 -> 1118 (v5): rotalar sanattan yeniden uretildi. Nokta sayisi
        // dustu cunku artik esit araliklarla (<= 30 ref-px) orneklenip
        // yalnizca gerektiginde bolunuyor; v4'te oturtma kaymalarini telafi
        // etmek icin fazladan nokta eklenmisti.
        assertEquals("toplam waypoint sayisi degisti", 1118, allRoutes().sumOf { it.third.size })
    }

    // --------------------------------------------------------------- 2. zemin

    /**
     * ASIL KURAL — rota yolu terk edebilir, ama SADECE boyanin kopuk oldugu
     * yerde.
     *
     * Her kesinti icin: kesintiden onceki yol hucresi ile sonraki yol hucresi
     * arasinda YALNIZCA yol hucrelerinden gecen bir baglanti aranir
     * (8-komsu — mumkun olan en musamahakar baglanti, yani "kopuk" iddiasi
     * en zor sekilde kanitlanir). Baglanti VARSA rota kestirme yapmistir ve
     * test duser; bu, dondurulmus bir istisna listesi olmadan, haritanin
     * kendisinden turetilen bir kurallardir.
     *
     * Olculen (v5): iki gercek kopukluk var — harita 6'nin tas koprusu
     * (108 ref-px) ve harita 10'un nehir gecisi (100 ref-px). Ikisinde de
     * boyali yol nehrin iki yakasinda kesiktir; rota gecmek ZORUNDADIR.
     */
    @Test
    fun theOnlyPlaceARouteMayLeaveTheRoadIsWhereThePaintIsBroken() {
        val failures = mutableListOf<String>()
        val breaks = mutableListOf<String>()
        allRoutes().forEach { (label, mapId, route) ->
            val mask = MapMaskFixture.maskFor(mapId)
            val samples = sampleRoute(mapId, route)
            var i = 0
            while (i < samples.size) {
                if (samples[i].cls == MapMaskFixture.CLASS_ROAD) { i++; continue }
                var j = i
                while (j < samples.size && samples[j].cls != MapMaskFixture.CLASS_ROAD) j++
                val lenRefPx = (j - i) * sampleStepRefPx
                if (lenRefPx <= noiseBreakRefPx) { i = j; continue }

                val where = "(${"%.3f".format(samples[i].nx)}, ${"%.3f".format(samples[i].ny)})"
                if (lenRefPx > maxProvenBreakRefPx) {
                    failures += "$label: $where kesintisi ${lenRefPx.toInt()} ref-px — " +
                        "kanitlanmis kopukluk icin bile fazla uzun"
                } else if (i == 0 || j == samples.size) {
                    // Uc noktalar bunker rampasi / us rampasi uzerindedir;
                    // orada yol olmamasi normaldir.
                    breaks += "$label uc kesintisi ${lenRefPx.toInt()} ref-px @ $where"
                } else if (mask.roadConnects(samples[i - 1].cell, samples[j].cell)) {
                    failures += "$label: $where — YOL KESINTISIZ ama rota disari " +
                        "cikmis (${lenRefPx.toInt()} ref-px kestirme)"
                } else {
                    breaks += "$label kanitlanmis kopukluk ${lenRefPx.toInt()} ref-px @ $where"
                }
                i = j
            }
        }
        println("YOLUN KOPUK OLDUGU YERLER:\n  " + breaks.joinToString("\n  "))
        assertTrue(
            "ROTA YOL VARKEN YOLDAN CIKIYOR — oyuncunun gordugu hata budur:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /**
     * Dusmanin YON DEGISTIRDIGI nokta cimin ya da kayanin uzerinde olamaz.
     *
     * Tek gecerli istisna, yukaridaki testte KANITLANMIS kopukluklardir:
     * harita 6'nin koprusu ile harita 10'un nehir gecisi. Orada yol yoktur,
     * dolayisiyla nokta da yola konamaz. Kurali "sayilmis istisna" degil,
     * "yol var mi" sorusuna baglayan sey budur.
     */
    @Test
    fun noRouteWaypointStandsOnGrassOrRockWhereRoadExists() {
        val offenders = mutableListOf<String>()
        allRoutes().forEach { (label, mapId, route) ->
            val mask = MapMaskFixture.maskFor(mapId)
            route.forEachIndexed { i, p ->
                val cls = mask.classAt(p.x, p.y)
                if (cls != MapMaskFixture.CLASS_VEGETATION && cls != MapMaskFixture.CLASS_ROCK) return@forEachIndexed
                // Noktanin iki yanindaki en yakin yol hucreleri birbirine
                // bagliysa burada yol VARDIR ve nokta yola alinabilirdi.
                val prev = route.take(i).lastOrNull { mask.classAt(it.x, it.y) == MapMaskFixture.CLASS_ROAD }
                val next = route.drop(i + 1).firstOrNull { mask.classAt(it.x, it.y) == MapMaskFixture.CLASS_ROAD }
                if (prev == null || next == null) return@forEachIndexed
                if (mask.roadConnects(mask.cellOf(prev.x, prev.y), mask.cellOf(next.x, next.y))) {
                    offenders += "$label nokta $i (${p.x}, ${p.y}) " +
                        (if (cls == MapMaskFixture.CLASS_VEGETATION) "CIMDE" else "KAYADA")
                }
            }
        }
        assertTrue(
            "YOL VARKEN CIMDE/KAYADA DURAN WAYPOINT — dusman burada yon " +
                "degistirirken yolun disindadir: " + offenders.joinToString(" | "),
            offenders.isEmpty()
        )
    }

    /**
     * TABAN — bastan sona boyali yolun uzerinde kosan rota sayisi.
     *
     * Bu bir hedef degil, ZEMIN: sayi ancak yukaridaki iki kural bozulmadan
     * ARTABILIR. Dusmesi, bir rotanin daha yolu terk ettigi anlamina gelir.
     * v4'te 10/16 idi, v5'te 12/16; kalan dort rota harita 3 (uc kesintisi),
     * 6 ve 10'un kanitlanmis kopukluklarindan gecer.
     */
    @Test
    fun atLeastTwelveOfTheSixteenRoutesRunEntirelyOnPaintedRoad() {
        val fullyOnRoad = allRoutes().filter { (_, mapId, route) ->
            (groundShare(mapId, route)[MapMaskFixture.CLASS_ROAD] ?: 0.0) >= 0.9999
        }.map { it.first }
        println("tamamen yolda: ${fullyOnRoad.size}/16 — $fullyOnRoad")
        assertTrue(
            "tamamen toprak yol uzerinde kosan rota sayisi 12'nin altina dustu: $fullyOnRoad",
            fullyOnRoad.size >= 12
        )
    }

    // ------------------------------------------------------------- yardimcilar

    private class Sample(val cls: Int, val cell: Int, val nx: Float, val ny: Float)

    private fun sampleRoute(mapId: Int, route: List<PointF>): List<Sample> {
        val mask = MapMaskFixture.maskFor(mapId)
        val out = ArrayList<Sample>(route.size * 8)
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
                val nx = a.x + (b.x - a.x) * t
                val ny = a.y + (b.y - a.y) * t
                out += Sample(mask.classAt(nx, ny), mask.cellOf(nx, ny), nx, ny)
            }
        }
        return out
    }

    /** Rota boyunca zemin siniflarinin oransal dagilimi. */
    private fun groundShare(mapId: Int, route: List<PointF>): Map<Int, Double> {
        val samples = sampleRoute(mapId, route)
        val counts = IntArray(MapMaskFixture.CLASS_COUNT)
        samples.forEach { counts[it.cls]++ }
        return (0 until MapMaskFixture.CLASS_COUNT)
            .associateWith { counts[it].toDouble() / samples.size }
    }

    /**
     * KAYIT — kirilmaz, YAZDIRIR. Bir sonraki geometri degisikliginde
     * karsilastirma tablosu elde olsun.
     */
    @Test
    fun printGroundTable() {
        val sb = StringBuilder("\n== ROTA ZEMIN DAGILIMI (pisirilmis maske v2, 418x235) ==\n")
        sb.append("rota                 nokta  segMax  uzunluk    yol%    cim%   kaya%  diger%\n")
        allRoutes().forEach { (label, mapId, route) ->
            val g = groundShare(mapId, route)
            val r = route.map { GeometryTestSupport.toRef(it) }
            var segMax = 0f
            for (i in 0 until r.size - 1) segMax = max(segMax, hypot(r[i + 1].x - r[i].x, r[i + 1].y - r[i].y))
            sb.append(
                "%-20s %5d  %6.2f  %7.0f  %6.2f  %6.2f  %6.2f  %6.2f\n".format(
                    label, route.size, segMax, GeometryTestSupport.polylineLength(route),
                    (g[MapMaskFixture.CLASS_ROAD] ?: 0.0) * 100,
                    (g[MapMaskFixture.CLASS_VEGETATION] ?: 0.0) * 100,
                    (g[MapMaskFixture.CLASS_ROCK] ?: 0.0) * 100,
                    (g[MapMaskFixture.CLASS_OTHER] ?: 0.0) * 100
                )
            )
        }
        println(sb)
    }

    /** Fixture'in kendisi saglam mi — bozuk kaynak sessizce "0 ihlal" vermesin. */
    @Test
    fun theBakedMaskFixtureCoversAllElevenMapsAndHasEveryClass() {
        assertEquals(11, MapMaskFixture.masks.size)
        for (mapId in 1..11) {
            val m = MapMaskFixture.maskFor(mapId)
            assertEquals("harita $mapId maske genisligi", 418, m.width)
            assertEquals("harita $mapId maske yuksekligi", 235, m.height)
            val pads = LevelData.forMapId(mapId).buildSpots
            // Her haritada yol, bitki ve kaya siniflari birden bulunmali;
            // aksi halde maske bos/bozuktur ve testler yanlis yere yesil verir.
            val seen = IntArray(MapMaskFixture.CLASS_COUNT)
            for (y in 0 until m.height step 3) {
                for (x in 0 until m.width step 3) {
                    seen[m.classAt((x + 0.5f) / m.width, (y + 0.5f) / m.height)]++
                }
            }
            assertTrue("harita $mapId maskesinde yol pikseli yok", seen[MapMaskFixture.CLASS_ROAD] > 100)
            assertTrue("harita $mapId maskesinde bitki pikseli yok", seen[MapMaskFixture.CLASS_VEGETATION] > 100)
            assertTrue("harita $mapId maskesinde kaya pikseli yok", seen[MapMaskFixture.CLASS_ROCK] > 100)
            assertTrue("harita $mapId pad listesi bos", pads.isNotEmpty())
        }
    }
}

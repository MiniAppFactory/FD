package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.LevelGeometry
import com.miniappfactory.frontlinedefender.game.model.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * VERI BUTUNLUGU — olculmus harita geometrisi (docs/level_geometry/).
 *
 * Bu testler kasitli olarak **yalnizca veriye** bakar. `GameEngine` /
 * `GameCanvas` imzalari degisse bile gecerli kalirlar.
 */
class LevelGeometryDataTest {

    private val maps get() = LevelGeometry.ALL_MAPS

    // ---------------------------------------------------------------- yapisal

    @Test
    fun thereAreExactlyElevenMaps() {
        assertEquals("olculmus harita sayisi", 11, maps.size)
    }

    @Test
    fun mapIdsAreUniqueAndCoverOneThroughEleven() {
        val ids = maps.map { it.levelId }
        assertEquals("levelId'ler tekil olmali", ids.size, ids.toSet().size)
        assertEquals((1..11).toSet(), ids.toSet())
    }

    @Test
    fun everyMapHasANonBlankNameAndDescription() {
        maps.forEach { m ->
            assertTrue("harita ${m.levelId} adi bos", m.name.isNotBlank())
            assertTrue("harita ${m.levelId} aciklamasi bos", m.description.isNotBlank())
        }
    }

    // ------------------------------------------------------------- build pads

    @Test
    fun everyMapHasAtLeastEightBuildPads() {
        maps.forEach { m ->
            assertTrue(
                "harita ${m.levelId} yalnizca ${m.buildSpots.size} pad iceriyor (>=8 gerekli)",
                m.buildSpots.size >= 8
            )
        }
    }

    @Test
    fun buildPadIdsAreUniqueWithinEachMap() {
        maps.forEach { m ->
            val ids = m.buildSpots.map { it.id }
            assertEquals(
                "harita ${m.levelId} pad id'leri tekil degil: $ids",
                ids.size, ids.toSet().size
            )
        }
    }

    @Test
    fun buildPadCoordinatesAreInsideTheUnitSquare() {
        maps.forEach { m ->
            m.buildSpots.forEach { s ->
                assertTrue(
                    "harita ${m.levelId} pad ${s.id} normX=${s.normX} 0..1 disinda",
                    s.normX in 0f..1f
                )
                assertTrue(
                    "harita ${m.levelId} pad ${s.id} normY=${s.normY} 0..1 disinda",
                    s.normY in 0f..1f
                )
            }
        }
    }

    /**
     * Iki pad ust uste binerse oyuncu istedigini secemez. Dokunma yaricapi
     * referans tuvalde 46 px, yani iki pad merkezi arasinda en az 2*46 = 92
     * referans px olmali ki secim tek anlamli kalsin.
     */
    @Test
    fun buildPadsAreFartherApartThanTheTapRadius() {
        val minSeparation = 2f * GameConfig.TAP_RADIUS_REF_PX
        maps.forEach { m ->
            val pts = m.buildSpots.map { it.id to GeometryTestSupport.toRef(PointF(it.normX, it.normY)) }
            for (i in pts.indices) {
                for (j in i + 1 until pts.size) {
                    val d = hypot(pts[i].second.x - pts[j].second.x, pts[i].second.y - pts[j].second.y)
                    assertTrue(
                        "harita ${m.levelId}: pad ${pts[i].first} ve ${pts[j].first} " +
                            "arasi yalnizca ${"%.1f".format(d)} ref-px (>= $minSeparation gerekli)",
                        d >= minSeparation
                    )
                }
            }
        }
    }

    @Test
    fun totalBuildPadCountMatchesTheMeasuredGeometryReport() {
        // GEOMETRY_REPORT.md: 11 harita, 134 build pad.
        assertEquals(134, maps.sumOf { it.buildSpots.size })
    }

    // ------------------------------------------------------------- waypoints

    @Test
    fun everyMapHasAtLeastTwoWaypoints() {
        maps.forEach { m ->
            assertTrue("harita ${m.levelId} rota noktasi < 2", m.waypoints.size >= 2)
        }
    }

    @Test
    fun everyRouteStartsOffScreenLeftAndEndsOffScreenRight() {
        allRoutes().forEach { (label, route) ->
            assertEquals("$label ilk nokta x", -0.05f, route.first().x, 1e-4f)
            assertEquals("$label son nokta x", 1.05f, route.last().x, 1e-4f)
        }
    }

    @Test
    fun everyRouteAdvancesMonotonicallyWithNoZeroLengthSegments() {
        allRoutes().forEach { (label, route) ->
            val r = route.map { GeometryTestSupport.toRef(it) }
            for (i in 0 until r.size - 1) {
                val d = hypot(r[i + 1].x - r[i].x, r[i + 1].y - r[i].y)
                assertTrue(
                    "$label segment $i sifir/negatif uzunlukta (d=$d)",
                    d > 0f
                )
                assertTrue(
                    "$label segment $i cok kisa (${"%.2f".format(d)} ref-px) — " +
                        "ardisik noktalar pratikte ayni",
                    d >= 5f
                )
            }
        }
    }

    @Test
    fun noRouteContainsDuplicateConsecutivePoints() {
        allRoutes().forEach { (label, route) ->
            for (i in 0 until route.size - 1) {
                assertTrue(
                    "$label indeks $i ve ${i + 1} ayni nokta",
                    route[i] != route[i + 1]
                )
            }
        }
    }

    // ------------------------------------------------------------ alt routes

    @Test
    fun altRoutesExistOnlyForTheForkedMaps() {
        // DECISIONS: yol yalnizca 1, 2, 3, 4, 11'de catallaniyor.
        assertEquals(setOf(1, 2, 3, 4, 11), LevelGeometry.ALT_ROUTES.keys)
    }

    @Test
    fun everyAltRouteHasAtLeastTwoPoints() {
        LevelGeometry.ALT_ROUTES.forEach { (id, route) ->
            assertTrue("ALT_ROUTES[$id] rota noktasi < 2", route.size >= 2)
        }
    }

    /**
     * Alt rota ile birincil rota AYNI USTE varmali — aksi halde bir kol
     * oyuncunun ussune, diger kol bosluga gider.
     */
    @Test
    fun altRoutesEndAtExactlyTheSameBaseAsTheirPrimaryRoute() {
        LevelGeometry.ALT_ROUTES.forEach { (id, alt) ->
            val primary = LevelData.forMapId(id).waypoints
            val d = hypot(
                (primary.last().x - alt.last().x) * GeometryTestSupport.refW,
                (primary.last().y - alt.last().y) * GeometryTestSupport.refH
            )
            assertEquals("harita $id: alt rota usse birincil rotadan farkli varıyor", 0f, d, 1f)
        }
    }

    /**
     * Spawn ucunda iki kol AYNI NOKTADAN cikmak zorunda degil (catallanma
     * spawn'in hemen sonrasinda olur), ama ayni spawn AGZINDA kalmali:
     * boyali yolun yarim genisligi ~37-49 ref-px oldugu icin 60 ref-px'lik
     * sapma hâlâ ayni yol agzi sayilir. Olculen en buyuk sapma: harita 2, 50.5.
     */
    @Test
    fun altRoutesStartWithinOneRoadWidthOfTheirPrimarySpawn() {
        val tolerance = 60f
        LevelGeometry.ALT_ROUTES.forEach { (id, alt) ->
            val primary = LevelData.forMapId(id).waypoints
            val d = hypot(
                (primary.first().x - alt.first().x) * GeometryTestSupport.refW,
                (primary.first().y - alt.first().y) * GeometryTestSupport.refH
            )
            assertTrue(
                "harita $id: alt rota spawn'i birincilden ${"%.1f".format(d)} ref-px " +
                    "uzakta (<= $tolerance gerekli)",
                d <= tolerance
            )
        }
    }

    // ------------------------------------------------- routesForMapId sozlesme

    @Test
    fun routesForMapIdNeverReturnsAnEmptyOrDegenerateRoute() {
        for (mapId in 1..11) {
            val routes = LevelData.routesForMapId(mapId)
            assertTrue("harita $mapId icin rota listesi bos", routes.isNotEmpty())
            routes.forEachIndexed { i, r ->
                assertTrue("harita $mapId rota $i en az 2 nokta icermeli", r.size >= 2)
            }
        }
    }

    @Test
    fun trueForkMapsExposeTwoRoutesAndAllOthersExposeOne() {
        // DECISIONS + LevelData: gercek catallanma 1, 2, 4, 11.
        // Harita 3 KASITLI olarak tek rotalidir ve kanonik rota ALT_ROUTES[3]'tur.
        val expectedTwo = setOf(1, 2, 4, 11)
        for (mapId in 1..11) {
            val n = LevelData.routesForMapId(mapId).size
            val want = if (mapId in expectedTwo) 2 else 1
            assertEquals("harita $mapId rota sayisi", want, n)
        }
    }

    @Test
    fun mapThreeUsesTheLongAlternateBranchAsItsCanonicalRoute() {
        // DECISIONS: "Harita 3'te kanonik rota ALT_ROUTES[3]'tur, waypoints degil:
        // kisa kol koca bir ilmegi atlayip 4 pad'i olu birakiyor."
        val routes = LevelData.routesForMapId(3)
        assertEquals(1, routes.size)
        assertEquals(LevelGeometry.ALT_ROUTES[3], routes.single())

        val canonical = GeometryTestSupport.polylineLength(routes.single())
        val shortArm = GeometryTestSupport.polylineLength(LevelData.forMapId(3).waypoints)
        assertTrue(
            "kanonik rota kisa koldan uzun olmali (kanonik=$canonical kisa=$shortArm)",
            canonical > shortArm
        )
    }

    @Test
    fun forMapIdReturnsTheMapWithTheRequestedId() {
        for (mapId in 1..11) {
            assertEquals(mapId, LevelData.forMapId(mapId).levelId)
        }
    }

    // ------------------------------------------- pad <-> yol erisilebilirligi

    /**
     * Bir pad'e kurulan kule yola ATES EDEBILMELI. Pad, oyundaki en uzun kule
     * menzilinden daha uzaktaysa o pad OLUDUR: uzerine ne kurulursa kurulsun
     * hicbir dusmani vuramaz.
     *
     * Faz 10: en uzun menzil ANTI_ARMOR kademe 2 (280) DEGIL, artik SLOW
     * kademe 2 (320 ref-px) — destek kulesinin menzili bilincli olarak en genis
     * (testci: "buz kulesinin kapsama alani buyuk olmali").
     *
     * Menzil buyudugu icin OLU PAD SAYISI 11'DEN 7'YE DUSTU: harita 7 pad 3,
     * harita 8 pad 8 ve 11, harita 10 pad 17 artik (yalnizca Frost Field ile)
     * yola erisiyor. Kalan 7 pad hâlâ olu ve DECISIONS bunlari sanat karari
     * olarak kabul etmis durumda. Liste yine donduruluyor ki gelecekte sessizce
     * degismesin.
     *
     * DECISIONS bu "uzak pad"leri sanat karari olarak KABUL ETTI, bu yuzden
     * test hard-fail etmiyor; bunun yerine listeyi **donduruyor**. Yeni bir olu
     * pad eklenirse ya da mevcut biri duzeltilirse bu test kirilir ve karar
     * yeniden gozden gecirilir.
     *
     * Oynanis etkisi ve tam mesafe tablosu: docs/QA_REPORT.md.
     */
    @Test
    fun theSetOfDeadBuildPadsMatchesTheFrozenKnownList() {
        val maxRange = GeometryTestSupport.maxTowerRange()
        assertEquals("en uzun kule menzili degisti — dondurulmus liste yenilenmeli", 320f, maxRange, 0.01f)

        // Faz 10 olcumu (maxRange 320 ref-px). Onceki liste (maxRange 280):
        //   3=[10], 6=[4,10], 7=[3,6], 8=[6,8,11], 9=[5], 10=[8,17]
        val frozenDeadPads: Map<Int, Set<Int>> = mapOf(
            3 to setOf(10),
            6 to setOf(4, 10),
            7 to setOf(6),
            8 to setOf(6),
            9 to setOf(5),
            10 to setOf(8)
        )

        val measured = sortedMapOf<Int, Set<Int>>()
        for (mapId in 1..11) {
            val routes = LevelData.routesForMapId(mapId)
            val dead = LevelData.forMapId(mapId).buildSpots
                .filter { GeometryTestSupport.padToNearestRoute(it.normX, it.normY, routes) > maxRange }
                .map { it.id }
                .toSet()
            if (dead.isNotEmpty()) measured[mapId] = dead
        }

        assertEquals(
            "olu pad kumesi degisti (harita -> pad id). Beklenen dondurulmus liste " +
                "ile olculen farkli. docs/QA_REPORT.md B-02'yi guncelle.",
            frozenDeadPads, measured.toMap()
        )
    }

    /**
     * `DECISIONS.md` P3 satiri "130 ref-px ile 106/134 pad karsilaniyor, medyan
     * 119" diyor. Medyan dogru; 106 sayisi DEGIL. Bu test gercek dagilimi
     * pinliyor ki dokumantasyon duzeltilene kadar sayi kaybolmasin.
     */
    @Test
    fun padToRoadDistanceDistributionIsPinned() {
        val distances = (1..11).flatMap { mapId ->
            val routes = LevelData.routesForMapId(mapId)
            LevelData.forMapId(mapId).buildSpots.map {
                GeometryTestSupport.padToNearestRoute(it.normX, it.normY, routes)
            }
        }.sorted()

        assertEquals(134, distances.size)

        val median = distances[distances.size / 2]
        assertEquals("medyan pad-yol mesafesi (DECISIONS P3: 119)", 120f, median, 3f)

        val within130 = distances.count { it <= 130f }
        val beyondShortestL1 = distances.count { it > GeometryTestSupport.minLevel1Range() }

        // Gercek olculum. DECISIONS P3'teki "106/134" yanlis; asil deger 79.
        // 106, "160 ref-px (MACHINE_GUN L1) icindeki pad sayisi = 105" ile
        // karistirilmis gorunuyor (134 - 28 uzak pad = 106).
        assertEquals("130 ref-px icindeki pad sayisi", 79, within130)
        assertEquals(
            // Faz 10: en kisa L1 menzili artik MACHINE_GUN (160 -> 150 ref-px);
            // SLOW 270'e cikti. Sayi ayni cunku esik degeri hâlâ 150.
            "en kisa L1 menzili (MACHINE_GUN = 150 ref-px) disindaki pad sayisi",
            41, beyondShortestL1
        )
        assertEquals(
            "160 ref-px disindaki pad sayisi — DECISIONS'in '28 uzak pad'i",
            29, distances.count { it > 160f }
        )
    }

    // -------------------------------------------------------------- yardimci

    private fun allRoutes(): List<Pair<String, List<PointF>>> = buildList {
        maps.forEach { add("harita ${it.levelId} birincil rota" to it.waypoints) }
        LevelGeometry.ALT_ROUTES.forEach { (id, r) -> add("harita $id ALT rota" to r) }
    }

    // -------------------------------------- LevelData varsayilan alan davranisi

    @Test
    fun levelGeometryNeverPopulatesTheLegacyAlternateWaypointsField() {
        // LevelData.alternateWaypoints olu bir alan: ikinci kol ALT_ROUTES'ta
        // durur ve motora routesForMapId() uzerinden gelir. Biri buraya veri
        // yazarsa iki farkli dogruluk kaynagi olusur.
        maps.forEach { m ->
            assertTrue(
                "harita ${m.levelId} alternateWaypoints dolu — ikinci kaynak olustu",
                m.alternateWaypoints.isEmpty()
            )
        }
    }

    @Test
    fun altRouteLookupIsNullForNonForkedMaps() {
        listOf(5, 6, 7, 8, 9, 10).forEach { id ->
            assertNull("harita $id catallanmiyor ama ALT_ROUTES kaydi var", LevelGeometry.ALT_ROUTES[id])
        }
        listOf(1, 2, 3, 4, 11).forEach { id ->
            assertNotNull("harita $id catallaniyor ama ALT_ROUTES kaydi yok", LevelGeometry.ALT_ROUTES[id])
        }
    }

    @Test
    fun routeLengthsAreWithinAPlausibleBand() {
        // Olculen aralik 2353..3195 ref-px. Cok kisa bir rota dusmanin usse
        // kosarcasina varmasi, cok uzun rota olu bekleme demektir.
        //
        // NOT: GEOMETRY_REPORT.md harita basina "yol=1785..2283 ref-px" diyor;
        // o sayi maske uzerindeki Dijkstra yolu, buradaki ise waypoint
        // polylinesinin uzunlugu (ekran disi spawn/us uzantilari dahil). Ayni
        // metrik degil, bu yuzden rapordaki sayiya karsi assert EDILMEZ.
        allRoutes().forEach { (label, route) ->
            val len = GeometryTestSupport.polylineLength(route)
            assertTrue("$label rota uzunlugu $len ref-px — 2000'den kisa", len > 2000f)
            assertTrue("$label rota uzunlugu $len ref-px — 3500'den uzun", len < 3500f)
        }
    }

    @Test
    fun referenceCanvasIsTheSixteenByNineContractFromDecisionB3() {
        assertEquals(1920f, GameConfig.REFERENCE_WIDTH, 0f)
        assertEquals(1080f, GameConfig.REFERENCE_HEIGHT, 0f)
        // Harita bitmap'i 1920x1081 -> en-boy orani buna gore.
        assertEquals(1920f / 1081f, GameConfig.MAP_ASPECT_RATIO, 1e-6f)
        assertTrue(
            "referans tuval en-boy orani harita en-boy oranindan cok sapmamali",
            abs(GameConfig.REFERENCE_WIDTH / GameConfig.REFERENCE_HEIGHT - GameConfig.MAP_ASPECT_RATIO) < 0.01f
        )
    }
}

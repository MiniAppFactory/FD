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

    /**
     * Betik her rotayi yay uzunlugunun %5'i araligiyla ornekler -> 21 OLCULMUS
     * nokta. v1'de buna 2 ekran disi uc eklendigi icin sayi 23'tu; uclar
     * kaldirildi. Sayi kilitli ki uc uzatmasi sessizce geri gelmesin.
     */
    @Test
    fun everyRouteHasExactlyTheTwentyOneMeasuredWaypoints() {
        allRoutes().forEach { (label, route) ->
            assertEquals("$label waypoint sayisi", 21, route.size)
        }
    }

    /**
     * KALICI KILIT — hicbir rota noktasi ekranin disinda olamaz.
     *
     * v1 geometrisi her rotanin iki ucunu YATAY olarak ekran disina uzatiyordu
     * (`extract_geometry.py::extend_ends`, ilk x=-0.05 / son x=1.05). Cihazda
     * iki gorunur hataya yol acti:
     *   1. Dusman ekran DISINDA doguyor, cikis bunkerinin agzindan degil onun
     *      USTUNDEN geciyordu.
     *   2. Hedef ussun rampasini gecip ekrandan disari yuruyor, can ancak orada
     *      dusuyordu — "usse girip kayboluyor" hissi yoktu.
     *
     * Uclar kaldirildi: her rotanin ilk noktasi bunker yol agzi, son noktasi us
     * rampasidir. Bu test o hatanin geri gelmesini engeller.
     */
    @Test
    fun everyRouteWaypointStaysInsideTheUnitSquare() {
        allRoutes().forEach { (label, route) ->
            route.forEachIndexed { i, p ->
                assertTrue(
                    "$label nokta $i x=${p.x} 0..1 disinda — ekran disi rota ucu geri geldi",
                    p.x in 0f..1f
                )
                assertTrue(
                    "$label nokta $i y=${p.y} 0..1 disinda — ekran disi rota ucu geri geldi",
                    p.y in 0f..1f
                )
            }
        }
    }

    /**
     * Ucu kaldirmak yetmez: uc noktanin OLCULEN kapi agzinda kalmasi gerekir.
     * Bir rota kenara cok yakin baslar/biterse dusman yine ekran kenarindan
     * belirmis gibi gorunur.
     */
    @Test
    fun everyRouteStartsAndEndsAtAMeasuredGateMouthNotAtTheScreenEdge() {
        allRoutes().forEach { (label, route) ->
            assertTrue(
                "$label spawn ucu x=${route.first().x} — ekran kenarina yapisik",
                route.first().x in 0.05f..0.30f
            )
            assertTrue(
                "$label us ucu x=${route.last().x} — ekran kenarina yapisik",
                route.last().x in 0.70f..0.95f
            )
        }
    }

    /**
     * Harita 1 uc noktalari cihazda gozle dogrulandi: sol kenardaki cikis
     * bunkerinin yol agzi ve sagdaki sekizgen ussun rampasi. Bu iki deger
     * pinlenir ki uretici betik degistiginde sessizce kaymasinlar.
     */
    @Test
    fun mapOneEndpointsMatchTheVisuallyVerifiedGateMouths() {
        val wp = LevelData.forMapId(1).waypoints
        assertEquals("harita 1 bunker agzi x", 0.1388f, wp.first().x, 1e-4f)
        assertEquals("harita 1 bunker agzi y", 0.4638f, wp.first().y, 1e-4f)
        assertEquals("harita 1 us rampasi x", 0.8804f, wp.last().x, 1e-4f)
        assertEquals("harita 1 us rampasi y", 0.6638f, wp.last().y, 1e-4f)
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
     * OLU PAD TABLOSU — harita basina, KANONIK kol uzerinde.
     *
     * ESKI HALI YANLISTI, iki ayri sebepten (docs/PAD_COVERAGE_REPORT.md 5):
     *  1. Mesafeyi `routes.minOf { }` ile, yani TUM kollarin minimumu olarak
     *     oluyordu. Harita 1 pad 3 icin ikinci kolun 116'sini goruyor, bolum
     *     1'de gecerli olan 444'u HIC gormuyordu — oysa ikinci kol bolum <
     *     [GameConfig.ALT_ROUTE_FIRST_LEVEL] iken motorda hic kullanilmaz.
     *     Sonuc: test 7 olu pad rapor ederken gercek sayi 34 pad-bolum ornegiydi.
     *  2. Esik olarak 320 (SLOW kademe-2) kullanilmisti. Oyuncu kuleyi once
     *     KURAR sonra yukseltir; kurulum aninda erisilebilen en genis menzil
     *     kademe-1'inkidir (SLOW 270). 320 hicbir bolumde gecerli degil.
     *
     * Bu test artik **harita geometrisinin kendisini** dondurur: her haritada,
     * o haritanin KANONIK kolunda (harita 3 icin `ALT_ROUTES[3]`, digerleri
     * icin `waypoints` — yani `routesForMapId(...).first()`) oyundaki en genis
     * kademe-1 menzilinin disinda kalan pad'ler. Bunlar hicbir bolumde, hicbir
     * kule ile calismayan pad'lerdir; sanatta pad'i tasimadan duzelmezler.
     *
     * BOLUM BAZINDA oynanabilirlik burada DEGIL, [PadReachabilityPerLevelTest]
     * icinde zorlanir — orasi hard-fail eder. Bu test yalnizca veri kaymasini
     * yakalar: liste buyurse yeni bir pad kullanilamaz hale gelmis demektir.
     */
    @Test
    fun theSetOfDeadBuildPadsMatchesTheFrozenKnownList() {
        val widestTier1 = GameConfig.TOWER_SPECS.values.maxOf { it.level1Range }
        assertEquals(
            "oyundaki en genis kademe-1 menzili degisti — dondurulmus liste yenilenmeli",
            270f, widestTier1, 0.01f
        )

        // Olcum: docs/PAD_COVERAGE_REPORT.md 2. tablo (kanonik kol, esik 270).
        val frozenDeadPads: Map<Int, Set<Int>> = mapOf(
            1 to setOf(3, 7, 9),            // 444 / 346 / 295 ref-px
            2 to setOf(3, 4, 7, 8, 10, 12), // 372 / 409 / 582 / 383 / 553 / 272
            3 to setOf(10),                 // 327
            4 to setOf(4, 7, 9, 12),        // 406 / 487 / 621 / 383
            5 to setOf(3),                  // 274
            6 to setOf(4, 10),              // 324 / 355
            7 to setOf(3, 6),               // 311 / 347
            8 to setOf(6, 8, 11),           // 336 / 316 / 311
            9 to setOf(5),                  // 320
            10 to setOf(8, 17),             // 535 / 316
            11 to setOf(1, 4, 5, 8, 10)     // 388 / 355 / 497 / 359 / 434
        )

        val measured = sortedMapOf<Int, Set<Int>>()
        for (mapId in 1..11) {
            val canonical = listOf(LevelData.routesForMapId(mapId).first())
            val dead = LevelData.forMapId(mapId).buildSpots
                .filter {
                    GeometryTestSupport.padToNearestOfRoutes(it.normX, it.normY, canonical) > widestTier1
                }
                .map { it.id }
                .toSet()
            if (dead.isNotEmpty()) measured[mapId] = dead
        }

        assertEquals(
            "olu pad kumesi degisti (harita -> pad id, kanonik kol, esik ${widestTier1.toInt()} " +
                "ref-px). docs/PAD_COVERAGE_REPORT.md 2. tabloyu guncelle.",
            frozenDeadPads, measured.toMap()
        )
    }

    /**
     * `DECISIONS.md` P3 satiri "130 ref-px ile 106/134 pad karsilaniyor, medyan
     * 119" diyor. Medyan dogru; 106 sayisi DEGIL. Bu test gercek dagilimi
     * pinliyor ki dokumantasyon duzeltilene kadar sayi kaybolmasin.
     *
     * KAPSAM UYARISI: buradaki dagilim **HAM HARITA GEOMETRISIDIR** — pad'in
     * haritadaki HERHANGI bir kola uzakligi. "Oyuncu bu pad'i kullanabilir mi"
     * sorusunun cevabi DEGILDIR; o soru bolume gore degisir ve
     * [PadReachabilityPerLevelTest] icinde AKTIF rota uzerinden olculur.
     * Ikisini karistirmak tam olarak olu-pad hatasinin kok nedeniydi, bu yuzden
     * fonksiyon adi da acikca `padToNearestOfRoutes`.
     */
    @Test
    fun padToRoadDistanceDistributionIsPinned() {
        val distances = (1..11).flatMap { mapId ->
            val routes = LevelData.routesForMapId(mapId)
            LevelData.forMapId(mapId).buildSpots.map {
                GeometryTestSupport.padToNearestOfRoutes(it.normX, it.normY, routes)
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
        // Olculen aralik 1660..2535 ref-px. Cok kisa bir rota dusmanin usse
        // kosarcasina varmasi, cok uzun rota olu bekleme demektir.
        //
        // Ekran disi uclar kaldirildiktan sonra bant ~690 ref-px asagi kaydi
        // (eski aralik 2353..3195). Yeni degerler artik GEOMETRY_REPORT.md'nin
        // Dijkstra "yol" uzunluklariyla (1697..2283 ref-px) ayni buyuklukte —
        // yine de ayni metrik degil (biri maske yolu, digeri waypoint
        // polylinesi), bu yuzden rapordaki sayiya karsi assert EDILMEZ.
        allRoutes().forEach { (label, route) ->
            val len = GeometryTestSupport.polylineLength(route)
            assertTrue("$label rota uzunlugu $len ref-px — 1500'den kisa", len > 1500f)
            assertTrue("$label rota uzunlugu $len ref-px — 3000'den uzun", len < 3000f)
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

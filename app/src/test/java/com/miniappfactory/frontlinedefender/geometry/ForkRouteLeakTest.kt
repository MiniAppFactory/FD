package com.miniappfactory.frontlinedefender.geometry

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * ROTA SIZINTISI — "dusmanin gelecegi kol daha dalga baslamadan belli mi?"
 * ===========================================================================
 *
 * Cihaz geri bildirimi: *"gelecekleri yol belli oluyor, bilerek mi boyle?
 * bence tumu aktif olmali ki gelecekleri yol belli olmasin."*
 *
 * MEKANIZMA: catallanan haritalarda (1, 2, 4, 11) sanat IKI yol cizer. Ikinci
 * kol kapaliyken `GameConfig.OUT_OF_RANGE_PADS` o kolun yanindaki pad'leri
 * gizler — cunku oraya kurulan kule gercekten hicbir seye ates edemez. Sonuc:
 * **kalan yesil pad'ler kullanilan kolu ciziyor.** Yani sizinti pad renginden
 * degil, haritanin yarisinin dekoratif olmasindan geliyor.
 *
 * COZUM (bkz. [GameConfig.ALT_ROUTE_FIRST_LEVEL]): esik 9 -> 3. Ucuncu
 * bolumden itibaren her iki kol da aktif; motor her dusmani seed'li olarak
 * bir kola atar, dolayisiyla gizlenecek pad de kalmaz.
 *
 * BU DOSYA NEYI KILITLER
 *  1. Catal ACIK olan bolumlerde, catallanan haritada gizlenen pad SAYISI —
 *     Act I'de sifir olmalidir (gizleme = sizinti).
 *  2. Ogretme diliminde (catal kapali) sizintinin BILEREK kabul edildigi
 *     bolumler donduruldu; liste buyurse test kirilir.
 *  3. Esik, Tedarigin birden fazla kule aldigi ilk bolumdur.
 */
class ForkRouteLeakTest {

    /** Sanatta gercekten iki kol cizili olan haritalar. */
    private val forkedMaps: List<Int> =
        (1..11).filter { LevelData.routesForMapId(it).size > 1 }

    @Test
    fun theMapsThatActuallyForkAreTheExpectedFour() {
        // Harita 3 KASITLI olarak disarida: oradaki "ikinci rota" serpantin U
        // donuslerinin sanatta birlesmesinden dogar, tasarim catallanmasi
        // degildir; kanonik rota tektir (LevelData.ALT_IS_CANONICAL_MAP_ID).
        assertEquals(listOf(1, 2, 4, 11), forkedMaps)
    }

    /**
     * SIZINTI OLCUMU. Catal ACIKKEN catallanan haritada gizlenmis pad kalmasi,
     * sizintinin devam ettigi anlamina gelir.
     */
    @Test
    fun noPadIsHiddenOnAForkedMapOnceBothArmsAreLive() {
        val leaks = mutableListOf<String>()
        GameConfig.CAMPAIGN.filter { it.act == 1 }.forEach { spec ->
            if (spec.mapId !in forkedMaps) return@forEach
            if (!GameConfig.usesAlternateRoutes(spec.levelId)) return@forEach
            if (spec.disabledPadIds.isNotEmpty()) {
                leaks += "blm ${spec.levelId} (harita ${spec.mapId}): " +
                    "iki kol acik ama ${spec.disabledPadIds} gizli"
            }
        }
        assertTrue(
            "CATAL ACIKKEN GIZLI PAD — kalan pad'ler yine kullanilan kolu cizer:\n" +
                leaks.joinToString("\n"),
            leaks.isEmpty()
        )
    }

    /**
     * OGRETME DILIMI — bilerek kabul edilen sizinti, DONDURULMUS liste.
     *
     * L1 Tedarik 80, L2 Tedarik 90 = **tek** Gatling. Iki kolu birden acmak bu
     * iki bolumu "iki kolu ayni anda goren tek pad'i bul" bulmacasina cevirir
     * (harita 1'de o pad yalnizca id 10'dur) ve RNG dogrudan yenilgi sebebi
     * olur — duzeltilmeye calisilan hatanin ta kendisi. Bu yuzden L1-L2 tek
     * kol kalir ve sizinti KABUL EDILIR.
     *
     * Liste buyurse test kirilir: "bir bolum daha ogretici olsun" karari
     * sessizce alinamaz.
     */
    @Test
    fun theOnlyLevelsThatStillLeakTheRouteAreTheTutorialPair() {
        val leaking = GameConfig.CAMPAIGN
            .filter { it.mapId in forkedMaps }
            .filter { !GameConfig.usesAlternateRoutes(it.levelId) }
            .map { it.levelId }
        assertEquals(
            "rota sizintisi olan bolumler degisti — ALT_ROUTE_FIRST_LEVEL ya da " +
                "kampanya harita sirasi degismis olmali",
            listOf(1, 2), leaking
        )
    }

    /**
     * Esikte iki kolu ayni anda savunmak GERCEKTEN mumkun mu? Kagit uzerinde
     * "iki kol var" demek yetmez: oyuncunun kadrosu her iki kola da yetismeli.
     */
    @Test
    fun atTheForkThresholdEachArmHasEnoughReachablePads() {
        val lv = GameConfig.ALT_ROUTE_FIRST_LEVEL
        GameConfig.CAMPAIGN.filter { it.levelId >= lv && it.act == 1 }
            .filter { it.mapId in forkedMaps }
            .forEach { spec ->
                val routes = LevelData.routesForMapId(spec.mapId)
                val range = GeometryTestSupport.maxUnlockedLevel1Range(spec.levelId)
                val pads = LevelData.forMapId(spec.mapId).buildSpots
                    .filter { it.id !in spec.disabledPadIds }
                routes.forEachIndexed { i, route ->
                    val reach = pads.count {
                        GeometryTestSupport.padToNearestOfRoutes(
                            it.normX, it.normY, listOf(route)
                        ) <= range
                    }
                    assertTrue(
                        "blm ${spec.levelId} (harita ${spec.mapId}) kol ${i + 1}: " +
                            "yalnizca $reach pad erisiyor — iki cephe kurulamaz",
                        reach >= 2
                    )
                }
            }
    }

    /**
     * KAYIT — kararin dayandigi tablo. Kirilmaz, YAZDIRIR: bir sonraki denge
     * degisikliginde ayni sorunun sayilari elde olsun.
     */
    @Test
    fun printForkCoverageTable() {
        val sb = StringBuilder(
            "\n== CATALLANAN HARITALARDA KOL KAPSAMASI ==\n" +
                "blm har catal menzil | tekKolOlu | ikiKolOlu | ikiKoluGoren | tedarik\n"
        )
        (1..11).forEach { lv ->
            val all = LevelData.routesForMapId(lv)
            val range = GeometryTestSupport.maxUnlockedLevel1Range(lv)
            val pads = LevelData.forMapId(lv).buildSpots
            val singleDead = pads.filter {
                GeometryTestSupport.padToNearestOfRoutes(it.normX, it.normY, listOf(all.first())) > range
            }.map { it.id }
            val bothDead = pads.filter {
                GeometryTestSupport.padToNearestOfRoutes(it.normX, it.normY, all) > range
            }.map { it.id }
            val seesBoth = if (all.size < 2) emptyList() else pads.filter { p ->
                all.all {
                    GeometryTestSupport.padToNearestOfRoutes(p.normX, p.normY, listOf(it)) <= range
                }
            }.map { it.id }
            sb.append(
                "%3d %3d %-5s %6d | %-22s | %-14s | %-14s | %d\n".format(
                    lv, lv, all.size > 1, range.toInt(),
                    singleDead.toString(), bothDead.toString(), seesBoth.toString(),
                    GameConfig.startingSupplyFor(lv)
                )
            )
        }
        println(sb)
    }
}

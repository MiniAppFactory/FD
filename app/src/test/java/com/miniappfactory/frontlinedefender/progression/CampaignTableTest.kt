package com.miniappfactory.frontlinedefender.progression

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.LevelData
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VERI BUTUNLUGU — `GameConfig.CAMPAIGN` (bolum <-> harita eslemesi).
 *
 * Bu tablo "TEK BIR YERDE" durmak zorunda (GameConfig KDoc); motor, bolum
 * secme ekrani ve dalga tanimlari hepsi buradan okuyor.
 */
class CampaignTableTest {

    private val campaign get() = GameConfig.CAMPAIGN

    @Test
    fun campaignHasExactlyTwentyTwoLevels() {
        assertEquals(22, campaign.size)
        assertEquals(22, GameConfig.CAMPAIGN_LEVEL_COUNT)
    }

    @Test
    fun levelIdsAreUniqueContiguousAndInAscendingOrder() {
        val ids = campaign.map { it.levelId }
        assertEquals("levelId'ler tekil olmali", ids.size, ids.toSet().size)
        assertEquals((1..22).toList(), ids)
    }

    @Test
    fun everyMapIdIsWithinTheMeasuredGeometryRange() {
        campaign.forEach { spec ->
            assertTrue(
                "bolum ${spec.levelId} mapId=${spec.mapId} — 1..11 disinda",
                spec.mapId in 1..11
            )
        }
    }

    @Test
    fun everyMapIdResolvesToRealMeasuredGeometry() {
        campaign.forEach { spec ->
            val geo = LevelData.forMapId(spec.mapId)
            assertEquals(
                "bolum ${spec.levelId} mapId=${spec.mapId} geometri cozumlemesi yanlis haritaya gitti",
                spec.mapId, geo.levelId
            )
            assertTrue("bolum ${spec.levelId} haritasinda pad yok", geo.buildSpots.isNotEmpty())
            assertTrue("bolum ${spec.levelId} haritasinda rota yok", geo.waypoints.size >= 2)
        }
    }

    @Test
    fun everyLevelHasWaveDefinitions() {
        campaign.forEach { spec ->
            val waves = WaveDefinitions.wavesFor(spec.levelId)
            assertTrue("bolum ${spec.levelId} icin dalga yok", waves.isNotEmpty())
            assertEquals(
                "LevelSpec.waveCount ile WaveDefinitions uyusmuyor",
                waves.size, spec.waveCount
            )
        }
    }

    @Test
    fun allElevenMapsAreUsedByTheCampaign() {
        assertEquals(
            "11 olculmus haritanin hepsi kullanilmali",
            (1..11).toSet(), campaign.map { it.mapId }.toSet()
        )
    }

    @Test
    fun actOneWalksTheMapsForwardAndActTwoWalksThemBackward() {
        val actOne = campaign.filter { it.act == 1 }
        val actTwo = campaign.filter { it.act == 2 }

        assertEquals("Act I 11 bolum olmali", 11, actOne.size)
        assertEquals("Act II 11 bolum olmali", 11, actTwo.size)

        assertEquals("Act I harita sirasi 1..11 olmali", (1..11).toList(), actOne.map { it.mapId })
        assertEquals("Act II harita sirasi 11..1 olmali", (1..11).reversed().toList(), actTwo.map { it.mapId })
    }

    @Test
    fun actNumbersAreOnlyOneOrTwoInVersionOne() {
        // DECISIONS: v1.0 = 22 bolum, Act III/IV sonraki surumler.
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} act=${spec.act} — v1.0'da 1 veya 2 olmali", spec.act in 1..2)
        }
    }

    @Test
    fun actOnePlaysInDaylightAndActTwoAtNight() {
        campaign.forEach { spec ->
            val expected = if (spec.act == 1) GameConfig.MapOverlay.NONE else GameConfig.MapOverlay.NIGHT
            assertEquals("bolum ${spec.levelId} overlay", expected, spec.overlay)
            val expectedBiome = if (spec.act == 1) GameConfig.Biome.TEMPERATE else GameConfig.Biome.NIGHT
            assertEquals("bolum ${spec.levelId} biome", expectedBiome, spec.biome)
        }
    }

    // ------------------------------------------------------ kilit / deployment

    @Test
    fun theFirstSixLevelsAreFreeToEnter() {
        // Oyuncu ilk oturumda coin biriktirmeden 6 bolum oynayabilmeli.
        (1..6).forEach { lv ->
            assertEquals(
                "bolum $lv bastan acik olmali (deploymentCost 0)",
                0, GameConfig.levelSpec(lv).deploymentCost
            )
        }
    }

    @Test
    fun deploymentCostNeverDecreasesAsTheCampaignProgresses() {
        for (lv in 2..22) {
            val prev = GameConfig.levelSpec(lv - 1).deploymentCost
            val curr = GameConfig.levelSpec(lv).deploymentCost
            assertTrue(
                "bolum $lv kilit ucreti ($curr) onceki bolumden ($prev) dusuk",
                curr >= prev
            )
        }
    }

    @Test
    fun deploymentCostsAreNeverNegative() {
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} negatif kilit ucreti", spec.deploymentCost >= 0)
        }
    }

    @Test
    fun everyLevelStartsWithAPositiveSupplyAndBaseLives() {
        campaign.forEach { spec ->
            assertTrue("bolum ${spec.levelId} baslangic tedariki pozitif olmali", spec.startingSupply > 0)
            assertTrue("bolum ${spec.levelId} us cani pozitif olmali", spec.maxBaseLives > 0)
        }
    }

    // ------------------------------------------------------- pad kisiti (Act II)

    @Test
    fun onlyActTwoLevelsDisablePads() {
        campaign.forEach { spec ->
            if (spec.act == 1) {
                assertTrue(
                    "bolum ${spec.levelId} Act I ama pad kisiti var: ${spec.disabledPadIds}",
                    spec.disabledPadIds.isEmpty()
                )
            } else {
                assertTrue(
                    "bolum ${spec.levelId} Act II ama pad kisiti yok — krater kisiti eksik",
                    spec.disabledPadIds.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun everyDisabledPadIdActuallyExistsOnThatLevelsMap() {
        campaign.forEach { spec ->
            val realIds = LevelData.forMapId(spec.mapId).buildSpots.map { it.id }.toSet()
            spec.disabledPadIds.forEach { id ->
                assertTrue(
                    "bolum ${spec.levelId} (harita ${spec.mapId}) var olmayan pad $id'yi " +
                        "devre disi birakiyor — gecerli id'ler: $realIds",
                    id in realIds
                )
            }
        }
    }

    @Test
    fun disabledPadListsContainNoDuplicates() {
        campaign.forEach { spec ->
            assertEquals(
                "bolum ${spec.levelId} pad kisit listesinde tekrar var: ${spec.disabledPadIds}",
                spec.disabledPadIds.size, spec.disabledPadIds.toSet().size
            )
        }
    }

    /** LEVEL_DESIGN.md F.3 D1: pad'lerin %25-40'i devre disi. */
    @Test
    fun disabledPadFractionStaysWithinTheDesignBand() {
        campaign.filter { it.act == 2 }.forEach { spec ->
            val total = LevelData.forMapId(spec.mapId).buildSpots.size
            val fraction = spec.disabledPadIds.size.toFloat() / total
            assertTrue(
                "bolum ${spec.levelId}: ${spec.disabledPadIds.size}/$total pad kapali " +
                    "(%${"%.0f".format(fraction * 100)}) — %25-40 bandinin disinda",
                fraction in 0.24f..0.41f
            )
        }
    }

    /** LEVEL_DESIGN.md F.3 D2: kalan pad sayisi >= max(6, toplamin %60'i). */
    @Test
    fun enoughPadsRemainPlayableAfterTheCraterConstraint() {
        campaign.forEach { spec ->
            val total = LevelData.forMapId(spec.mapId).buildSpots.size
            val remaining = total - spec.disabledPadIds.size
            val floor = maxOf(6, Math.round(total * 0.60f))
            assertTrue(
                "bolum ${spec.levelId}: $total pad'den yalnizca $remaining kaldi " +
                    "(en az $floor olmali)",
                remaining >= floor
            )
        }
    }

    // ------------------------------------------------------ isim / gorunum

    @Test
    fun everyMapHasAnEnglishDisplayName() {
        assertEquals((1..11).toSet(), GameConfig.MAP_NAMES_EN.keys)
        GameConfig.MAP_NAMES_EN.forEach { (id, name) ->
            assertTrue("harita $id adi bos", name.isNotBlank())
        }
        assertEquals(
            "harita adlari tekil olmali",
            GameConfig.MAP_NAMES_EN.size, GameConfig.MAP_NAMES_EN.values.toSet().size
        )
    }

    @Test
    fun displayNameNeverFallsBackToThePlaceholderForARealLevel() {
        campaign.forEach { spec ->
            assertTrue(
                "bolum ${spec.levelId} yer tutucu ad kullaniyor: ${spec.displayName}",
                !spec.displayName.startsWith("Sector ")
            )
        }
    }

    @Test
    fun theTwoActsReuseTheSameElevenMapNames() {
        // "Ters sira = harita SIRASI" (DECISIONS): Act II yeni harita sanati
        // getirmiyor, ayni 11 haritayi tersten oynatiyor.
        val actOneNames = campaign.filter { it.act == 1 }.map { it.displayName }.toSet()
        val actTwoNames = campaign.filter { it.act == 2 }.map { it.displayName }.toSet()
        assertEquals(actOneNames, actTwoNames)
    }

    // --------------------------------------------------- levelSpec() sozlesmesi

    @Test
    fun levelSpecReturnsTheRequestedLevelForEveryValidId() {
        for (lv in 1..22) {
            assertEquals(lv, GameConfig.levelSpec(lv).levelId)
        }
    }

    @Test
    fun levelSpecFallsBackToTheFirstLevelForOutOfRangeIds() {
        listOf(0, -5, 23, 999).forEach { bad ->
            assertEquals(
                "gecersiz bolum $bad icin bolum 1'e dusulmeli",
                1, GameConfig.levelSpec(bad).levelId
            )
        }
    }

    // ------------------------------------ APK'da bulunan harita bitmap'leri

    /**
     * `SHIPPED_MAP_IDS` motorun yedek harita dalini suruyor: bir bolumun
     * bitmap'i APK'da yoksa `MAP_FALLBACK_ID` geometrisine dusuyor.
     *
     * Su an YALNIZCA harita 1 gonderiliyor, yani 22 bolumun 20'si (harita 1'i
     * kullanan bolum 1 ve 22 haric) Meadow Pass olarak oynaniyor. Bu KASITLI
     * ve gecici; bu test hangi noktada oldugumuzu pinliyor ki 10 bitmap
     * eklendiginde test kirilip guncellenmeyi hatirlatsin.
     *
     * Oyuncuya gorunen sonuc ve bolum secme ekranindaki yanlis vaat:
     * docs/QA_REPORT.md B-05.
     */
    @Test
    fun shippedMapCoverageIsPinnedAndTheFallbackIsAlwaysShipped() {
        assertTrue(
            "MAP_FALLBACK_ID (${GameConfig.MAP_FALLBACK_ID}) SHIPPED_MAP_IDS icinde " +
                "OLMALI — yoksa yedek harita da cizilemez ve her bolum bozulur",
            GameConfig.MAP_FALLBACK_ID in GameConfig.SHIPPED_MAP_IDS
        )
        GameConfig.SHIPPED_MAP_IDS.forEach { id ->
            assertTrue("SHIPPED_MAP_IDS gecersiz harita $id iceriyor", id in 1..11)
        }

        // Faz 4b: 11 harita bitmap'inin TAMAMI pakete girdi
        // (bg_level_01..11.webp, 1920px WebP q80, toplam 3.34 MB).
        // Bu assertion bir GUARD: harita envanteri degisirse haber verir.
        assertEquals(
            "APK'daki harita bitmap sayisi degisti — res/drawable-nodpi ile " +
                "karsilastir ve docs/QA_REPORT.md B-05'i guncelle",
            (1..11).toSet(), GameConfig.SHIPPED_MAP_IDS
        )

        // Artik HICBIR bolum yedek haritaya dusmuyor: her bolum kendi
        // haritasinin bitmap'ini ciziyor. Yedek mekanizmasi yerinde kaliyor
        // ama tetiklenmemesi gerekiyor.
        val playedOnFallback = campaign.count { it.mapId !in GameConfig.SHIPPED_MAP_IDS }
        assertEquals(
            "yedek haritaya dusen bolum sayisi 0 OLMALI — bir bolum yedege " +
                "dusuyorsa o haritanin bitmap'i eksik",
            0, playedOnFallback
        )
    }

    @Test
    fun theRouteAssignmentSeedIsStableSoLevelsAreReplayable() {
        // Catallanan haritalarda rota atamasi seed'li olmali, yoksa ayni bolum
        // her oynanista farkli davranir ve denge dogrulanamaz.
        assertTrue("rota seed'i sifir olmamali", GameConfig.ROUTE_RNG_SEED_BASE != 0L)
    }

    @Test
    fun onlyTheDeprecatedSingleLevelWaveListHasSixWaves() {
        // GameConfig.WAVES eski 6 dalgalik tek-bolum listesi. HUD hâlâ bunu
        // okuyorsa "WAVE 9/6" gibi sacma bir sayac cikar (docs/QA_REPORT.md B-03).
        @Suppress("DEPRECATION")
        val legacy = GameConfig.WAVES.size
        assertEquals(6, legacy)
        assertTrue(
            "eski liste kampanyanin gercek dalga sayilariyla karistirilmamali: " +
                "bolum 22'de $legacy degil ${WaveDefinitions.waveCount(22)} dalga var",
            WaveDefinitions.waveCount(22) > legacy
        )
    }
}

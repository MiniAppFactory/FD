package com.miniappfactory.frontlinedefender.biome

import com.miniappfactory.frontlinedefender.game.model.Biome
import com.miniappfactory.frontlinedefender.game.model.BiomeSlotCache
import com.miniappfactory.frontlinedefender.game.model.BiomeVariants
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 11 — BIYOM TABLOSU sozlesmesi (docs/BIOME_VARIANTS.md).
 *
 * SAF JUnit: `BiomeVariants` bilincli olarak Android'e ve `GameConfig`'e
 * bagimli degildir, o yuzden burada ne Robolectric ne de `Context` var.
 *
 * Bu testin isi, tablo genisleyip 55 bolume cikarken **yayinlanmis 22 bolumun
 * sirasinin sessizce degismedigini** kanitlamak.
 */
class BiomeCampaignTableTest {

    /**
     * HARITA ATAMASININ TEK KAYNAGI — `GameConfig.CAMPAIGN`.
     *
     * `BiomeVariants.baseMapIdFor` KALDIRILDI: iki paralel harita tablosu
     * vardi ve Act III+ icin farkli cevaplar veriyorlardi (bkz. BiomeVariants
     * icindeki gerekce blogu). `BiomeVariants`in isi artik yalnizca
     * `levelId -> Biome`; harita id'sine ihtiyaci olan buradan okur.
     *
     * `levelSpec` bilinmeyen bolumde ilk bolume duser, yani sinir degerlerinde
     * de asla cokmez (55'in otesi L1'e sarar — [outOfRangeLevelIdsStayResolvable]).
     */
    private fun mapIdFor(levelId: Int): Int = GameConfig.levelSpec(levelId).mapId

    // =====================================================================
    // 1. Determinizm — ayni bolum HER ZAMAN ayni varyant
    // =====================================================================

    @Test
    fun mappingIsPureAndRepeatable() {
        repeat(3) {
            for (level in 1..BiomeVariants.TOTAL_LEVELS) {
                assertEquals(
                    "biome L$level tekrar cagrida degisti",
                    BiomeVariants.biomeFor(level),
                    BiomeVariants.biomeFor(level)
                )
                assertEquals(
                    "map L$level tekrar cagrida degisti",
                    mapIdFor(level),
                    mapIdFor(level)
                )
            }
        }
        // Ikinci bir ornek uzerinden: tum tabloyu iki kez uret, birebir esit olsun.
        fun snapshot() = (1..BiomeVariants.TOTAL_LEVELS)
            .map { mapIdFor(it) to BiomeVariants.biomeFor(it) }
        assertEquals(snapshot(), snapshot())
    }

    // =====================================================================
    // 2. 55 bolumun tamami gecerli bir (harita, biyom) cifti veriyor
    // =====================================================================

    @Test
    fun allFiftyFiveLevelsResolveToAValidPair() {
        assertEquals(55, BiomeVariants.TOTAL_LEVELS)
        for (level in 1..BiomeVariants.TOTAL_LEVELS) {
            val mapId = mapIdFor(level)
            assertTrue(
                "L$level gecersiz harita id: $mapId",
                mapId in GameConfig.MAP_ID_MIN..GameConfig.MAP_ID_MAX
            )
            assertTrue(
                "L$level pakette olmayan haritaya isaret ediyor: $mapId",
                mapId in GameConfig.SHIPPED_MAP_IDS
            )
        }
    }

    @Test
    fun everyPairIsUniqueAndAllFiftyFiveExist() {
        val pairs = (1..BiomeVariants.TOTAL_LEVELS)
            .map { mapIdFor(it) to BiomeVariants.biomeFor(it) }
        assertEquals("55 bolum -> 55 BENZERSIZ cift olmali", 55, pairs.toSet().size)

        // 11 harita x 5 biyom kartezyen carpiminin TAMAMI kullanilmali:
        // tek bir varyant bile bosta kalirsa "0 bayt maliyetle 55 bolum"
        // iddiasi tutmaz.
        val expected = (GameConfig.MAP_ID_MIN..GameConfig.MAP_ID_MAX)
            .flatMap { m -> Biome.entries.map { m to it } }
            .toSet()
        assertEquals(expected, pairs.toSet())
    }

    @Test
    fun eachActUsesEachMapExactlyOnceAndOneBiome() {
        for (act in 1..BiomeVariants.ACT_COUNT) {
            val levels = (1..BiomeVariants.TOTAL_LEVELS).filter { BiomeVariants.actFor(it) == act }
            assertEquals("act $act 11 bolum olmali", BiomeVariants.MAPS_PER_ACT, levels.size)
            assertEquals(
                "act $act her haritayi bir kez kullanmali",
                (1..11).toSet(),
                levels.map { mapIdFor(it) }.toSet()
            )
            assertEquals(
                "act $act tek biyom olmali",
                1,
                levels.map { BiomeVariants.biomeFor(it) }.toSet().size
            )
        }
    }

    // =====================================================================
    // 3. YAYINLANMIS 22 BOLUM — GameConfig.CAMPAIGN ile birebir
    //
    // BiomeVariants GameConfig'i OKUMAZ; bu test iki bagimsiz kaynagin ayni
    // seyi soyledigini kanitlar. Kampanya 55'e cikarildiginda GameConfig
    // tarafi bu tabloyu benimseyecek ve ilk 22 bolum degismeyecek.
    // =====================================================================

    private fun expectedBiome(biome: GameConfig.Biome): Biome = when (biome) {
        GameConfig.Biome.TEMPERATE -> Biome.ORIGINAL
        GameConfig.Biome.NIGHT -> Biome.NIGHT
        GameConfig.Biome.WINTER -> Biome.WINTER
        GameConfig.Biome.DESERT -> Biome.DESERT
        GameConfig.Biome.AUTUMN -> Biome.AUTUMN
    }

    @Test
    fun tableAgreesWithShippedCampaignForLevelsOneToTwentyTwo() {
        for (spec in GameConfig.CAMPAIGN.filter { it.levelId <= 22 }) {
            assertEquals(
                "L${spec.levelId} harita esleşmesi GameConfig ile ayristi",
                spec.mapId,
                mapIdFor(spec.levelId)
            )
            assertEquals(
                "L${spec.levelId} biyom GameConfig ile ayristi",
                expectedBiome(spec.biome),
                BiomeVariants.biomeFor(spec.levelId)
            )
        }
        assertEquals(55, GameConfig.CAMPAIGN.size)
    }

    /** Biyom/act eslemesi iki tarafta da 11'lik bloklar — hicbir bolumde ayrismaz. */
    @Test
    fun biomePerActAgreesWithTheCampaignTableForAllFiftyFiveLevels() {
        for (spec in GameConfig.CAMPAIGN) {
            assertEquals(
                "L${spec.levelId} biyom GameConfig ile ayristi",
                expectedBiome(spec.biome),
                BiomeVariants.biomeFor(spec.levelId)
            )
        }
    }

    @Test
    fun actOneIsAscendingDaylightAndActTwoIsDescendingNight() {
        for (i in 0..10) {
            assertEquals(i + 1, mapIdFor(i + 1))
            assertEquals(Biome.ORIGINAL, BiomeVariants.biomeFor(i + 1))
            assertEquals(11 - i, mapIdFor(12 + i))
            assertEquals(Biome.NIGHT, BiomeVariants.biomeFor(12 + i))
        }
    }

    // =====================================================================
    // 4. Tekrar araligi — ayni geometri ne kadar sonra geri geliyor
    // =====================================================================

    /**
     * L11 -> L12 ayni haritayi (11) ust uste kullanir. Bu, Act II'nin
     * yayinlanmis "ters sira" kararindan gelir ve DUZELTILMEZ; test bunu
     * isimlendirerek kilitler ki biri "iyilestirmek" isterse yayinlanmis
     * bolum sirasini bozdugunu gorsun.
     */
    @Test
    fun onlyKnownAdjacentRepeatIsTheActOneToActTwoSeam() {
        val adjacentRepeats = (1 until BiomeVariants.TOTAL_LEVELS).filter {
            mapIdFor(it) == mapIdFor(it + 1)
        }
        assertEquals("beklenmeyen ardisik harita tekrari: $adjacentRepeats", listOf(11), adjacentRepeats)
    }

    /**
     * L23'ten sonra ayni harita en erken **10 bolum** sonra geri gelir.
     *
     * Teorik maksimum 11'dir ve ancak butun perdeler AYNI harita sirasini
     * kullanirsa elde edilir; `GameConfig.CAMPAIGN` (CAMPAIGN_55.md K4) her
     * perdede sirayi bir slot kaydirdigi icin bilincli olarak bir bolum feda
     * eder. Karsiligi [actFinalesLandOnFourDifferentMaps]: dort perde finali
     * dort AYRI haritaya duser.
     */
    @Test
    fun fromActThreeOnwardMapRepeatsAreAtLeastTenLevelsApart() {
        val lastSeen = HashMap<Int, Int>()
        var minGap = Int.MAX_VALUE
        for (level in 1..BiomeVariants.TOTAL_LEVELS) {
            val map = mapIdFor(level)
            val previous = lastSeen[map]
            if (previous != null && level > 23) {
                val gap = level - previous
                assertTrue(
                    "L$level harita $map, L$previous ile yalnizca $gap bolum arayla " +
                        "tekrar ediyor (en az 10 olmali)",
                    gap >= 10
                )
                minGap = minOf(minGap, gap)
            }
            lastSeen[map] = level
        }
        assertEquals("olculen minimum tekrar araligi 10 olmali", 10, minGap)
    }

    /** Ayni geometri geri gelirken biyom DEGISMELI: perde blogu 11 bolum. */
    @Test
    fun aMapNeverReturnsWithTheSameBiome() {
        val seen = HashSet<Pair<Int, Biome>>()
        for (level in 1..BiomeVariants.TOTAL_LEVELS) {
            val key = mapIdFor(level) to BiomeVariants.biomeFor(level)
            assertTrue("L$level ayni (harita, biyom) ciftini tekrar ediyor: $key", seen.add(key))
        }
    }

    /**
     * PERDE FINALLERI DORT FARKLI HARITA — kaydirmali siranin GEREKCESI.
     *
     * Kaydirmasiz (11..1) tekrar edilen bir sirada L22/L33/L44/L55'in DORDU DE
     * harita 01'e duserdi: oyuncunun hatirladigi "perde finali" hep ayni tahta
     * olurdu. `LATE_ACT_MAP_ORDER` bunu 01 / 11 / 10 / 09'a dagitir.
     */
    @Test
    fun actFinalesLandOnFourDifferentMaps() {
        val finales = listOf(22, 33, 44, 55).map { mapIdFor(it) }
        assertEquals("perde finalleri ayni haritaya dusuyor: $finales", 4, finales.toSet().size)
        assertNotEquals(mapIdFor(11), mapIdFor(22))
    }

    // =====================================================================
    // 5. Sinir degerleri — asla cokmez
    // =====================================================================

    @Test
    fun outOfRangeLevelIdsStayResolvable() {
        for (level in intArrayOf(Int.MIN_VALUE, -7, 0, 56, 111, Int.MAX_VALUE)) {
            val mapId = mapIdFor(level)
            assertTrue("L$level -> gecersiz harita $mapId", mapId in 1..11)
            // biomeFor cagrisi ArrayIndexOutOfBounds atmamali
            BiomeVariants.biomeFor(level)
            assertTrue(BiomeVariants.actFor(level) in 1..BiomeVariants.ACT_COUNT)
        }
        // 55'in otesi bassa sarar -> L56 ile L1 ayni cift.
        assertEquals(mapIdFor(1), mapIdFor(56))
        assertEquals(BiomeVariants.biomeFor(1), BiomeVariants.biomeFor(56))
    }

    // =====================================================================
    // 6. ONBELLEK — bolum degisince temizlenir, ayni bolumde korunur
    //
    // `BiomeSlotCache` generic; `ImageBitmap` JVM'de uretilemedigi icin
    // burada `String` ile ayni politika test ediliyor. Uygulama tarafindaki
    // `BiomeBackgroundCache` bu sinifin tek bir ornegidir.
    // =====================================================================

    @Test
    fun cacheHoldsAtMostOneBackground() {
        val cache = BiomeSlotCache<String>()
        assertEquals(0, cache.size)

        cache.put(1, Biome.ORIGINAL, "L1")
        assertEquals(1, cache.size)

        // Bolum degisti -> onceki varyant BIRAKILIR (55 x 8.3 MB = OOM).
        cache.put(7, Biome.WINTER, "L29")
        assertEquals("onbellek buyuyemez", 1, cache.size)
        assertNull("eski varyant hâlâ tutuluyor", cache.get(1, Biome.ORIGINAL))
        assertEquals("L29", cache.get(7, Biome.WINTER))
    }

    @Test
    fun sameLevelRevisitReusesCachedVariant() {
        val cache = BiomeSlotCache<String>()
        val produced = "recolored-bitmap"
        cache.put(4, Biome.AUTUMN, produced)

        // Yenilgi -> TEKRAR DENE: ayni bolum, yeniden uretim OLMAMALI.
        assertSame(produced, cache.get(4, Biome.AUTUMN))
        assertTrue(cache.holds(4, Biome.AUTUMN))
    }

    @Test
    fun sameMapDifferentBiomeIsACacheMiss() {
        val cache = BiomeSlotCache<String>()
        cache.put(5, Biome.NIGHT, "gece")
        // Ayni geometri, farkli biyom = FARKLI bitmap. Anahtar ikisini de icermeli,
        // aksi halde L18 (harita 5 / gece) L5'in gunduz haritasini gosterirdi.
        assertNull(cache.get(5, Biome.ORIGINAL))
        assertNull(cache.get(5, Biome.WINTER))
        assertEquals("gece", cache.get(5, Biome.NIGHT))
    }

    @Test
    fun clearReleasesTheSlot() {
        val cache = BiomeSlotCache<String>()
        cache.put(2, Biome.DESERT, "x")
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get(2, Biome.DESERT))
    }

    @Test
    fun everyCampaignLevelIsAddressableInTheCache() {
        val cache = BiomeSlotCache<String>()
        for (level in 1..BiomeVariants.TOTAL_LEVELS) {
            val map = mapIdFor(level)
            val biome = BiomeVariants.biomeFor(level)
            cache.put(map, biome, "L$level")
            assertEquals("L$level", cache.get(map, biome))
            assertEquals(1, cache.size)
        }
    }
}

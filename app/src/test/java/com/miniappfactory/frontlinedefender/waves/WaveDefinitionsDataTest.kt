package com.miniappfactory.frontlinedefender.waves

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.GameConfig.EnemyType
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import com.miniappfactory.frontlinedefender.game.model.WaveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * VERI BUTUNLUGU — 22 bolumluk kampanyanin dalga tanimlari.
 *
 * Kaynak sozlesme: docs/LEVEL_DESIGN.md + DECISIONS.md ("v1.0 = 22 bolum").
 */
class WaveDefinitionsDataTest {

    /** LEVEL_DESIGN.md'deki bolum basina dalga sayisi listesi — TEK DOGRULUK KAYNAGI. */
    private val expectedWaveCounts = listOf(
        6, 6, 7, 8, 8, 9, 9, 10, 10, 11, 12,
        12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 18
    )

    @Test
    fun campaignContainsExactlyTwentyTwoLevels() {
        assertEquals(22, WaveDefinitions.CAMPAIGN.size)
        assertEquals(22, WaveDefinitions.CAMPAIGN_LEVEL_COUNT)
        assertEquals(
            "GameConfig ve WaveDefinitions bolum sayisi uyusmuyor",
            GameConfig.CAMPAIGN_LEVEL_COUNT, WaveDefinitions.CAMPAIGN_LEVEL_COUNT
        )
    }

    @Test
    fun campaignKeysAreExactlyOneThroughTwentyTwo() {
        assertEquals((1..22).toSet(), WaveDefinitions.CAMPAIGN.keys)
    }

    @Test
    fun everyLevelHasTheWaveCountFromLevelDesign() {
        for (level in 1..22) {
            assertEquals(
                "bolum $level dalga sayisi LEVEL_DESIGN.md ile uyusmuyor",
                expectedWaveCounts[level - 1],
                WaveDefinitions.waveCount(level)
            )
        }
    }

    @Test
    fun waveCountsNeverDecreaseAcrossTheCampaign() {
        // Zorluk egrisi monoton olmayabilir ama DALGA SAYISI hic azalmamali.
        for (level in 2..22) {
            val prev = WaveDefinitions.waveCount(level - 1)
            val curr = WaveDefinitions.waveCount(level)
            assertTrue(
                "bolum $level dalga sayisi ($curr) onceki bolumden ($prev) az",
                curr >= prev
            )
        }
    }

    @Test
    fun waveIndicesAreOneBasedContiguousAndInOrder() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEachIndexed { i, wave ->
                assertEquals(
                    "bolum $level dalga listesi indeks $i icin waveIndex hatali",
                    i + 1, wave.waveIndex
                )
            }
        }
    }

    @Test
    fun everyWaveHasANonBlankInternalLabel() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                assertTrue("bolum $level dalga ${w.waveIndex} etiketi bos", w.title.isNotBlank())
            }
        }
    }

    @Test
    fun everyWaveSpawnsAtLeastOneEnemy() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                assertTrue(
                    "bolum $level dalga ${w.waveIndex} bos — dalga aninda tamamlanir " +
                        "ve motor sonsuz VICTORY dongusune girer",
                    w.spawns.isNotEmpty()
                )
            }
        }
    }

    /**
     * `delaySeconds` motorda "bu spawn'dan SONRAKI bekleme". 0 veya negatif
     * olursa `timeUntilNextSpawn` hemen tukenir ve dalganin tamami TEK KAREDE
     * dogar — hem denge hem kare suresi acisindan yikici.
     */
    @Test
    fun everySpawnDelayIsStrictlyPositive() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                w.spawns.forEachIndexed { i, s ->
                    assertTrue(
                        "bolum $level dalga ${w.waveIndex} spawn $i gecikmesi " +
                            "${s.delaySeconds} — pozitif olmali",
                        s.delaySeconds > 0f
                    )
                }
            }
        }
    }

    @Test
    fun spawnDelaysStayWithinAPlayableBand() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                w.spawns.forEach { s ->
                    assertTrue(
                        "bolum $level dalga ${w.waveIndex} gecikme ${s.delaySeconds}s cok kisa",
                        s.delaySeconds >= 0.25f
                    )
                    assertTrue(
                        "bolum $level dalga ${w.waveIndex} gecikme ${s.delaySeconds}s cok uzun",
                        s.delaySeconds <= 5f
                    )
                }
            }
        }
    }

    @Test
    fun everyEnemyTypeUsedByAnyWaveHasASpecEntry() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                w.spawns.forEach { s ->
                    assertTrue(
                        "bolum $level dalga ${w.waveIndex}: ${s.enemyType} ENEMY_SPECS'te yok",
                        GameConfig.ENEMY_SPECS.containsKey(s.enemyType)
                    )
                }
            }
        }
    }

    @Test
    fun everyDeclaredEnemyTypeIsActuallyUsedSomewhereInTheCampaign() {
        val used = WaveDefinitions.CAMPAIGN.values
            .flatten().flatMap { it.spawns }.map { it.enemyType }.toSet()
        val unused = EnemyType.values().toSet() - used
        assertTrue("hic kullanilmayan dusman tipi var: $unused", unused.isEmpty())
    }

    @Test
    fun wavesForThrowsForLevelsOutsideTheCampaign() {
        listOf(0, -1, 23, 999).forEach { bad ->
            try {
                WaveDefinitions.wavesFor(bad)
                fail("bolum $bad icin hata beklenirdi")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("$bad"))
            }
        }
    }

    // ------------------------------------------------- mechanic introduction

    @Test
    fun eachEnemyTypeIsIntroducedInTheLevelLevelDesignPromises() {
        // Ilk goruldugu bolum. LEVEL_DESIGN.md ogretme sirasi.
        val firstSeen = mutableMapOf<EnemyType, Int>()
        for (level in 1..22) {
            WaveDefinitions.wavesFor(level).forEach { w ->
                w.spawns.forEach { s -> firstSeen.putIfAbsent(s.enemyType, level) }
            }
        }
        assertEquals("INFANTRY bolum 1'de olmali", 1, firstSeen[EnemyType.INFANTRY])
        assertEquals("FAST_SOLDIER bolum 1'de olmali", 1, firstSeen[EnemyType.FAST_SOLDIER])
        assertEquals("ARMORED_VEHICLE bolum 5'te tanitilir", 5, firstSeen[EnemyType.ARMORED_VEHICLE])
        assertEquals("TANK bolum 7'de tanitilir", 7, firstSeen[EnemyType.TANK])
        assertEquals("SHIELDED_TROOPER bolum 9'da tanitilir", 9, firstSeen[EnemyType.SHIELDED_TROOPER])
        assertEquals("COMMAND_TANK (boss) Act I finalinde = bolum 11", 11, firstSeen[EnemyType.COMMAND_TANK])
    }

    @Test
    fun theFirstLevelOnlyEverSpawnsTheTwoTutorialEnemyTypes() {
        val types = WaveDefinitions.wavesFor(1).flatMap { it.spawns }.map { it.enemyType }.toSet()
        assertEquals(
            "ogretici bolumde yalnizca piyade ve kosucu olmali",
            setOf(EnemyType.INFANTRY, EnemyType.FAST_SOLDIER), types
        )
    }

    @Test
    fun bossesOnlyEverAppearAtTheEndOfTheirWave() {
        // mix() boss'lari dalganin SONUNA ekler. Boss ortada dogarsa arkasindaki
        // hafif dusmanlar boss'un golgesinde gecer ve dalga okunamaz hale gelir.
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                val idx = w.spawns.indices.filter { w.spawns[it].enemyType == EnemyType.COMMAND_TANK }
                if (idx.isNotEmpty()) {
                    val expectedTail = (w.spawns.size - idx.size) until w.spawns.size
                    assertEquals(
                        "bolum $level dalga ${w.waveIndex}: boss'lar dalganin sonunda degil",
                        expectedTail.toList(), idx
                    )
                }
            }
        }
    }

    @Test
    fun bossWavesOccurOnlyInTheLevelsLevelDesignNames() {
        val bossLevels = (1..22).filter { level ->
            WaveDefinitions.wavesFor(level).any { w ->
                w.spawns.any { it.enemyType == EnemyType.COMMAND_TANK }
            }
        }
        // L11 Act I finali, L16 cift boss, L22 kampanya finali.
        assertEquals(listOf(11, 16, 22), bossLevels)
    }

    @Test
    fun theCampaignFinaleEndsWithThreeCommandTanks() {
        val finale = WaveDefinitions.wavesFor(22).last()
        val bosses = finale.spawns.count { it.enemyType == EnemyType.COMMAND_TANK }
        assertEquals("L22 son dalgasi 3 boss icermeli", 3, bosses)
    }

    // ------------------------------------------------------- WaveMetrics AEHP

    /**
     * `WaveMetrics.AEHP` bir OLCUM ARACIdir ve `ENEMY_SPECS`'ten TURETILMISTIR
     * (docs/LEVEL_DESIGN.md E.1): referans batarya %50 kursun / %25 patlama /
     * %25 delici. ENEMY_SPECS degisir de bu tablo guncellenmezse butun zorluk
     * egrisi olcumleri sessizce yanlis olur.
     *
     * Bu test tabloyu ENEMY_SPECS'ten YENIDEN HESAPLAR.
     */
    @Test
    fun aehpTableIsConsistentWithEnemySpecs() {
        val pierce = GameConfig.TOWER_SPECS[GameConfig.TowerType.ANTI_ARMOR]!!.armorPierce
        assertEquals("referans delici oran degisti", 0.85f, pierce, 1e-6f)

        EnemyType.values().forEach { type ->
            val spec = GameConfig.ENEMY_SPECS.getValue(type)

            // kursun: zirh tam etkili
            val bullet = 1f - spec.armor
            // patlama: zirhi BYPASS eder (DECISIONS B2) ve splashVulnerability ile olceklenir
            val explosive = spec.splashVulnerability
            // delici: zirhin %85'i asilir
            val piercing = 1f - spec.armor * (1f - pierce)

            val multiplier = 0.50f * bullet + 0.25f * explosive + 0.25f * piercing
            val expected = spec.maxHp / multiplier

            val actual = WaveMetrics.AEHP.getValue(type)
            assertEquals(
                "$type AEHP tablosu ENEMY_SPECS ile tutarsiz " +
                    "(maxHp=${spec.maxHp} armor=${spec.armor} " +
                    "splashVuln=${spec.splashVulnerability} -> beklenen ${"%.1f".format(expected)})",
                expected, actual, expected * 0.01f
            )
        }
    }

    @Test
    fun aehpTableCoversEveryEnemyType() {
        assertEquals(EnemyType.values().toSet(), WaveMetrics.AEHP.keys)
        WaveMetrics.AEHP.forEach { (type, v) ->
            assertTrue("$type AEHP pozitif olmali", v > 0f)
        }
    }

    @Test
    fun aehpIsStrictlyOrderedFromLightestToHeaviest() {
        val order = listOf(
            EnemyType.FAST_SOLDIER,
            EnemyType.INFANTRY,
            EnemyType.SHIELDED_TROOPER,
            EnemyType.ARMORED_VEHICLE,
            EnemyType.TANK,
            EnemyType.COMMAND_TANK
        )
        for (i in 1 until order.size) {
            val prev = WaveMetrics.AEHP.getValue(order[i - 1])
            val curr = WaveMetrics.AEHP.getValue(order[i])
            assertTrue(
                "${order[i]} (${curr}) ${order[i - 1]} (${prev}) kadar tehditkar degil",
                curr > prev
            )
        }
    }

    // -------------------------------------------------------- zorluk egrisi

    @Test
    fun totalLevelDifficultyTrendsUpwardAcrossTheCampaign() {
        val aehp = (1..22).map { WaveMetrics.levelAehp(WaveDefinitions.wavesFor(it)) }

        // Ilk bolum en kolay, son bolum en zor olmali.
        assertEquals("en kolay bolum L1 olmali", aehp.min(), aehp.first(), 0.01f)
        assertEquals("en zor bolum L22 olmali", aehp.max(), aehp.last(), 0.01f)

        // "Nefes" bolumleri (L8, L15, L19) kasitli olarak daha hafiftir, bu
        // yuzden adim adim monotonluk ISTENMEZ. Bunun yerine 5 bolumluk
        // pencerelerin ortalamasi artmali.
        val windows = aehp.chunked(5).filter { it.size == 5 }.map { it.average() }
        for (i in 1 until windows.size) {
            assertTrue(
                "zorluk penceresi $i (${"%.0f".format(windows[i])}) oncekinden " +
                    "(${"%.0f".format(windows[i - 1])}) dusuk",
                windows[i] > windows[i - 1]
            )
        }
    }

    @Test
    fun noSingleWaveIsMoreThanTwiceAsHeavyAsAnythingSeenEarlierInTheSameLevel() {
        // Bu testin korudugu sey: oyuncunun ANI BIR DUVARA carpmamasi.
        //
        // Olcut "onceki dalga" DEGIL, "o ana kadar gorulen EN AGIR dalga".
        // Sebep gercek bir yanlis pozitif: L2 dalgalari 600 -> 360 -> 930 ilerliyor
        // ve W2 kasitli olarak hafif bir "kosucu dersi" (az HP, yuksek hiz).
        // Onceki-dalga olcutu bunu 2.6x ziplama diye raporluyordu, oysa oyuncu
        // 930'dan once zaten 600'u karsilamisti — duvar yok. Kasitli nefes
        // dalgasini cezalandirmak, tasarimi metrige uydurmak olurdu.
        //
        // Boss dalgalari MUAF (boss'un tamami tek dusmanda toplaniyor).
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            var seenMax = WaveMetrics.waveAehp(waves.first())
            for (i in 1 until waves.size) {
                val curr = WaveMetrics.waveAehp(waves[i])
                val hasBoss = waves[i].spawns.any { it.enemyType == EnemyType.COMMAND_TANK }
                if (!hasBoss) {
                    assertTrue(
                        "bolum $level dalga ${i + 1}: o ana kadarki en agir dalga " +
                            "${"%.0f".format(seenMax)} iken bu dalga " +
                            "${"%.0f".format(curr)} (${"%.1f".format(curr / seenMax)}x) — " +
                            "oyuncu hazirliksiz bir duvara carpiyor",
                        curr <= seenMax * 2.5f
                    )
                }
                if (curr > seenMax) seenMax = curr
            }
        }
    }

    @Test
    fun spawnWindowsAreLongEnoughToBePlayable() {
        WaveDefinitions.CAMPAIGN.forEach { (level, waves) ->
            waves.forEach { w ->
                val window = WaveMetrics.spawnWindowSeconds(w)
                assertTrue(
                    "bolum $level dalga ${w.waveIndex} spawn penceresi ${window}s — cok kisa",
                    window >= 3f
                )
                assertTrue(
                    "bolum $level dalga ${w.waveIndex} spawn penceresi ${window}s — " +
                        "2 dakikadan uzun, oyuncu sikilir",
                    window <= 120f
                )
            }
        }
    }

    @Test
    fun waveGeneratorIsDeterministic() {
        // "Sifir RNG. Ayni bolum her zaman ayni dalgalari uretir." (dosya notu 4)
        for (level in 1..22) {
            val a = WaveDefinitions.wavesFor(level)
            val b = WaveDefinitions.wavesFor(level)
            assertEquals("bolum $level iki cagri arasinda farkli sonuc verdi", a, b)
        }
    }

    @Test
    fun waveCountHelperAgreesWithTheCampaignMap() {
        for (level in 1..22) {
            assertEquals(
                WaveDefinitions.CAMPAIGN.getValue(level).size,
                WaveDefinitions.waveCount(level)
            )
        }
    }
}

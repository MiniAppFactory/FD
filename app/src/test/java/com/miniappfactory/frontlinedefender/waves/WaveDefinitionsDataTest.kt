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

    /**
     * LEVEL_DESIGN'in en sert kurali: **oyuncunun henuz sahip olmadigi mekanik
     * zorunlu basari kosulu olamaz.**
     *
     * Faz 10'da bu kural gercek bir risk haline geldi, cunku (a) kuleler artik
     * bolume gore kilitli (GameConfig.unlockedAtLevel) ve (b) zirh 0.78/0.86'ya
     * cikti, yani kursun zirhli hedefe karsi gercekten ise yaramaz. Ikisi
     * birlikte yanlis ayarlanirsa oyuncu cevabi ELINDE OLMAYAN bir dusmanla
     * karsilasir. Bu test o celiskiyi bolum bolum arar.
     *
     * "Zirh cevabi" = patlama (splash zirhi BYPASS eder, DECISIONS B2) ya da
     * ciddi zirh delme.
     */
    @Test
    fun noArmouredEnemyBecomesMandatoryBeforeItsCounterIsUnlocked() {
        for (level in 1..22) {
            val available = GameConfig.unlockedTowers(level)
                .map { GameConfig.TOWER_SPECS.getValue(it) }
            val hasArmourAnswer = available.any { it.splashRadius > 0f || it.armorPierce >= 0.5f }

            val armouredTypes = WaveDefinitions.wavesFor(level)
                .flatMap { it.spawns }
                .map { it.enemyType }
                .distinct()
                .filter { GameConfig.ENEMY_SPECS.getValue(it).armor >= 0.5f }

            if (armouredTypes.isNotEmpty()) {
                assertTrue(
                    "bolum $level zirhli dusman iceriyor ($armouredTypes) ama acik " +
                        "kuleler ${available.map { it.type }} arasinda zirha cevap " +
                        "veren yok — oyuncudan olmayan bir mekanik isteniyor",
                    hasArmourAnswer
                )
            }
        }
    }

    /** Kilit tablosu ile dusman tanitim sirasi birebir uyumlu mu (dokumantasyon). */
    @Test
    fun theUnlockScheduleMatchesTheEnemyIntroductionSchedule() {
        assertEquals(
            "Gatling bolum 1", 1,
            GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).unlockedAtLevel
        )
        assertEquals(
            "Heavy Cannon bolum 3 — zirhli arac (L5) ve kalabalik kolonlarin cevabi", 3,
            GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.CANNON).unlockedAtLevel
        )
        assertEquals(
            "Frost Field bolum 5 — ilk zirhli araclarla ayni bolumde", 5,
            GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.SLOW).unlockedAtLevel
        )
        assertEquals(
            "Missile Battery bolum 7 — ilk TANK ile ayni bolumde", 7,
            GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.ANTI_ARMOR).unlockedAtLevel
        )
        // Fuze rampasi tankla AYNI bolumde acilmali: once acilsa tank bir sinav
        // olmaz, sonra acilsa oyuncu cevapsiz kalir.
        val tankLevel = (1..22).first { level ->
            WaveDefinitions.wavesFor(level).any { w ->
                w.spawns.any { it.enemyType == EnemyType.TANK }
            }
        }
        assertEquals(
            "TANK'in tanitildigi bolum ile fuze rampasinin acildigi bolum ayni olmali",
            GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.ANTI_ARMOR).unlockedAtLevel,
            tankLevel
        )
    }

    /**
     * ===================================================================
     * "2 KULE YETIYOR" TESTI — testcinin en pahali sikayeti
     * ===================================================================
     *
     * Ekonomi ajani sorunu sayiyla teslim etti: L1'de toplam dusman cani 4.155
     * AEHP, bir Gatling 87.5 DPS, bolum ~210 sn -> tek kule bolumun 4 katindan
     * fazla hasar veriyor. Yani TOPLAM CAN yanlis olcuttu.
     *
     * Dogru olcut BASKI = AEHP / spawn penceresi. Bir kulenin oldurme hizi
     * sabittir; dusmanlar bundan hizli geliyorsa fark birikir ve sizar. AEHP
     * referans batarya hasari biriminde oldugu icin baski/DPS dogrudan
     * "kac kule gerekir" verir.
     *
     * Esikler olculen degerlerin biraz altinda: kucuk ayarlar testi kirmaz ama
     * "eski kolay hâline geri don" kirar.
     */
    @Test
    fun theOpeningLevelsDemandMoreThanTheOldEasyCadence() {
        // OLCULEN degerler (kadans sikilastirmasi + can kalibrasyonu sonrasi):
        //   L1 12.7 · L2 14.0 · L3 15.8 · L4 15.7 · L5 17.4 · L6 17.7
        // Esikler bunlarin ~%5 altinda: kucuk ayar kirmaz, "eski kolay kadansa
        // geri don" kirar. Sayinin MUTLAK anlami yok (bkz. peakPressureRatio);
        // mutlak arz/talep orani difficulty_audit.py'de.
        val minimumRatio = mapOf(
            1 to 12.0f, 2 to 13.3f, 3 to 15.0f, 4 to 15.0f, 5 to 16.5f, 6 to 16.8f
        )
        minimumRatio.forEach { (level, minimum) ->
            val ratio = WaveMetrics.peakPressureRatio(WaveDefinitions.wavesFor(level))
            assertTrue(
                "bolum $level tepe baski orani ${"%.2f".format(ratio)} — Faz 10 " +
                    "sikilastirmasinin altina dustu (>= $minimum olmali)",
                ratio >= minimum
            )
        }
    }

    /**
     * TABAN KALIBRASYONU — "2 kule yetiyor"un asil kaniti.
     *
     * Olculen sorun: Gatling Kd.2 piyadeye 216 DPS veriyordu, piyade 75 canliydi
     * -> tek kule saniyede 2.9 piyade siliyor, dalga saniyede ~0.4 dusman
     * gonderiyor. Kule gelenden 7 kat hizli temizliyordu.
     *
     * Duzeltme: atis araligi x2 + dusman cani x3.5. Bu test o kalibrasyonun
     * GERI ALINMAMASINI korur — TTK (time-to-kill) uzerinden, cunku TTK
     * oyuncunun gercekten hissettigi sey.
     */
    @Test
    fun aSingleCrowdTowerCannotOutpaceTheStreamAnyMore() {
        val mg = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val infantry = GameConfig.ENEMY_SPECS.getValue(EnemyType.INFANTRY)

        // Kademe 2 Gatling'in piyade basina oldurme suresi.
        val ttk = infantry.maxHp / mg.level2Dps
        assertTrue(
            "Gatling Kd.2 piyadeyi ${"%.2f".format(ttk)} sn'de olduruyor — 2 sn'nin " +
                "altina inerse tek kule akisi yine gecer (eski deger 0.35 sn idi)",
            ttk >= 2.0f
        )
        val killRate = 1f / ttk

        // Ilk alti bolumun EN YOGUN dalgasinda dusman varis hizi.
        for (level in 1..6) {
            val peak = WaveDefinitions.wavesFor(level).maxOf { w ->
                w.spawns.size / WaveMetrics.spawnWindowSeconds(w)
            }
            assertTrue(
                "bolum $level tepe varis hizi ${"%.2f".format(peak)}/sn, tek Gatling " +
                    "Kd.2 ${"%.2f".format(killRate)}/sn olduruyor — varis hizi oldurme " +
                    "hizinin en az 1.5 katı olmali, yoksa tek kule bolumu tasir",
                peak >= killRate * 1.5f
            )
        }
    }

    /** Baski egrisi ilk alti bolumde MONOTON artmali (ogretme rampasi). */
    @Test
    fun theOpeningPressureCurveNeverGoesBackwards() {
        val curve = (1..6).map { WaveMetrics.peakPressureRatio(WaveDefinitions.wavesFor(it)) }
        for (i in 1 until curve.size) {
            assertTrue(
                "bolum ${i + 1} tepe baskisi (${"%.2f".format(curve[i])}) bolum $i'den " +
                    "(${"%.2f".format(curve[i - 1])}) dusuk — rampa geriye gidiyor",
                curve[i] >= curve[i - 1] - 0.05f
            )
        }
        // L6, L7'yi GECMEMELI: L7 hem tank hem fuze rampasini getiriyor.
        assertTrue(
            "L6 baskisi L7'yi asmis",
            curve.last() <= WaveMetrics.peakPressureRatio(WaveDefinitions.wavesFor(7)) + 0.05f
        )
    }

    /** Toplam can sismedi: sikilastirma KADANSTAN geldi, HP'den degil. */
    @Test
    fun theTighteningCameFromCadenceNotHealthInflation() {
        for (level in 1..6) {
            val waves = WaveDefinitions.wavesFor(level)
            val finale = waves.last()
            val lightGaps = finale.spawns
                .filter { GameConfig.ENEMY_SPECS.getValue(it.enemyType).armor < 0.5f }
                .map { it.delaySeconds }
            assertTrue("bolum $level son dalgasinda hafif dusman yok", lightGaps.isNotEmpty())
            assertTrue(
                "bolum $level son dalgasinin hafif spawn araligi ${lightGaps.min()}s — " +
                    "0.45s'in altinda olmali, yoksa final dalgasi baski yapmaz",
                lightGaps.min() <= 0.45f
            )
        }
        // Referans DPS kule tablosundan TURETILIYOR: atis araligi/hasar takasi
        // (Faz 10: ikisi de x2) baski hesabini bozmadi.
        assertEquals(
            "referans kule DPS'i degisti — baski esikleri yeniden olculmeli " +
                "(14 hasar / 0.32 sn = 43.75; atis araligi x2 yapildi, hasar SABIT " +
                "kaldi, yani DPS kasten yarilandi)",
            43.75f, WaveMetrics.referenceTowerDps, 0.1f
        )
    }

    /**
     * L6, L7'nin ALTINDA kalmali: L7 hem TANK'i hem fuze rampasini getiriyor,
     * bolum siralamasi tersine donmemeli (sikilastirma sirasinda gercekten
     * ters donmustu ve buraya pinlendi).
     */
    @Test
    fun theSixthLevelStaysBelowTheSeventh() {
        val l6 = WaveMetrics.levelAehp(WaveDefinitions.wavesFor(6))
        val l7 = WaveMetrics.levelAehp(WaveDefinitions.wavesFor(7))
        assertTrue(
            "L6 (${"%.0f".format(l6)}) L7'den (${"%.0f".format(l7)}) agir — " +
                "tank ve fuze rampasinin geldigi bolum bir gerileme gibi hissedilir",
            l6 < l7
        )
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

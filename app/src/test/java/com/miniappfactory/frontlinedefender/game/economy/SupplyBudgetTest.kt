package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.WaveDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 10 / 10.1 — SAVAS ICI TEDARIK BUTCESI (ECONOMY_SPEC A).
 *
 * Testcinin sikayetini olculebilir hale getirir ve uygulanan sayilarin gercekten
 * cozdugunu kanitlar:
 *   *"6 tane kule yapacak para kazaniyorsun ama 2 tanesi yetiyor."*
 *
 * FAZ 10.1'DE DEGISEN SEY: model artik hicbir dalga/kule sayisini KOPYALAMIYOR;
 * gelir `WaveDefinitions` x `ENEMY_SPECS`, bolen ise `TOWER_SPECS`ten canli
 * turetiliyor ([CampaignFacts]). Bu yuzden bu dosyadaki beklenen degerler artik
 * "dokumandaki tablo" degil **bugunku oyunun olculmus gercegi**dir ve dalga tablosu
 * degistiginde kirilmalari ISTENEN davranistir — sessizce yaniltici olmalari degil.
 */
class SupplyBudgetTest {

    // =================================================================================
    // 1. TESHIS — bolluk sayiyla kanitlanir
    // =================================================================================

    /**
     * Faz 10 ONCESI ekonomi, BUGUNKU dalga tablosunda. SPI > 4 = artan Tedarigin
     * harcanacak yeri yok, karar olur.
     *
     * Iki tarafin AYNI dalga tablosunda karsilastirilmasi kasitli: eski dalga
     * tablosunun toplamlariyla karsilastirmak, dalga sikilastirmasinin getirdigi
     * geliri odul kesintisinin hanesine yazardi.
     */
    @Test
    fun theEconomyBeforeTighteningWasMeasurablyFarTooFlush() {
        for (level in 1..6) {
            val spi = SupplyBudgetModel.legacySupplyPressureIndex(level)
            assertTrue(
                "L$level sikilastirma oncesi SPI %.2f — sikayet yoksa test yanlis".format(spi),
                spi > 4.0,
            )
        }
    }

    /**
     * TESHIS ARTIK TARIHTIR — bu test o teshisin GERI GELMEMESINI koruyor.
     *
     * Onceki hali (`aGatlingGunCostsOnlyFiveInfantryKillsToday`) bugunku bolluğu
     * belgeliyordu: Gatling 60 Tedarik, piyade 12 oduyor -> **5 oldurme = 1 kule**,
     * bir dalga 6-14 piyade getirdigi icin her dalga 1-3 kule finanse ediyordu.
     *
     * Odul tablosu x1/3 uygulandiginda (gameplay ajani, ECONOMY_SPEC 9 madde 2)
     * bu test KIRILDI — kirilmasi istenen bir testti. Yerine gecen sey teshisin
     * tersi: kule basi oldurme sayisi hedef banda GIRMIS olmali.
     */
    @Test
    fun aGatlingGunNowCostsAboutFifteenInfantryKills() {
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).buildCost
        val infantry = GameConfig.ENEMY_SPECS.getValue(GameConfig.EnemyType.INFANTRY).rewardGold
        assertEquals(60, gatling)
        assertEquals("odul tablosu x1/3 uygulanmadi", 4, infantry)

        val kills = SupplyBudgetModel.killsPerTower(gatling, infantry)
        assertEquals(15.0, kills, 1e-9)
        assertTrue(
            "kule basi $kills oldurme — 12'nin altina inerse eski bolluk geri gelir",
            kills >= 12.0,
        )
    }

    @Test
    fun tightenedRewardMakesATowerCostAboutFifteenKills() {
        // HEDEF: bir kule ~15 oldurme. Odul tablosu 12 -> 4 olunca tam olarak bu cikar.
        val gatling = 60
        val tightenedInfantry = SupplyBudgetModel.TIGHTENED_ENEMY_SUPPLY_REWARD.getValue("INFANTRY")
        assertEquals(4, tightenedInfantry)
        assertEquals(15.0, SupplyBudgetModel.killsPerTower(gatling, tightenedInfantry), 1e-9)
        assertTrue(
            "kule basi oldurme sayisi 12'nin altinda kalirsa bolluk cozulmemis olur",
            SupplyBudgetModel.killsPerTower(gatling, tightenedInfantry) >= 12.0,
        )
    }

    @Test
    fun tightenedRewardTablePreservesEnemyValueRatios() {
        // Kule kimlikleri ve hedef secimi DEGISMEMELI: her dusmanin piyadeye gore
        // goreli degeri eski tabloyla ayni kalmali (+-%15).
        val legacy = SupplyBudgetModel.LEGACY_ENEMY_SUPPLY_REWARD
        val tightened = SupplyBudgetModel.TIGHTENED_ENEMY_SUPPLY_REWARD
        assertEquals(legacy.keys, tightened.keys)
        legacy.forEach { (name, old) ->
            val oldRatio = old.toDouble() / legacy.getValue("INFANTRY")
            val newRatio = tightened.getValue(name).toDouble() / tightened.getValue("INFANTRY")
            assertEquals("$name goreli degeri kaydi", oldRatio, newRatio, oldRatio * 0.15)
        }
    }

    @Test
    fun integerRoundingKeepsTheEffectiveScaleTight() {
        // Tamsayi yuvarlama bolum bazinda olcegi kaydirir; kayma bandinin disina
        // cikarsa bir bolum sessizce daha bol/kit olur.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val scale = SupplyBudgetModel.effectiveRewardScale(level)
            assertTrue(
                "L$level etkin olcek %.3f — 0,32..0,36 bandi disinda".format(scale),
                scale in 0.32..0.36,
            )
        }
    }

    @Test
    fun startingSupplyIsNotTheDominantLever() {
        // Onemli bulgu: baslangic Tedariki L1 butcesinin %13'u, L6'nin %5'i.
        // Yalnizca onu dusurmek sikayeti COZMEZ; asil kaldirac dalga geliri.
        for (level in 1..6) {
            val share = 100.0 * SupplyBudgetModel.LEGACY_STARTING_SUPPLY /
                SupplyBudgetModel.legacySupplyBudget(level)
            assertTrue(
                "L$level: baslangic Tedariki butcenin %%%.1f'i — beklenen <%%20".format(share),
                share < 20.0,
            )
        }
    }

    // =================================================================================
    // 2. ONERILEN SAYILAR — ECONOMY_SPEC A tablosu birebir
    // =================================================================================

    @Test
    fun recommendedStartingSupplyMatchesEconomySpecTable() {
        assertEquals(80, startingSupplyFor(1))
        assertEquals(90, startingSupplyFor(2))
        assertEquals(110, startingSupplyFor(3))
        assertEquals(120, startingSupplyFor(4))
        assertEquals(140, startingSupplyFor(5))
        assertEquals(150, startingSupplyFor(6))
        // L7..L22 taban degerde kalir (sozlesme testini bozmamak icin).
        for (level in 7..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals("L$level", EconomyConfig.BASE_STARTING_SUPPLY, startingSupplyFor(level))
        }
    }

    /**
     * ONCEKI HALI (`recommendedBudgetTableMatchesEconomySpec`) 454/556/654/950/877/1188
     * bekliyordu. O sayilar Faz 10'un dalga tablosundan turetilmisti ve kule ajani
     * dalgalari sikilastirinca **sessizce yanlis** oldular — ama test yesil kalmaya
     * devam etti, cunku model de ayni bayat diziyi okuyordu. Iki taraf ayni yanlisi
     * paylastigi icin hata ancak elle olculdugunde goruldu.
     *
     * Faz 10.1'de model canli dalga tablosunu okuyor; bu yuzden buradaki sayilar
     * BUGUNKU olcumdur ve dalga tablosu degistiginde bu test **kirilmalidir**.
     * Kirildiginda yapilacak sey esigi guncellemek degil, SPI'ye bakmaktir.
     */
    @Test
    fun budgetTableMatchesTodaysMeasuredWaveTable() {
        val expected = mapOf(
            1 to 570, 2 to 692, 3 to 820, 4 to 1260,
            5 to 1054, 6 to 1374, 7 to 1175, 8 to 1273,
        )
        expected.forEach { (level, budget) ->
            assertEquals("L$level butce", budget, SupplyBudgetModel.supplyBudget(level))
        }
    }

    @Test
    fun everyModelledLevelLandsInsideTheTargetPressureBand() {
        // SERT KURAL: hedef bant 1,5..2,6. Disina cikan bolum tasarim hatasidir.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val spi = SupplyBudgetModel.supplyPressureIndex(level)
            assertTrue(
                "L$level SPI %.2f, hedef bant %.1f..%.1f".format(
                    spi, SupplyBudgetModel.SPI_TARGET_MIN, SupplyBudgetModel.SPI_TARGET_MAX,
                ),
                spi >= SupplyBudgetModel.SPI_TARGET_MIN && spi <= SupplyBudgetModel.SPI_TARGET_MAX,
            )
        }
    }

    /**
     * BANT KILIDI — bandin icinde olmak yetmez, NEREDE oldugu da kilitli.
     *
     * Sebep: Faz 10'da SPI bandin ustune cikti ve bunu **hicbir test yakalamadi**,
     * cunku bant testi bayat bir diziyi okuyordu. Bant testi tek basina yeterli
     * degil; bir sonraki dalga degisikliginin bolum bolum ne yaptigini gormek
     * istiyoruz. Bu test kirildiginda mesaj dogrudan yeni SPI'yi yazar.
     *
     * Bu sayilari guncellemek MESRU bir islemdir — esigi gevsetmek degildir —
     * yeter ki [everyModelledLevelLandsInsideTheTargetPressureBand] yesil kalsin.
     */
    @Test
    fun theSupplyPressureIndexIsLockedLevelByLevel() {
        val expected = mapOf(
            1 to 2.28, 2 to 1.85, 3 to 1.88, 4 to 2.03,
            5 to 1.70, 6 to 2.22, 7 to 1.62, 8 to 1.76,
        )
        expected.forEach { (level, spi) ->
            assertEquals(
                "L$level SPI kaydi (butce ${SupplyBudgetModel.supplyBudget(level)}, " +
                    "kadro ${SupplyBudgetModel.designedRoster(level)} = " +
                    "${SupplyBudgetModel.designedLoadoutCost(level)})",
                spi,
                SupplyBudgetModel.supplyPressureIndex(level),
                0.02,
            )
        }
    }

    // =================================================================================
    // 2b. BAYATLAMA KALKANI — model dalga/kule tablosunu KOPYALAMIYOR
    // =================================================================================

    /**
     * Bu proje ayni hatayi iki kez yapti: `WaveMetrics.AEHP` ve
     * `SupplyBudgetModel.TIGHTENED_WAVE_KILL_SUPPLY` elle yazilmisti, altindaki karar
     * degisti, tablolar kaldi. Asagidaki uc test ucuncusunu yapisal olarak engeller.
     */
    @Test
    fun killIncomeIsRecomputedFromTheLiveWaveTable() {
        // Testin kendisi toplami BAGIMSIZ olarak yeniden hesaplar. Model bir yerde
        // onceden hesaplanmis toplam saklıyorsa bu test onu yakalar.
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val expected = WaveDefinitions.wavesFor(level).sumOf { wave ->
                wave.spawns.sumOf { spawn ->
                    GameConfig.ENEMY_SPECS.getValue(spawn.enemyType).rewardGold
                }
            }
            val actMul = GameConfig.actRewardMultiplier(GameConfig.levelSpec(level).act)
            if (actMul == 1f) {
                assertEquals(
                    "L$level oldurme geliri canli dalga tablosuyla uyusmuyor",
                    expected,
                    SupplyBudgetModel.waveKillSupply(level),
                )
            }
        }
        assertEquals(
            "L1 dalga sayisi canli tablodan gelmiyor",
            WaveDefinitions.wavesFor(1).size,
            SupplyBudgetModel.waveCount(1),
        )
    }

    @Test
    fun killIncomeFollowsAChangedWaveTable() {
        // Dalga tablosu degistiginde gelirin GERCEKTEN degistigini kanitlar: sahte
        // bir tabloda dusman sayisi yarilanirsa gelir de yarilanmali.
        val halved = object : CampaignFacts by GameConfigCampaignFacts {
            override fun enemyCounts(level: Int): Map<String, Int> =
                GameConfigCampaignFacts.enemyCounts(level).mapValues { (_, n) -> n / 2 }
        }
        val full = SupplyBudgetModel.waveKillSupply(1)
        val half = SupplyBudgetModel.waveKillSupply(1, halved)
        assertEquals("gelir dalga tablosunu takip etmiyor", full / 2.0, half.toDouble(), 2.0)
        assertTrue("model bir ara toplam sakliyor olmali", half < full)
    }

    @Test
    fun designedLoadoutIsPricedFromLiveTowerSpecs() {
        // Bolen de bayatlamamali: kule fiyatlari degisirse tasarlanan kadro maliyeti
        // kendiliginden takip etmeli (Faz 10'da CANNON 90->95, SLOW 80->100 oldu ve
        // elle yazilmis bolen bunu takip etmiyordu).
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val expected = SupplyBudgetModel.designedRoster(level).sumOf { name ->
                val type = GameConfig.TowerType.valueOf(name)
                val spec = GameConfig.TOWER_SPECS.getValue(type)
                spec.buildCost + spec.level2UpgradeCost
            }
            assertEquals(
                "L$level tasarlanan kadro maliyeti canli TOWER_SPECS ile uyusmuyor",
                expected,
                SupplyBudgetModel.designedLoadoutCost(level),
            )
        }

        val pricier = object : CampaignFacts by GameConfigCampaignFacts {
            override val towerBuildCost: Map<String, Int> =
                GameConfigCampaignFacts.towerBuildCost.mapValues { (_, c) -> c * 2 }
        }
        assertTrue(
            "kule fiyati iki katina cikti, bolen degismedi — bolen kopyalanmis",
            SupplyBudgetModel.designedLoadoutCost(1, pricier) >
                SupplyBudgetModel.designedLoadoutCost(1),
        )
    }

    @Test
    fun theDesignedRosterOnlyEverContainsUnlockedTowers() {
        // "Oyuncunun sahip olmadigi mekanik zorunlu basari kosulu olamaz" kuralinin
        // ekonomi tarafi: tasarlanan kadro o bolumde kurulamayan bir kule iceremez,
        // yoksa SPI'nin boleni oyuncunun ERISEMEDIGI bir savunmayi fiyatlar.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            SupplyBudgetModel.designedRoster(level).forEach { name ->
                val spec = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.valueOf(name))
                assertTrue(
                    "L$level kadrosunda $name var ama L${spec.unlockedAtLevel}'de aciliyor",
                    spec.unlockedAtLevel <= level,
                )
            }
        }
    }

    @Test
    fun theDesignedRosterNeverShrinksAsTheCampaignProgresses() {
        for (level in 2..SupplyBudgetModel.MODELLED_LEVELS) {
            assertTrue(
                "L$level tasarlanan kadrosu L${level - 1}'den ucuz — oyuncu kule sokmuyor",
                SupplyBudgetModel.designedLoadoutCost(level) >=
                    SupplyBudgetModel.designedLoadoutCost(level - 1),
            )
        }
    }

    /**
     * BOLENDEN BAGIMSIZ BOLLUK OLCUMU.
     *
     * SPI'nin boleni Faz 10.1'de degisti; "bant asimini boleni buyuterek cozduk"
     * itirazina kapali bir olcut gerekiyor. Testcinin cumlesi zaten boyle bir olcut:
     * *"her dalga 1-3 kule finanse ediyor."* Bu test onu dogrudan olcer ve hicbir
     * tasarim niyetine bakmaz — yalnizca canli dalga tablosu ve canli kule fiyati.
     */
    @Test
    fun noSingleWaveFinancesAWholeUpgradedTower() {
        val tierTwoGatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
            .let { it.buildCost + it.level2UpgradeCost }
        assertEquals(125, tierTwoGatling)

        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val perWave = SupplyBudgetModel.waveKillSupply(level).toDouble() /
                SupplyBudgetModel.waveCount(level)
            val towersPerWave = perWave / tierTwoGatling
            assertTrue(
                "L$level: bir dalga %.2f kademe-2 Gatling finanse ediyor — 1,1'i gecerse "
                    .format(towersPerWave) + "eski bolluk geri gelir",
                towersPerWave <= 1.1,
            )
            // Faz 10 oncesiyle karsilastirma: kesinti gercekten calisti mi.
            val legacyPerWave = SupplyBudgetModel.legacyWaveKillSupply(level).toDouble() /
                SupplyBudgetModel.waveCount(level) / tierTwoGatling
            assertTrue(
                "L$level: dalga basi kule finansmani %.2f -> %.2f, iki katindan az "
                    .format(legacyPerWave, towersPerWave) + "iyilesme yetersiz",
                legacyPerWave >= towersPerWave * 2,
            )
        }
    }

    @Test
    fun tighteningIsAtLeastATwoFoldReductionOnEveryEarlyLevel() {
        for (level in 1..6) {
            val before = SupplyBudgetModel.legacySupplyBudget(level)
            val after = SupplyBudgetModel.supplyBudget(level)
            assertTrue(
                "L$level: $before -> $after, iki katindan az sikilasma yetersiz",
                after * 2 <= before,
            )
        }
    }

    @Test
    fun budgetStillCoversTheDesignedLoadoutOnEveryLevel() {
        // SOFT-LOCK KALKANI: sikilastirma bolumu gecilemez YAPMAMALI. Butce, tasarlanan
        // kadronun tamamini HER bolumde karsilamali (uzerine de pay kalmali).
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            assertTrue(
                "L$level: butce ${SupplyBudgetModel.supplyBudget(level)} < " +
                    "tasarlanan kadro ${SupplyBudgetModel.designedLoadoutCost(level)}",
                SupplyBudgetModel.supplyBudget(level) > SupplyBudgetModel.designedLoadoutCost(level),
            )
        }
    }

    @Test
    fun openingSupplyBuysAtLeastOneTowerAndAtMostTwoOnEveryEarlyLevel() {
        // Testcinin "her kurusu saysin" istegi ACILISTA baslar: bir kule kesin kurulur,
        // ucuncu kule ASLA acilista kurulamaz — dalga geliriyle kazanilir.
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN).buildCost
        for (level in 1..6) {
            val opening = startingSupplyFor(level)
            assertTrue("L$level acilista tek kule bile kurulamiyor", opening >= gatling)
            assertTrue(
                "L$level acilista ${opening / gatling} kule kurulabiliyor — en fazla 2 olmali",
                opening / gatling <= 2,
            )
        }
    }

    @Test
    fun startingSupplyNeverDecreasesAsTheCampaignProgresses() {
        for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
            assertTrue(
                "L$level baslangic Tedariki L${level - 1}'den dusuk",
                startingSupplyFor(level) >= startingSupplyFor(level - 1),
            )
        }
    }

    @Test
    fun waveClearBonusIsNoLongerTheHiddenBulkOfTheBudget() {
        // Bugun ikramiye 35 ve bolum uzunluguyla sessizce buyuyor (L1 175, L8 315).
        assertEquals(175, SupplyBudgetModel.waveClearBonusTotal(1, SupplyBudgetModel.LEGACY_WAVE_CLEAR_BONUS))
        assertEquals(315, SupplyBudgetModel.waveClearBonusTotal(8, SupplyBudgetModel.LEGACY_WAVE_CLEAR_BONUS))
        // Onerilen 18 ile ikramiye hicbir bolumde butcenin %25'ini gecmez.
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val share = 100 * SupplyBudgetModel.waveClearBonusTotal(level) /
                SupplyBudgetModel.supplyBudget(level)
            assertTrue("L$level dalga ikramiyesi butcenin %%$share'i", share <= 25)
        }
    }

    // =================================================================================
    // 3. META YUKSELTME SIKILASTIRMAYI KIRMAZ (ama odullendirir)
    // =================================================================================

    @Test
    fun metaSupplyUpgradeRewardsInvestmentWithoutRestoringTheOldAbundance() {
        // Maksli STARTING_SUPPLY (+150) L1'de 80 -> 230 yapar. Bu kasitli bir oduldur;
        // ama L1 butcesi hala eski 1.177'nin cok altinda kalmali.
        val metaBonus = MetaUpgrades(startingSupplyRank = 6).startingSupply -
            EconomyConfig.BASE_STARTING_SUPPLY
        assertEquals(150, metaBonus)

        for (level in 1..6) {
            val maxedBudget = SupplyBudgetModel.supplyBudget(level) + metaBonus
            assertTrue(
                "L$level: maksli meta ile butce $maxedBudget, eski bol butce " +
                    "${SupplyBudgetModel.legacySupplyBudget(level)} — bolluk geri geldi",
                maxedBudget < SupplyBudgetModel.legacySupplyBudget(level),
            )
        }
    }

    @Test
    fun metaBaselineStaysCompatibleWithTheGameConfigContract() {
        // L7+ tabani 150 kalmali, yoksa `EconomyGameConfigContractTest`in
        // `BASE_STARTING_SUPPLY == INITIAL_GOLD` ve `MetaUpgrades().startingSupply`
        // sozlesmeleri kirilir.
        assertEquals(EconomyConfig.BASE_STARTING_SUPPLY, MetaUpgrades().startingSupply)
        assertEquals(GameConfig.INITIAL_GOLD, startingSupplyFor(EconomyConfig.FIRST_PAID_LEVEL))
    }

    // =================================================================================
    // 4. COIN EKONOMISI ETKILENMEZ (enflasyon/deflasyon yok)
    // =================================================================================

    @Test
    fun tighteningTouchesOnlySupplyAndNeverCoinRewards() {
        // Tedarik ve Coin arasinda donusum YOK (GDD D.4): sikilastirma bolum odullerini,
        // kilit bedellerini ve agac fiyatlarini DEGISTIRMEZ.
        assertEquals(3_140, EconomyConfig.TOTAL_LOCK_COST)
        assertEquals(13_900, EconomyConfig.TREE_TOTAL_COST)
        assertEquals(450, EconomyConfig.R1_COIN_BUDGET_PER_DAY)
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(140 + 30 * (level - 1), baseLevelReward(level))
        }
    }

    @Test
    fun everyLevelIsStillClearableWithoutAnyBoosterAfterTightening() {
        // PAY-TO-WIN KALKANI: sikilastirma "guclendirici almazsan gecemezsin" durumu
        // yaratmamali. Guclendiricisiz butce tasarlanan kadroyu karsiliyor mu?
        for (level in 1..SupplyBudgetModel.MODELLED_LEVELS) {
            val withoutBoosters = SupplyBudgetModel.supplyBudget(level)
            assertTrue(
                "L$level guclendiricisiz butce kadroyu karsilamiyor",
                withoutBoosters >= SupplyBudgetModel.designedLoadoutCost(level),
            )
            // Hava Destegi Tedarik HARCAR; onu almak kadroyu imkansiz kilmamali.
            if (boosterUnlocked(BoosterType.AIR_SUPPORT, level)) {
                assertTrue(
                    "L$level: hava destegi alan oyuncu kadroyu kuramaz hale geliyor",
                    withoutBoosters - boosterPrice(BoosterType.AIR_SUPPORT, level) >=
                        SupplyBudgetModel.designedLoadoutCost(level) * 80 / 100,
                )
            }
        }
    }

    // =================================================================================
    // 5. DEVIR TAMAMLANDI — sozlesme kilidi
    // =================================================================================

    /**
     * Onceki hali (`gameConfigStillUsesTheLegacyFlatSupply`) bugunku gercegi
     * belgeliyordu: `LevelSpec.startingSupply` alani vardi ama hicbir bolumde
     * override edilmiyordu, 22 bolum de 150 ile basliyordu. **Kirilmasi ISTENEN**
     * bir testti ve gameplay ajani ECONOMY_SPEC 9 madde 1'i uygulayinca kirildi.
     *
     * Yerine gecen sey artik bir "yapilacaklar" notu degil, iki katmani birbirine
     * baglayan KILIT: model ile GameConfig ayrisirsa bolum secme ekrani bir butce
     * gosterip savas baska butceyle baslar.
     */
    @Test
    fun gameConfigStartingSupplyMatchesTheBudgetModel() {
        GameConfig.CAMPAIGN.forEach { spec ->
            assertEquals(
                "L${spec.levelId} baslangic Tedariki model ile uyusmuyor",
                startingSupplyFor(spec.levelId),
                spec.startingSupply,
            )
        }
        // Ilk alti bolum artik DUZ DEGIL: sikilastirmanin gercekten uygulandiginin
        // kaniti (aksi halde yukaridaki assert duz tabloda da gecerdi).
        val early = (1..6).map { GameConfig.levelSpec(it).startingSupply }
        assertEquals("ilk alti bolum butcesi", listOf(80, 90, 110, 120, 140, 150), early)
        assertEquals(
            "L7+ taban Tedarige (INITIAL_GOLD) donmeli",
            GameConfig.INITIAL_GOLD,
            GameConfig.levelSpec(7).startingSupply,
        )
    }
}

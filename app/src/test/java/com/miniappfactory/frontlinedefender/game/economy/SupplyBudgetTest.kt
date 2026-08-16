package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.model.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 10 — SAVAS ICI TEDARIK BUTCESI (ECONOMY_SPEC A).
 *
 * Testcinin sikayetini olculebilir hale getirir ve onerilen sayilarin gercekten
 * cozdugunu kanitlar:
 *   *"6 tane kule yapacak para kazaniyorsun ama 2 tanesi yetiyor."*
 *
 * Bu sinif **oneriyi** dogrular; `GameConfig` henuz sikilastirilmadigi icin motora
 * karsi sozlesme testi burada YOKTUR (uygulanmasi baska ajanin isi, ECONOMY_SPEC 9).
 * Uygulama yapildiginda [gameConfigStillUsesTheLegacyFlatSupply] testi ters cevrilir
 * ve gercek bir sozlesme kilidine donusur.
 */
class SupplyBudgetTest {

    // =================================================================================
    // 1. TESHIS — bolluk sayiyla kanitlanir
    // =================================================================================

    @Test
    fun todaysEconomyIsMeasurablyFarTooFlush() {
        // SPI > 4 = artan Tedarigin harcanacak yeri yok, karar olur.
        for (level in 1..6) {
            val spi = SupplyBudgetModel.legacySupplyPressureIndex(level)
            assertTrue(
                "L$level bugunku SPI %.2f — sikayet yoksa test yanlis".format(spi),
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
        val legacy = mapOf(
            "INFANTRY" to 12, "FAST_SOLDIER" to 15, "SHIELDED_TROOPER" to 22,
            "ARMORED_VEHICLE" to 28, "TANK" to 60, "COMMAND_TANK" to 180,
        )
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

    @Test
    fun recommendedBudgetTableMatchesEconomySpec() {
        // ECONOMY_SPEC A.3 tablosunun "yeni butce" kolonu.
        val expected = mapOf(
            1 to 454, 2 to 556, 3 to 654, 4 to 950, 5 to 877, 6 to 1188,
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

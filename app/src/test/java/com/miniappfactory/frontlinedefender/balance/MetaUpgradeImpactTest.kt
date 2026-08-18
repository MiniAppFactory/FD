package com.miniappfactory.frontlinedefender.balance

import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.MetaUpgrades
import com.miniappfactory.frontlinedefender.game.economy.UpgradeLine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.TowerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ===========================================================================
 * META YUKSELTMELERIN **HISSEDILIRLIGI** — FUN_AUDIT TOP-10 madde 6
 * ===========================================================================
 *
 * Denetim iki ayri hastalik buldu; bu dosya ikisini de kilitler.
 *
 * ## 1. Rank 0 oyuncuyu GERILETEMEZ ("gizli vergi" yasagi)
 * Hurda Degeri hatti tam olarak bunu yapiyordu: oyunun her zamanki satis iadesi
 * %70 iken meta tabani 0,50 gonderilmisti, yani 1.700 coinlik hat oyuncuya
 * ZATEN SAHIP OLDUGU seyi geri satiyordu. Kural: **bir hattin rank 0 degeri, o
 * yukseltme hic alinmamisken oyuncunun sahip oldugu degerdir.**
 *
 * ## 2. Rank basina adim FARK EDILEBILIR olmali
 * +%3/rank, Gatling kd.1'in DPS'ini 43,8 -> 45,1 yapiyordu (0,03 kule kadar ek
 * guc): oyuncu icin bu bir odul degil bir makbuzdur. Esik
 * [EconomyConfig.MIN_PERCEPTIBLE_STEP].
 *
 * ## Neden hem ANALITIK hem OLCULMUS kilit var
 * Yuzde esigi sayiyi kontrol eder ama "sahada ne demek" sorusunu cevaplamaz.
 * [CampaignSimulator] ayni motorla meta 0 ve tam rank kosar, yani iddia
 * oynanisla dogrulanir. Sizinti sayisi, gecebilen tum dikkatli-oyuncu
 * davranislarinin EN IYISINDEN alinir ([MetaImpact.bestLeaks]); tek bir
 * davranisin sonucu meta degisince davranis da degistigi icin yaniltir.
 */
class MetaUpgradeImpactTest {

    /** Ogretici bant + perde finalleri + gec oyun. */
    private val probeLevels = listOf(1, 4, 8, 11, 22, 34, 45, 55)

    private val tutorialLevels = 1..8

    private fun maxedTree(): MetaUpgrades = MetaUpgrades(
        firepower = UpgradeLine.FIREPOWER.maxRank,
        optics = UpgradeLine.OPTICS.maxRank,
        startingSupplyRank = UpgradeLine.STARTING_SUPPLY.maxRank,
        fortification = UpgradeLine.FORTIFICATION.maxRank,
        salvage = UpgradeLine.SALVAGE.maxRank,
    )

    private fun only(line: UpgradeLine, rank: Int) = MetaUpgrades().withRank(line, rank)

    // =================================================================================
    // 1. RANK 0 = OYUNCUNUN ZATEN SAHIP OLDUGU DEGER
    // =================================================================================

    /**
     * **HURDA HATTININ REGRESYON KILIDI.**
     *
     * Motor kule kurarken `salvageRate = metaSalvageRate` verir
     * (`GameEngine.refreshMetaUpgrades`), yani `TowerEntity`nin varsayilani sahaya
     * HIC cikmaz: rank 0 oyuncunun gercek iadesi meta tabanidir. Ikisi ayrisirsa
     * yukseltme hatti sessizce bir VERGIYE doner. Bu test iki sayiyi birbirine
     * baglar — elle yazilmis bir sabite degil, entity'nin KENDI varsayilanina.
     */
    @Test
    fun salvageRankZeroMatchesTheRefundThePlayerAlreadyHad() {
        val engineDefault = TowerEntity(
            type = GameConfig.TowerType.MACHINE_GUN, buildSpotId = 1, posX = 0f, posY = 0f,
            totalInvestedGold = 60
        ).salvageRate

        assertEquals(
            "rank 0 iadesi, yukseltme alinmadan once oyuncunun sahip oldugu iade olmali",
            engineDefault.toDouble(), MetaUpgrades().salvageRatio, 1e-6,
        )
        assertEquals(
            "EconomyConfig tabani ile TowerEntity varsayilani ayrisamaz",
            EconomyConfig.BASE_SALVAGE_RATIO, engineDefault.toDouble(), 1e-6,
        )
    }

    /** Hattaki HICBIR rank oyuncuyu baslangictan geriye goturemez. */
    @Test
    fun noUpgradeRankLeavesThePlayerWorseOffThanBuyingNothing() {
        val zero = MetaUpgrades()
        for (line in UpgradeLine.entries) {
            for (rank in 1..line.maxRank) {
                val m = only(line, rank)
                assertTrue("$line r$rank hasar geriledi", m.damageMultiplier >= zero.damageMultiplier)
                assertTrue("$line r$rank menzil geriledi", m.rangeMultiplier >= zero.rangeMultiplier)
                assertTrue("$line r$rank sermaye geriledi", m.startingSupply >= zero.startingSupply)
                assertTrue("$line r$rank can geriledi", m.maxBaseHealth >= zero.maxBaseHealth)
                assertTrue("$line r$rank iade geriledi", m.salvageRatio >= zero.salvageRatio)
            }
        }
    }

    /**
     * Ayni kural oynanis tarafinda: rank 0 bir kulenin satis degeri, motorun
     * yukseltmesiz varsayilaniyla AYNI olmali (dusuk degil).
     */
    @Test
    fun sellingATowerAtRankZeroRefundsTheSameAsWithoutTheUpgradeLine() {
        GameConfig.TowerType.values().forEach { type ->
            val spec = GameConfig.TOWER_SPECS.getValue(type)
            val invested = spec.buildCost + (spec.tier(2).upgradeCost)
            val default = TowerEntity(
                type = type, buildSpotId = 1, posX = 0f, posY = 0f,
                level = 2, totalInvestedGold = invested
            )
            val rankZero = TowerEntity(
                type = type, buildSpotId = 1, posX = 0f, posY = 0f,
                level = 2, totalInvestedGold = invested,
                salvageRate = MetaUpgrades().salvageRatio.toFloat()
            )
            assertEquals(
                "$type: Hurda Degeri rank 0 oyuncuyu geriletiyor " +
                    "(${default.sellValue} -> ${rankZero.sellValue})",
                default.sellValue, rankZero.sellValue,
            )
        }
    }

    // =================================================================================
    // 2. MONOTONLUK + ANLAMLI ADIM
    // =================================================================================

    @Test
    fun everyLineIsStrictlyMonotonicRankOverRank() {
        for (line in UpgradeLine.entries) {
            for (rank in 1..line.maxRank) {
                val prev = only(line, rank - 1)
                val cur = only(line, rank)
                val grew = when (line) {
                    UpgradeLine.FIREPOWER -> cur.damageMultiplier > prev.damageMultiplier
                    UpgradeLine.OPTICS -> cur.rangeMultiplier > prev.rangeMultiplier
                    UpgradeLine.STARTING_SUPPLY -> cur.startingSupply > prev.startingSupply
                    UpgradeLine.FORTIFICATION -> cur.maxBaseHealth > prev.maxBaseHealth
                    UpgradeLine.SALVAGE -> cur.salvageRatio > prev.salvageRatio
                }
                assertTrue("$line r${rank - 1} -> r$rank etkiyi buyutmuyor", grew)
                assertTrue("$line r$rank fiyati pozitif olmali", line.costOfRank(rank) > 0)
            }
        }
    }

    /**
     * **HISSEDILIRLIK ESIGI.** Her rank, kendi ekseninin TABANININ en az
     * [EconomyConfig.MIN_PERCEPTIBLE_STEP] kadarini degistirmeli. Yuzde tabanli
     * hatlarda bu dogrudan yuzde adimidir; sayi tabanli hatlarda adimin tabana
     * oranidir (+25/150 = %16,7 · +2/20 = %10 · +5 puan iade = %7,1).
     *
     * Bu esik, `+%3/rank`in bir daha sessizce geri gelmesini engeller.
     */
    @Test
    fun everyRankChangesEnoughToBeNoticed() {
        val min = EconomyConfig.MIN_PERCEPTIBLE_STEP
        for (line in UpgradeLine.entries) {
            for (rank in 1..line.maxRank) {
                val prev = only(line, rank - 1)
                val cur = only(line, rank)
                val relative = when (line) {
                    UpgradeLine.FIREPOWER -> cur.damageMultiplier - prev.damageMultiplier
                    UpgradeLine.OPTICS -> cur.rangeMultiplier - prev.rangeMultiplier
                    UpgradeLine.STARTING_SUPPLY ->
                        (cur.startingSupply - prev.startingSupply).toDouble() /
                            EconomyConfig.BASE_STARTING_SUPPLY
                    UpgradeLine.FORTIFICATION ->
                        (cur.maxBaseHealth - prev.maxBaseHealth).toDouble() /
                            EconomyConfig.BASE_MAX_HEALTH
                    UpgradeLine.SALVAGE ->
                        (cur.salvageRatio - prev.salvageRatio) / EconomyConfig.BASE_SALVAGE_RATIO
                }
                assertTrue(
                    "$line r$rank adimi %.1f%% — %.0f%% esiginin altinda, oyuncu FARK ETMEZ"
                        .format(relative * 100, min * 100),
                    relative >= min - 1e-9,
                )
            }
        }
    }

    /** Agacin toplam gucu ve fiyati GDD F ile ayni kalmali (granularite degisti, guc degil). */
    @Test
    fun regranulationKeptTheTreeTotalsIntact() {
        val maxed = maxedTree()
        assertEquals(EconomyConfig.TREE_TOTAL_COST, maxed.spentCoins())
        assertEquals(EconomyConfig.TREE_TOTAL_RANKS, maxed.totalRanks())
        assertEquals("hasar +%24", 1.24, maxed.damageMultiplier, 1e-9)
        assertEquals("menzil +%15", 1.15, maxed.rangeMultiplier, 1e-9)
        assertEquals(300, maxed.startingSupply)
        assertEquals(30, maxed.maxBaseHealth)
        assertEquals("etkin verim 1,426 — zorluk egrisi kilidi bunun uzerine kurulu",
            1.4260, maxed.effectiveThroughput, 1e-4)
        assertEquals(4_000, UpgradeLine.FIREPOWER.totalCost())
        assertEquals(2_750, UpgradeLine.OPTICS.totalCost())
    }

    /**
     * **FIYAT/ETKI EGRISI KORUNDU.** Yeniden bolumleme bir zam DEGIL: Ates Gucu
     * hattinda ayni yuzdeye ulasmanin bedeli birebir aynidir (eski r1+r2 =
     * 150+250 = 400 = yeni r1). Bu test, "hissedilirlik" bahanesiyle fiyat
     * yukseltilmedigini kilitler (Hard Rule: booster/rank fiyati yalnizca gelir
     * amacli yukseltilemez).
     */
    @Test
    fun biggerStepsDidNotRaiseThePricePerPercent() {
        val expectedCumulative = mapOf(0.06 to 400, 0.12 to 1_200, 0.18 to 2_400, 0.24 to 4_000)
        var cumulative = 0
        for (rank in 1..UpgradeLine.FIREPOWER.maxRank) {
            cumulative += UpgradeLine.FIREPOWER.costOfRank(rank)
            val effect = only(UpgradeLine.FIREPOWER, rank).damageMultiplier - 1.0
            val expected = expectedCumulative.entries.first { Math.abs(it.key - effect) < 1e-9 }
            assertEquals(
                "hasar +%${(effect * 100).toInt()} icin odenen toplam degismemeli",
                expected.value, cumulative,
            )
        }
    }

    // =================================================================================
    // 3. OLCULMUS OYNANIS ETKISI
    // =================================================================================

    /**
     * Tam agac, dikkatli oyuncunun sonucunu OLCULEBILIR sekilde iyilestirmeli ve
     * hicbir bolumde kotulestirmemeli. "Kotulestirmeme" sarti, hurda hattindaki
     * gizli verginin oynanis tarafindaki karsiligidir.
     */
    @Test
    fun theFullTreeMeasurablyImprovesPlayAndNeverMakesALevelWorse() {
        val full = maxedTree()
        var zeroTotal = 0
        var fullTotal = 0
        for (level in probeLevels) {
            val zero = MetaImpact.bestLeaks(level)
            val maxed = MetaImpact.bestLeaks(level, full)
            assertTrue("L$level meta 0 ile gecilemiyor", zero.cleared)
            assertTrue("L$level tam meta ile gecilemiyor", maxed.cleared)
            assertTrue(
                "L$level: tam agac sizintiyi ARTIRIYOR (${zero.leaked} -> ${maxed.leaked})",
                maxed.leaked <= zero.leaked,
            )
            assertTrue(
                "L$level: tam agac yildizi DUSURUYOR (${zero.stars} -> ${maxed.stars})",
                maxed.stars >= zero.stars,
            )
            zeroTotal += zero.leaked
            fullTotal += maxed.leaked
        }
        assertTrue(
            "tam agac olcum bolumlerinde hicbir sey degistirmiyor " +
                "($zeroTotal -> $fullTotal sizinti)",
            fullTotal < zeroTotal,
        )
    }

    /**
     * Hasar/menzil/sermaye hatlari **tek baslarina** da olculebilir bir fark
     * yaratmali. Tahkimat ve Hurda Degeri bu olcute GIRMEZ ve girmemeli:
     * Tahkimat sizintiyi azaltmaz, TOLERE EDILEN sizintiyi buyutur; Hurda Degeri
     * kule satisiyla calisir ve simulator kule satmaz. Ikisinin kilitleri
     * asagida kendi eksenlerinde.
     */
    @Test
    fun eachOffensiveLineOnItsOwnMeasurablyImprovesPlay() {
        val baseline = probeLevels.sumOf { MetaImpact.bestLeaks(it).leaked }
        listOf(
            UpgradeLine.FIREPOWER,
            UpgradeLine.OPTICS,
            UpgradeLine.STARTING_SUPPLY,
        ).forEach { line ->
            val withLine = probeLevels.sumOf {
                MetaImpact.bestLeaks(it, only(line, line.maxRank)).leaked
            }
            assertTrue(
                "$line tam rank tek basina hicbir sey degistirmiyor " +
                    "($baseline -> $withLine sizinti)",
                withLine < baseline,
            )
        }
    }

    /**
     * **ILK SATIN ALMA HISSEDILMELI.** Yeni oyuncunun ilk aldigi rank, agacin
     * tamamini beklemeden olculebilir bir fark yapmali; aksi halde "para
     * biriktirdim, hicbir sey degismedi" hissi ilk saatte olusur.
     */
    @Test
    fun theVeryFirstRankOfAnOffensiveLineIsAlreadyMeasurable() {
        val baseline = probeLevels.sumOf { MetaImpact.bestLeaks(it).leaked }
        listOf(UpgradeLine.FIREPOWER, UpgradeLine.OPTICS, UpgradeLine.STARTING_SUPPLY)
            .forEach { line ->
                val afterFirst = probeLevels.sumOf {
                    MetaImpact.bestLeaks(it, only(line, 1)).leaked
                }
                assertTrue(
                    "$line rank 1 (${line.costOfRank(1)} coin) olculebilir bir sey vermiyor " +
                        "($baseline -> $afterFirst sizinti)",
                    afterFirst < baseline,
                )
            }
    }

    /** Tahkimat sizintiyi degil TOLERANSI buyutur — kendi ekseninde olculur. */
    @Test
    fun fortificationBuysSurvivalMarginNotFewerLeaks() {
        var prev = -1
        for (rank in 0..UpgradeLine.FORTIFICATION.maxRank) {
            val survivable = only(UpgradeLine.FORTIFICATION, rank).maxBaseHealth - 1
            assertTrue("Tahkimat r$rank tolerans buyutmuyor", survivable > prev)
            prev = survivable
        }
        assertEquals(
            "tam Tahkimat, tolere edilen sizintiyi %50 buyutmeli (19 -> 29)",
            29, only(UpgradeLine.FORTIFICATION, UpgradeLine.FORTIFICATION.maxRank).maxBaseHealth - 1,
        )
    }

    /** Hurda Degeri kule satisiyla olculur; ust sinir 1,0'in ALTINDA kalmali. */
    @Test
    fun salvageBuysBackASubstantialShareOfAMaxedTower() {
        val spec = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val invested = spec.buildCost + spec.tier(2).upgradeCost + spec.tier(3).upgradeCost
        fun refund(rank: Int) = TowerEntity(
            type = GameConfig.TowerType.MACHINE_GUN, buildSpotId = 1, posX = 0f, posY = 0f,
            level = 3, totalInvestedGold = invested,
            salvageRate = only(UpgradeLine.SALVAGE, rank).salvageRatio.toFloat()
        ).sellValue

        val zero = refund(0)
        val maxed = refund(UpgradeLine.SALVAGE.maxRank)
        assertTrue("Hurda Degeri maks rank iadeyi buyutmuyor ($zero -> $maxed)", maxed > zero)
        assertTrue(
            "tam hat, kd.3 kulenin yatiriminin en az %20'sini daha geri vermeli " +
                "($zero -> $maxed / $invested)",
            (maxed - zero) >= invested / 5,
        )
        assertTrue(
            "iade orani 1,0'a ULASAMAZ, yoksa kur-sat dongusu bedava olur",
            only(UpgradeLine.SALVAGE, UpgradeLine.SALVAGE.maxRank).salvageRatio < 1.0,
        )
    }

    // =================================================================================
    // 4. OGRETICI EGRI (L1-L8) TAM META ILE DE BOZULMAZ
    // =================================================================================

    /**
     * Meta, oynamanin YERINE gecemez: tam agacla bile hicbir sey kurmayan oyuncu
     * ogretici bandin hicbir bolumunu gecemez.
     */
    @Test
    fun theFullTreeStillCannotWinATutorialLevelWithoutBuildingAnything() {
        val full = maxedTree()
        val survived = tutorialLevels.filter { level ->
            CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.IDLE,
                null,
                full,
            ).cleared
        }
        assertTrue(
            "tam meta ile hic kule kurmadan gecilen ogretici bolum(ler): $survived",
            survived.isEmpty(),
        )
    }

    /**
     * **"IKINCI KULE" KARARI TAM META ILE DE ANLAMLI KALIR.**
     *
     * Olculen gercek (rapor aracindaki ogretici bant tablosu):
     *
     *     L   tek kule (meta 0)        tek kule (tam agac)
     *     1   GECTI 17/20  2 yildiz    GECTI 30/30  3 yildiz
     *     4   GECTI  1/20  1 yildiz    GECTI 14/30  1 yildiz
     *     5   KAYIP                    GECTI 12/30  1 yildiz
     *     8   KAYIP                    GECTI  9/30  1 yildiz
     *
     * Yani tam agac, ilerlemis oyuncunun ogretici bolumleri TEK kuleyle
     * BITIRMESINE izin verir — bu bir hata degil, 13.900 coin odemis oyuncunun
     * hak ettigi guc hissidir (ve zaten yalnizca tekrar oynarken karsilasilir).
     * Kirilmamasi gereken sey ZAFER degil YILDIZ: yildiz meta-notr oldugu icin
     * (starHealthFromLeaks) fazladan can 3 yildiz SATIN ALAMAZ. L4'ten
     * itibaren tek kule tam metayla bile 3 yildiz alamaz, yani "ikinci kuleyi
     * ne zaman kurayim" karari yildiz kovalayan oyuncu icin ayakta kalir.
     *
     * Ilk uc bolum bu kilidin DISINDA: onlar zaten tek kuleyle 3 yildiz
     * alinabilecek kadar hafif tasarlandi (meta 0'da bile L1 2 yildiz veriyor).
     */
    @Test
    fun theStarChaseStillNeedsMoreThanOneTowerEvenWithTheFullTree() {
        val full = maxedTree()
        for (level in 4..8) {
            val solo = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.SINGLE_TOWER,
                null,
                full,
            )
            assertTrue(
                "L$level tam metayla TEK kuleyle ${solo.stars} yildiz aliyor " +
                    "(kadro ${solo.roster}) — ikinci kule karari anlamsizlasti",
                solo.stars < 3,
            )
        }
    }

    /**
     * Simulatorun META 0 davranisi degismedi: yeni oyuncunun ogretici egrisi
     * (WaveDefinitionsDataTest'teki "tek kalabalik kulesi akisi gecemez"
     * kuralinin ta kendisi) meta destegi eklendikten sonra da aynen duruyor.
     * Cihaz kaniti (2026-08-18): L1 tek Gatling ile 3. dalgada kaybedildi;
     * kule olduruyordu ama tek kule yetmiyordu.
     */
    @Test
    fun withoutAnyMetaASingleTowerStillCannotClearTheArmouredTutorialLevels() {
        for (level in 5..6) {
            val solo = CampaignSimulator.play(
                CampaignSimulator.LevelModel(level),
                CampaignSimulator.Playstyle.SINGLE_TOWER,
            )
            assertTrue(
                "L$level meta 0 ile TEK kuleyle geciliyor (kadro ${solo.roster}) — " +
                    "tek kule akisi gecemez kurali kirildi",
                !solo.cleared,
            )
        }
    }
}

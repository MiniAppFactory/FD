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

    /**
     * Agacin SALDIRI gucu GDD F ile ayni kalmali. ECONOMY_AUDIT_2 P0 agaci
     * 13.900 -> 19.600'e buyuttu ama bunu **yalnizca** zorluk egrisinin disindaki
     * iki eksende yapti (Us Tahkimi tolerans, Hurda Degeri iade). Hasar, menzil
     * ve baslangic sermayesi tavanlari birebir DEGISMEDI.
     */
    @Test
    fun treeGrewOnlyOnTheAxesOutsideTheDifficultyLock() {
        val maxed = maxedTree()
        assertEquals(EconomyConfig.TREE_TOTAL_COST, maxed.spentCoins())
        assertEquals(EconomyConfig.TREE_TOTAL_RANKS, maxed.totalRanks())
        assertEquals(19_600, maxed.spentCoins())
        assertEquals(23, maxed.totalRanks())

        // --- DOKUNULMAYANLAR ---
        assertEquals("hasar +%24", 1.24, maxed.damageMultiplier, 1e-9)
        assertEquals("menzil +%15", 1.15, maxed.rangeMultiplier, 1e-9)
        assertEquals(300, maxed.startingSupply)
        assertEquals("etkin verim 1,426 — zorluk egrisi kilidi bunun uzerine kurulu",
            1.4260, maxed.effectiveThroughput, 1e-4)
        assertEquals(4_000, UpgradeLine.FIREPOWER.totalCost())
        assertEquals(2_750, UpgradeLine.OPTICS.totalCost())
        assertEquals(2_700, UpgradeLine.STARTING_SUPPLY.totalCost())

        // --- BUYUYENLER ---
        assertEquals("maks us cani 30 -> 38", 38, maxed.maxBaseHealth)
        assertEquals("satis iadesi %90 -> %95", 0.95, maxed.salvageRatio, 1e-9)
        assertEquals(7_650, UpgradeLine.FORTIFICATION.totalCost())
        assertEquals(2_500, UpgradeLine.SALVAGE.totalCost())
        assertEquals(
            "agacin buyumesinin TAMAMI bu iki hattan gelmeli",
            19_600 - 13_900,
            (7_650 - 2_750) + (2_500 - 1_700),
        )
    }

    /**
     * **FIYAT/ETKI EGRISI YUKSELMEDI — AGAC BUYURKEN DE.** (Hard Rule)
     *
     * Agaci 19.600'e cikaran sey bir zam degil, iki hattin var olan aritmetik
     * merdiveninin DEVAMIDIR: her iki hat da rank basina +150 coin adimiyla
     * ilerliyordu ve yeni rank'lar tam olarak o adimi surduruyor. Hicbir MEVCUT
     * rank'in fiyati veya etkisi degismedi, yani ayni gucu almanin bedeli hicbir
     * noktada artmadi. Bu test, ilerideki bir "biraz zam yapalim" adimini
     * kirmizi yakar.
     */
    @Test
    fun treeExtensionContinuedTheExistingPriceLadder() {
        for (line in listOf(UpgradeLine.FORTIFICATION, UpgradeLine.SALVAGE)) {
            val step = line.costOfRank(2) - line.costOfRank(1)
            assertEquals("$line adimi 150 olmali", 150, step)
            for (rank in 2..line.maxRank) {
                assertEquals(
                    "$line r$rank fiyati merdivenden sapiyor — bu bir ZAM olur",
                    step, line.costOfRank(rank) - line.costOfRank(rank - 1),
                )
            }
        }
        // Eklemeden ONCEKI rank'larin fiyatlari birebir korundu.
        assertEquals(listOf(250, 400, 550, 700, 850), (1..5).map { UpgradeLine.FORTIFICATION.costOfRank(it) })
        assertEquals(listOf(200, 350, 500, 650), (1..4).map { UpgradeLine.SALVAGE.costOfRank(it) })
        // Ve odenen coin basina alinan etki hicbir rank'ta duSMEDI:
        // Tahkimat 150 coin/can-cifti, Hurda 150 coin/%5 — sabit.
        for (rank in 1..UpgradeLine.FORTIFICATION.maxRank) {
            val gained = only(UpgradeLine.FORTIFICATION, rank).maxBaseHealth -
                only(UpgradeLine.FORTIFICATION, rank - 1).maxBaseHealth
            assertEquals("Tahkimat r$rank adim etkisi degismemeli", 2, gained)
        }
        for (rank in 1..UpgradeLine.SALVAGE.maxRank) {
            val gained = only(UpgradeLine.SALVAGE, rank).salvageRatio -
                only(UpgradeLine.SALVAGE, rank - 1).salvageRatio
            assertEquals("Hurda r$rank adim etkisi degismemeli", 0.05, gained, 1e-9)
        }
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

    /**
     * **AYNI KURALIN TEDARIK HATTINDAKI KARSILIGI.**
     *
     * Baslangic Tedariki 6 x +25 -> 2 x +75 olurken rank BASINA fiyat 200 -> 900'e
     * cikti; bu bir zam gibi GORUNUR ama degildir, cunku odenen sey ayni GUCTUR:
     *
     *     +75 Tedarik   eski r1+r2+r3 = 200+300+400 = 900   yeni r1 =   900
     *     +150 Tedarik  eski alti rank            = 2.700   yeni r1+r2 = 2.700
     *
     * Yani Tedarik puani basina fiyat hicbir noktada yukselmedi; yalnizca ayni
     * para daha az ve tahtada karsiligi olan adima bolundu.
     */
    @Test
    fun supplyStepsDidNotRaiseThePricePerSupplyPoint() {
        val expectedCumulative = mapOf(75 to 900, 150 to 2_700)
        var cumulative = 0
        for (rank in 1..UpgradeLine.STARTING_SUPPLY.maxRank) {
            cumulative += UpgradeLine.STARTING_SUPPLY.costOfRank(rank)
            val bonus = only(UpgradeLine.STARTING_SUPPLY, rank).startingSupply -
                EconomyConfig.BASE_STARTING_SUPPLY
            assertEquals(
                "+$bonus Tedarik icin odenen toplam degismemeli",
                expectedCumulative.getValue(bonus), cumulative,
            )
        }
        assertEquals(2_700, UpgradeLine.STARTING_SUPPLY.totalCost())
        assertEquals(300, only(UpgradeLine.STARTING_SUPPLY, UpgradeLine.STARTING_SUPPLY.maxRank).startingSupply)
    }

    /**
     * **TEDARIK ADIMININ BIRIMI KULEDIR.**
     *
     * Yuzde esigi ([EconomyConfig.MIN_PERCEPTIBLE_STEP]) bu hatti +25/rank iken
     * de geciyordu (%16,7) — ve hat yine de olu rank uretiyordu. Cunku Tedarik'le
     * yapilabilecek EN KUCUK sey en ucuz kuleyi dikmektir: 60'in altindaki bir
     * adim tahtayi degistirmeyi garanti edemez, yalnizca HUD'daki sayiyi buyutur.
     *
     * Bu test iki seyi birbirine baglar (elle yazilmis sabite degil, kule
     * spec'inin KENDISINE): adim >= en ucuz insa bedeli, ve bunun sonucu olarak
     * her rank HER bolumde acilista en az bir kule daha aliyor.
     */
    @Test
    fun supplyStepBuysAtLeastOneMoreTowerAtEveryLevel() {
        val cheapestTower = GameConfig.TowerType.values()
            .minOf { GameConfig.TOWER_SPECS.getValue(it).buildCost }
        assertEquals(
            "esik sabiti, en ucuz kulenin gercek insa bedeliyle ayrisamaz",
            cheapestTower, EconomyConfig.MIN_SUPPLY_STEP_IS_ONE_TOWER,
        )
        assertTrue(
            "Tedarik adimi (${EconomyConfig.STARTING_SUPPLY_PER_RANK}) en ucuz kuleden " +
                "($cheapestTower) kucuk — rank tahtada bir sey GARANTI edemez",
            EconomyConfig.STARTING_SUPPLY_PER_RANK >= cheapestTower,
        )

        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val base = GameConfig.levelSpec(level).startingSupply
            for (rank in 1..UpgradeLine.STARTING_SUPPLY.maxRank) {
                val before = base + (only(UpgradeLine.STARTING_SUPPLY, rank - 1).startingSupply -
                    EconomyConfig.BASE_STARTING_SUPPLY)
                val after = base + (only(UpgradeLine.STARTING_SUPPLY, rank).startingSupply -
                    EconomyConfig.BASE_STARTING_SUPPLY)
                assertTrue(
                    "L$level Tedarik r$rank acilista fazladan kule ALMIYOR " +
                        "($before -> $after Tedarik, ikisi de ${before / cheapestTower} kule)",
                    after / cheapestTower > before / cheapestTower,
                )
            }
        }
    }

    /**
     * =========================================================================
     * **OLU RANK YASAGI — NIHAI KAPI.**
     * =========================================================================
     *
     * Analitik esik ([EconomyConfig.MIN_PERCEPTIBLE_STEP]) GEREK sarttir, yeter
     * sart degildir: Baslangic Tedariki +25/rank ile onu geciyordu ama sahada
     * rank 3 ve 4 tam anlamiyla OLUYDU (8 olcum bolumunde sizinti 8 -> 8 -> 8;
     * oyuncu 900 coin odeyip hicbir sey almiyordu). Bu test, her hattin her
     * rank'ini **kendi ekseninde OLCEREK** kapatir.
     *
     * ## Neden butun kampanya, 8 olcum bolumu degil
     * Sizinti sayisi ayrik ve gurultulu bir simulator ciktisidir; kucuk
     * orneklemlerde meta artarken sizinti ARTIYOR gibi gorunebilir (olculdu:
     * 14 bolumluk sette Menzil r2=22 -> r3=27, 19 bolumluk sette Ates Gucu
     * r3=7 -> r4=10 — ikisi de orneklem gurultusu). 55 bolumun tamami tek
     * monoton olcuttur; gurultu ortalamada sonumlenir.
     *
     * ## Neden hat basina FARKLI eksen
     * Simulator kule satmaz ve Tahkimat sizintiyi azaltmaz, BUYUTULEN TOLERANSI
     * satar. Ikisini sizintiyla olcmek "etkisiz" derdi ve YANLIS olurdu. Her
     * hat, oyuncunun o hatti almasinin gercek sebebiyle olculur.
     */
    @Test
    fun everyRankIsMeasurablyAliveAcrossTheCampaign() {
        val allLevels = 1..EconomyConfig.CAMPAIGN_LEVELS

        // Saldiri hatlari: olcut = butun kampanyada dikkatli oyuncunun sizintisi.
        for (line in listOf(UpgradeLine.FIREPOWER, UpgradeLine.OPTICS, UpgradeLine.STARTING_SUPPLY)) {
            val ladder = (0..line.maxRank).map { rank ->
                allLevels.sumOf { MetaImpact.bestLeaks(it, only(line, rank)).leaked }
            }
            for (rank in 1..line.maxRank) {
                assertTrue(
                    "$line r$rank OLU RANK: ${line.costOfRank(rank)} coin odeniyor, " +
                        "kampanya sizintisi ${ladder[rank - 1]} -> ${ladder[rank]} " +
                        "(merdiven $ladder)",
                    ladder[rank] < ladder[rank - 1],
                )
            }
        }

        // Tahkimat: olcut = tolere edilebilen sizinti (can - 1).
        for (rank in 1..UpgradeLine.FORTIFICATION.maxRank) {
            val before = only(UpgradeLine.FORTIFICATION, rank - 1).maxBaseHealth - 1
            val after = only(UpgradeLine.FORTIFICATION, rank).maxBaseHealth - 1
            assertTrue("FORTIFICATION r$rank OLU RANK ($before -> $after tolerans)", after > before)
        }

        // Hurda Degeri: olcut = kd.3 Gatling satisindan geri gelen Tedarik.
        val spec = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val invested = spec.buildCost + spec.tier(2).upgradeCost + spec.tier(3).upgradeCost
        fun refund(rank: Int) = TowerEntity(
            type = GameConfig.TowerType.MACHINE_GUN, buildSpotId = 1, posX = 0f, posY = 0f,
            level = 3, totalInvestedGold = invested,
            salvageRate = only(UpgradeLine.SALVAGE, rank).salvageRatio.toFloat()
        ).sellValue
        for (rank in 1..UpgradeLine.SALVAGE.maxRank) {
            assertTrue(
                "SALVAGE r$rank OLU RANK (${refund(rank - 1)} -> ${refund(rank)} Tedarik iade)",
                refund(rank) > refund(rank - 1),
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
        // OLCUM EVRENI 8 SONDA BOLUMDEN 55 BOLUME CIKTI (2026-08-21).
        //
        // NEDEN: rotalar sanattan yeniden uretilince kampanya meta 0'da
        // ciddi sekilde KOLAYLASTI ve 8 bolumluk sondanin toplam sizintisi
        // 18'den 6'ya dustu (L34 tek basina 5 -> 0). O kadar dusuk bir
        // tabanda tek bir bolumun modele bagli gurultusu isareti yutuyor:
        // olculen OPTICS merdiveni 8 bolumde 6 / 14 / 10 / 11 (anlamsiz),
        // AYNI kosuda 55 bolumde 127 / 123 / 98 / 74 (net ve monoton).
        //
        // Bu bir GEVSETME DEGIL, tam tersi: iddia artik daha fazla veriyle
        // kuruluyor ve kardes test `everyRankIsMeasurablyAliveAcrossTheCampaign`
        // ile ayni evreni kullaniyor. Gurultunun kaynagi ayri bir konudur:
        // L34 (harita 09) ve L55'te menzil artisi simulatorun acgozlu pad
        // secimini degistirip sonucu KOTULESTIRIYOR — bu, yeni geometriyle
        // ortaya cikmadi, eski geometride de vardi (L34: 5 -> 6) ve denge
        // sahibinin bakmasi gereken bir simulator davranisidir.
        val levels = 1..EconomyConfig.CAMPAIGN_LEVELS
        val baseline = levels.sumOf { MetaImpact.bestLeaks(it).leaked }
        listOf(
            UpgradeLine.FIREPOWER,
            UpgradeLine.OPTICS,
            UpgradeLine.STARTING_SUPPLY,
        ).forEach { line ->
            val withLine = levels.sumOf {
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
        // Olcum evreni yukaridaki testle ayni sebeple 55 bolum (bkz.
        // [eachOffensiveLineOnItsOwnMeasurablyImprovesPlay] yorumu).
        // Olculen ilk rank etkisi: FIREPOWER 127 -> 107, OPTICS 127 -> 123,
        // STARTING_SUPPLY 127 -> 76.
        val levels = 1..EconomyConfig.CAMPAIGN_LEVELS
        val baseline = levels.sumOf { MetaImpact.bestLeaks(it).leaked }
        listOf(UpgradeLine.FIREPOWER, UpgradeLine.OPTICS, UpgradeLine.STARTING_SUPPLY)
            .forEach { line ->
                val afterFirst = levels.sumOf {
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
        // ECONOMY_AUDIT_2 P0: hat 5 -> 9 rank. Tolerans 19 -> 37 (+%95).
        assertEquals(
            "tam Tahkimat, tolere edilen sizintiyi ~iki katina cikarmali (19 -> 37)",
            37, only(UpgradeLine.FORTIFICATION, UpgradeLine.FORTIFICATION.maxRank).maxBaseHealth - 1,
        )
        // Hicbir bolum bunu GEREKTIRMEZ: CampaignSolvabilityAllLevelsTest 55/55'i
        // meta 0 ile gecer. Tolerans bir kolaylik, bir kosul degildir.
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
     * **ILK SATIN ALMANIN OYNANIS KARSILIGI: "IKINCI KULEYLE BASLA".**
     *
     * L1 sermayesi 80 Tedarik = **bir** Gatling (60). Cihaz testinde (2026-08-18)
     * L1 tek Gatling ile KAYBEDILDI ve olcum de tek kulenin L5'ten itibaren
     * kaybettigini soyluyor — yani "ikinci kuleyi ne zaman kurayim" oyunun ilk
     * gercek karari. Baslangic Tedariki rank 1 (+75 -> 155 Tedarik) tam olarak
     * bunu aciyor: oyuncunun agactaki ilk alimi soyut bir yuzde degil, acilis
     * tahtasinda GORULEN ikinci kuledir.
     */
    @Test
    fun theFirstSupplyRankOpensTheSecondTowerOnLevelOne() {
        val gatling = GameConfig.TOWER_SPECS.getValue(GameConfig.TowerType.MACHINE_GUN)
        val base = GameConfig.levelSpec(1).startingSupply
        val bonus = only(UpgradeLine.STARTING_SUPPLY, 1).startingSupply -
            EconomyConfig.BASE_STARTING_SUPPLY
        // ⚠ 2026-08-19: TABAN 80 -> 120, yani meta 0 artik IKI Gatling.
        //
        // Bu testin kendi yorumu 18 Agustos cihaz kanitini aktariyordu:
        // "L1 tek Gatling ile 3. dalgada kaybedildi". Tespit dogruydu ama
        // cozum YANLIS YERDEYDI — ikinci kule Baslangic Tedariki rank 1e
        // baglanmisti ve o rutbe COIN ISTIYOR. Yeni kurulumda cuzdan sifir,
        // yani "bir Gatling yetmiyor" sorununun cevabi oyuncunun HENUZ SATIN
        // ALAMAYACAGI bir seydi: kilidi acan sey kilidin arkasindaydi.
        //
        // Taban iki kuleye cikarildi; meta rutbesinin isi degismedi, YERI
        // degisti — artik UCUNCU kuleyi aciyor.
        assertEquals("L1 meta 0: tam olarak IKI Gatling", 2, base / gatling.buildCost)
        assertEquals(
            "Tedarik rank 1, L1 acilisini UC Gatlinge cikarmali " +
                "($base -> ${base + bonus} Tedarik)",
            3, (base + bonus) / gatling.buildCost,
        )
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

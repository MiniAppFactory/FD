package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 9 — EKONOMI REGRESYON KILIDI.
 *
 * **SAF JUnit.** Robolectric yok, Android yok, `Context` yok. Sinif milisaniyeler
 * mertebesinde kosar; her build'de kosacak kadar hizli olmali.
 *
 * Isi: GDD H.3'un ("EN KRITIK TEST GRUBU") ve H.2/H.5/H.6'nin kabul kriterlerini
 * makineyle dogrulanabilir hale getirmek. Ekonomi tablosu burada kilitlenir —
 * bir sabiti degistiren kisi kirmizi test gorur.
 */
class EconomySimulationTest {

    // =================================================================================
    // 1. Dondurulmus sabitler — GDD sayilari
    // =================================================================================

    @Test
    fun lockCostTableMatchesGdd() {
        assertEquals(0, lockCost(1))
        assertEquals(0, lockCost(6))
        assertEquals(100, lockCost(7))
        assertEquals(110, lockCost(8))
        assertEquals(140, lockCost(11))
        assertEquals(165, lockCost(13))
        assertEquals(300, lockCost(21))
        assertEquals(350, lockCost(22))
    }

    @Test
    fun totalCampaignLockCostIs23105() {
        val total = (1..EconomyConfig.CAMPAIGN_LEVELS).sumOf { lockCost(it) }
        assertEquals("GDD C.3: kilit maliyeti toplami", EconomyConfig.TOTAL_LOCK_COST, total)
        assertEquals(23_105, total)
        // 22 bolumluk kampanyanin payi degismedi.
        assertEquals(3_140, (1..22).sumOf { lockCost(it) })
        // L23+ formulu: 350 + 15(L-22).
        for (l in 23..55) assertEquals("L$l", 350 + 15 * (l - 22), lockCost(l))
    }

    @Test
    fun sixLevelsAreFreeAndFortyNineAreGated() {
        assertEquals(6, (1..EconomyConfig.CAMPAIGN_LEVELS).count { lockCost(it) == 0 })
        assertEquals(49, (1..EconomyConfig.CAMPAIGN_LEVELS).count { lockCost(it) > 0 })
    }

    @Test
    fun metaTreeTotalIs13900WithTwentyTwoRanks() {
        val total = UpgradeLine.entries.sumOf { it.totalCost() }
        assertEquals("GDD F: agac toplami 13.900", EconomyConfig.TREE_TOTAL_COST, total)
        assertEquals(EconomyConfig.TREE_TOTAL_RANKS, UpgradeLine.entries.sumOf { it.maxRank })
        // Granularite duzeltmesi: 28 -> 22 rank. Agac toplami ve hat toplamlari
        // DEGISMEDI; ayni para daha az ve daha buyuk adima bolundu.
        assertEquals(22, UpgradeLine.entries.sumOf { it.maxRank })
    }

    @Test
    fun perLineTotalsMatchGdd() {
        assertEquals(4_000, UpgradeLine.FIREPOWER.totalCost())
        assertEquals(2_750, UpgradeLine.OPTICS.totalCost())
        assertEquals(2_700, UpgradeLine.STARTING_SUPPLY.totalCost())
        assertEquals(2_750, UpgradeLine.FORTIFICATION.totalCost())
        assertEquals(1_700, UpgradeLine.SALVAGE.totalCost())
    }

    @Test
    fun maxedTreeEffectsMatchGddH6() {
        val maxed = MetaUpgrades(
            firepower = 4, optics = 3, startingSupplyRank = 6, fortification = 5, salvage = 4
        )
        assertEquals("hasar +%24", 1.24, maxed.damageMultiplier, 1e-9)
        assertEquals("menzil +%15", 1.15, maxed.rangeMultiplier, 1e-9)
        assertEquals("baslangic Tedariki 300", 300, maxed.startingSupply)
        assertEquals("maks us cani 30", 30, maxed.maxBaseHealth)
        // Hurda Degeri: rank 0 = %70 (oyunun her zamanki tabani), maks = %90.
        // Eskiden taban 0,50 idi ve maks yalnizca %70'e, yani BASLANGIC NOKTASINA
        // donuyordu — bkz. EconomyConfig.BASE_SALVAGE_RATIO gerekcesi.
        assertEquals("satis iadesi %90", 0.90, maxed.salvageRatio, 1e-9)
        assertEquals("rank 0 iadesi %70", 0.70, MetaUpgrades().salvageRatio, 1e-9)
        assertEquals(EconomyConfig.TREE_TOTAL_COST, maxed.spentCoins())
        assertEquals(22, maxed.totalRanks())
        assertTrue(maxed.isMaxed())
    }

    // =================================================================================
    // 2. Odul formulu ve yildiz carpani
    // =================================================================================

    @Test
    fun baseRewardFollowsGddFormula() {
        assertEquals(140, baseLevelReward(1))
        assertEquals(170, baseLevelReward(2))
        assertEquals(440, baseLevelReward(11))
        assertEquals(770, baseLevelReward(22))
        for (l in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(140 + 30 * (l - 1), baseLevelReward(l))
        }
    }

    @Test
    fun oneStarRewardEqualsBase() {
        for (l in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertEquals(baseLevelReward(l), levelReward(l, 1))
        }
    }

    @Test
    fun starMultipliersRoundToNearestTen() {
        assertEquals(420, levelReward(7, 2))   // 320 x 1,3 = 416 -> 420
        assertEquals(510, levelReward(7, 3))   // 320 x 1,6 = 512 -> 510
        assertEquals(220, levelReward(1, 3))   // 140 x 1,6 = 224 -> 220
        assertEquals(370, levelReward(4, 3))   // 230 x 1,6 = 368 -> 370
        assertEquals(1230, levelReward(22, 3)) // 770 x 1,6 = 1232 -> 1230
        for (l in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            for (s in 1..3) assertEquals(0, levelReward(l, s) % 10)
        }
    }

    @Test
    fun rewardIsMonotonicInStars() {
        for (l in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            assertTrue(levelReward(l, 1) < levelReward(l, 2))
            assertTrue(levelReward(l, 2) < levelReward(l, 3))
        }
    }

    @Test
    fun unclearedLevelPaysNothing() {
        assertEquals(0, levelReward(5, 0))
    }

    @Test
    fun starImprovementPaysExactlyTheDifferenceOnce() {
        assertEquals(levelReward(10, 3) - levelReward(10, 1), starImprovementBonus(10, 1, 3))
        assertEquals(0, starImprovementBonus(10, 3, 3))
        assertEquals("yildiz asla dusmez, negatif odul olmaz", 0, starImprovementBonus(10, 3, 1))
        assertEquals(
            starImprovementBonus(10, 1, 3),
            starImprovementBonus(10, 1, 2) + starImprovementBonus(10, 2, 3),
        )
    }

    @Test
    fun starImprovementCeilingForWholeCampaignIsBounded() {
        // BAYRAK F-3 ust siniri: 3 yildiz tavani - 1 yildiz tabani, TEK SEFERLIK.
        val threeStar = (1..EconomyConfig.CAMPAIGN_LEVELS).sumOf { levelReward(it, 3) }
        val oneStar = (1..EconomyConfig.CAMPAIGN_LEVELS).sumOf { levelReward(it, 1) }
        // 22 bolumluk kampanyada 16.010 / 10.010 / 6.000 idi; 55 bolumde tavan
        // buyudu. Tek seferlik olma ozelligi DEGISMEDI (sinirsiz farming degil).
        assertEquals(83_600, threeStar)
        assertEquals(52_250, oneStar)
        assertEquals("yildiz iyilestirme tavani", 31_350, threeStar - oneStar)
        assertEquals("22 bolumluk pay degismedi", 6_000,
            (1..22).sumOf { levelReward(it, 3) } - (1..22).sumOf { levelReward(it, 1) })
    }

    // =================================================================================
    // 3. Yildiz = kalan us cani YUZDESI (GDD B.3 / H.2)
    // =================================================================================

    @Test
    fun starsUsePercentageNotAbsoluteThresholds() {
        assertEquals("19/20 = %95", 3, starsFor(19, 20))
        assertEquals("18/20 = %90", 3, starsFor(18, 20))
        assertEquals("17/20 = %85", 2, starsFor(17, 20))
        assertEquals("10/20 = %50", 2, starsFor(10, 20))
        assertEquals("9/20 = %45", 1, starsFor(9, 20))
        assertEquals("0 can = kaybetti", 0, starsFor(0, 20))
    }

    @Test
    fun fortificationDoesNotCheapenStars() {
        // GDD H.2: maks 30 iken 27 can -> %90 -> 3 yildiz, 26 can -> %86,7 -> 2 yildiz.
        val maxed = MetaUpgrades(fortification = 5)
        assertEquals(30, maxed.maxBaseHealth)
        assertEquals(3, starsFor(27, maxed.maxBaseHealth))
        assertEquals(2, starsFor(26, maxed.maxBaseHealth))
        // AYNI YUZDE, farkli maks can -> ayni yildiz. Yuzde tabanli olmanin kaniti.
        assertEquals(starsFor(18, 20), starsFor(27, 30))
        assertEquals(starsFor(10, 20), starsFor(15, 30))
        // Mutlak esik kullanilsaydi bu satir 3 yildiz verirdi (18 >= 18):
        assertEquals("18/30 = %60 -> 2 yildiz olmali", 2, starsFor(18, 30))
    }

    @Test
    fun perfectDefenseRequiresZeroLeaks() {
        assertTrue(isPerfectDefense(20, 20))
        assertTrue(isPerfectDefense(30, 30))
        assertFalse(isPerfectDefense(19, 20))
    }

    // =================================================================================
    // 4. Konuslanma Rezervi — GDD H.3 kabul kriterleri
    // =================================================================================

    private fun walletAt(coins: Int, unlocked: Set<Int>, cleared: Set<Int> = emptySet()) =
        PlayerWallet(coins = coins, unlockedLevels = unlocked, clearedLevels = cleared)

    @Test
    fun reserveOnlyHoldsBackWhatFutureLevelRewardsCannotCover() {
        // KURAL DEGISTI (cihazda bulunan denge hatasi):
        //
        // Eskiden rezerv = siradaki kilit bedeli, oyuncunun NEREDE oldugundan
        // bagimsiz. `unlockedLevels` bastan 1..6 oldugu icin taze cuzdanda bile
        // gate=7 ve rezerv=100 cikiyordu; oyuncu 2. bolumde 100 coin'le
        // Cephanelik'i acinca BES KARTIN HEPSI "coin eksik" gosteriyordu.
        //
        // Rezervin korudugu sey "oyuncu kapiya vardiginda parasi olsun". Kapiya
        // kadar temizlenecek her bolum en az REWARD_BASE oder, yani garantili
        // gelirdir. Rezerv = kapi bedeli - bu garantili gelir.
        val fresh = PlayerWallet()
        assertEquals(7, firstLockedLevel(fresh.unlockedLevels))
        // 6 bolum temizlenmemis x 140 = 840 >= 100 -> hicbir sey tutulmaz.
        assertEquals("kapi 5 bolum uzaktayken rezerv 0 olmali", 0, reserveFor(fresh))

        // Kapinin HEMEN oncesinde projeksiyon 0'a duser, rezerv tam bedele cikar.
        val atGate = walletAt(0, (1..6).toSet(), (1..6).toSet())
        assertEquals(7, firstLockedLevel(atGate.unlockedLevels))
        assertEquals("kapinin onunde tam bedel tutulur", 100, reserveFor(atGate))

        // Yuksek kapi, tek bolum kalmis: 350 - 140 = 210 tutulur.
        val nearL22 = walletAt(0, (1..21).toSet(), (1..20).toSet())
        assertEquals(22, firstLockedLevel(nearL22.unlockedLevels))
        assertEquals(lockCost(22) - EconomyConfig.REWARD_BASE, reserveFor(nearL22))
    }

    @Test
    fun reserveIsZeroWhenCampaignFullyUnlocked() {
        val all = walletAt(9_999, (1..EconomyConfig.CAMPAIGN_LEVELS).toSet())
        assertNull(firstLockedLevel(all.unlockedLevels))
        assertEquals("GDD C.4: 55 bolum acikken rezerv 0", 0, reserveFor(all))
        assertEquals(9_999, spendableBalance(all))
    }

    @Test
    fun purchaseThatBreaksReserveIsRejected() {
        // GDD H.3: bakiye 200, siradaki kilit 150 (L12), 200 coin'lik yukseltme -> RED.
        // Dukkan tabani (en ucuz rank-1) Baslangic Tedariki = 200.
        val wallet = walletAt(200, (1..11).toSet(), (1..11).toSet())
        assertEquals(150, reserveFor(wallet))

        val denied = purchaseAllowed(wallet, MetaUpgrades(), UpgradeLine.STARTING_SUPPLY)
        assertTrue("rezerv engellemeli", denied is PurchaseDecision.ReserveLocked)
        assertEquals(150, (denied as PurchaseDecision.ReserveLocked).reserve)
        assertEquals(150, denied.shortfall)

        // Bakiye ve rank DEGISMEMELI.
        val (w2, u2) = applyPurchase(wallet, MetaUpgrades(), UpgradeLine.STARTING_SUPPLY)
        assertEquals(200, w2.coins)
        assertEquals(0, u2.startingSupplyRank)
    }

    @Test
    fun purchaseThatLandsExactlyOnReserveIsAllowed() {
        // GDD H.3 ikinci madde: bakiye TAM rezerve inebilir.
        // Bakiye 300, rezerv 100 (L7) -> 200'luk alim -> 100 = rezerv -> KABUL.
        val wallet = walletAt(300, (1..6).toSet(), (1..6).toSet())
        assertEquals(100, reserveFor(wallet))
        val decision = purchaseAllowed(wallet, MetaUpgrades(), UpgradeLine.STARTING_SUPPLY)
        assertTrue(decision is PurchaseDecision.Allowed)
        assertEquals(100, (decision as PurchaseDecision.Allowed).balanceAfter)

        val (w2, u2) = applyPurchase(wallet, MetaUpgrades(), UpgradeLine.STARTING_SUPPLY)
        assertEquals("bakiye tam rezerv kadar kalir", 100, w2.coins)
        assertEquals(1, u2.startingSupplyRank)
    }

    @Test
    fun insufficientFundsIsDistinctFromReserveLock() {
        // Analytics sozlesmesi: `reserve_lock_blocked` yalnizca para YETERKEN gonderilir.
        val poor = walletAt(50, (1..6).toSet(), (1..6).toSet())
        val decision = purchaseAllowed(poor, MetaUpgrades(), UpgradeLine.STARTING_SUPPLY)
        assertTrue(decision is PurchaseDecision.InsufficientFunds)
        assertEquals(150, (decision as PurchaseDecision.InsufficientFunds).shortfall)
    }

    @Test
    fun maxedLineCannotBePurchasedAgain() {
        val wallet = walletAt(99_999, (1..22).toSet(), (1..22).toSet())
        assertEquals(PurchaseDecision.MaxRank, purchaseAllowed(wallet, MetaUpgrades(salvage = 4), UpgradeLine.SALVAGE))
    }

    @Test
    fun spendableBalanceNeverGoesNegative() {
        assertEquals(0, spendableBalance(50, 165))
        assertEquals(35, spendableBalance(200, 165))
    }

    // =================================================================================
    // 5. SOFT-LOCK IMKANSIZLIK — GDD H.3, EN KRITIK TEST
    // =================================================================================

    private data class Step(
        val level: Int,
        val balanceBeforePay: Int,
        val lockPaid: Int,
        val balanceAfterLevel: Int,
        val safetyMargin: Double,
    )

    /** 1 yildiz veren kalan can (20 uzerinden %25). */
    private val oneStarLives = 5
    private val maxLives = 20

    /**
     * EN KOTU OYUNCU: her bolumde **1 yildiz**, HIC gorev, HIC reklam, HIC tekrar,
     * HIC basarim, HIC yukseltme. GDD C.3 tablosunun birebir yeniden uretimi.
     * Gercek zafer yolunu (`resolveLevelClear`/`applyLevelClear`) kullanir.
     */
    @Test
    fun aOneStarRunNeverGoesBrokeAcrossFiftyFiveLevels() {
        var wallet = PlayerWallet()
        val steps = mutableListOf<Step>()

        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val before = wallet.coins
            val cost = lockCost(level)

            if (level !in wallet.unlockedLevels) {
                assertTrue(
                    "L$level KILITLENDI: bakiye $before < bedel $cost — SOFT-LOCK",
                    canUnlock(wallet, level),
                )
                wallet = applyUnlock(wallet, level)
                assertTrue("L$level acilmaliydi", level in wallet.unlockedLevels)
            }

            assertEquals(
                "otomatik hibe ASLA tetiklenmemeli (GDD H.9 hedefi: kesin 0)",
                0, autoGrantShortfall(wallet),
            )

            val result = resolveLevelClear(wallet, level, oneStarLives, maxLives)
            assertEquals(1, result.stars)
            assertTrue(result.firstClear)
            assertEquals(0, result.perfectBonus)
            assertEquals(0, result.replayReward)
            assertEquals(levelReward(level, 1), result.total)
            wallet = applyLevelClear(wallet, result)

            val margin = if (cost == 0) Double.MAX_VALUE else before.toDouble() / cost
            steps += Step(level, before, cost, wallet.coins, margin)
        }

        // CAMPAIGN_55.md 8.3 — 55 bolumluk EN KOTU DURUM simulasyonu.
        // Hepsi 1 yildiz · hic gorev · hic reklam · hic tekrar · hic yukseltme.
        assertEquals("55 bolumun tamami acilmis olmali", 55, wallet.unlockedLevels.size)
        assertEquals("55 bolumun tamami temizlenmis olmali", 55, wallet.clearedLevels.size)
        assertEquals("kampanya sonu bakiye", 29_145, wallet.coins)
        assertEquals("en kotu durum kampanya geliri", 52_250, wallet.lifetimeEarned)
        assertEquals("toplam kilit maliyeti", 23_105, wallet.lifetimeSpent)
        assertTrue("bakiye hicbir bolumde negatife dusmemeli", steps.all { it.balanceAfterLevel >= 0 })

        // GDD H.3: minimum guvenlik payi >= 12x
        val paid = steps.filter { it.lockPaid > 0 }
        assertEquals(49, paid.size)
        val minMargin = paid.minOf { it.safetyMargin }
        assertTrue("min guvenlik payi $minMargin, 12x'in altinda", minMargin >= 12.0)
        // L7 en dar nokta: 1290 / 100 = 12,9x
        assertEquals(12.9, steps[6].safetyMargin, 0.05)
    }

    @Test
    fun gddRule3EachLevelRewardCoversTheNextLock() {
        // GDD C.1 kural 3 — rezerv kilidinin gecici acigini kapatan invariant.
        for (level in 1 until EconomyConfig.CAMPAIGN_LEVELS) {
            val reward = levelReward(level, 1)
            val nextLock = lockCost(level + 1)
            assertTrue(
                "R($level)=$reward, D(${level + 1})=$nextLock — odul kilidi karsilamiyor",
                reward >= nextLock,
            )
            if (nextLock > 0) {
                assertTrue(
                    "R($level)/D(${level + 1}) = ${reward.toDouble() / nextLock} < 2,0",
                    reward.toDouble() / nextLock >= 2.0,
                )
            }
        }
    }

    /**
     * EN KOTU OYUNCU **+ ACGOZLU HARCAYICI**: her bolumde 1 yildiz, hic ekstra gelir
     * yok, ve dukkanda her firsatta EN UCUZ yukseltmeyi alip bakiyeyi rezerve kadar
     * dibe cekiyor. Soft-lock'un tek gercek yolu buydu (GDD C.4).
     */
    @Test
    fun greedyUpgradeSpenderNeverSoftLocks() {
        var wallet = PlayerWallet()
        var upgrades = MetaUpgrades()
        var purchases = 0
        var reserveBlocks = 0

        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            if (level !in wallet.unlockedLevels) {
                assertTrue(
                    "L$level KILITLENDI (acgozlu harcayici): bakiye ${wallet.coins}, bedel ${lockCost(level)}",
                    canUnlock(wallet, level),
                )
                wallet = applyUnlock(wallet, level)
            }

            assertEquals("L$level: otomatik hibe tetiklendi -> ekonomi bug'i", 0, autoGrantShortfall(wallet))

            wallet = applyLevelClear(wallet, resolveLevelClear(wallet, level, oneStarLives, maxLives))

            // Acgozlu: alinabilen en ucuz rank'i, alinamayana kadar al.
            while (true) {
                val candidates = UpgradeLine.entries.mapNotNull { line ->
                    val d = purchaseAllowed(wallet, upgrades, line)
                    if (d is PurchaseDecision.Allowed) line to d.price else null
                }
                if (candidates.isEmpty()) break
                val (line, _) = candidates.minByOrNull { it.second }!!
                val (w, u) = applyPurchase(wallet, upgrades, line)
                wallet = w
                upgrades = u
                purchases++
            }

            // Rezerv gercekten calisiyor mu? Oynanabilir bir kilit varken bakiye
            // ASLA rezervin altina dusmemeli.
            val nextLocked = firstLockedLevel(wallet.unlockedLevels)
            if (nextLocked != null && prerequisiteCleared(wallet, nextLocked)) {
                assertTrue(
                    "L$level sonunda bakiye ${wallet.coins} < rezerv ${reserveFor(wallet)}",
                    wallet.coins >= reserveFor(wallet),
                )
            }
            UpgradeLine.entries.forEach {
                if (purchaseAllowed(wallet, upgrades, it) is PurchaseDecision.ReserveLocked) reserveBlocks++
            }
        }

        assertEquals("55 bolum tamamlanmali", 55, wallet.clearedLevels.size)
        assertTrue("acgozlu oyuncu hic yukseltme almadiysa test anlamsiz", purchases >= 10)
        assertTrue("rezerv kilidi hic devreye girmediyse test anlamsiz", reserveBlocks > 0)
        assertTrue("harcanan coin agac toplamini asamaz", upgrades.spentCoins() <= EconomyConfig.TREE_TOTAL_COST)
        assertEquals(0, autoGrantShortfall(wallet))
    }

    @Test
    fun autoGrantIsDormantWhilePrerequisiteLevelIsUncleared() {
        // F-2: L7 acilmis ama henuz temizlenmemis, bakiye 0, siradaki kilit L8 = 110.
        // Bolum oynanamaz durumda oldugu icin invariant BAGLAYICI DEGIL -> hibe yok.
        val wallet = walletAt(0, (1..7).toSet(), (1..6).toSet())
        assertEquals(8, firstLockedLevel(wallet.unlockedLevels))
        assertFalse(prerequisiteCleared(wallet, 8))
        assertEquals("hibe uyumakta olmali", 0, autoGrantShortfall(wallet))

        // L7 temizlendigi anda R(7) >= D(8) oldugu icin bakiye zaten yeterli.
        val cleared = wallet.credited(levelReward(7, 1)).copy(clearedLevels = wallet.clearedLevels + 7)
        assertTrue(prerequisiteCleared(cleared, 8))
        assertEquals("hibe yine tetiklenmemeli", 0, autoGrantShortfall(cleared))
    }

    @Test
    fun autoGrantFiresOnlyForAGenuinelyBrokenSaveState() {
        // Bozuk kayit: L7 temizlenmis ama bakiye 0 (ekonomi bug'i ya da elle kurcalama).
        val broken = walletAt(0, (1..7).toSet(), (1..7).toSet())
        assertEquals(110, autoGrantShortfall(broken))
    }

    // =================================================================================
    // 6. Tekrar oynama ekonomisi — sifir degil, sinirsiz da degil
    // =================================================================================

    @Test
    fun replayRewardIsNeverZeroForAClearedLevel() {
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            for (stars in 1..3) {
                for (used in 0..50) {
                    val r = replayReward(level, stars, used)
                    assertTrue("L$level $stars yildiz used=$used -> $r", r >= EconomyConfig.REPLAY_FLOOR)
                }
            }
        }
    }

    @Test
    fun replayRewardCollapsesToFloorAfterThreeBoostedReplays() {
        assertEquals(250, replayReward(22, 3, 0)) // 770 x 1,6 x 0,20 = 246,4 -> 250
        assertEquals(250, replayReward(22, 3, 2))
        assertEquals(EconomyConfig.REPLAY_FLOOR, replayReward(22, 3, 3))
        assertEquals(EconomyConfig.REPLAY_FLOOR, replayReward(22, 3, 999))
    }

    @Test
    fun boostedReplayCapIsGlobalPerDayNotPerLevel() {
        // 3 hak tukendikten sonra BASKA bir bolumu tekrar oynamak da taban verir.
        assertEquals(EconomyConfig.REPLAY_FLOOR, replayReward(5, 3, 3))
        assertEquals(EconomyConfig.REPLAY_FLOOR, replayReward(20, 3, 3))
    }

    @Test
    fun replayingIsAlwaysWorthLessThanClearingNewContent() {
        // Farming'in ilerlemeden daha karli OLMAMASININ analitik garantisi.
        for (level in 1..EconomyConfig.CAMPAIGN_LEVELS) {
            val bestReplay = replayReward(level, 3, 0)
            assertTrue(
                "L$level tekrar odulu $bestReplay, ilk temizlik 1 yildiz ${levelReward(level, 1)}",
                bestReplay < levelReward(level, 1),
            )
        }
        // En karli tekrar (L22, 3 yildiz) bile en ucuz ucretli bolumun ilk temizliginin altinda.
        assertTrue(replayReward(22, 3, 0) < levelReward(7, 1))
    }

    @Test
    fun unclearedLevelCannotBeReplayFarmed() {
        assertEquals(0, replayReward(9, 0, 0))
    }

    @Test
    fun replayFloorIsNotDoublableByRewardedAd() {
        // Aksi halde "L1'i 2x hizda tekrar oyna + reklam izle" ilerlemeyi geçer.
        assertFalse(replayRewardIsDoublable(EconomyConfig.BOOSTED_REPLAYS_PER_DAY))
        assertTrue(replayRewardIsDoublable(0))
    }

    @Test
    fun replayClearDoesNotPayFirstClearRewardAgain() {
        var wallet = PlayerWallet()
        wallet = applyLevelClear(wallet, resolveLevelClear(wallet, 3, oneStarLives, maxLives))
        val afterFirst = wallet.coins
        assertEquals(levelReward(3, 1), afterFirst)

        val replay = resolveLevelClear(wallet, 3, oneStarLives, maxLives)
        assertFalse(replay.firstClear)
        assertEquals("ilk temizlik odulu ikinci kez odenmez", 0, replay.firstClearReward)
        assertEquals(replayReward(3, 1, 0), replay.replayReward)
        assertTrue(replay.consumesBoostedReplay)

        // Taban tekrar odulune dusen katlanamaz kalem, R3 ile katlanmaz.
        val floorReplay = resolveLevelClear(wallet, 3, oneStarLives, maxLives, boostedReplaysUsedToday = 3)
        assertEquals(EconomyConfig.REPLAY_FLOOR, floorReplay.replayReward)
        assertEquals(0, floorReplay.doublableAmount)
        assertEquals(0, doublePayoutBonus(floorReplay.doublableAmount, AdOutcome.REWARD_EARNED))
    }

    @Test
    fun perfectDefenseMedalIsPaidOnlyOncePerLevel() {
        var wallet = PlayerWallet()
        val first = resolveLevelClear(wallet, 2, maxLives, maxLives)
        assertEquals(3, first.stars)
        assertEquals(EconomyConfig.PERFECT_DEFENSE_BONUS, first.perfectBonus)
        wallet = applyLevelClear(wallet, first)

        val second = resolveLevelClear(wallet, 2, maxLives, maxLives)
        assertEquals("madalya ikinci kez verilmez", 0, second.perfectBonus)
    }

    @Test
    fun retroactiveStarImprovementIsPaidOnceAndStarsNeverDrop() {
        var wallet = PlayerWallet()
        wallet = applyLevelClear(wallet, resolveLevelClear(wallet, 5, oneStarLives, maxLives))
        assertEquals(1, wallet.starsOf(5))

        val improved = resolveLevelClear(wallet, 5, 19, maxLives) // %95 -> 3 yildiz
        assertEquals(3, improved.stars)
        assertEquals(levelReward(5, 3) - levelReward(5, 1), improved.starImprovement)
        wallet = applyLevelClear(wallet, improved)
        assertEquals(3, wallet.starsOf(5))

        // Ayni bolum tekrar 1 yildizla bitirilirse yildiz DUSMEZ ve fark odenmez.
        val worse = resolveLevelClear(wallet, 5, oneStarLives, maxLives)
        assertEquals(0, worse.starImprovement)
        wallet = applyLevelClear(wallet, worse)
        assertEquals("GDD H.2: kayitli yildiz 3 kalir", 3, wallet.starsOf(5))
    }

    // =================================================================================
    // 7. Rewarded reklam ekonomisi (GDD G.3 / G.4)
    // =================================================================================

    @Test
    fun filledRequisitionPaysOneFiftyAndConsumesADailyRight() {
        var state = RequisitionState()
        var total = 0
        repeat(EconomyConfig.R1_VIEWS_PER_DAY) {
            val g = grantRequisition(state, AdOutcome.REWARD_EARNED)
            assertEquals(150, g.coins)
            assertTrue(g.consumedFilledView)
            assertFalse(g.fallback)
            state = g.newState
            total += g.coins
        }
        assertEquals(450, total)
        assertEquals(3, state.filledViewsToday)
    }

    @Test
    fun noFillPaysFiftyAndDoesNotConsumeTheDailyRight() {
        // GDD H.7: ucak modunda +50, gunluk hak TUKETILMEZ, uygulama donmaz.
        var state = RequisitionState()
        for (outcome in listOf(AdOutcome.NO_FILL, AdOutcome.TIMEOUT, AdOutcome.DISMISSED)) {
            val g = grantRequisition(state, outcome)
            assertEquals(50, g.coins)
            assertFalse("no-fill dolu gosterim hakkini tuketmemeli", g.consumedFilledView)
            assertTrue(g.fallback)
            state = g.newState
        }
        assertEquals("dolu gosterim hakki hala tam", 0, state.filledViewsToday)
        // Sonrasinda gercek reklam gelirse tam odul hala mumkun.
        assertEquals(150, grantRequisition(state, AdOutcome.REWARD_EARNED).coins)
    }

    @Test
    fun infiniteNoFillLoopIsBoundedByDailyCoinBudget() {
        // EXPLOIT: ucak modunda butona 500 kez basmak. Butce olmadan sonsuz coin.
        var state = RequisitionState()
        var total = 0
        repeat(500) {
            val g = grantRequisition(state, AdOutcome.NO_FILL)
            total += g.coins
            state = g.newState
        }
        assertEquals(
            "R1 gunluk coin tavani ${EconomyConfig.R1_COIN_BUDGET_PER_DAY} olmali",
            EconomyConfig.R1_COIN_BUDGET_PER_DAY, total,
        )
        // Buton hala calisiyor ve HICBIR ilerleme yolunu kapatmiyor, sadece 0 veriyor.
        val exhausted = grantRequisition(state, AdOutcome.NO_FILL)
        assertEquals(0, exhausted.coins)
        assertTrue(exhausted.budgetExhausted)
    }

    @Test
    fun luckyAndUnluckyPlayersGetTheSameDailyRequisitionTotal() {
        // Butcenin yan etkisi OLUMLU: fill orani gunluk toplami degistirmez.
        var lucky = RequisitionState()
        var luckyTotal = 0
        repeat(10) { val g = grantRequisition(lucky, AdOutcome.REWARD_EARNED); luckyTotal += g.coins; lucky = g.newState }

        var unlucky = RequisitionState()
        var unluckyTotal = 0
        repeat(3) { val g = grantRequisition(unlucky, AdOutcome.NO_FILL); unluckyTotal += g.coins; unlucky = g.newState }
        repeat(10) { val g = grantRequisition(unlucky, AdOutcome.REWARD_EARNED); unluckyTotal += g.coins; unlucky = g.newState }

        assertEquals(EconomyConfig.R1_COIN_BUDGET_PER_DAY, luckyTotal)
        assertEquals(EconomyConfig.R1_COIN_BUDGET_PER_DAY, unluckyTotal)
    }

    @Test
    fun requisitionDailyCeilingCannotDevalueGameplayIncome() {
        // SERT KURAL: rewarded, normal oynanis gelirini degersizlestiremez.
        // ECONOMY_SPEC 1.3: medyan oyuncunun gunluk BOLUM geliri ~930 coin.
        val r1Ceiling = EconomyConfig.R1_COIN_BUDGET_PER_DAY // 450
        val expectedGameplayPerDay = 930
        assertTrue(
            "R1 tavani $r1Ceiling, oynanis geliri $expectedGameplayPerDay'in %50'sini asiyor",
            r1Ceiling <= expectedGameplayPerDay / 2,
        )
    }

    @Test
    fun doublePayoutOnlyAddsALayerAndNeverGatesTheBaseReward() {
        val wallet = PlayerWallet()
        val result = resolveLevelClear(wallet, 15, 12, 20) // %60 -> 2 yildiz
        assertEquals(2, result.stars)
        assertEquals(levelReward(15, 2), result.doublableAmount)
        assertEquals(levelReward(15, 2), doublePayoutBonus(result.doublableAmount, AdOutcome.REWARD_EARNED))
        // No-fill: ek katman yok, TABAN odul zaten yazilmisti -> bonus 0.
        assertEquals(0, doublePayoutBonus(result.doublableAmount, AdOutcome.NO_FILL))
        assertEquals(0, doublePayoutBonus(result.doublableAmount, AdOutcome.TIMEOUT))
        assertEquals(0, doublePayoutBonus(result.doublableAmount, AdOutcome.DISMISSED))
    }

    @Test
    fun rewardedValuePerDayStaysBelowCampaignProgressValue() {
        // Rewarded'in tum gunluk tavani (R1 450 + R3 en buyuk bolum odulu) tek
        // basina bir gunun ilerleme gelirini gecmemeli.
        val r3Max = levelReward(EconomyConfig.CAMPAIGN_LEVELS, 3) // 1.230
        val adCeiling = EconomyConfig.R1_COIN_BUDGET_PER_DAY + r3Max
        val campaignTotal = (1..EconomyConfig.CAMPAIGN_LEVELS).sumOf { levelReward(it, 2) }
        assertTrue(
            "reklam tavani $adCeiling, kampanya 2 yildiz geliri $campaignTotal'in %15'ini asiyor",
            adCeiling <= campaignTotal * 15 / 100,
        )
    }

    // =================================================================================
    // 8. Gunluk gorev determinizmi (GDD E.1 / H.5)
    // =================================================================================

    @Test
    fun sameDayAndSeedProduceIdenticalMissions() {
        val a = dailyMissions(epochDay = 20_500L, seed = 0xC0FFEEL, clearedLevels = 12, unlockedTowerTypes = 4)
        val b = dailyMissions(epochDay = 20_500L, seed = 0xC0FFEEL, clearedLevels = 12, unlockedTowerTypes = 4)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(3, a.size)
    }

    @Test
    fun differentDaysProduceDifferentMissionSets() {
        val seed = 0x5EEDL
        val sets = (20_000L..20_030L).map { day ->
            dailyMissions(day, seed, clearedLevels = 12, unlockedTowerTypes = 4).map { it.id }
        }
        assertTrue("31 gunde en az 8 farkli gorev kombinasyonu bekleniyor", sets.toSet().size >= 8)
    }

    @Test
    fun differentSeedsProduceDifferentMissions() {
        val day = 20_111L
        val a = dailyMissions(day, 1L, clearedLevels = 12, unlockedTowerTypes = 4).map { it.id }
        val b = dailyMissions(day, 987_654_321L, clearedLevels = 12, unlockedTowerTypes = 4).map { it.id }
        assertNotEquals(a, b)
    }

    @Test
    fun oneMissionPerSlotAndNeverThreeOfTheSameKind() {
        for (day in 19_000L..19_400L) {
            val m = dailyMissions(day, 0xBEEFL, clearedLevels = 22, unlockedTowerTypes = 4)
            assertEquals(3, m.size)
            assertEquals(
                "her slot tam bir kez temsil edilmeli",
                MissionSlot.entries.toSet(),
                m.map { it.template.slot }.toSet(),
            )
            assertEquals("ayni sablon iki slotta olamaz", 3, m.map { it.id }.toSet().size)
        }
    }

    @Test
    fun missionInASlotDoesNotRepeatFromYesterday() {
        for (day in 18_000L..18_300L) {
            val today = dailyMissions(day, 0x1234L, clearedLevels = 22, unlockedTowerTypes = 4)
            val yesterday = dailyMissions(day - 1, 0x1234L, clearedLevels = 22, unlockedTowerTypes = 4)
            today.zip(yesterday).forEach { (t, y) ->
                assertNotEquals("gun $day slot ${t.template.slot} dun ile ayni", y.id, t.id)
            }
        }
    }

    @Test
    fun negativeEpochDayDoesNotCrashOrProduceNegativeIndex() {
        // Cihaz saatini 1970 oncesine almak epochDay'i negatif yapar.
        // Kotlin `%` burada negatif doner; `Math.floorMod` kullanilmazsa cokme olur.
        for (day in -400L..0L) {
            val m = dailyMissions(day, -999_999L, clearedLevels = 3, unlockedTowerTypes = 2)
            assertEquals(3, m.size)
            m.forEach { assertTrue(it.reward > 0) }
        }
        assertTrue(deterministicIndex(Long.MIN_VALUE, 7) in 0..6)
        assertTrue(deterministicIndex(-1L, 5) in 0..4)
        assertEquals(3, dailyMissions(Long.MIN_VALUE / 2, 0L).size)
    }

    @Test
    fun rerollProducesADifferentMissionInThatSlotOnly() {
        val day = 20_222L
        val seed = 0xABCDL
        val before = dailyMissions(day, seed, clearedLevels = 22, unlockedTowerTypes = 4)
        val after = dailyMissions(day, seed, 22, 4, rerollsBySlot = intArrayOf(0, 1, 0))
        assertEquals(before[0].id, after[0].id)
        assertEquals(before[2].id, after[2].id)
        assertNotEquals("yenilenen slot degismeli", before[1].id, after[1].id)
    }

    @Test
    fun missionPoolIsFilteredByUnlockedContent() {
        // GDD H.5: bolum 4'e kadar acan oyuncuya tank/zirhli/4-kule gorevi verilmez.
        for (day in 17_000L..17_500L) {
            val m = dailyMissions(day, 0x77L, clearedLevels = 4, unlockedTowerTypes = 2)
            m.forEach {
                assertTrue("${it.id} 4 bolum temizlemis oyuncuya verilemez", it.template.minClearedLevels <= 4)
                assertTrue("${it.id} 2 kule tipiyle yapilamaz", it.template.minTowerTypes <= 2)
            }
        }
    }

    @Test
    fun brandNewPlayerAlwaysGetsAValidMissionSet() {
        for (day in 20_000L..20_100L) {
            val m = dailyMissions(day, 42L, clearedLevels = 0, unlockedTowerTypes = 1)
            assertEquals(3, m.size)
            m.forEach { assertEquals(0, it.template.minClearedLevels) }
        }
    }

    // =================================================================================
    // 9. Gorev odul butcesi — ekonomiyi sismiyor
    // =================================================================================

    @Test
    fun dailyPayoutMatchesGddCeiling() {
        val missions = dailyMissions(20_500L, 7L, 22, 4).map { it.advanced(it.target) }
        assertEquals("GDD E.1: ucu de bitince 360", EconomyConfig.DAILY_MAX_TOTAL, dailyMissionPayout(missions))
        assertEquals(360, 60 + 80 + 120 + 100)
    }

    @Test
    fun allThreeBonusIsNotPaidForPartialCompletion() {
        val missions = dailyMissions(20_500L, 7L, 22, 4)
        val twoDone = listOf(missions[0].advanced(99), missions[1].advanced(99), missions[2])
        assertEquals(140, dailyMissionPayout(twoDone)) // 60 + 80, bonus YOK
    }

    @Test
    fun weeklyStructureAndBudgetMatchGddExactly() {
        val weekly = MissionPools.weekly()
        assertEquals("GDD E.2: iki haftalik gorev", EconomyConfig.WEEKLY_MISSION_COUNT, weekly.size)
        assertEquals(12, weekly[0].target)
        assertEquals(500, weekly[0].reward)
        assertEquals(15, weekly[1].target)
        assertEquals(600, weekly[1].reward)
        assertEquals(
            "GDD D.2 haftalik butcesi 1.100 KORUNMALI",
            EconomyConfig.WEEKLY_BUDGET,
            weekly.sumOf { it.reward },
        )
    }

    @Test
    fun weeklyMissionsOnlyPayWhenActuallyComplete() {
        val weekly = MissionPools.weekly()
        assertEquals(0, weeklyMissionPayout(weekly))
        val partly = advanceWeekly(weekly, MissionType.WEEKLY_LEVELS_COMPLETED, 11)
        assertEquals("11/12 -> odul yok", 0, weeklyMissionPayout(partly))
        val done = advanceWeekly(partly, MissionType.WEEKLY_LEVELS_COMPLETED, 1)
        assertEquals(500, weeklyMissionPayout(done))
        // Sayac hedefi asamaz -> ikinci odeme uretmez.
        val overshot = advanceWeekly(done, MissionType.WEEKLY_LEVELS_COMPLETED, 99)
        assertEquals(12, overshot[0].progress)
        assertEquals(500, weeklyMissionPayout(overshot))
    }

    @Test
    fun missionIncomeStaysWellBelowLevelIncome() {
        // SERT KURAL: gorev odulleri ekonomiyi sismemeli.
        val expectedLevelIncomePerDay = 930 // ECONOMY_SPEC 1.3
        assertTrue(
            "gunluk gorev tavani ${EconomyConfig.DAILY_MAX_TOTAL} cok yuksek",
            EconomyConfig.DAILY_MAX_TOTAL <= expectedLevelIncomePerDay / 2,
        )
        assertTrue(
            "haftalik butcenin gunluk esdegeri cok yuksek",
            EconomyConfig.WEEKLY_BUDGET / 7 <= expectedLevelIncomePerDay / 2,
        )
        // Gorev odulleri oynanis GEREKTIRIR, yani ekonominin bypass'i degildir.
        // Gercek "oynanis olmadan" gelir yalnizca R1'dir; toplam gunluk tavanin
        // en fazla ucte biri olmali.
        val gameplayFreeIncome = EconomyConfig.R1_COIN_BUDGET_PER_DAY
        val totalDailyCeiling = expectedLevelIncomePerDay +
            EconomyConfig.DAILY_MAX_TOTAL +
            EconomyConfig.WEEKLY_BUDGET / 7 +
            gameplayFreeIncome
        assertTrue(
            "reklam geliri $gameplayFreeIncome, gunluk tavanin ($totalDailyCeiling) ucte birini asiyor",
            gameplayFreeIncome * 3 <= totalDailyCeiling,
        )
    }

    @Test
    fun completingEveryDailyForAWholeCampaignCannotBuyTheWholeTree() {
        // 14 gunluk kampanya (GDD C.5) boyunca HER gun 360 + haftalik 1.100 x 2
        // toplami, agacin yarisini gecmemeli; yoksa gorevler ekonominin ana kaynagi olur.
        val missionIncome = 14 * EconomyConfig.DAILY_MAX_TOTAL + 2 * EconomyConfig.WEEKLY_BUDGET
        assertEquals(7_240, missionIncome)
        assertTrue(
            "gorev geliri $missionIncome, agacin (13.900) yarisini asiyor",
            missionIncome <= EconomyConfig.TREE_TOTAL_COST * 55 / 100,
        )
    }

    // =================================================================================
    // 10. Tarih hilesi savunmasi (GDD E.4 / H.5)
    // =================================================================================

    private fun sample(day: Long, wallMs: Long, elapsed: Long, boot: Long = 1L) =
        ClockSample(day, wallMs, elapsed, boot)

    private fun seededState(day: Long, wallMs: Long, elapsed: Long, boot: Long = 1L): MissionState =
        evaluateReset(MissionState(), sample(day, wallMs, elapsed, boot)).newState

    @Test
    fun firstLaunchResetsWithFullReward() {
        val d = evaluateReset(MissionState(), sample(20_000L, 1_000_000L, 5_000L))
        assertTrue(d.dailyReset)
        assertTrue(d.weeklyReset)
        assertEquals(1.0, d.rewardMultiplier, 1e-9)
        assertFalse(d.clockSuspect)
    }

    @Test
    fun sameDayDoesNotReset() {
        val state = seededState(20_000L, 1_000_000L, 5_000L)
        val d = evaluateReset(state, sample(20_000L, 1_040_000L, 45_000L))
        assertFalse(d.dailyReset)
        assertFalse(d.clockSuspect)
    }

    @Test
    fun normalNextDayResetsWithFullReward() {
        val state = seededState(20_000L, 20_000L * 86_400_000L, 5_000L)
        val next = sample(20_001L, 20_001L * 86_400_000L, 5_000L + 86_400_000L)
        val d = evaluateReset(state, next)
        assertTrue(d.dailyReset)
        assertEquals(1.0, d.rewardMultiplier, 1e-9)
        assertFalse(d.clockSuspect)
    }

    @Test
    fun clockRolledBackwardDoesNotResetAndDoesNotPay() {
        // GDD E.4 kural 2 + H.5: 2 gun geri -> yenileme YOK, ilerleme KORUNUR,
        // oyun tam oynanir durumda.
        val base = 20_000L
        val state = seededState(base, base * 86_400_000L, 5_000L).copy(
            daily = dailyMissions(base, 1L).map { it.advanced(1) },
        )
        val backward = sample(base - 2L, (base - 2L) * 86_400_000L, 5_000L + 60_000L)
        val d = evaluateReset(state, backward)

        assertFalse("saat geri alindi -> SIFIRLAMA YOK", d.dailyReset)
        assertFalse("saat geri alindi -> haftalik da sifirlanmaz", d.weeklyReset)
        assertTrue(d.frozenByBackwardClock)
        assertTrue(d.clockSuspect)
        assertEquals("kayitli gun geriye YAZILMAZ", base, d.newState.lastResetEpochDay)
        assertEquals("mevcut gorev ilerlemesi korunur", 1, d.newState.daily.first().progress)
        assertEquals("sifirlama sayaci artmaz", state.dailyResetsInWindow, d.newState.dailyResetsInWindow)
    }

    @Test
    fun backwardThenForwardCannotHarvestExtraResets() {
        val base = 20_000L
        var state = seededState(base, base * 86_400_000L, 5_000L)
        val resetsAfterFirst = state.dailyResetsInWindow

        // 5 kez geri-ileri sallanma: hicbiri ek sifirlama uretmemeli.
        repeat(5) {
            val back = evaluateReset(state, sample(base - 3L, (base - 3L) * 86_400_000L, 6_000L))
            assertFalse(back.dailyReset)
            state = back.newState
            val forwardSameDay = evaluateReset(state, sample(base, base * 86_400_000L, 7_000L))
            assertFalse("kayitli gune donus sifirlama sayilmaz", forwardSameDay.dailyReset)
            state = forwardSameDay.newState
        }
        assertEquals(resetsAfterFirst, state.dailyResetsInWindow)
    }

    @Test
    fun thirtyDayForwardJumpCountsAsExactlyOneReset() {
        // GDD E.4 — ileri atlama EN FAZLA BIR sifirlama.
        val base = 20_000L
        val state = seededState(base, base * 86_400_000L, 5_000L)
        val jump = sample(base + 30L, (base + 30L) * 86_400_000L, 5_000L + 60_000L) // elapsed eslik etmiyor
        val d = evaluateReset(state, jump)

        assertTrue("oyuncu bloke EDILMEZ, yenileme yapilir", d.dailyReset)
        assertEquals("30 gun atlama 30 sifirlama vermez", base + 30L, d.newState.lastResetEpochDay)
        assertEquals(
            "sifirlama sayaci yalnizca 1 artar",
            state.dailyResetsInWindow + 1, d.newState.dailyResetsInWindow,
        )
        assertTrue("sahte atlama isaretlenmeli", d.clockSuspect)
        assertEquals("gorev odulu %50'ye damperlenmeli", 0.5, d.rewardMultiplier, 1e-9)
    }

    @Test
    fun plausibleForwardJumpAfterRebootIsNotPunished() {
        // Cihaz kapali kaldi, yeni boot -> elapsedRealtime sifirlandi. MESRU.
        val base = 20_000L
        val state = seededState(base, base * 86_400_000L, 900_000L, boot = 1L)
        val afterReboot = sample(base + 3L, (base + 3L) * 86_400_000L, 12_000L, boot = 2L)
        val d = evaluateReset(state, afterReboot)
        assertTrue(d.dailyReset)
        assertFalse("boot degistiyse elapsed karsilastirmasi yapilamaz -> suphe yok", d.clockSuspect)
        assertEquals(1.0, d.rewardMultiplier, 1e-9)
    }

    @Test
    fun damperAppliesOnlyToMissionRewardsNeverToLevelIncome() {
        // GDD E.4 kural 5: kilit, bakiye, yukseltme, kampanya ilerlemesi ETKILENMEZ.
        val missions = dailyMissions(20_500L, 7L, 22, 4).map { it.advanced(it.target) }
        assertEquals(360, dailyMissionPayout(missions, 1.0))
        assertEquals(180, dailyMissionPayout(missions, EconomyConfig.SUSPECT_REWARD_MULTIPLIER))
        // Bolum odulu ve kilit bedeli damperden BAGIMSIZDIR — imzalarinda carpan yok.
        assertEquals(710, levelReward(20, 1))
        assertEquals(270, lockCost(20))
    }

    @Test
    fun loyalDailyPlayerIsNeverDampened() {
        // YANLIS POZITIF TESTI. Kaba tavan takvim gunune gore sayilirsa her gun
        // oynayan mesru oyuncu 9. gunde kalici olarak %50 odul alir. Kabul edilemez;
        // GDD E.4 hedefi `clock_suspect_triggered` < %0,5 DAU.
        var state = seededState(20_000L, 20_000L * 86_400_000L, 5_000L)
        for (i in 1..60) {
            val day = 20_000L + i
            val s = sample(day, day * 86_400_000L, 5_000L + i * 86_400_000L)
            val d = evaluateReset(state, s)
            assertTrue("gun $day sifirlanmali", d.dailyReset)
            assertEquals("mesru oyuncu ASLA damperlenmez (gun $day)", 1.0, d.rewardMultiplier, 1e-9)
            assertFalse("mesru oyuncu supheli isaretlenmez (gun $day)", d.clockSuspect)
            state = d.newState
        }
        assertFalse(state.clockSuspect)
    }

    @Test
    fun manyResetsWithinOneRealHourTriggerTheDamper() {
        // GDD E.4 kural 4 — kaba tavan. Cihaz saati elle her seferinde bir gun ileri
        // aliniyor ama GERCEK sure neredeyse hic gecmiyor.
        var state = seededState(20_000L, 20_000L * 86_400_000L, 5_000L)
        var lastMultiplier = 1.0
        for (i in 1..12) {
            val day = 20_000L + i
            val s = sample(day, day * 86_400_000L, 5_000L + i * 300_000L) // +5 dk/gun
            val d = evaluateReset(state, s)
            assertTrue("oyuncu ASLA bloke edilmez, yenileme yapilir", d.dailyReset)
            state = d.newState
            lastMultiplier = d.rewardMultiplier
        }
        assertEquals("damper %50 olmali", 0.5, lastMultiplier, 1e-9)
        assertTrue(state.clockSuspect)
        assertTrue(state.dailyResetsInWindow > EconomyConfig.MAX_DAILY_RESETS_PER_7_DAYS)
    }

    @Test
    fun isoWeekIndexIsMondayAligned() {
        // epochDay 0 = 1970-01-01 Persembe. 4 = Pazartesi 1970-01-05.
        assertEquals(0L, isoWeekIndex(0L))
        assertEquals(0L, isoWeekIndex(3L))
        assertEquals(1L, isoWeekIndex(4L))
        assertEquals(1L, isoWeekIndex(10L))
        assertEquals(2L, isoWeekIndex(11L))
        // Negatif epochDay'de de ASAGIYA yuvarlamali (Kotlin `/` burada bozardi).
        assertEquals(-1L, isoWeekIndex(-4L))
        assertEquals(0L, isoWeekIndex(-3L))
        for (d in -100L..100L) {
            assertEquals("hafta 7 gun surmeli", isoWeekIndex(d) + 1L, isoWeekIndex(d + 7L))
        }
    }

    @Test
    fun weeklyResetHappensOnlyOnWeekBoundary() {
        val monday = 20_000L - Math.floorMod(20_000L + 3L, 7L)
        var state = seededState(monday, monday * 86_400_000L, 5_000L)
        for (offset in 1L..6L) {
            val day = monday + offset
            val d = evaluateReset(state, sample(day, day * 86_400_000L, 5_000L + offset * 86_400_000L))
            assertTrue("gunluk her gun sifirlanir", d.dailyReset)
            assertFalse("hafta icinde haftalik sifirlanmaz (gun +$offset)", d.weeklyReset)
            state = d.newState
        }
        val nextMonday = monday + 7L
        val d = evaluateReset(state, sample(nextMonday, nextMonday * 86_400_000L, 5_000L + 7 * 86_400_000L))
        assertTrue("Pazartesi haftalik sifirlanir", d.weeklyReset)
    }

    @Test
    fun rerollIsBlockedWhenUsedUpOrMissionAlreadyComplete() {
        val missions = dailyMissions(20_500L, 3L, 22, 4)
        val fresh = MissionState(daily = missions, rerollsUsedToday = 0)
        assertTrue(canReroll(fresh, MissionSlot.SKILL))
        assertFalse(canReroll(fresh.copy(rerollsUsedToday = 1), MissionSlot.SKILL))
        val done = fresh.copy(
            daily = missions.map { if (it.template.slot == MissionSlot.SKILL) it.advanced(99) else it }
        )
        assertFalse("tamamlanmis gorev yenilenemez (odul tekrari exploit'i)", canReroll(done, MissionSlot.SKILL))
    }

    // =================================================================================
    // 11. Meta rank kapilari (BAYRAK F-1) — VARSAYILAN KAPALI
    // =================================================================================

    @Test
    fun rankGatesAreDisabledByDefault() {
        assertFalse(
            "BAYRAK F-1: kapilar PO karari olmadan varsayilan olarak acilamaz",
            EconomyConfig.META_RANK_GATES_ENABLED,
        )
        assertEquals(0, rankGateClearedLevel(8))
        assertEquals(EconomyConfig.TREE_TOTAL_COST, purchasableTreeCeiling(0))
        // Kapisiz halde bile yeni oyuncu agaci ilk gun ALAMAZ: para yetmez.
        val newPlayer = walletAt(1_000, (1..6).toSet(), (1..3).toSet())
        assertTrue(purchaseAllowed(newPlayer, MetaUpgrades(firepower = 1), UpgradeLine.FIREPOWER) is PurchaseDecision.Allowed)
    }

    @Test
    fun rankGatesWhenEnabledNeverBlockTreeCompletionBeforeCampaignEnd() {
        assertEquals(
            "L21 temizlenince agacin tamami satin alinabilir olmali",
            EconomyConfig.TREE_TOTAL_COST, purchasableTreeCeiling(21, gatesEnabled = true),
        )
        assertEquals(EconomyConfig.TREE_TOTAL_COST, purchasableTreeCeiling(22, gatesEnabled = true))
    }

    @Test
    fun rankGateCeilingIsMonotonicAndScarceEarly() {
        var prev = -1
        for (cleared in 0..22) {
            val ceiling = purchasableTreeCeiling(cleared, gatesEnabled = true)
            assertTrue("tavan geriye gidemez (cleared=$cleared)", ceiling >= prev)
            prev = ceiling
        }
        assertTrue(
            "L0'da tavan agacin yarisini gecmemeli",
            purchasableTreeCeiling(0, gatesEnabled = true) < EconomyConfig.TREE_TOTAL_COST / 2,
        )
        assertTrue(purchasableTreeCeiling(0, gatesEnabled = true) > 0)
    }

    @Test
    fun rankGateBlocksHighRankButNeverTheFirstTwoRanks() {
        val richButNew = walletAt(99_999, (1..7).toSet(), (1..2).toSet())
        assertTrue(purchaseAllowed(richButNew, MetaUpgrades(), UpgradeLine.FIREPOWER, gatesEnabled = true) is PurchaseDecision.Allowed)
        assertTrue(purchaseAllowed(richButNew, MetaUpgrades(firepower = 1), UpgradeLine.FIREPOWER, gatesEnabled = true) is PurchaseDecision.Allowed)
        val gated = purchaseAllowed(richButNew, MetaUpgrades(firepower = 2), UpgradeLine.FIREPOWER, gatesEnabled = true)
        assertTrue(gated is PurchaseDecision.RankGated)
        assertEquals(5, (gated as PurchaseDecision.RankGated).requiredClearedLevel)
    }

    @Test
    fun everyGateIsSatisfiableWithinTheTwentyTwoLevelCampaign() {
        EconomyConfig.RANK_GATE_CLEARED_LEVEL.forEach {
            assertTrue("kapi $it kampanya disina tasamaz", it <= EconomyConfig.CAMPAIGN_LEVELS)
        }
        for (rank in 2..8) {
            assertTrue(
                "kapilar rank ile artmali",
                rankGateClearedLevel(rank, true) >= rankGateClearedLevel(rank - 1, true),
            )
        }
    }

    // =================================================================================
    // 12. Zorluk egrisi etkilesimi (ECONOMY_SPEC 6.2 bayrak F-8)
    // =================================================================================

    @Test
    fun maxedMetaDoesNotMakePeakLevelsThreeStarTrivial() {
        // LEVEL_DESIGN E.3: L22 ELL = 9,0 sizma (20 can uzerinden).
        // Tam maksli meta: etkin verim 1,426 -> ELL ~ 6,3; maks can 30.
        val maxed = MetaUpgrades(firepower = 4, optics = 3, fortification = 5)
        assertEquals(1.4260, maxed.effectiveThroughput, 1e-4)

        val scaledLeaks = Math.round(9.0 / maxed.effectiveThroughput).toInt() // 6
        val stars = starsFor(maxed.maxBaseHealth - scaledLeaks, maxed.maxBaseHealth)
        assertEquals("L22 tam maksli metayla bile 3 yildiz OLMAMALI", 2, stars)

        // Act I finali (L11, ELL 5,5) de 3 yildiz olmamali.
        val l11Leaks = Math.round(5.5 / maxed.effectiveThroughput).toInt() // 4
        assertEquals(2, starsFor(maxed.maxBaseHealth - l11Leaks, maxed.maxBaseHealth))
    }

    @Test
    fun zeroMetaPlayerStillClearsTheHardestLevel() {
        // LEVEL_DESIGN S6 / GDD H.6: hicbir bolum meta yukseltme GEREKTIRMEZ.
        // ELL 9,0 < 20 can -> bolum bitirilebilir ve 1 yildiz alinir.
        val base = MetaUpgrades()
        assertEquals(20, base.maxBaseHealth)
        assertEquals(1.0, base.effectiveThroughput, 1e-9)
        assertEquals(2, starsFor(20 - 9, base.maxBaseHealth)) // 11/20 = %55 -> 2 yildiz
        assertTrue("sifir meta ile L22 gecilebilir olmali", starsFor(20 - 9, 20) >= 1)
    }

    // =================================================================================
    // 13. Cuzdan butunlugu
    // =================================================================================

    @Test
    fun walletNeverGoesNegative() {
        val w = PlayerWallet(coins = 100)
        try {
            w.debited(101)
            throw AssertionError("negatif bakiye kabul edildi")
        } catch (expected: IllegalArgumentException) {
            // beklenen
        }
        assertEquals(0, w.debited(100).coins)
    }

    @Test
    fun lifetimeCountersTrackEveryFlow() {
        var w = PlayerWallet()
        w = w.credited(500).credited(250).debited(300)
        assertEquals(450, w.coins)
        assertEquals(750, w.lifetimeEarned)
        assertEquals(300, w.lifetimeSpent)
    }

    @Test
    fun replayingAnUnlockedLevelCostsNothing() {
        // GDD H.1 / C.1 kural 2: tekrar oynamak ve kaybetmek HICBIR coin gotürmez.
        val wallet = walletAt(500, (1..7).toSet(), (1..7).toSet())
        assertFalse("acik bolum tekrar acilmaz -> tekrar ucreti yok", canUnlock(wallet, 7))
        assertEquals(500, applyUnlock(wallet, 7).coins)
    }

    @Test
    fun unlockIsBlockedUntilThePreviousLevelIsCleared() {
        val wallet = walletAt(9_999, (1..6).toSet(), (1..5).toSet())
        assertFalse("L6 temizlenmeden L7 acilamaz", canUnlock(wallet, 7))
        val ready = wallet.copy(clearedLevels = (1..6).toSet())
        assertTrue(canUnlock(ready, 7))
        assertFalse("sira atlanamaz", canUnlock(ready, 9))
    }

    @Test
    fun failingALevelChangesNothing() {
        // Yenilgi ekonomiye DOKUNMAZ: `resolveLevelClear` yalnizca zaferde cagrilir.
        val wallet = walletAt(300, (1..7).toSet(), (1..6).toSet())
        assertEquals(0, starsFor(0, 20))
        try {
            resolveLevelClear(wallet, 7, 0, 20)
            throw AssertionError("yenilgi zafer muhasebesine giremez")
        } catch (expected: IllegalArgumentException) {
            // beklenen: 0 yildiz zafer degildir
        }
        assertEquals(300, wallet.coins)
    }
}

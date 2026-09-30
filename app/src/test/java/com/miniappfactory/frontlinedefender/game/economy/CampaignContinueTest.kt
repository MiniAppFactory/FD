package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COIN KARSILIGI DEVAM — CUZDAN ETKILI DAVRANIS (`CampaignProgressImpl`).
 *
 * Eski `AdRewardBridgeTest`in R2 "Takviye" testlerinin (yildiz-notr, savasa kapsamli sayac,
 * motor reddi) anlamini, reklam KALKTIKTAN sonra COIN devamina tasir ve uc yeni kural ekler:
 * tek sefer borclandirma, rezerv (bedava-devam acigi) ve satin alinmis coin degismezleri.
 *
 * **SAF JUnit**: `CampaignProgressImpl(SaveManager(InMemoryKeyValueStore()), sabitSaat)`.
 * Motor bir lambda ile taklit edilir (`GameEngine.reinforceAfterDefeat`in sozlesmesi:
 * uygulanan can sayisini dondurur, DEFEAT disinda 0).
 */
class CampaignContinueTest {

    /** Sabit saat: gun donusu testin ortasinda tetiklenmesin. */
    private class FixedClock(private val day: Long = 20_500L) : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = day,
            wallClockMs = day * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private class Fixture(val save: SaveManager, val progress: CampaignProgressImpl, val events: List<Pair<String, Map<String, Any>>>) {
        /** Kalici cuzdan (lifetimeEarned/lifetimeSpent burada okunur). */
        val wallet: PlayerWallet get() = save.loadWallet()
    }

    /**
     * [level] bolumu TEKRAR oynanan (1..level temizlenmis) cuzdan. Sonraki kilitli bolumun
     * rezervi ve oto-hibe (autoGrantShortfall) korumasi TAM devrede. [level] <= 6 bedavadir.
     */
    private fun fixture(level: Int, coins: Int, clearedUpTo: Int = level): Fixture {
        val save = SaveManager(InMemoryKeyValueStore())
        save.saveWallet(
            PlayerWallet(
                coins = coins,
                unlockedLevels = (1..maxOf(level, EconomyConfig.FIRST_PAID_LEVEL - 1)).toSet(),
                clearedLevels = (1..clearedUpTo).toSet(),
            )
        )
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val progress = CampaignProgressImpl(save, FixedClock()) { name, params -> events += name to params }
        progress.beginBattle(level)
        return Fixture(save, progress, events)
    }

    /** Sayaci tutan, verilen can sayisini aynen uygulayan calisan motor. */
    private class WorkingEngine {
        var calls = 0
        var requestedLives = -1
        val resume: (Int) -> Int = { lives -> calls++; requestedLives = lives; lives }
    }

    private class Refusal(private val returns: Int = 0) {
        var calls = 0
        val resume: (Int) -> Int = { calls++; returns }
    }

    // =================================================================================
    // (a) TAM BIR KEZ
    // =================================================================================

    @Test
    fun continueDebitsThePriceExactlyOnceAndASecondCallIsDeclined() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = 1_000)
        val engine = WorkingEngine()

        val first = f.progress.continueAfterDefeat(level, engine.resume)

        assertEquals(ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES), first)
        assertEquals("motor bedelli lives ile cagrildi", EconomyConfig.CONTINUE_LIVES, engine.requestedLives)
        assertEquals(1, engine.calls)
        assertEquals("bedel TAM BIR KEZ dusuldu", 1_000 - price, f.progress.coins)
        assertEquals("kalici cuzdan da ayni", 1_000 - price, f.wallet.coins)

        val coinsAfterFirst = f.progress.coins
        val second = f.progress.continueAfterDefeat(level, engine.resume)

        assertEquals(ContinueResult.Declined(ContinueDecision.AlreadyUsed), second)
        assertEquals("ikinci cagri cuzdani DEGISTIRMEZ", coinsAfterFirst, f.progress.coins)
        assertEquals("ikinci cagri MOTORU CAGIRMAZ", 1, engine.calls)
        assertEquals(ContinueDecision.AlreadyUsed, f.progress.continueDecision(level))
    }

    @Test
    fun rapidRepeatedTapsDebitOnceEvenIfTheEngineWouldKeepAccepting() {
        // En kotu durum: motor her seferinde "uygulandi" diyor (gercek motor DEFEAT disinda 0
        // doner; burada o ikinci savunma hatti kapali). Tek savunma ekonomi sayaci olur.
        val level = 7
        val price = continuePrice(level)
        val f = fixture(level, coins = 5_000)
        val engine = WorkingEngine()

        val results = List(10) { f.progress.continueAfterDefeat(level, engine.resume) }

        assertEquals(1, results.count { it is ContinueResult.Continued })
        assertEquals(9, results.count { it == ContinueResult.Declined(ContinueDecision.AlreadyUsed) })
        assertEquals(5_000 - price, f.progress.coins)
        assertEquals(1, engine.calls)
        assertEquals("tek 'continue_used' olayi", 1, f.events.count { it.first == "continue_used" })
        assertEquals(price, f.events.single { it.first == "continue_used" }.second["price"])
    }

    // =================================================================================
    // (b) MOTOR REDDI — hak ve para tuketilmez
    // =================================================================================

    @Test
    fun anEngineRefusalChangesNothingAndDoesNotConsumeTheUse() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = 1_000)

        for (refused in intArrayOf(0, -3)) {
            val refusal = Refusal(returns = refused)
            assertEquals(ContinueResult.EngineRefused, f.progress.continueAfterDefeat(level, refusal.resume))
            assertEquals("motor cagrildi (ekonomi izin vermisti)", 1, refusal.calls)
            assertEquals("reddedilen devam cuzdani degistirmez", 1_000, f.progress.coins)
            assertEquals(0, f.wallet.lifetimeSpent)
            assertTrue("hak tuketilmedi", f.progress.continueDecision(level).isAllowed)
            assertEquals("reddedilen devam yildizi cezalandirmaz", 7, f.progress.starHealthFor(7))
        }

        // Hak tuketilmedigi icin calisan bir motorla SONRAKI cagri basarili olur.
        val engine = WorkingEngine()
        assertEquals(
            ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES),
            f.progress.continueAfterDefeat(level, engine.resume),
        )
        assertEquals(1_000 - price, f.progress.coins)
        assertEquals(price, f.wallet.lifetimeSpent)
    }

    // =================================================================================
    // (c) YETERSIZ BAKIYE
    // =================================================================================

    @Test
    fun oneCoinShortIsDeclinedWithTheExactShortfallAndTheEngineIsNeverCalled() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = price - 1)
        val engine = WorkingEngine()

        val result = f.progress.continueAfterDefeat(level, engine.resume)

        assertEquals(
            ContinueResult.Declined(ContinueDecision.InsufficientCoins(price = price, shortfall = 1)),
            result,
        )
        assertEquals("motor CAGRILMADI", 0, engine.calls)
        assertEquals(price - 1, f.progress.coins)
        assertTrue("hak tuketilmedi", f.progress.continueDecision(level) is ContinueDecision.InsufficientCoins)
        assertEquals("engellenen deneme olculur", 1, f.events.count { it.first == "continue_blocked" })
    }

    @Test
    fun exactlyThePriceIsEnoughWhenNoReserveIsHeld() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = price)
        assertEquals("on kosul: rezerv yok", 0, f.progress.reserve)

        val result = f.progress.continueAfterDefeat(level, WorkingEngine().resume)

        assertEquals(ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES), result)
        assertEquals("bakiye tam sifira iner, negatife degil", 0, f.progress.coins)
    }

    // =================================================================================
    // (d) REZERV — bedava-devam acigi
    // =================================================================================

    @Test
    fun aReserveLockedContinueChangesNothingAndNeverTriggersAFreeAutoGrant() {
        // L7 tekrar oynaniyor, L8 kilitli. Bakiye fiyati (160) karsiliyor ama fiyat + rezerv
        // (160 + 110) icin 1 coin eksik.
        val level = 7
        val price = continuePrice(level)
        val reserve = lockCost(level + 1)
        val coins = price + reserve - 1
        val f = fixture(level, coins = coins)
        val engine = WorkingEngine()

        assertEquals("on kosul: rezerv D(L+1)", reserve, f.progress.reserve)
        // Bu testin BOSA GITMEDIGININ kaniti: bedel dusulseydi oto-hibe devreye girerdi.
        assertTrue(
            "on kosul: dusulse autoGrantShortfall > 0 olurdu",
            autoGrantShortfall(f.wallet.debited(price)) > 0,
        )

        val result = f.progress.continueAfterDefeat(level, engine.resume)

        assertEquals(
            ContinueResult.Declined(ContinueDecision.ReserveLocked(price = price, reserve = reserve, shortfall = 1)),
            result,
        )
        assertEquals("motor CAGRILMADI", 0, engine.calls)
        assertEquals("bakiye ayni", coins, f.progress.coins)
        assertEquals("oto-hibe gerekmiyor", 0, autoGrantShortfall(f.wallet))
        assertEquals("oto-hibe tetiklenmedi (geri yazilan coin yok)", 0, f.progress.enforceUnlockInvariant())
        assertEquals(coins, f.progress.coins)
        assertEquals("hicbir coin geri yazilmadi", 0, f.wallet.lifetimeEarned)
        assertEquals(0, f.events.count { it.first == "auto_grant_fired" })
    }

    @Test
    fun theSameContinueIsAllowedOnceTheBalanceCoversPricePlusReserve() {
        val level = 7
        val price = continuePrice(level)
        val reserve = lockCost(level + 1)
        val f = fixture(level, coins = price + reserve)

        val result = f.progress.continueAfterDefeat(level, WorkingEngine().resume)

        assertEquals(ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES), result)
        assertEquals("bakiye tam rezervde durur", reserve, f.progress.coins)
        assertEquals(0, autoGrantShortfall(f.wallet))
        assertEquals(0, f.progress.enforceUnlockInvariant())
        assertEquals("geri yazilan coin yok", 0, f.wallet.lifetimeEarned)
    }

    // =================================================================================
    // (e) YILDIZ NOTRLUGU
    // =================================================================================

    @Test
    fun aContinuedVictoryBuysSurvivalNeverStarsOrCoins() {
        val level = 4
        // Sinir vakasi: maks can = devam cani. Devamsiz 5/5 = 3 yildiz olurdu; DEVAMLA 1 yildiz.
        val maxLives = EconomyConfig.CONTINUE_LIVES
        assertEquals("on kosul: devamsiz bu sonuc 3 yildiz", 3, starsFor(EconomyConfig.CONTINUE_LIVES, maxLives))

        // Kontrol grubu: devam KULLANILMADAN ayni sonuc 3 yildiz + kusursuz madalya odul.
        val control = fixture(level, coins = 1_000, clearedUpTo = level - 1)
        val controlClear = control.progress.onLevelCleared(level, EconomyConfig.CONTINUE_LIVES, maxLives)
        assertEquals(3, controlClear.stars)
        assertTrue(controlClear.total > levelReward(level, 1))

        val f = fixture(level, coins = 1_000, clearedUpTo = level - 1)
        assertEquals(
            "devam oncesi kimlik fonksiyonu",
            EconomyConfig.CONTINUE_LIVES,
            f.progress.starHealthFor(EconomyConfig.CONTINUE_LIVES),
        )
        assertTrue(f.progress.continueAfterDefeat(level, WorkingEngine().resume) is ContinueResult.Continued)

        val clear = f.progress.onLevelCleared(level, EconomyConfig.CONTINUE_LIVES, maxLives)

        assertEquals("devamla kazanilan zafer EN FAZLA 1 yildiz", 1, clear.stars)
        assertTrue(clear.firstClear)
        assertEquals("1 yildiz odulunden fazla coin yok", levelReward(level, 1), clear.total)
        assertEquals(0, clear.perfectBonus)
        assertEquals(0, clear.starImprovement)
        assertEquals(1, f.progress.starsFor(level))
    }

    @Test
    fun aContinuedVictoryIsAtMostOneStarForEveryReachableSurvivorCount() {
        // Devamdan sonra motorda en fazla CONTINUE_LIVES can vardir (yenilenme yok); bu
        // araligin HER degeri, HER maks-can icin (kucuk/gercekci) en fazla 1 yildiz vermeli.
        val level = 4
        for (maxLives in intArrayOf(EconomyConfig.CONTINUE_LIVES, EconomyConfig.CONTINUE_LIVES + 1, 10, 20)) {
            for (survivors in 1..EconomyConfig.CONTINUE_LIVES) {
                val f = fixture(level, coins = 1_000, clearedUpTo = level - 1)
                assertTrue(f.progress.continueAfterDefeat(level, WorkingEngine().resume) is ContinueResult.Continued)
                assertEquals(
                    "maxLives=$maxLives kalan=$survivors",
                    1,
                    f.progress.starHealthFor(survivors),
                )
                val clear = f.progress.onLevelCleared(level, survivors, maxLives)
                assertEquals("maxLives=$maxLives kalan=$survivors", 1, clear.stars)
                assertEquals(levelReward(level, 1), clear.total)
            }
        }
    }

    // =================================================================================
    // (f) SAVAS KAPSAMI
    // =================================================================================

    @Test
    fun beginBattleResetsTheCounterAndAContinueDoesNot() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = 1_000)
        val engine = WorkingEngine()

        assertTrue(f.progress.continueAfterDefeat(level, engine.resume) is ContinueResult.Continued)
        // Devam sayaci SIFIRLAMAZ.
        assertEquals(ContinueDecision.AlreadyUsed, f.progress.continueDecision(level))
        assertTrue(f.progress.continueAfterDefeat(level, engine.resume) is ContinueResult.Declined)

        // "TEKRAR DENE" = yeni savas: hak geri gelir, yildiz notrlugu de temizlenir.
        f.progress.beginBattle(level)
        assertTrue("yeni savas yeni hak", f.progress.continueDecision(level).isAllowed)
        assertEquals(
            "onceki savasin devami yeni savasin yildizini dusuremez",
            EconomyConfig.CONTINUE_LIVES,
            f.progress.starHealthFor(EconomyConfig.CONTINUE_LIVES),
        )
        assertEquals(
            ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES),
            f.progress.continueAfterDefeat(level, engine.resume),
        )
        assertEquals("iki savas = iki bedel", 1_000 - 2 * price, f.progress.coins)
        assertEquals(2, engine.calls)
    }

    @Test
    fun endBattleAlsoResetsTheCounterAndTheReinforcementBookkeeping() {
        val level = 4
        val f = fixture(level, coins = 1_000)
        assertTrue(f.progress.continueAfterDefeat(level, WorkingEngine().resume) is ContinueResult.Continued)
        assertEquals(1, f.progress.starHealthFor(EconomyConfig.CONTINUE_LIVES))

        f.progress.endBattle()

        assertTrue(f.progress.continueDecision(level).isAllowed)
        assertEquals(EconomyConfig.CONTINUE_LIVES, f.progress.starHealthFor(EconomyConfig.CONTINUE_LIVES))
    }

    @Test
    fun aDeclinedOrRefusedContinueDoesNotConsumeThePerBattleUse() {
        val level = 7
        val price = continuePrice(level)
        val f = fixture(level, coins = price - 1)
        val engine = WorkingEngine()

        assertTrue(f.progress.continueAfterDefeat(level, engine.resume) is ContinueResult.Declined)
        assertEquals(0, engine.calls)
        // Bakiye sonradan yeterli olur (ornegin gorev odulu): ayni savasin hakki hala duruyor.
        f.progress.grantPurchasedCoins("token-top-up", 1_000)
        assertTrue(f.progress.continueDecision(level).isAllowed)
    }

    // =================================================================================
    // (g) noteReinforcement / starHealthFor — GENEL API DEGISMEDI
    // =================================================================================

    @Test
    fun noteReinforcementAndStarHealthForKeepTheirPublicContract() {
        val f = fixture(4, coins = 0)

        // Hicbir sey geri verilmediyse girdi AYNEN doner (Faz 9 davranisi).
        for (lives in intArrayOf(0, 1, 7, 20)) assertEquals(lives, f.progress.starHealthFor(lives))

        f.progress.noteReinforcement(-3) // negatif geri verme sayilmaz
        assertEquals(7, f.progress.starHealthFor(7))

        f.progress.noteReinforcement(5)
        assertEquals("geri verilen can yildiz hesabindan dusulur", 2, f.progress.starHealthFor(7))
        assertEquals("kazanilan zafer en az 1", 1, f.progress.starHealthFor(3))
        assertEquals("yenilgi 0 kalir", 0, f.progress.starHealthFor(0))

        f.progress.noteReinforcement(2) // birikir
        assertEquals(1, f.progress.starHealthFor(7)) // 7 - 7 = 0 -> zafer icin en az 1
    }

    // =================================================================================
    // (h) SATIN ALINMIS COIN DEGISMEZLERI
    // =================================================================================

    @Test
    fun theDebitGoesThroughWalletDebitedSoLifetimeSpentGrowsAndLifetimeEarnedDoesNot() {
        val level = 4
        val price = continuePrice(level)
        val f = fixture(level, coins = 1_000)
        val before = f.wallet
        assertEquals(0, before.lifetimeSpent)

        assertTrue(f.progress.continueAfterDefeat(level, WorkingEngine().resume) is ContinueResult.Continued)

        val after = f.wallet
        assertEquals("harcanan toplam tam fiyat kadar artar", before.lifetimeSpent + price, after.lifetimeSpent)
        assertEquals("kazanilan toplam DEGISMEZ", before.lifetimeEarned, after.lifetimeEarned)
        assertEquals(before.coins - price, after.coins)
    }

    @Test
    fun purchasedCoinsSpentOnAContinueNeverEnterLifetimeEarned() {
        val level = 2
        val price = continuePrice(level)
        val save = SaveManager(InMemoryKeyValueStore())
        val progress = CampaignProgressImpl(save, FixedClock())
        progress.beginBattle(level)
        assertTrue(progress.grantPurchasedCoins("token-1", 1_000))
        assertEquals("satin alinan coin kazanilmis sayilmaz", 0, save.loadWallet().lifetimeEarned)
        assertEquals(1_000, progress.lifetimePurchasedCoins)

        assertTrue(progress.continueAfterDefeat(level, WorkingEngine().resume) is ContinueResult.Continued)

        val wallet = save.loadWallet()
        assertEquals(1_000 - price, wallet.coins)
        assertEquals(price, wallet.lifetimeSpent)
        assertEquals(0, wallet.lifetimeEarned)
        assertEquals("satin alma kaydi devamdan etkilenmez", 1_000, progress.lifetimePurchasedCoins)
    }

    // =================================================================================
    // (i) HIRSLI DIZI — rezerv hicbir seviyede delinmez
    // =================================================================================

    @Test
    fun greedyContinueNeverCreatesAnAutoGrantShortfallAtAnyLevel() {
        for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = continuePrice(level)
            val probe = fixture(level, coins = 0)
            val reserve = probe.progress.reserve
            // "Rezervin hemen ustu": tam sinir (fiyat + rezerv) ve 5 coin fazlasi.
            for (extra in intArrayOf(0, 5)) {
                val coins = price + reserve + extra
                val f = fixture(level, coins = coins)
                val engine = WorkingEngine()

                val result = f.progress.continueAfterDefeat(level, engine.resume)
                assertEquals("L$level +$extra", ContinueResult.Continued(price, EconomyConfig.CONTINUE_LIVES), result)

                assertEquals("L$level +$extra oto-hibe acigi", 0, autoGrantShortfall(f.wallet))
                assertEquals("L$level +$extra oto-hibe tetiklenmedi", 0, f.progress.enforceUnlockInvariant())
                assertEquals("L$level +$extra bakiye", reserve + extra, f.progress.coins)
                assertEquals("L$level +$extra geri yazilan coin yok", 0, f.wallet.lifetimeEarned)
                assertTrue("L$level +$extra bakiye rezervin altina inmedi", f.progress.coins >= reserve)

                // Yeni savasta AYNI bakiyeyle tekrar devam (ac gozlu ikinci tur): bakiye
                // artik fiyat + rezerve yetmez; reddedilir ve HICBIR SEY geri yazilmaz.
                f.progress.beginBattle(level)
                val again = f.progress.continueAfterDefeat(level, engine.resume)
                assertTrue("L$level +$extra ikinci tur reddedilmeli ($again)", again is ContinueResult.Declined)
                assertEquals(1, engine.calls)
                assertEquals(0, autoGrantShortfall(f.wallet))
                assertEquals(0, f.wallet.lifetimeEarned)
            }
        }
    }

    @Test
    fun aRefusedContinueBelowTheBoundaryNeverDipsIntoTheReserveAtAnyLevel() {
        for (level in 2..EconomyConfig.CAMPAIGN_LEVELS) {
            val price = continuePrice(level)
            val reserve = fixture(level, coins = 0).progress.reserve
            val coins = price + reserve - 1
            val f = fixture(level, coins = coins)
            val engine = WorkingEngine()

            val result = f.progress.continueAfterDefeat(level, engine.resume)

            // Rezervli bolumde ReserveLocked, rezervsiz (L55 / kampanya sonu) bolumde InsufficientCoins.
            assertTrue("L$level ($result)", result is ContinueResult.Declined)
            assertEquals("L$level bakiye", coins, f.progress.coins)
            assertEquals("L$level motor", 0, engine.calls)
            assertEquals("L$level oto-hibe acigi", 0, autoGrantShortfall(f.wallet))
            assertFalse(f.progress.continueDecision(level).isAllowed)
        }
    }
}

package com.miniappfactory.frontlinedefender.game.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COIN KARSILIGI DEVAM (2026-09-30; eski reklamli R2 "Takviye"nin yerine) — SAF KARAR MANTIGI.
 *
 * **SAF JUnit**: `continuePrice` / `continueAllowed` Android, `Context` ve
 * `CampaignProgressImpl` gerektirmez. Cuzdan-etkili davranis (tek sefer borclandirma,
 * motor reddi, savas kapsami) `CampaignContinueTest`te.
 *
 * Bu dosya KURAL yazar, sayi dondurmaz: fiyat, bolum odulunden ve kilit tablosundan
 * TURETILEN bir esitsizlikle dogrulanir; yalnizca sozlesmenin kendisi olan capa
 * degerler (L2 80 ... L55 880) elle hesaplanmis olarak ayrica kilitlenir.
 *
 * ## Fiyatin uc korkuluk kurali (her biri asagida ayri test)
 *  1. `price < R(L,1)`  -> devamla kazanilan ILK temizlik asla NET ZARAR degildir.
 *  2. `price <= R(L,1) - D(L+1)` -> rezerv olmasa bile soft-lock guvenli.
 *  3. `price == eliteTicketPrice(L)` -> %50; iki "yarim odul" fiyati birbirinden kopamaz.
 */
class ContinueSystemTest {

    private val levels = 1..EconomyConfig.CAMPAIGN_LEVELS

    /**
     * L. bolum OYNANIYOR ve TEKRAR oynaniyor (1..L temizlenmis) olan cuzdan: bir sonraki
     * kilitli bolumun rezervi ve oto-hibe koruma kurali (autoGrantShortfall) TAM devrede.
     * L <= 6 bolumleri bedavadir, bu yuzden acik kume en az 1..6'dir.
     */
    private fun replayWallet(level: Int, coins: Int) = PlayerWallet(
        coins = coins,
        unlockedLevels = (1..maxOf(level, EconomyConfig.FIRST_PAID_LEVEL - 1)).toSet(),
        clearedLevels = (1..level).toSet(),
    )

    // =================================================================================
    // 1. FIYAT — turetilmis kurallar
    // =================================================================================

    @Test
    fun priceEqualsTheEliteTicketAndIsAMultipleOfTen() {
        for (level in levels) {
            val price = continuePrice(level)
            assertEquals("L$level: devam fiyati elit bilet fiyatiyla ayni formul (%50)", eliteTicketPrice(level), price)
            assertEquals("L$level: fiyat 10'un kati", 0, price % 10)
            assertTrue("L$level: fiyat pozitif ($price)", price > 0)
        }
    }

    @Test
    fun aContinuedFirstClearNeverNetsALoss() {
        // Yenilgiden sonra devam edip 1 yildizla bitiren oyuncu, ilk temizlik odulunu
        // ALIR (yildiz-notr: 1 yildiz). Fiyat bu oduldan kucukse net kazanc > 0.
        for (level in levels) {
            val price = continuePrice(level)
            val firstClear = levelReward(level, 1)
            assertTrue(
                "L$level: devam fiyati $price >= ilk temizlik odulu $firstClear (net zarar)",
                price < firstClear,
            )
        }
    }

    /**
     * SOFT-LOCK GUVENLIGI, REZERV KONTROLUNDEN BAGIMSIZ (aritmetik):
     *
     *   Kampanya kurali (CampaignTableTest / EconomySimulationTest): R(L,1) >= 2,0 x D(L+1)
     *   yani D(L+1) <= R(L,1) / 2.
     *   Fiyat = floor10(R(L,1) x 50 / 100) <= R(L,1) / 2  (asagi yuvarlama fiyati YALNIZCA dusurur).
     *   Dolayisiyla   price <= R/2 <= R - D(L+1)  ->  R - price >= D(L+1).
     *
     * Yani bir bolumun ilk temizlik odulu, devam bedelini odedikten SONRA da bir sonraki
     * kilidi karsilar; rezerv `continueAllowed`te ayrica uygulanir ama fiyatin kendisi de
     * guvenlidir (rezerv 2. savunma hattidir, tek savunma degil).
     */
    @Test
    fun priceIsSoftLockSafeEvenIgnoringTheReserve() {
        for (level in 1 until EconomyConfig.CAMPAIGN_LEVELS) {
            val price = continuePrice(level)
            val firstClear = levelReward(level, 1)
            val nextLock = lockCost(level + 1)
            assertTrue(
                "L$level: fiyat $price > R(L,1) $firstClear - D(L+1) $nextLock (soft-lock riski)",
                price <= firstClear - nextLock,
            )
        }
    }

    @Test
    fun pricesNeverDecreaseWithLevel() {
        var previous = 0
        for (level in levels) {
            val price = continuePrice(level)
            assertTrue("L$level: fiyat $price, onceki bolum $previous'den dusuk", price >= previous)
            previous = price
        }
    }

    /**
     * ELLE HESAPLANMIS CAPALAR (sozlesmenin kendisi; formulu tekrar yazmak degil).
     * R(L,1) = (140 + 30 x (L-1)) x 1,0 (10'a yuvarli); fiyat = floor10(R / 2):
     *  L2:  R=170 -> 85  -> 80
     *  L4:  R=230 -> 115 -> 110
     *  L7:  R=320 -> 160 -> 160
     *  L22: R=140+30x21=770  -> 385 -> 380
     *  L55: R=140+30x54=1760 -> 880 -> 880
     */
    @Test
    fun anchorPricesMatchTheHandComputedTable() {
        assertEquals(80, continuePrice(2))
        assertEquals(110, continuePrice(4))
        assertEquals(160, continuePrice(7))
        assertEquals(380, continuePrice(22))
        assertEquals(880, continuePrice(55))
    }

    @Test
    fun continueRestoresAFixedPositiveNumberOfLivesAndAllowsOneUsePerBattle() {
        assertTrue(EconomyConfig.CONTINUE_LIVES > 0)
        assertEquals(1, EconomyConfig.CONTINUE_USES_PER_BATTLE)
        val allowed = continueAllowed(replayWallet(4, 10_000), 4, 0) as ContinueDecision.Allowed
        assertEquals(EconomyConfig.CONTINUE_LIVES, allowed.lives)
    }

    // =================================================================================
    // 2. KARAR — siralama: kill switch -> savas basi hak -> bakiye -> rezerv
    // =================================================================================

    @Test
    fun alreadyUsedIsCheckedBeforeAnyBalanceCheck() {
        for (level in intArrayOf(1, 4, 7, 22, 55)) {
            val used = EconomyConfig.CONTINUE_USES_PER_BATTLE
            // Bos cuzdan: bakiye kontrolu ONCE gelseydi InsufficientCoins donerdi.
            assertEquals(
                "L$level bos cuzdan",
                ContinueDecision.AlreadyUsed,
                continueAllowed(replayWallet(level, 0), level, used),
            )
            // Zengin cuzdan: bakiye yetiyor, hak yine tukenmis.
            assertEquals(
                "L$level zengin cuzdan",
                ContinueDecision.AlreadyUsed,
                continueAllowed(replayWallet(level, 1_000_000), level, used),
            )
            // Sayac tavani asmis olsa da ayni (>=, == degil).
            assertEquals(
                ContinueDecision.AlreadyUsed,
                continueAllowed(replayWallet(level, 1_000_000), level, used + 3),
            )
        }
    }

    @Test
    fun insufficientCoinsReportsTheExactShortfall() {
        for (level in levels) {
            val price = continuePrice(level)
            for (coins in intArrayOf(0, 1, price / 2, price - 1)) {
                val decision = continueAllowed(replayWallet(level, coins), level, 0)
                assertEquals(
                    "L$level coins=$coins",
                    ContinueDecision.InsufficientCoins(price = price, shortfall = price - coins),
                    decision,
                )
            }
        }
    }

    @Test
    fun balanceIsCheckedBeforeTheReserve() {
        // Rezerv > 0 olan bir cuzdanda fiyatin ALTINDAKI bakiye ReserveLocked degil
        // InsufficientCoins olmali (analytics: reserve_lock_blocked yalnizca "para var
        // ama rezerv tutuyor" durumunu olcer).
        val level = 22
        val wallet = replayWallet(level, coins = continuePrice(level) - 1)
        assertTrue("on kosul: rezerv > 0", reserveFor(wallet) > 0)
        assertTrue(continueAllowed(wallet, level, 0) is ContinueDecision.InsufficientCoins)
    }

    @Test
    fun reserveLockedOnlyWhenTheBalanceCoversThePriceButNotThePriceAndReserve() {
        var reserveCasesSeen = 0
        for (level in levels) {
            val price = continuePrice(level)
            val reserve = reserveFor(replayWallet(level, 0))
            // Fiyat + rezerv - 1 .. fiyat: tam sinir bandi taranir (rezerv <= ~800).
            for (coins in price until price + reserve) {
                val wallet = replayWallet(level, coins)
                val left = coins - price
                assertEquals("on kosul: bu cuzdanda rezerv sabit", reserve, reserveFor(wallet))
                val decision = continueAllowed(wallet, level, 0)
                assertEquals(
                    "L$level coins=$coins",
                    ContinueDecision.ReserveLocked(price = price, reserve = reserve, shortfall = reserve - left),
                    decision,
                )
                reserveCasesSeen++
            }
        }
        assertTrue("rezerv bandi gercekten tarandi ($reserveCasesSeen)", reserveCasesSeen > 1_000)
    }

    @Test
    fun allowedAtTheExactReserveBoundaryWithAnExactBalanceAfter() {
        for (level in levels) {
            val price = continuePrice(level)
            val reserve = reserveFor(replayWallet(level, 0))
            for (extra in intArrayOf(0, 1, 7, 500)) {
                val coins = price + reserve + extra
                val decision = continueAllowed(replayWallet(level, coins), level, 0)
                assertEquals(
                    "L$level coins=$coins",
                    ContinueDecision.Allowed(
                        price = price,
                        lives = EconomyConfig.CONTINUE_LIVES,
                        balanceAfter = coins - price,
                    ),
                    decision,
                )
                assertTrue(decision.isAllowed)
                assertTrue("kalan bakiye rezervin altina inmez", (decision as ContinueDecision.Allowed).balanceAfter >= reserve)
            }
        }
    }

    @Test
    fun onlyAllowedIsAllowed() {
        assertFalse(ContinueDecision.Disabled.isAllowed)
        assertFalse(ContinueDecision.AlreadyUsed.isAllowed)
        assertFalse(ContinueDecision.InsufficientCoins(80, 10).isAllowed)
        assertFalse(ContinueDecision.ReserveLocked(80, 100, 5).isAllowed)
        assertTrue(ContinueDecision.Allowed(80, 5, 20).isAllowed)
    }

    /**
     * KILL SWITCH — KISMEN TEST EDILEBILIR. `CONTINUE_ENABLED` bir `const val`; testten
     * cevrilemez. Bu yuzden `Disabled` dali burada YALNIZCA bayragin kendi degerine gore
     * dogrulanir (bayrak acikken Disabled ASLA donmez, kapaliyken HER ZAMAN doner). Kapali
     * dalin gercek davranisi ancak bayrak kaynakta cevrilip bu test yeniden kosulursa
     * denenir; bu dosya o kosumu kanitlamaz (UNTESTED: bayrak kapali yol).
     */
    @Test
    fun theKillSwitchBranchIsConsistentWithTheFlag() {
        val decision = continueAllowed(replayWallet(4, 10_000), 4, 0)
        assertEquals(
            "Disabled <=> CONTINUE_ENABLED == false",
            !EconomyConfig.CONTINUE_ENABLED,
            decision == ContinueDecision.Disabled,
        )
    }
}

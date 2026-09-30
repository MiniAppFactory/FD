package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CampaignProgressImpl.undoBooster` — MOTOR REDDETTIGINDE ODEMENIN GERI ALINMASI.
 *
 * `BoosterRail` bir guclendiriciyi aktive eder (coin duser), motor uygulamayi REDDEDERSE
 * (savas durumu arada degisti) `undoBooster` cagrilir; aksi halde oyuncu karsiliksiz odeme
 * yapmis olurdu. Kilitlenen sozlesme:
 *
 *  1. Cuzdan AYNEN geri gelir: `coins` VE `lifetimeSpent` (ve gelir sayaci `lifetimeEarned`
 *     DEGISMEZ — iade `credited()` ile yapilsaydi gelir sisirdi ve harcama sayaci kalirdi).
 *  2. `BoosterState` (kullanim sayaci, bekleme, kayitli tamir) aktivasyondan ONCEKI haline doner;
 *     ayni tipin onceki kullanimi da dahil.
 *  3. Yalnizca EN SON aktivasyon geri alinabilir; eski/yabanci/tekrarlanan cagri etkisiz
 *     (`false`) ve hicbir seyi degistirmez (hizli cift dokunus cift iade yapmaz).
 *  4. Geri alindiktan sonra ayni guclendirici tekrar aktive edilebilir ve TAM BIR KEZ tahsil edilir.
 *
 * **SAF JUnit**: `InMemoryKeyValueStore` + sabit saat; Robolectric yok.
 */
class BoosterUndoTest {

    private class FixedClock : ClockProvider {
        override fun sample(): ClockSample = ClockSample(
            epochDay = 20_500L,
            wallClockMs = 20_500L * EconomyConfig.MS_PER_DAY,
            elapsedRealtimeMs = 1_000L,
            bootId = 1L,
        )
    }

    private val level = 10
    private val airCooldown = EconomyConfig.AIR_SUPPORT_COOLDOWN_MS
    private val repairCooldown = EconomyConfig.BASE_REPAIR_COOLDOWN_MS

    /** Tum bolumler acik (rezerv 0); sayaclar SIFIRDAN FARKLI ki geri yukleme anlamli olsun. */
    private val startWallet = PlayerWallet(
        coins = 5_000,
        unlockedLevels = (1..EconomyConfig.CAMPAIGN_LEVELS).toSet(),
        clearedLevels = (1..9).toSet(),
        bestStars = (1..9).associateWith { 2 },
        lifetimeEarned = 7_000,
        lifetimeSpent = 1_234,
    )

    private class Fixture(wallet: PlayerWallet, level: Int) {
        val save = SaveManager(InMemoryKeyValueStore()).also { it.saveWallet(wallet) }
        val progress = CampaignProgressImpl(save, FixedClock())

        init {
            progress.beginBattle(level)
        }

        /** Diskteki (kalici) cuzdan — Compose state'i degil, gercek kayit. */
        val wallet: PlayerWallet get() = save.loadWallet()
        val state: BoosterState get() = progress.boosterState!!
    }

    private fun fixture() = Fixture(startWallet, level)

    private fun Fixture.air(nowMs: Long = 0L) =
        progress.activateBooster(BoosterType.AIR_SUPPORT, enemiesOnField = 5, nowMs = nowMs)

    private fun Fixture.repair(nowMs: Long = 0L) =
        progress.activateBooster(BoosterType.BASE_REPAIR, baseHealth = 5, nowMs = nowMs)

    private fun Fixture.emergency() =
        progress.activateBooster(BoosterType.EMERGENCY_SUPPLY)

    // =================================================================================
    // 1. Cuzdan
    // =================================================================================

    @Test
    fun undoRestoresCoinsAndLifetimeSpentExactlyAndNeverTouchesLifetimeEarned() {
        val f = fixture()
        val before = f.wallet
        assertEquals(5_000, before.coins)
        assertEquals(1_234, before.lifetimeSpent)
        assertEquals(7_000, before.lifetimeEarned)

        val act = f.air()
        val price = boosterPrice(BoosterType.AIR_SUPPORT, level)
        assertTrue(act.applied)
        assertEquals("aktivasyon tahsil etmedi (test bos olurdu)", 5_000 - price, f.progress.coins)
        assertEquals(1_234 + price, f.wallet.lifetimeSpent)

        assertTrue(f.progress.undoBooster(act))

        assertEquals("coin", 5_000, f.progress.coins)
        assertEquals("kalici coin", 5_000, f.wallet.coins)
        assertEquals("lifetimeSpent geri gelmedi", 1_234, f.wallet.lifetimeSpent)
        assertEquals(
            "lifetimeEarned degisti — iade credited() ile yapilmis (gelir sisti)",
            7_000, f.wallet.lifetimeEarned,
        )
        assertEquals("cuzdanin tamami aktivasyondan onceki haline donmeli", before, f.wallet)
    }

    @Test
    fun undoWorksForEveryBoosterAndRestoresTheWalletEachTime() {
        listOf<Pair<BoosterType, (Fixture) -> BoosterActivation>>(
            BoosterType.EMERGENCY_SUPPLY to { it.emergency() },
            BoosterType.AIR_SUPPORT to { it.air() },
            BoosterType.BASE_REPAIR to { it.repair() },
        ).forEach { (type, activate) ->
            val f = fixture()
            val before = f.wallet
            val act = activate(f)
            assertTrue("$type", act.applied)
            assertTrue("$type: coin dusmedi", f.progress.coins < before.coins)

            assertTrue("$type", f.progress.undoBooster(act))
            assertEquals("$type cuzdan", before, f.wallet)
            assertEquals("$type coin", before.coins, f.progress.coins)
            assertEquals("$type durum", BoosterState.startBattle(level), f.state)
        }
    }

    @Test
    fun undoIsPersistedNotOnlyInMemory() {
        val f = fixture()
        val act = f.emergency()
        assertTrue(f.progress.undoBooster(act))
        // Ayni kayittan yeni bir CampaignProgressImpl kur: iade diskte de gorunmeli.
        val reopened = CampaignProgressImpl(f.save, FixedClock())
        assertEquals(5_000, reopened.coins)
        assertEquals(1_234, f.save.loadWallet().lifetimeSpent)
    }

    // =================================================================================
    // 2. BoosterState (kullanim, bekleme, kayitli tamir)
    // =================================================================================

    @Test
    fun undoOfAFreshBoosterRestoresTheEmptyBattleState() {
        val f = fixture()
        val fresh = f.state
        assertEquals(BoosterState.startBattle(level), fresh)
        val act = f.air(nowMs = 500L)
        assertNotEquals(fresh, f.state)
        assertTrue(f.progress.undoBooster(act))
        assertEquals(fresh, f.state)
        assertEquals(0, f.state.usesOf(BoosterType.AIR_SUPPORT))
        assertTrue("bekleme geri alinmadi", f.state.lastUseMs.isEmpty())
    }

    /** Ayni tipin ONCEKI kullanimi korunur: yalnizca son aktivasyonun etkisi silinir. */
    @Test
    fun undoRestoresThePreviousUseOfTheSameTypeIncludingItsCooldownStamp() {
        val f = fixture()
        val first = f.air(nowMs = 0L)
        assertTrue(first.applied)
        val afterFirst = f.state
        assertEquals(1, afterFirst.usesOf(BoosterType.AIR_SUPPORT))
        assertEquals(0L, afterFirst.lastUseMs[BoosterType.AIR_SUPPORT])
        val walletAfterFirst = f.wallet

        val second = f.air(nowMs = airCooldown)
        assertTrue(second.applied)
        assertEquals(2, f.state.usesOf(BoosterType.AIR_SUPPORT))
        assertEquals(airCooldown, f.state.lastUseMs[BoosterType.AIR_SUPPORT])

        assertTrue(f.progress.undoBooster(second))
        assertEquals("durum 1. kullanim sonrasina donmeli", afterFirst, f.state)
        assertEquals(1, f.state.usesOf(BoosterType.AIR_SUPPORT))
        assertEquals("bekleme damgasi 1. kullanimin damgasi olmali", 0L, f.state.lastUseMs[BoosterType.AIR_SUPPORT])
        assertEquals("cuzdan 1. kullanim sonrasina donmeli", walletAfterFirst, f.wallet)
    }

    @Test
    fun undoOfTheSecondRepairRestoresTheRecordedRepairedHealth() {
        val f = fixture()
        val first = f.repair(nowMs = 0L)
        assertEquals(4, first.healthRestored)
        assertEquals(4, f.state.repairedHealth)
        val afterFirst = f.state

        val second = f.repair(nowMs = repairCooldown)
        assertEquals(7, second.healthRestored)
        assertEquals(11, f.state.repairedHealth)

        assertTrue(f.progress.undoBooster(second))
        assertEquals("kayitli tamir 11'den 4'e donmeli (yildiz notrlugu)", 4, f.state.repairedHealth)
        assertEquals(afterFirst, f.state)
    }

    // =================================================================================
    // 3. Yalnizca EN SON aktivasyon; eski / tekrarlanan / yabanci cagri etkisiz
    // =================================================================================

    @Test
    fun undoOfAStaleActivationIsANoOp() {
        val f = fixture()
        val first = f.air(nowMs = 0L)
        val second = f.air(nowMs = airCooldown)
        val stateAfterBoth = f.state
        val walletAfterBoth = f.wallet

        assertFalse("eski aktivasyon geri alindi", f.progress.undoBooster(first))
        assertEquals(stateAfterBoth, f.state)
        assertEquals(walletAfterBoth, f.wallet)

        // Sonuncusu hala geri alinabilir ve yalnizca onu siler.
        assertTrue(f.progress.undoBooster(second))
        assertEquals(1, f.state.usesOf(BoosterType.AIR_SUPPORT))
    }

    @Test
    fun undoAfterANewActivationOfAnotherBoosterIsANoOp() {
        val f = fixture()
        val es = f.emergency()
        val air = f.air()
        val stateAfter = f.state
        val walletAfter = f.wallet

        assertFalse("yeni aktivasyondan sonra eskisi geri alindi", f.progress.undoBooster(es))
        assertEquals(stateAfter, f.state)
        assertEquals(walletAfter, f.wallet)
        assertTrue(f.progress.undoBooster(air))
    }

    @Test
    fun aSecondUndoOfTheSameActivationIsANoOpSoRapidTapsCannotRefundTwice() {
        val f = fixture()
        val act = f.repair()
        assertTrue(f.progress.undoBooster(act))
        val stateAfter = f.state
        val walletAfter = f.wallet

        repeat(10) {
            assertFalse("tekrar #$it iade etti", f.progress.undoBooster(act))
        }
        assertEquals(stateAfter, f.state)
        assertEquals("cift iade: cuzdan degisti", walletAfter, f.wallet)
        assertEquals(startWallet, f.wallet)
    }

    @Test
    fun undoOfARejectedActivationChangesNothing() {
        val f = Fixture(startWallet.copy(coins = 0), level)
        val rejected = f.air()
        assertFalse(rejected.applied)
        val before = f.wallet
        val stateBefore = f.state
        assertFalse(f.progress.undoBooster(rejected))
        assertEquals(before, f.wallet)
        assertSame(stateBefore, f.state)
    }

    @Test
    fun undoIsClearedWhenTheBattleEndsOrARetryBegins() {
        val ended = fixture()
        val a = ended.emergency()
        ended.progress.endBattle()
        assertNull("savas bitti, durum atilmali", ended.progress.boosterState)
        val walletAfterEnd = ended.wallet
        assertFalse("bitmis savasin aktivasyonu geri alindi", ended.progress.undoBooster(a))
        assertEquals(walletAfterEnd, ended.wallet)

        val retried = fixture()
        val b = retried.emergency()
        val walletAfterBuy = retried.wallet
        retried.progress.beginBattle(level)   // TEKRAR DENE = yeni savas, yeni sayaclar
        assertFalse("yeni savasta eski aktivasyon geri alindi", retried.progress.undoBooster(b))
        assertEquals("iade edilmemeli: satin alim gecmis savasta gerceklesti", walletAfterBuy, retried.wallet)
        assertEquals(BoosterState.startBattle(level), retried.state)
    }

    // =================================================================================
    // 4. Geri alindiktan sonra ayni hak tekrar kullanilabilir ve TAM BIR KEZ tahsil edilir
    // =================================================================================

    @Test
    fun afterUndoTheSameBoosterCanBeActivatedAgainAndIsChargedOnce() {
        listOf<Triple<BoosterType, (Fixture) -> BoosterActivation, Long>>(
            Triple(BoosterType.EMERGENCY_SUPPLY, { it.emergency() }, 0L),
            Triple(BoosterType.AIR_SUPPORT, { it.air(nowMs = 1L) }, 1L),
            Triple(BoosterType.BASE_REPAIR, { it.repair(nowMs = 1L) }, 1L),
        ).forEach { (type, activate, _) ->
            val f = fixture()
            val price1 = boosterPrice(type, level, 1)

            val first = activate(f)
            assertTrue("$type", first.applied)
            assertTrue(f.progress.undoBooster(first))

            // Hak, bekleme ve fiyat basamagi (1. kullanim fiyati!) geri gelmis olmali.
            val again = activate(f)
            assertTrue("$type: geri alma sonrasi tekrar aktive edilemedi: ${again.decision}", again.applied)
            assertEquals("$type: 1. kullanim fiyati (2. degil)", price1, again.coinsSpent)
            assertEquals("$type: tam bir kez tahsil", 5_000 - price1, f.progress.coins)
            assertEquals(1_234 + price1, f.wallet.lifetimeSpent)
            assertEquals("$type: kullanim sayaci 1 olmali", 1, f.state.usesOf(type))
        }
    }

    @Test
    fun afterUndoThePaidLimitStillHoldsForTheRightsThatWereReallyUsed() {
        val f = fixture()
        val act = f.emergency()
        assertTrue(f.progress.undoBooster(act))
        assertTrue(f.emergency().applied)                                // hak yeniden alindi
        val denied = f.emergency()                                       // ... ve YALNIZCA bir kez
        assertEquals(BoosterDecision.PaidLimitReached, denied.decision)
        assertEquals(5_000 - boosterPrice(BoosterType.EMERGENCY_SUPPLY, level), f.progress.coins)
    }

    @Test
    fun undoClearsTheCooldownSoTheSameBoosterIsImmediatelyAvailableAgain() {
        val f = fixture()
        val act = f.air(nowMs = 10_000L)
        // Aktivasyon aninda bekleme aktif: 1 sn sonra reddedilir.
        assertTrue(f.progress.boosterDecision(BoosterType.AIR_SUPPORT, enemiesOnField = 5, nowMs = 11_000L) is BoosterDecision.Cooldown)
        assertTrue(f.progress.undoBooster(act))
        assertTrue(
            "geri alinan aktivasyonun beklemesi kaldi",
            f.progress.boosterDecision(BoosterType.AIR_SUPPORT, enemiesOnField = 5, nowMs = 11_000L).isAllowed,
        )
    }
}

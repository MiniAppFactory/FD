package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DEBUG PANELI SENARYOLARI — GERCEK islemci hattindan.
 *
 * `FakeBilling` her senaryoda bir [PurchaseInfo] uretir; `BillingManager.debugSubmit` onu
 * [FakeBilling.newDebugFulfiller] ile kurulan HATTA verir. Burada AYNI hat, gercek `SaveManager`
 * defteriyle kurulur — yani panelin calistirdigi sey test edilir, paralel bir sahte yol degil.
 */
class FakeBillingScenarioTest {

    private lateinit var save: SaveManager
    private lateinit var fulfiller: PurchaseFulfiller

    @Before
    fun setUp() {
        FakeBilling.resetForTest()
        save = SaveManager(InMemoryKeyValueStore())
        fulfiller = FakeBilling.newDebugFulfiller({ save.fulfillPurchase(it) })
    }

    @After
    fun tearDown() = FakeBilling.resetForTest()

    private fun run(scenario: FakeBilling.Scenario): FulfillmentOutcome? =
        FakeBilling.infoFor(scenario)?.let { fulfiller.process(it) }

    private val balance get() = save.loadWallet().coins

    @Test
    fun theDebugPanelIsOnlyEnabledInDebugBuilds() {
        // JVM birim testi debug varyantinda kosar; release'te R8 bunu `false`a katlar.
        assertTrue(FakeBilling.enabled)
    }

    @Test
    fun buyGrantsTheSmallestPackOnceAndConsumesIt() {
        val base = balance
        val out = run(FakeBilling.Scenario.BUY)
        assertEquals(FulfillmentOutcome.Granted(StoreCatalog.PACK_RECRUIT, 1_000, base + 1_000, legacy = false), out)
        assertEquals(base + 1_000, balance)
        assertEquals(1, FakeBilling.consumedTokens.size)
    }

    @Test
    fun everyBuyUsesAFreshToken() {
        val a = FakeBilling.infoFor(FakeBilling.Scenario.BUY)!!.purchaseToken
        val b = FakeBilling.infoFor(FakeBilling.Scenario.BUY)!!.purchaseToken
        assertNotEquals(a, b)
        assertTrue(a.startsWith("debug-"))
    }

    @Test
    fun pendingThenCompleteGrantsNothingFirstAndExactlyOnceAfter() {
        val base = balance
        assertTrue(run(FakeBilling.Scenario.PENDING) is FulfillmentOutcome.Pending)
        assertEquals("PENDING coin vermez", base, balance)
        assertEquals("PENDING tuketilmez", emptyList<String>(), FakeBilling.consumedTokens)

        assertTrue(run(FakeBilling.Scenario.COMPLETE_PENDING) is FulfillmentOutcome.Granted)
        assertEquals(base + 1_000, balance)

        // Tamamlanan bekleyen yok artik: ikinci COMPLETE hicbir sey uretmez.
        assertNull(FakeBilling.infoFor(FakeBilling.Scenario.COMPLETE_PENDING))
        assertEquals(base + 1_000, balance)
    }

    @Test
    fun completeWithoutAPendingPurchaseProducesNothing() {
        assertNull(FakeBilling.infoFor(FakeBilling.Scenario.COMPLETE_PENDING))
    }

    @Test
    fun replayOfTheSameTokenNeverChangesTheBalance() {
        run(FakeBilling.Scenario.BUY)
        val afterBuy = balance
        val again = run(FakeBilling.Scenario.REPLAY_LAST)
        assertTrue("$again", again is FulfillmentOutcome.AlreadyCredited)
        assertEquals(afterBuy, balance)
    }

    @Test
    fun replayWithNothingBeforeItProducesNothing() {
        assertNull(FakeBilling.infoFor(FakeBilling.Scenario.REPLAY_LAST))
    }

    @Test
    fun legacyCoins1200IsPaidTheOldAmount() {
        val base = balance
        val out = run(FakeBilling.Scenario.LEGACY_COINS_1200)
        assertEquals(FulfillmentOutcome.Granted("coins_1200", 1_200, base + 1_200, legacy = true), out)
        assertEquals(base + 1_200, balance)
    }

    @Test
    fun quantityTwoDoublesTheAmountAndTheLedgerKeepsTheProduct() {
        val base = balance
        val info = FakeBilling.infoFor(FakeBilling.Scenario.QUANTITY_2)!!
        assertEquals(2, info.quantity)
        fulfiller.process(info)
        assertEquals(base + 2_000, balance)
        assertEquals(2_000, save.purchaseRecordOf(info.purchaseToken)?.coins)
    }

    @Test
    fun anArmedPersistFailureBlocksTheGrantAndTheConsumeThenTheReplayGrantsOnce() {
        val base = balance
        FakeBilling.armPersistFailure()

        val failed = run(FakeBilling.Scenario.BUY)
        assertTrue("$failed", failed is FulfillmentOutcome.PersistFailed)
        assertEquals("yazim basarisizken coin yok", base, balance)
        assertEquals("yazim basarisizken TUKETME yok", emptyList<String>(), FakeBilling.consumedTokens)

        // Silah TEK atimlik: ayni satin almayi yeniden gonder -> gercek deftere gider, bir kez odenir.
        val retry = run(FakeBilling.Scenario.REPLAY_LAST)
        assertTrue("$retry", retry is FulfillmentOutcome.Granted)
        assertEquals(base + 1_000, balance)
        assertEquals(1, FakeBilling.consumedTokens.size)
    }

    @Test
    fun scenariosWithoutAPurchaseProduceNoInfo() {
        assertNull(FakeBilling.infoFor(FakeBilling.Scenario.CANCEL))
        assertNull(FakeBilling.infoFor(FakeBilling.Scenario.ARM_PERSIST_FAILURE))
    }

    @Test
    fun coinsOfIsCompatibleWithTheOldDebugPanelAndKnowsLegacyAmounts() {
        assertEquals(1_000, FakeBilling.coinsOf(StoreCatalog.PACK_RECRUIT))
        assertEquals(1_200, FakeBilling.coinsOf("coins_1200"))
        assertEquals(0, FakeBilling.coinsOf(StoreCatalog.PRODUCT_REMOVE_ADS))
        assertEquals(0, FakeBilling.coinsOf("unknown"))
        val token = FakeBilling.newToken(StoreCatalog.PACK_RECRUIT)
        FakeBilling.rememberToken(token)
        assertEquals(token, FakeBilling.lastToken)
        assertNotNull(token)
    }
}

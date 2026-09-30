package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * SATIN ALMA KARAR MATRISI + UYGULAYICI (JVM, Android/Play YOK)
 * =============================================================================
 *
 * Iki katman ayri sinanir:
 *  1. [PurchasePolicy.decide] — SAF: girdi (urun id'leri, jeton, durum, adet, onay) -> eylem.
 *  2. [PurchaseFulfiller] — eylemi UYGULAR: defteri sorar, YALNIZ dayanikli sonuc sonrasi tuketir.
 *     Defter/Play sahte portlarla (ve gercek `SaveManager` defteriyle) enjekte edilir.
 *
 * Bu matris KronDx `BillingFulfillmentTest`inin FD'ye uyarlanmisi + FD'ye ozgu
 * durumlar (eski id'ler, eski `remove_ads` onayi, adet, kontrollu aritmetik).
 */
class PurchaseFulfillmentTest {

    private fun info(
        id: String,
        token: String = "tok",
        state: PurchaseState = PurchaseState.PURCHASED,
        quantity: Int = 1,
        acknowledged: Boolean = false,
    ) = PurchaseInfo(listOf(id), token, state, quantity, acknowledged)

    private class ScriptedPort(var next: GrantResult) : PurchaseGrantPort {
        val requests = mutableListOf<PurchaseGrantRequest>()
        var throwOnGrant = false
        override fun grant(request: PurchaseGrantRequest): GrantResult {
            requests += request
            if (throwOnGrant) throw IllegalStateException("boom")
            return next
        }
    }

    /** Cagri sirasini tek listede tutar: "grant:<jeton>" / "consume:<jeton>" / "ack:<jeton>". */
    private class Journal {
        val events: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
    }

    private class RecordingFinisher(
        private val journal: Journal = Journal(),
        var consumeResult: ConsumeResult = ConsumeResult.CONSUMED,
        var deferCallbacks: Boolean = false,
    ) : PurchaseFinisher {
        // Es zamanli test tuketmeyi birden cok is parcaciginden cagirir.
        val consumed: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
        val acknowledged: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf<String>())
        val deferred = mutableListOf<() -> Unit>()

        override fun consume(purchaseToken: String, onResult: (ConsumeResult) -> Unit) {
            consumed += purchaseToken
            journal.events += "consume:$purchaseToken"
            if (deferCallbacks) deferred += { onResult(consumeResult) } else onResult(consumeResult)
        }

        override fun acknowledge(purchaseToken: String, onResult: (AcknowledgeResult) -> Unit) {
            acknowledged += purchaseToken
            journal.events += "ack:$purchaseToken"
            onResult(AcknowledgeResult.ACKNOWLEDGED)
        }
    }

    // =========================================================================
    // 1. KARAR (saf)
    // =========================================================================

    @Test
    fun everySalePackIsGrantedItsExactCatalogAmountAndConsumed() {
        StoreCatalog.coinPacks.forEach { pack ->
            val action = PurchasePolicy.decide(info(pack.id, token = "t-${pack.id}"))
            assertEquals(
                FulfillmentAction.GrantAndConsume(pack.id, "t-${pack.id}", pack.coins, quantity = 1, legacy = false),
                action,
            )
        }
    }

    @Test
    fun legacyCoinIdsAreGrantedTheirOriginalAmountsNotTheNewOnes() {
        // Bilincli SABIT: eski oyuncu bu miktarlara para odedi.
        val original = mapOf(
            "coins_1200" to 1_200,
            "coins_4000" to 4_000,
            "coins_7400" to 7_400,
            "coins_16000" to 16_000,
            "coins_25000" to 25_000,
        )
        original.forEach { (id, amount) ->
            val action = PurchasePolicy.decide(info(id, token = "legacy-$id"))
            assertEquals(
                "$id eski miktarla odenmeli",
                FulfillmentAction.GrantAndConsume(id, "legacy-$id", amount, quantity = 1, legacy = true),
                action,
            )
        }
        // Eski 0,99$ paketi 1.200'dur; yeni 0,99$ paketi 1.000 — ikisi ASLA karismaz.
        val old = PurchasePolicy.decide(info("coins_1200")) as FulfillmentAction.GrantAndConsume
        val new = PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT)) as FulfillmentAction.GrantAndConsume
        assertEquals(1_200, old.coins)
        assertEquals(1_000, new.coins)
    }

    @Test
    fun legacyRemoveAdsIsAcknowledgeOnlyWithoutCoinsOrConsume() {
        assertEquals(
            FulfillmentAction.AcknowledgeOnly("remove_ads", "t", alreadyAcknowledged = false),
            PurchasePolicy.decide(info("remove_ads", token = "t", acknowledged = false)),
        )
        assertEquals(
            FulfillmentAction.AcknowledgeOnly("remove_ads", "t", alreadyAcknowledged = true),
            PurchasePolicy.decide(info("remove_ads", token = "t", acknowledged = true)),
        )
    }

    @Test
    fun pendingNeverGrantsConsumesOrAcknowledgesForAnyKnownProduct() {
        val all = StoreCatalog.allProductIds + StoreCatalog.legacyCoinProductIds + StoreCatalog.PRODUCT_REMOVE_ADS
        all.forEach { id ->
            assertEquals(
                "$id PENDING iken hicbir sey yapilmamali",
                FulfillmentAction.LeavePending(id),
                PurchasePolicy.decide(info(id, state = PurchaseState.PENDING)),
            )
        }
        // Bos jetonlu PENDING bile yalnizca PENDING'dir (Reject degil).
        assertEquals(
            FulfillmentAction.LeavePending(StoreCatalog.PACK_RECRUIT),
            PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, token = "", state = PurchaseState.PENDING)),
        )
    }

    @Test
    fun unspecifiedStateIsLeftAloneWithAReason() {
        val action = PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, state = PurchaseState.UNSPECIFIED))
        assertTrue(action is FulfillmentAction.LeaveUnknown)
        assertTrue((action as FulfillmentAction.LeaveUnknown).reason.contains("UNSPECIFIED"))
    }

    @Test
    fun unknownEmptyOrMultipleProductIdsAreLeftAloneWithAReason() {
        val cases = listOf(
            PurchaseInfo(listOf("nope"), "t", PurchaseState.PURCHASED, 1, false),
            PurchaseInfo(emptyList(), "t", PurchaseState.PURCHASED, 1, false),
            PurchaseInfo(listOf(StoreCatalog.PACK_RECRUIT, "nope"), "t", PurchaseState.PURCHASED, 1, false),
            PurchaseInfo(listOf(StoreCatalog.PACK_RECRUIT, StoreCatalog.PACK_SERGEANT), "t", PurchaseState.PURCHASED, 1, false),
            PurchaseInfo(listOf(StoreCatalog.PACK_RECRUIT, StoreCatalog.PACK_RECRUIT), "t", PurchaseState.PURCHASED, 1, false),
            PurchaseInfo(listOf("coins_1200", "remove_ads"), "t", PurchaseState.PURCHASED, 1, false),
        )
        cases.forEach { c ->
            val action = PurchasePolicy.decide(c)
            assertTrue("$c -> $action", action is FulfillmentAction.LeaveUnknown)
            assertTrue("neden bos olmamali", (action as FulfillmentAction.LeaveUnknown).reason.isNotBlank())
        }
    }

    @Test
    fun blankTokenOnAPurchasedCoinPackIsRejected() {
        listOf("", "   ").forEach { blank ->
            assertTrue(
                PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, token = blank)) is FulfillmentAction.Reject
            )
            assertTrue(PurchasePolicy.decide(info("coins_1200", token = blank)) is FulfillmentAction.Reject)
            assertTrue(PurchasePolicy.decide(info("remove_ads", token = blank)) is FulfillmentAction.Reject)
        }
    }

    @Test
    fun quantityMultipliesTheCoinsWithCheckedArithmetic() {
        listOf(1, 2, 3, 10).forEach { q ->
            val a = PurchasePolicy.decide(info(StoreCatalog.PACK_GENERAL, quantity = q)) as FulfillmentAction.GrantAndConsume
            assertEquals(7_000 * q, a.coins)
            assertEquals(q, a.quantity)
        }
        val legacy = PurchasePolicy.decide(info("coins_25000", quantity = 3)) as FulfillmentAction.GrantAndConsume
        assertEquals(75_000, legacy.coins)
    }

    @Test
    fun quantityBelowOneIsRejected() {
        listOf(0, -1, Int.MIN_VALUE).forEach { q ->
            assertTrue(
                "adet $q reddedilmeli",
                PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, quantity = q)) is FulfillmentAction.Reject,
            )
        }
    }

    @Test
    fun quantityOverflowIsRejectedNotWrapped() {
        // 1000 * 2_200_000 > Int.MAX_VALUE. Kontrolsuz Int carpimi negatif/yanlis miktar uretirdi.
        val action = PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, quantity = 3_000_000))
        assertTrue("tasan carpim reddedilmeli: $action", action is FulfillmentAction.Reject)
        assertTrue(
            PurchasePolicy.decide(info(StoreCatalog.PACK_GENERAL, quantity = Int.MAX_VALUE)) is FulfillmentAction.Reject
        )
        // Sinirin hemen altindaki adet hala calisir (sinir siki).
        val ok = PurchasePolicy.decide(info(StoreCatalog.PACK_RECRUIT, quantity = 2_000_000)) as FulfillmentAction.GrantAndConsume
        assertEquals(2_000_000_000, ok.coins)
    }

    // =========================================================================
    // 2. UYGULAYICI — sahte portlarla
    // =========================================================================

    @Test
    fun consumeHappensOnlyAfterTheGrantAndOnlyForGrantedOrAlreadyGranted() {
        val journal = Journal()
        val finisher = RecordingFinisher(journal)
        val port = object : PurchaseGrantPort {
            var next: GrantResult = GrantResult.Granted(1_000, 1_000)
            override fun grant(request: PurchaseGrantRequest): GrantResult {
                journal.events += "grant:${request.purchaseToken}"
                return next
            }
        }
        val fulfiller = PurchaseFulfiller(port, finisher)

        val granted = fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "a"))
        assertEquals(FulfillmentOutcome.Granted(StoreCatalog.PACK_RECRUIT, 1_000, 1_000, legacy = false), granted)
        assertEquals("kredi TUKETMEDEN once", listOf("grant:a", "consume:a"), journal.events)

        journal.events.clear()
        port.next = GrantResult.AlreadyGranted(LedgerRecord(StoreCatalog.PACK_RECRUIT, 1_000, 1L))
        assertEquals(
            FulfillmentOutcome.AlreadyCredited(StoreCatalog.PACK_RECRUIT),
            fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "b")),
        )
        assertEquals(listOf("grant:b", "consume:b"), journal.events)
    }

    @Test
    fun rejectedAndPersistFailedNeverConsume() {
        val finisher = RecordingFinisher()
        val port = ScriptedPort(GrantResult.Rejected(RejectReason.WALLET_OVERFLOW))
        val fulfiller = PurchaseFulfiller(port, finisher)

        assertTrue(fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "r")) is FulfillmentOutcome.Rejected)
        port.next = GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH)
        assertTrue(fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "r2")) is FulfillmentOutcome.Rejected)
        port.next = GrantResult.PersistFailed
        assertTrue(fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "p")) is FulfillmentOutcome.PersistFailed)

        assertEquals("reddedilen/yazilamayan satin alma TUKETILMEMELI", emptyList<String>(), finisher.consumed)
    }

    @Test
    fun aThrowingGrantCallbackLeavesThePurchaseUnconsumed() {
        val finisher = RecordingFinisher()
        val port = ScriptedPort(GrantResult.Granted(1_000, 1_000)).also { it.throwOnGrant = true }
        val logs = mutableListOf<String>()
        val outcome = PurchaseFulfiller(port, finisher, log = { logs += it })
            .process(info(StoreCatalog.PACK_RECRUIT, "x"))

        assertTrue("istisna disari SIZMAMALI, sonuc olmali: $outcome", outcome is FulfillmentOutcome.PersistFailed)
        assertEquals(emptyList<String>(), finisher.consumed)
        assertTrue("istisna loglanmali", logs.any { it.contains("threw") })
    }

    @Test
    fun unknownProductsNeverReachTheLedgerOrPlayButAreLogged() {
        val finisher = RecordingFinisher()
        val port = ScriptedPort(GrantResult.Granted(1, 1))
        val logs = mutableListOf<String>()
        val outcome = PurchaseFulfiller(port, finisher, log = { logs += it })
            .process(PurchaseInfo(listOf("mystery_pack"), "t", PurchaseState.PURCHASED, 1, false))

        assertTrue(outcome is FulfillmentOutcome.Unknown)
        assertEquals("defter cagrilmamali", 0, port.requests.size)
        assertEquals(emptyList<String>(), finisher.consumed)
        assertEquals(emptyList<String>(), finisher.acknowledged)
        assertTrue("neden loglanmali", logs.any { it.contains("mystery_pack") })
    }

    @Test
    fun legacyRemoveAdsIsAcknowledgedButNeverGrantedOrConsumed() {
        val finisher = RecordingFinisher()
        val port = ScriptedPort(GrantResult.Granted(1, 1))
        val fulfiller = PurchaseFulfiller(port, finisher)

        assertEquals(
            FulfillmentOutcome.LegacyRemoveAds(acknowledgeRequested = true),
            fulfiller.process(info("remove_ads", "ra1", acknowledged = false)),
        )
        assertEquals(listOf("ra1"), finisher.acknowledged)

        // Zaten onayli: Play'e ikinci onay GONDERILMEZ.
        assertEquals(
            FulfillmentOutcome.LegacyRemoveAds(acknowledgeRequested = false),
            fulfiller.process(info("remove_ads", "ra2", acknowledged = true)),
        )
        assertEquals(listOf("ra1"), finisher.acknowledged)

        assertEquals("remove_ads defter/coin ile ilgilenmez", 0, port.requests.size)
        assertEquals("remove_ads TUKETILMEZ", emptyList<String>(), finisher.consumed)
    }

    @Test
    fun pendingLegacyRemoveAdsIsNotAcknowledged() {
        val finisher = RecordingFinisher()
        val outcome = PurchaseFulfiller(ScriptedPort(GrantResult.Granted(1, 1)), finisher)
            .process(info("remove_ads", "ra", state = PurchaseState.PENDING))
        assertEquals(FulfillmentOutcome.Pending("remove_ads"), outcome)
        assertEquals(emptyList<String>(), finisher.acknowledged)
    }

    @Test
    fun consumeItemNotOwnedIsAlreadyConsumedAndHarmless() {
        val finisher = RecordingFinisher(consumeResult = ConsumeResult.ALREADY_CONSUMED)
        val results = mutableListOf<Pair<String, ConsumeResult>>()
        val logs = mutableListOf<String>()
        val outcome = PurchaseFulfiller(
            ScriptedPort(GrantResult.AlreadyGranted(null)), finisher,
            log = { logs += it }, onConsumeResult = { t, r -> results += t to r },
        ).process(info("coins_1200", "old"))

        assertEquals(FulfillmentOutcome.AlreadyCredited("coins_1200"), outcome)
        assertEquals(listOf("old" to ConsumeResult.ALREADY_CONSUMED), results)
        assertTrue("ITEM_NOT_OWNED loglanmali", logs.any { it.contains("ITEM_NOT_OWNED") })
    }

    @Test
    fun aFailedConsumeKeepsTheGrantDurableAndTheNextReplayConsumesAgain() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val finisher = RecordingFinisher(consumeResult = ConsumeResult.FAILED)
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        assertTrue(fulfiller.process(info(StoreCatalog.PACK_SERGEANT, "c1")) is FulfillmentOutcome.Granted)
        assertEquals(base + 2_200, save.loadWallet().coins)

        // Sonraki yenileme ayni jetonu getirir: kredi YOK, tuketme tekrar denenir.
        finisher.consumeResult = ConsumeResult.CONSUMED
        assertEquals(
            FulfillmentOutcome.AlreadyCredited(StoreCatalog.PACK_SERGEANT),
            fulfiller.process(info(StoreCatalog.PACK_SERGEANT, "c1")),
        )
        assertEquals(base + 2_200, save.loadWallet().coins)
        assertEquals(listOf("c1", "c1"), finisher.consumed)
    }

    @Test
    fun aConsumeAlreadyInFlightIsNotSentTwice() {
        val save = SaveManager(InMemoryKeyValueStore())
        val finisher = RecordingFinisher(deferCallbacks = true)
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "f1"))
        fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "f1")) // akis + ON_RESUME ayni anda
        assertEquals("ucustaki tuketme ikinci kez GONDERILMEMELI", listOf("f1"), finisher.consumed)

        finisher.deferred.forEach { it() } // Play yanit verdi
        fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "f1"))
        assertEquals("yanit sonrasi yeniden denenebilir", listOf("f1", "f1"), finisher.consumed)
    }

    // =========================================================================
    // 3. UYGULAYICI + GERCEK DEFTER
    // =========================================================================

    @Test
    fun pendingThenPurchasedGrantsExactlyOnceAndOnlyAfterPurchased() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val finisher = RecordingFinisher()
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        val pending = fulfiller.process(info(StoreCatalog.PACK_CAPTAIN, "pp", state = PurchaseState.PENDING))
        assertEquals(FulfillmentOutcome.Pending(StoreCatalog.PACK_CAPTAIN), pending)
        assertEquals("PENDING coin vermez", base, save.loadWallet().coins)
        assertEquals(emptyList<String>(), finisher.consumed)
        assertEquals(emptyList<String>(), finisher.acknowledged)
        assertFalse(save.isPurchaseTokenGranted("pp"))

        // Odeme tamamlandi: AYNI jeton PURCHASED gelir.
        assertTrue(fulfiller.process(info(StoreCatalog.PACK_CAPTAIN, "pp")) is FulfillmentOutcome.Granted)
        assertEquals(base + 3_600, save.loadWallet().coins)
        assertEquals(listOf("pp"), finisher.consumed)

        // Sonraki yenilemeler tekrar getirir: kredi YOK.
        repeat(3) { fulfiller.process(info(StoreCatalog.PACK_CAPTAIN, "pp")) }
        assertEquals(base + 3_600, save.loadWallet().coins)
    }

    @Test
    fun quantityTwoStoresTheMultipliedAmountAndAReplayComparesEqual() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val finisher = RecordingFinisher()
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        val first = fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "q2", quantity = 2))
        assertEquals(FulfillmentOutcome.Granted(StoreCatalog.PACK_RECRUIT, 2_000, base + 2_000, legacy = false), first)
        assertEquals("defter CARPILMIS miktari saklar", 2_000, save.purchaseRecordOf("q2")?.coins)

        // Ayni jeton ayni adetle: esit -> AlreadyCredited, kredi YOK.
        assertEquals(
            FulfillmentOutcome.AlreadyCredited(StoreCatalog.PACK_RECRUIT),
            fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "q2", quantity = 2)),
        )
        // Ayni jeton FARKLI adetle: defter reddeder ve TUKETILMEZ.
        val consumesBefore = finisher.consumed.size
        assertTrue(fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "q2", quantity = 1)) is FulfillmentOutcome.Rejected)
        assertEquals(consumesBefore, finisher.consumed.size)
        assertEquals(base + 2_000, save.loadWallet().coins)
    }

    @Test
    fun theSameTokenWithADifferentProductIsRejectedAndNeverConsumed() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val finisher = RecordingFinisher()
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        fulfiller.process(info(StoreCatalog.PACK_RECRUIT, "same"))
        val consumesBefore = finisher.consumed.size
        val outcome = fulfiller.process(info(StoreCatalog.PACK_GENERAL, "same"))

        assertTrue("farkli urun reddedilmeli: $outcome", outcome is FulfillmentOutcome.Rejected)
        assertEquals(consumesBefore, finisher.consumed.size)
        assertEquals(base + 1_000, save.loadWallet().coins)
        assertEquals(StoreCatalog.PACK_RECRUIT, save.purchaseRecordOf("same")?.productId)
    }

    @Test
    fun legacyCoinPurchaseIsPaidOutAtTheOldAmountThroughTheRealLedger() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, RecordingFinisher())

        val out = fulfiller.process(info("coins_7400", "L1"))
        assertEquals(FulfillmentOutcome.Granted("coins_7400", 7_400, base + 7_400, legacy = true), out)
        assertEquals(7_400, save.lifetimePurchasedCoins)
        assertEquals("coins_7400", save.purchaseRecordOf("L1")?.productId)
    }

    @Test
    fun aBlankTokenNeverReachesTheLedgerAndIsNeverConsumed() {
        val finisher = RecordingFinisher()
        val port = ScriptedPort(GrantResult.Granted(1, 1))
        val outcome = PurchaseFulfiller(port, finisher).process(info(StoreCatalog.PACK_RECRUIT, token = "  "))
        assertTrue(outcome is FulfillmentOutcome.Rejected)
        assertEquals(0, port.requests.size)
        assertEquals(emptyList<String>(), finisher.consumed)
    }

    @Test
    fun concurrentDuplicateDeliveriesOfOneTokenCreditExactlyOnce() {
        val save = SaveManager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val finisher = RecordingFinisher()
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)

        val threads = 24
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val granted = AtomicInteger()
        val already = AtomicInteger()
        val other = AtomicInteger()
        repeat(threads) {
            pool.submit {
                start.await()
                when (fulfiller.process(info(StoreCatalog.PACK_COLONEL, "dup"))) {
                    is FulfillmentOutcome.Granted -> granted.incrementAndGet()
                    is FulfillmentOutcome.AlreadyCredited -> already.incrementAndGet()
                    else -> other.incrementAndGet()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue("is parcaciklari bitmedi", pool.awaitTermination(30, TimeUnit.SECONDS))

        assertEquals("tam BIR Granted", 1, granted.get())
        assertEquals(threads - 1, already.get())
        assertEquals(0, other.get())
        assertEquals("kredi tam BIR kez", base + 5_200, save.loadWallet().coins)
        assertEquals(5_200, save.lifetimePurchasedCoins)
    }
}

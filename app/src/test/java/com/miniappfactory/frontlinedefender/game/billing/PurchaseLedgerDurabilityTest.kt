package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.game.billing.FailableKeyValueStore.CommitMode
import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.KeyValueStore
import com.miniappfactory.frontlinedefender.game.data.PurchaseGrantLedger
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.data.StoreBatch
import com.miniappfactory.frontlinedefender.game.data.StoreOp
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * SATIN ALMA DEFTERI — DAYANIKLILIK (basarisizlik, yeniden deneme, yeniden baslatma)
 * =============================================================================
 *
 * GERCEK PARA: buradaki her test "odenmis coin cift/hic kredilenir" ya da "tuketilmis ama
 * kaydedilmemis" sonucuna varabilecek bir yolu kapatir.
 *
 * Basarisizlik [FailableKeyValueStore] ile uretilir: iki haritali (bellek + disk) sahte, Android
 * `SharedPreferences`in iki tuzagini kopyalar — (1) basarisiz `commit()` bellegi YINE DE gunceller,
 * (2) ayni degerlerle ikinci `commit()` yeniden yazmadan `true` doner. "Surec yeniden basladi" =
 * [FailableKeyValueStore.restart]: yalnizca DISKten yeni bellek + yeni guard.
 */
class PurchaseLedgerDurabilityTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val K_COINS = "eco_coins"
        const val K_LIFETIME = "iap_lifetime_purchased_coins"
        const val K_SEQ = PurchaseGrantLedger.KEY_COMMIT_SEQ
        const val K_TOKENS = "iap_granted_tokens"
        fun ledgerKey(token: String) = PurchaseGrantLedger.KEY_LEDGER_PREFIX + token
    }

    private fun manager(store: KeyValueStore) = SaveManager(store) { NOW }

    private fun req(token: String, product: String = StoreCatalog.PACK_RECRUIT, coins: Int = 1_000) =
        PurchaseGrantRequest(token, product, coins)

    private fun FailableKeyValueStore.seq(): Long = (disk[K_SEQ] as? Long) ?: 0L

    // =========================================================================
    // 1. Atomiklik + acik sonuc
    // =========================================================================

    @Test
    fun aGrantIsOneAtomicBatchOfWalletLedgerLifetimeAndCounter() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins

        val result = save.fulfillPurchase(req("tok"))

        assertEquals(GrantResult.Granted(1_000, base + 1_000), result)
        assertEquals("TEK commit()", 1, store.commitCalls)
        val keys = store.batches.single().operations.map { it.key }.toSet()
        assertEquals(setOf(K_COINS, K_LIFETIME, ledgerKey("tok"), K_SEQ), keys)
        // Hepsi diskte, hepsi ayni yazimdan.
        assertEquals(base + 1_000, store.disk[K_COINS])
        assertEquals(1_000, store.disk[K_LIFETIME])
        assertEquals("G|${StoreCatalog.PACK_RECRUIT}|1000|$NOW", store.disk[ledgerKey("tok")])
        assertEquals(1L, store.disk[K_SEQ])
        assertEquals(LedgerRecord(StoreCatalog.PACK_RECRUIT, 1_000, NOW), save.purchaseRecordOf("tok"))
    }

    @Test
    fun theResultIsExplicitForEveryOutcome() {
        val save = manager(InMemoryKeyValueStore())
        assertTrue(save.fulfillPurchase(req("a")) is GrantResult.Granted)
        val again = save.fulfillPurchase(req("a"))
        assertEquals(GrantResult.AlreadyGranted(save.purchaseRecordOf("a")), again)
        assertEquals(StoreCatalog.PACK_RECRUIT, (again as GrantResult.AlreadyGranted).record?.productId)
        assertEquals(
            GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH),
            save.fulfillPurchase(req("a", StoreCatalog.PACK_GENERAL, 7_000)),
        )
        assertEquals(GrantResult.Rejected(RejectReason.INVALID_REQUEST), save.fulfillPurchase(req("")))
    }

    // =========================================================================
    // 2. Basarisiz yazim -> yeniden deneme (bellek-mutasyon tuzagi dahil)
    // =========================================================================

    /** Android semantigi: commit basarisiz, bellek yeni degeri gosterir. Ledger geri alir. */
    @Test
    fun aFailedCommitIsReportedRolledBackAndTheRetryGrantsExactlyOnce() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        store.script += CommitMode.FAIL_KEEP_MEMORY

        val first = save.fulfillPurchase(req("tok"))

        assertEquals("dogrulanamayan yazim Granted OLAMAZ", GrantResult.PersistFailed, first)
        // Bellek geri alindi: cuzdan sahibinin bayat kopyasiyla uyumlu.
        assertEquals(base, save.loadWallet().coins)
        assertEquals(0, save.lifetimePurchasedCoins)
        assertNull(save.purchaseRecordOf("tok"))
        assertFalse(save.isPurchaseTokenGranted("tok"))
        // Disk hic degismedi.
        assertNull(store.disk[ledgerKey("tok")])
        assertEquals("bakiye diskte de eski", base, store.disk[K_COINS] ?: base)

        val retry = save.fulfillPurchase(req("tok"))

        assertEquals(GrantResult.Granted(1_000, base + 1_000), retry)
        assertEquals("tam BIR kredi", base + 1_000, save.loadWallet().coins)
        assertEquals(1_000, save.lifetimePurchasedCoins)
        assertNotNull("kayit diskte", store.disk[ledgerKey("tok")])
        assertEquals(base + 1_000, store.disk[K_COINS])
    }

    /**
     * Geri almanin gerekcesi: basarisiz commit'ten sonra oyun cuzdanin bayat kopyasini yazmaya
     * devam eder ve bu (apply) TUM bellek haritasini diske gonderir. Geri alma olmasaydi defter
     * kaydi diske INER ama cuzdan eski degerle ustune yazilirdi: "odendi" denir, coin yoktur.
     */
    @Test
    fun aStaleWalletWriteAfterAFailedCommitCannotLeaveALedgerRecordWithoutCoins() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val staleWallet = save.loadWallet() // cuzdan sahibinin bellekteki kopyasi
        val base = staleWallet.coins
        store.script += CommitMode.FAIL_KEEP_MEMORY

        assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))
        // Oyun (bir bolum bitti) bayat kopyayi yazar: tum bellek diske gider.
        save.saveWallet(staleWallet.credited(300))

        val restarted = manager(store.restart())
        assertEquals(base + 300, restarted.loadWallet().coins)
        assertNull("diske sizmis defter kaydi YOK", restarted.purchaseRecordOf("tok"))
        assertEquals(0, restarted.lifetimePurchasedCoins)

        // Satin alma yeniden gelir ve BIR kez odenir; oyun kazanci korunur.
        val replay = restarted.fulfillPurchase(req("tok"))
        assertEquals(GrantResult.Granted(1_000, base + 300 + 1_000), replay)
    }

    /** Surec yeniden baslarsa disk gercektir: basarisiz commit'in izi YOKTUR, satin alma yeniden gelir. */
    @Test
    fun afterAFailedCommitAProcessRestartNeverSeesTheGrantAndTheReplayGrantsOnce() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        // Hem grant hem geri alma diske inemedi: disk hic degismedi.
        store.script += listOf(CommitMode.FAIL_KEEP_MEMORY, CommitMode.FAIL_KEEP_MEMORY)

        assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))

        val restartedStore = store.restart()
        val afterRestart = manager(restartedStore)
        assertEquals("yeniden baslatma: kredi GORUNMEZ", base, afterRestart.loadWallet().coins)
        assertNull(afterRestart.purchaseRecordOf("tok"))
        assertFalse(afterRestart.isPurchaseTokenGranted("tok"))

        assertEquals(GrantResult.Granted(1_000, base + 1_000), afterRestart.fulfillPurchase(req("tok")))

        // Ikinci yeniden baslatma: artik dayanikli; tekrar gelirse kredi YOK.
        val again = manager(restartedStore.restart())
        assertEquals(base + 1_000, again.loadWallet().coins)
        assertTrue(again.fulfillPurchase(req("tok")) is GrantResult.AlreadyGranted)
        assertEquals(base + 1_000, again.loadWallet().coins)
    }

    /**
     * ASIL TUZAK. Geri alma da uygulanamadi (bellek yeni degerleri tasimaya devam ediyor) ve kayit
     * bellekte GORUNUYOR. Yeniden okuyan bir deneme "zaten odenmis" der, tuketir — ve kayit hic
     * diske inmemistir. Ledger bunu ASLA AlreadyGranted saymaz; yeniden kalici yazar.
     */
    @Test
    fun theInMemoryMutationTrapNeverTurnsAnUnpersistedTokenIntoAlreadyGranted() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        store.script += listOf(CommitMode.FAIL_KEEP_MEMORY, CommitMode.REJECT_ENTIRELY)

        assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))

        // Tuzak GORUNUR (fake dogru kuruldu): bellek kaydi gosteriyor, disk gostermiyor.
        assertNotNull("bellekte kayit gorunmeli (tuzak)", store.memory[ledgerKey("tok")])
        assertNull("diskte kayit OLMAMALI", store.disk[ledgerKey("tok")])
        val seqAtFailure = store.memory[K_SEQ] as Long

        // Guvenlik: gorunen ama dogrulanmamis jeton "odenmis" SAYILMAZ.
        assertFalse(save.isPurchaseTokenGranted("tok"))
        assertNull(save.purchaseRecordOf("tok"))

        val retry = save.fulfillPurchase(req("tok"))

        assertTrue("AlreadyGranted DONMEMELI: $retry", retry !is GrantResult.AlreadyGranted)
        assertEquals(GrantResult.Granted(1_000, base + 1_000), retry)
        // Yalnizca commit true dondugunde dayanikli basari: kayit ARTIK diskte.
        assertEquals("G|${StoreCatalog.PACK_RECRUIT}|1000|$NOW", store.disk[ledgerKey("tok")])
        assertEquals(base + 1_000, store.disk[K_COINS])
        assertEquals(1_000, store.disk[K_LIFETIME])
        assertTrue("yazim sayaci ARTMALI (ayni-deger commit atlanmasin)", store.seq() > seqAtFailure)
        assertEquals("kredi tam bir kez (bellekte iki kez degil)", base + 1_000, save.loadWallet().coins)

        // Yeniden baslatma: dayanikli -> AlreadyGranted, kredi degismez.
        val restarted = manager(store.restart())
        assertEquals(base + 1_000, restarted.loadWallet().coins)
        assertTrue(restarted.fulfillPurchase(req("tok")) is GrantResult.AlreadyGranted)
        assertEquals(base + 1_000, restarted.loadWallet().coins)
        assertEquals(1_000, restarted.lifetimePurchasedCoins)
    }

    /** Sahtenin kendisi: tuzak 2'yi (ayni degerle commit = yeniden yazma yok) kanitlar. */
    @Test
    fun theFakeReproducesTheIdenticalValuesCommitTrap() {
        val store = FailableKeyValueStore()
        val batch = { StoreBatch().putInt("k", 1).putLong(K_SEQ, 5L) }

        assertTrue(store.commit(batch()))
        assertEquals(1, store.disk["k"])
        store.disk.clear() // disk "kayboldu"
        assertTrue("ayni degerler: basarili sayilir", store.commit(batch()))
        assertNull("...ama diske YENIDEN YAZILMAZ", store.disk["k"])
        // Deger degisince yazilir — ledger'in sayaci tam bu yuzden var.
        assertTrue(store.commit(StoreBatch().putInt("k", 1).putLong(K_SEQ, 6L)))
        assertEquals(1, store.disk["k"])
    }

    /** Belirsiz jeton + gorunen kayit ama oyun bu arada cuzdani degistirdi: krediyi KANITLAYAMAYIZ. */
    @Test
    fun anUncertainTokenWhoseVisibleStateChangedIsNotClaimedAndTheDiskDecidesAtRestart() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        store.script += listOf(CommitMode.FAIL_KEEP_MEMORY, CommitMode.REJECT_ENTIRELY)
        assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))

        // Oyun, bellekteki (yalanci) bakiyeyi okuyup 50 ekler; apply() tum bellegi diske gonderir.
        save.saveWallet(save.loadWallet().credited(50))

        val retry = save.fulfillPurchase(req("tok"))
        assertEquals("gorunum degisti: iddia edilmez", GrantResult.PersistFailed, retry)

        // Restart: disk (kayit + kredi + oyun kazanci birlikte) -> AlreadyGranted, KAYIP YOK, CIFT YOK.
        val restarted = manager(store.restart())
        assertEquals(base + 1_000 + 50, restarted.loadWallet().coins)
        assertTrue(restarted.fulfillPurchase(req("tok")) is GrantResult.AlreadyGranted)
        assertEquals(base + 1_000 + 50, restarted.loadWallet().coins)
    }

    @Test
    fun aThrowingCommitIsTreatedAsNotPersistedAndCanBeRetried() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        store.script += CommitMode.THROW

        assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))
        assertEquals(base, save.loadWallet().coins)
        assertNull(save.purchaseRecordOf("tok"))

        assertEquals(GrantResult.Granted(1_000, base + 1_000), save.fulfillPurchase(req("tok")))
        assertNotNull(store.disk[ledgerKey("tok")])
    }

    @Test
    fun repeatedFailuresNeverAccumulateCreditOrLeakState() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        repeat(5) {
            store.script += CommitMode.FAIL_KEEP_MEMORY
            assertEquals(GrantResult.PersistFailed, save.fulfillPurchase(req("tok")))
            assertEquals("her basarisizlikta bakiye eski", base, save.loadWallet().coins)
        }
        assertEquals(GrantResult.Granted(1_000, base + 1_000), save.fulfillPurchase(req("tok")))
        assertEquals(base + 1_000, save.loadWallet().coins)
        assertTrue(store.seq() >= 6L)
    }

    /** Uctan uca: yazim basarisizken TUKETME YOK; dayanikli olunca tam bir tuketme. */
    @Test
    fun aPersistFailureNeverConsumesAndTheRetryConsumesOnce() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val consumed = mutableListOf<String>()
        val finisher = object : PurchaseFinisher {
            override fun consume(purchaseToken: String, onResult: (ConsumeResult) -> Unit) {
                consumed += purchaseToken; onResult(ConsumeResult.CONSUMED)
            }
            override fun acknowledge(purchaseToken: String, onResult: (AcknowledgeResult) -> Unit) = Unit
        }
        val fulfiller = PurchaseFulfiller({ save.fulfillPurchase(it) }, finisher)
        val info = PurchaseInfo(listOf(StoreCatalog.PACK_SERGEANT), "tok", PurchaseState.PURCHASED, 1, false)
        val base = save.loadWallet().coins

        store.script += listOf(CommitMode.FAIL_KEEP_MEMORY, CommitMode.REJECT_ENTIRELY) // en kotu: iz bellekte kaldi
        assertTrue(fulfiller.process(info) is FulfillmentOutcome.PersistFailed)
        assertEquals("yazim dogrulanmadan TUKETME YOK", emptyList<String>(), consumed)

        // Yeniden deneme (ayni bellek-mutasyon durumu): AlreadyGranted DEGIL, yeniden kalici yazim.
        val retry = fulfiller.process(info)
        assertTrue("$retry", retry is FulfillmentOutcome.Granted)
        assertEquals(listOf("tok"), consumed)
        assertEquals(base + 2_200, save.loadWallet().coins)
        assertNotNull("tuketmeden ONCE kayit diskte", store.disk[ledgerKey("tok")])
    }

    // =========================================================================
    // 3. Yinelenen ve es zamanli teslimat
    // =========================================================================

    @Test
    fun duplicateCallbacksCreditATokenOnce() {
        val save = manager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        assertTrue(save.fulfillPurchase(req("dup")) is GrantResult.Granted)
        repeat(6) {
            val r = save.fulfillPurchase(req("dup"))
            assertTrue("tekrar $it: $r", r is GrantResult.AlreadyGranted)
            assertEquals("eslesen kayit dondu", StoreCatalog.PACK_RECRUIT, (r as GrantResult.AlreadyGranted).record?.productId)
        }
        assertEquals(base + 1_000, save.loadWallet().coins)
        assertEquals(1_000, save.lifetimePurchasedCoins)
    }

    @Test
    fun concurrentDuplicatesOfOneTokenGrantExactlyOnce() {
        val save = manager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val threads = 32
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val granted = AtomicInteger()
        val already = AtomicInteger()
        val other = AtomicInteger()
        repeat(threads) {
            pool.submit {
                start.await()
                when (save.fulfillPurchase(req("race", StoreCatalog.PACK_CAPTAIN, 3_600))) {
                    is GrantResult.Granted -> granted.incrementAndGet()
                    is GrantResult.AlreadyGranted -> already.incrementAndGet()
                    else -> other.incrementAndGet()
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        assertEquals(1, granted.get())
        assertEquals(threads - 1, already.get())
        assertEquals(0, other.get())
        assertEquals(base + 3_600, save.loadWallet().coins)
        assertEquals(3_600, save.lifetimePurchasedCoins)
    }

    @Test
    fun concurrentDistinctTokensLoseNoCredit() {
        val store = InMemoryKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        val n = 24
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(n) { i ->
            pool.submit {
                start.await()
                save.fulfillPurchase(req("t$i"))
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        assertEquals("her odeme tam bir kez, kayip guncelleme yok", base + n * 1_000, save.loadWallet().coins)
        assertEquals(n * 1_000, save.lifetimePurchasedCoins)
        assertEquals(n.toLong(), store.getLong(K_SEQ, 0L))
    }

    @Test
    fun twoSaveManagersOverTheSameStoreShareTheLockAndTheLedger() {
        val store = InMemoryKeyValueStore()
        val a = manager(store)
        val b = manager(store)
        val base = a.loadWallet().coins
        assertTrue(a.fulfillPurchase(req("x")) is GrantResult.Granted)
        assertTrue("ikinci ornek ayni kaydi gorur", b.fulfillPurchase(req("x")) is GrantResult.AlreadyGranted)
        assertEquals(base + 1_000, b.loadWallet().coins)
    }

    // =========================================================================
    // 4. Tasma / gecersiz girdi / bozuk kayit
    // =========================================================================

    @Test
    fun aWalletOverflowIsRejectedWithoutWritingAnythingAndSucceedsOnceThereIsRoom() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        store.putInt(K_COINS, Int.MAX_VALUE - 999) // 1.000 eklemek 1 tasar
        val commitsBefore = store.commitCalls

        assertEquals(GrantResult.Rejected(RejectReason.WALLET_OVERFLOW), save.fulfillPurchase(req("of")))

        assertEquals("hicbir sey yazilmadi", commitsBefore, store.commitCalls)
        assertEquals(Int.MAX_VALUE - 999, save.loadWallet().coins)
        assertNull(save.purchaseRecordOf("of"))
        assertEquals(0, save.lifetimePurchasedCoins)

        // 1 coin harcayinca sigar (sinir SIKI): tam Int.MAX_VALUE.
        store.putInt(K_COINS, Int.MAX_VALUE - 1_000)
        assertEquals(GrantResult.Granted(1_000, Int.MAX_VALUE), save.fulfillPurchase(req("of")))
        assertEquals(Int.MAX_VALUE, save.loadWallet().coins)
    }

    @Test
    fun theLifetimeCounterSaturatesInsteadOfRejectingAPaidPurchase() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        store.putInt(K_LIFETIME, Int.MAX_VALUE - 10)

        assertEquals(GrantResult.Granted(1_000, base + 1_000), save.fulfillPurchase(req("lt")))
        assertEquals("yalnizca raporlama: doyar, tasmaz", Int.MAX_VALUE, save.lifetimePurchasedCoins)
    }

    @Test
    fun invalidRequestsAreRejectedAndTouchNothing() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        listOf(
            PurchaseGrantRequest("", StoreCatalog.PACK_RECRUIT, 1_000),
            PurchaseGrantRequest("   ", StoreCatalog.PACK_RECRUIT, 1_000),
            PurchaseGrantRequest("t", "", 1_000),
            PurchaseGrantRequest("t", StoreCatalog.PACK_RECRUIT, 0),
            PurchaseGrantRequest("t", StoreCatalog.PACK_RECRUIT, -5),
            PurchaseGrantRequest("t", "a|b", 1_000),
        ).forEach {
            assertEquals("$it", GrantResult.Rejected(RejectReason.INVALID_REQUEST), save.fulfillPurchase(it))
        }
        assertEquals(0, store.commitCalls)
        assertEquals(base, save.loadWallet().coins)
    }

    @Test
    fun aCorruptLedgerRecordIsRejectedAndNeverRepairedOrCredited() {
        val store = FailableKeyValueStore(mapOf(ledgerKey("bad") to "garbage"))
        val save = manager(store)
        val base = save.loadWallet().coins

        assertEquals(GrantResult.Rejected(RejectReason.CORRUPT_RECORD), save.fulfillPurchase(req("bad")))

        assertEquals("garbage", store.disk[ledgerKey("bad")])
        assertEquals(base, save.loadWallet().coins)
        assertEquals(0, store.commitCalls)
        listOf("G|p|0|1", "G||5|1", "X|p|5|1", "G|p|5", "G|p|abc|1", "G|p|-3|1").forEach { junk ->
            val s = manager(FailableKeyValueStore(mapOf(ledgerKey("j") to junk)))
            assertEquals("'$junk'", GrantResult.Rejected(RejectReason.CORRUPT_RECORD), s.fulfillPurchase(req("j")))
        }
    }

    @Test
    fun theSameTokenWithADifferentProductOrAmountIsRejectedBeforeAndAfterARestart() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        val base = save.loadWallet().coins
        assertTrue(save.fulfillPurchase(req("m")) is GrantResult.Granted)

        val mismatch = GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH)
        assertEquals(mismatch, save.fulfillPurchase(req("m", StoreCatalog.PACK_GENERAL, 7_000)))
        assertEquals("ayni urun, farkli miktar", mismatch, save.fulfillPurchase(req("m", StoreCatalog.PACK_RECRUIT, 2_000)))

        val restarted = manager(store.restart())
        assertEquals(mismatch, restarted.fulfillPurchase(req("m", StoreCatalog.PACK_GENERAL, 7_000)))
        assertEquals(StoreCatalog.PACK_RECRUIT, restarted.purchaseRecordOf("m")?.productId)
        assertEquals(base + 1_000, restarted.loadWallet().coins)
    }

    // =========================================================================
    // 5. Eski (legacy) veriyle uyum
    // =========================================================================

    /** Eski miktarlar DEGISMEDEN kredilenir; defter urun kimligini saklar. */
    @Test
    fun legacyExactAmountsAreCreditedUnchanged() {
        val save = manager(InMemoryKeyValueStore())
        val base = save.loadWallet().coins
        val original = linkedMapOf(
            "coins_1200" to 1_200, "coins_4000" to 4_000, "coins_7400" to 7_400,
            "coins_16000" to 16_000, "coins_25000" to 25_000,
        )
        var expected = base
        original.forEach { (id, amount) ->
            expected += amount
            assertEquals(GrantResult.Granted(amount, expected), save.fulfillPurchase(req("L-$id", id, amount)))
            assertEquals(id, save.purchaseRecordOf("L-$id")?.productId)
        }
        assertEquals(53_600, save.lifetimePurchasedCoins)
        assertEquals(base + 53_600, save.loadWallet().coins)
    }

    /** Eski `iap_granted_tokens` jetonu tekrar gelirse ALREADY_GRANTED, kredi YOK, yazim YOK. */
    @Test
    fun anOldTokenReplaysAsAlreadyGrantedWithNoCreditAndNoWrite() {
        val store = FailableKeyValueStore(mapOf(K_TOKENS to "old-1, old-2", K_COINS to 500, K_LIFETIME to 2_400))
        val save = manager(store)

        assertEquals(GrantResult.AlreadyGranted(null), save.fulfillPurchase(req("old-1", "coins_1200", 1_200)))
        assertEquals(GrantResult.AlreadyGranted(null), save.fulfillPurchase(req("old-2", "coins_4000", 4_000)))
        assertTrue(save.isPurchaseTokenGranted("old-1"))
        assertEquals(500, save.loadWallet().coins)
        assertEquals(2_400, save.lifetimePurchasedCoins)
        assertEquals("eski jeton icin HIC yazim yok", 0, store.commitCalls)

        // Eski jeton YENI bir urun kimligiyle gelirse tutarsizdir: reddet.
        assertEquals(
            GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH),
            save.fulfillPurchase(req("old-1", StoreCatalog.PACK_RECRUIT, 1_000)),
        )
        assertEquals(500, save.loadWallet().coins)
    }

    /** Gelistirici cihazindaki kayit (4 sahte 'debug-' jeton, 4.800 omur, ~770 coin) DEGISMEDEN yuklenir. */
    @Test
    fun theDevDeviceSaveLoadsUnchanged() {
        val debugTokens = listOf(
            "debug-coins_1200-a1-1", "debug-coins_1200-a1-2", "debug-coins_1200-b2-1", "debug-coins_1200-c3-1",
        )
        val seed = mapOf<String, Any>(
            "save_version" to EconomyConfig.SAVE_VERSION,
            K_COINS to 770,
            K_LIFETIME to 4_800,
            K_TOKENS to debugTokens.joinToString(","),
            "iap_remove_ads" to false,
            "eco_prestige_rank" to 2,
            "tutorial_status" to 1,
        )
        val store = FailableKeyValueStore(seed)
        val save = manager(store)

        assertEquals(770, save.loadWallet().coins)
        assertEquals(4_800, save.lifetimePurchasedCoins)
        assertFalse(save.adsRemoved)
        debugTokens.forEach { assertTrue(save.isPurchaseTokenGranted(it)) }
        // Acilis IAP anahtarlarina DOKUNMADI.
        assertEquals(4_800, store.memory[K_LIFETIME])
        assertEquals(debugTokens.joinToString(","), store.memory[K_TOKENS])
        assertTrue("defter anahtari yok (eklemeli)", store.memory.keys.none { PurchaseGrantLedger.isLedgerKey(it) })
        assertNull("sayac yok", store.memory[K_SEQ])

        // Eski jeton tekrar: kredi yok.
        assertEquals(GrantResult.AlreadyGranted(null), save.fulfillPurchase(req(debugTokens[0], "coins_1200", 1_200)))
        assertEquals(770, save.loadWallet().coins)
        // Yeni satin alma normal ve omur sayaci ustune eklenir.
        assertEquals(GrantResult.Granted(1_000, 1_770), save.fulfillPurchase(req("fresh")))
        assertEquals(5_800, save.lifetimePurchasedCoins)
    }

    // =========================================================================
    // 6. Sifirlama
    // =========================================================================

    @Test
    fun resetProgressPreservesTheLedgerTheTokensAndTheCounterAndBlocksARegrant() {
        val store = FailableKeyValueStore(mapOf(K_TOKENS to "old-1"))
        val save = manager(store)
        assertTrue(save.fulfillPurchase(req("kept")) is GrantResult.Granted)
        save.prestigeRank = 3 // ilerleme: silinmeli
        val seq = store.memory[K_SEQ]

        save.resetProgress()

        assertEquals("ilerleme silindi", 0, save.prestigeRank)
        assertEquals("cuzdan silindi", 0, save.loadWallet().coins)
        assertNotNull("defter kaydi KORUNDU", store.memory[ledgerKey("kept")])
        assertEquals("eski jeton listesi KORUNDU", "old-1", store.memory[K_TOKENS])
        assertEquals("omur sayaci KORUNDU", 1_000, store.memory[K_LIFETIME])
        assertEquals("yazim sayaci KORUNDU", seq, store.memory[K_SEQ])

        // Sifirlama, ayni odenmis satin almayi yeniden coin'e CEVIRMEZ.
        assertTrue(save.fulfillPurchase(req("kept")) is GrantResult.AlreadyGranted)
        assertTrue(save.fulfillPurchase(req("old-1", "coins_1200", 1_200)) is GrantResult.AlreadyGranted)
        assertEquals(0, save.loadWallet().coins)
    }

    // =========================================================================
    // 7. Diger sozlesmeler
    // =========================================================================

    @Test
    fun purchasedCoinsNeverEnterLifetimeEarned() {
        val save = manager(FailableKeyValueStore())
        val before = save.loadWallet()
        save.fulfillPurchase(req("e"))
        val after = save.loadWallet()
        assertEquals(before.lifetimeEarned, after.lifetimeEarned)
        assertEquals(1_000, save.lifetimePurchasedCoins)
    }

    @Test
    fun theWalletSyncSignalIsClaimedExactlyOnceByTheLegacyShape() {
        val save = manager(InMemoryKeyValueStore())
        // Yeni yol: fulfillPurchase kredilendi -> cuzdan sahibi (CampaignProgressImpl) shim ile YENILENIR.
        assertTrue(save.fulfillPurchase(req("w")) is GrantResult.Granted)
        assertTrue("ilk cagri: yenile sinyali", save.grantPurchasedCoins("w", 1_000))
        assertFalse("ikinci cagri: sinyal tuketildi, KREDI yok", save.grantPurchasedCoins("w", 1_000))

        // Eski sekil (debug paneli): jeton yeni -> kendisi kredilendi; ayni jeton tekrari false.
        val base = save.loadWallet().coins
        assertTrue(save.grantPurchasedCoins("dbg", 1_000))
        assertEquals(base + 1_000, save.loadWallet().coins)
        assertFalse(save.grantPurchasedCoins("dbg", 1_000))
        assertEquals(base + 1_000, save.loadWallet().coins)
    }

    @Test
    fun isPurchaseTokenGrantedIsFalseForAnUncertainTokenUntilTheCommitIsConfirmed() {
        val store = FailableKeyValueStore()
        val save = manager(store)
        store.script += listOf(CommitMode.FAIL_KEEP_MEMORY, CommitMode.REJECT_ENTIRELY)
        save.fulfillPurchase(req("u"))
        assertFalse(save.isPurchaseTokenGranted("u"))
        save.fulfillPurchase(req("u"))
        assertTrue(save.isPurchaseTokenGranted("u"))
        assertTrue(manager(store.restart()).isPurchaseTokenGranted("u"))
    }

    @Test
    fun ledgerRecordsUseOnlyKeysThatResetTreatsAsPreserved() {
        // Sozlesme: defter anahtar oneki ve sayac anahtari tek yerde tanimli.
        assertTrue(PurchaseGrantLedger.isLedgerKey(ledgerKey("anything")))
        assertFalse(PurchaseGrantLedger.isLedgerKey("eco_coins"))
        // Batch islem tipleri tam bu bes.
        val ops = StoreBatch().putInt("a", 1).putLong("b", 2L).putString("c", "d").putBoolean("e", true).remove("f").operations
        assertEquals(
            listOf(StoreOp.PutInt("a", 1), StoreOp.PutLong("b", 2L), StoreOp.PutString("c", "d"), StoreOp.PutBoolean("e", true), StoreOp.Remove("f")),
            ops,
        )
    }
}

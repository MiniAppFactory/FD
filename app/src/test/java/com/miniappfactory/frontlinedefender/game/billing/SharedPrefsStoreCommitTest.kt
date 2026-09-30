package com.miniappfactory.frontlinedefender.game.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.data.PurchaseGrantLedger
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.data.SharedPrefsStore
import com.miniappfactory.frontlinedefender.game.data.StoreBatch
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GERCEK `SharedPreferences` (Robolectric'in cerceve gerceklemesi) uzerinde `commit(batch)`.
 *
 * `FailableKeyValueStore` basarisizligi simule eder; bu test ise URETIM deposunun
 * (`SharedPrefsStore`) toplu yazimi gercekten tek `Editor`/`commit()` ile yaptigini, donus degerini
 * ilettigini ve yeni bir ornegin/`SaveManager`in yazilani okudugunu dogrular. Cihazda calistirilmadi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedPrefsStoreCommitTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefsName = "frontline_defender_prefs"

    @Before
    fun clean() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aBatchIsWrittenTogetherAndReadBackByAFreshStoreInstance() {
        val batch = StoreBatch()
            .putInt("i", 7).putLong("l", 9_000_000_000L).putString("s", "x|y").putBoolean("b", true)
            .putInt("gone", 1)
        assertTrue(SharedPrefsStore(context).commit(batch))
        assertTrue(SharedPrefsStore(context).commit(StoreBatch().remove("gone").putInt("i", 8)))

        val fresh = SharedPrefsStore(context)
        assertEquals(8, fresh.getInt("i", 0))
        assertEquals(9_000_000_000L, fresh.getLong("l", 0))
        assertEquals("x|y", fresh.getString("s", ""))
        assertTrue(fresh.getBoolean("b", false))
        assertFalse("silme ayni toplu yazimda uygulandi", fresh.hasKey("gone"))
    }

    @Test
    fun everyInstanceSharesOneGuardBecauseTheyShareOneFile() {
        assertSame(SharedPrefsStore(context).purchaseGuard, SharedPrefsStore(context).purchaseGuard)
    }

    @Test
    fun aRealPurchaseIsDurableAcrossANewSaveManagerOverTheSamePreferences() {
        val request = PurchaseGrantRequest("real-tok", StoreCatalog.PACK_SERGEANT, 2_200)
        val first = SaveManager(context)
        val base = first.loadWallet().coins
        assertEquals(GrantResult.Granted(2_200, base + 2_200), first.fulfillPurchase(request))

        // "Surec yeniden basladi": yeni SaveManager + yeni SharedPrefsStore, ayni dosya.
        val second = SaveManager(context)
        assertEquals(base + 2_200, second.loadWallet().coins)
        assertEquals(2_200, second.lifetimePurchasedCoins)
        assertTrue(second.fulfillPurchase(request) is GrantResult.AlreadyGranted)
        assertEquals(base + 2_200, second.loadWallet().coins)

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertNotNull(prefs.getString(PurchaseGrantLedger.KEY_LEDGER_PREFIX + "real-tok", null))
        assertEquals(1L, prefs.getLong(PurchaseGrantLedger.KEY_COMMIT_SEQ, 0L))
    }

    @Test
    fun resetProgressKeepsTheLedgerInRealPreferencesToo() {
        val save = SaveManager(context)
        save.fulfillPurchase(PurchaseGrantRequest("keep", StoreCatalog.PACK_RECRUIT, 1_000))
        save.resetProgress()

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        assertNotNull(prefs.getString(PurchaseGrantLedger.KEY_LEDGER_PREFIX + "keep", null))
        assertNull("cuzdan silindi", prefs.all["eco_coins"])
        assertTrue(SaveManager(context).fulfillPurchase(PurchaseGrantRequest("keep", StoreCatalog.PACK_RECRUIT, 1_000)) is GrantResult.AlreadyGranted)
    }
}

package com.miniappfactory.frontlinedefender.game.data

import com.miniappfactory.frontlinedefender.game.billing.GrantResult
import com.miniappfactory.frontlinedefender.game.billing.LedgerRecord
import com.miniappfactory.frontlinedefender.game.billing.PurchaseGrantRequest
import com.miniappfactory.frontlinedefender.game.billing.RejectReason
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog

// =====================================================================================
// Atomik toplu yazim ilkelleri
// =====================================================================================

/** [StoreBatch]in tek bir islemi. */
sealed interface StoreOp {
    val key: String

    data class PutInt(override val key: String, val value: Int) : StoreOp
    data class PutLong(override val key: String, val value: Long) : StoreOp
    data class PutString(override val key: String, val value: String) : StoreOp
    data class PutBoolean(override val key: String, val value: Boolean) : StoreOp
    data class Remove(override val key: String) : StoreOp
}

/** [KeyValueStore.commit]e verilen, TEK atomik yazim olarak uygulanacak islemler. */
class StoreBatch {
    private val ops = ArrayList<StoreOp>()
    val operations: List<StoreOp> get() = ops

    fun putInt(key: String, value: Int) = apply { ops += StoreOp.PutInt(key, value) }
    fun putLong(key: String, value: Long) = apply { ops += StoreOp.PutLong(key, value) }
    fun putString(key: String, value: String) = apply { ops += StoreOp.PutString(key, value) }
    fun putBoolean(key: String, value: Boolean) = apply { ops += StoreOp.PutBoolean(key, value) }
    fun remove(key: String) = apply { ops += StoreOp.Remove(key) }
}

/**
 * Ayni DEPOLAMA uzerindeki tum [SaveManager] ornekleri icin ORTAK kilit ve
 * "belirsiz" jeton kaydi.
 *
 * **Surece ozgu OLMASI KASITLI.** Belirsiz bir odeme yalnizca bu sureci
 * ilgilendirir: surec olunce bellekteki (yalanci) gorunum de gider ve DISK
 * tek dogruluk kaynagi olur — yarim kalan satin alma yeniden gelir ve bir kez
 * odenir. `SharedPrefsStore` ornekleri ayni dosyayi paylastigi icin ayni guard'i
 * paylasir; `InMemoryKeyValueStore` ornegi basina bir guard tasir.
 */
class PurchaseGuard {
    val lock = Any()

    /** Commit'i DOGRULANAMAMIS jetonlar -> yazilmaya calisilan bellek gorunumu. Kilit altinda okunur/yazilir. */
    internal val uncertain = HashMap<String, UncertainGrant>()
}

/** Basarisiz commit'in yazmaya calistigi bakiye/omur degerleri. */
internal data class UncertainGrant(val afterBalance: Int, val afterLifetime: Int)

// =====================================================================================
// Satin alma defteri
// =====================================================================================

/**
 * GERCEK PARAYLA ODENMIS coin'in KALICI defteri.
 *
 * ## Neden yeniden yazildi (2026-09-30)
 *
 * Eski `SaveManager.grantPurchasedCoins` uc AYRI yazim yapiyordu (cuzdan,
 * omur sayaci, jeton listesi) ve her yazim kendi `apply()`iydi: yazimlarin
 * arasinda sunan bir cokme ya da diske yazma hatasi coin'i IKI KEZ ya da HIC
 * odeyebilirdi. Kod yorumu "ayni islemde" diyordu; degildi. Ustelik sonuc
 * `Boolean`di ve `false` hem "zaten odenmis" hem "yazilamadi" demekti.
 *
 * ## Sozlesme
 *
 *  * Cuzdan + defter kaydi + omur sayaci + [KEY_COMMIT_SEQ] **TEK** [StoreBatch].
 *    `SharedPreferences.Editor.commit()` senkron ve dondurdugu deger KONTROL
 *    EDILIR: yalnizca `true` "diskte" demektir.
 *  * Sonuc [GrantResult]: Granted / AlreadyGranted (ESLESEN kayitla) /
 *    Rejected / PersistFailed. `consume` yalnizca ilk ikisinde yapilir.
 *  * Kilit ([PurchaseGuard.lock]) altinda: ayni jetonun es zamanli iki isleme
 *    girmesi tek `Granted` + digerleri `AlreadyGranted` verir.
 *  * Hesap kontrollu: bakiye tasmasi [RejectReason.WALLET_OVERFLOW]; omur
 *    sayaci yalnizca raporlama oldugundan `Int.MAX_VALUE`da doyar (odemeyi
 *    reddetmez).
 *
 * ## ⚠ TUZAK: BASARISIZ commit() BELLEGI YINE DE GUNCELLER
 *
 * Android'de `commit()` once bellek haritasini gunceller, SONRA diske yazar.
 * Disk yazimi basarisiz olursa `false` doner ama bellek haritasi YENI
 * degerleri gosterir. Iki sonucu var ve ikisi de para kaybi:
 *
 *  1. **Yeniden okuyan yeniden deneme "zaten odenmis" gorur**, tuketir, ve
 *     kayit hic diske inmemistir: surec olunce coin da defter de gider, Play
 *     satin almayi bir daha vermez.
 *  2. **Bayat cuzdan geri yazimi.** Oyun cuzdanin bellekteki kopyasini yazmaya
 *     devam eder (`saveWallet`): tum harita diske gittiginde defter kaydi
 *     gecerli, ama cuzdan eski degerle ustune yazilmis olur — "odendi" denir,
 *     coin yoktur.
 *
 * Ayrica ayni degerlerle ikinci bir `commit()`, degisiklik olmadigi icin
 * yeniden yazmadan `true` dondurebilir.
 *
 * Bu sinif uc sey yapar:
 *
 *  a. Basarisiz commit'in ardindan bellek gorunumunu **geri alir** (cuzdan,
 *     omur sayaci ve defter anahtari onceki degerlere doner). Cuzdan sahibinin
 *     bayat kopyasi bellekle yeniden uyumlu olur; ikinci sorun kapanir.
 *  b. Jetonu surec icinde **belirsiz** isaretler ([PurchaseGuard.uncertain]).
 *     Geri alma da uygulanamamissa ve kayit gorunuyorsa, jeton **asla**
 *     "AlreadyGranted" sayilmaz: gorunum bizim yazdigimizla BIREBIR ayniysa
 *     yeniden kalici yazilir, degilse [GrantResult.PersistFailed] doner ve
 *     karar surec yeniden baslayinca DISKE birakilir.
 *  c. Her yazima artan bir sayac ([KEY_COMMIT_SEQ]) koyar: yeniden yazimin
 *     "hic degisiklik yok" olarak atlanmasi imkansizlasir.
 *
 * Surec yeniden baslarsa disk tek gercektir: disk yeni durumu tasiyorsa
 * jeton `AlreadyGranted` olur ve tuketilir; tasimiyorsa satin alma yeniden
 * gelir ve bir kez odenir.
 */
class PurchaseGrantLedger(
    private val store: KeyValueStore,
    private val walletCoinsKey: String,
    private val lifetimeCoinsKey: String,
    private val legacyTokensKey: String,
    private val clock: () -> Long,
) {
    companion object {
        /** Her defter yazimiyla artan sayac. RESET'TE KORUNUR. */
        const val KEY_COMMIT_SEQ = "iap_commit_seq"

        /** Jeton basina defter kaydi: `iap_ledger_<jeton>` = `G|<urun>|<coin>|<ms>`. RESET'TE KORUNUR. */
        const val KEY_LEDGER_PREFIX = "iap_ledger_"

        private const val RECORD_TAG = "G"
        private const val FIELD_SEPARATOR = '|'

        fun isLedgerKey(key: String): Boolean = key.startsWith(KEY_LEDGER_PREFIX)
    }

    private fun ledgerKey(token: String) = KEY_LEDGER_PREFIX + token

    /** Toplam satin alinan coin (raporlama). */
    val lifetimePurchasedCoins: Int
        get() = store.getInt(lifetimeCoinsKey, 0).coerceAtLeast(0)

    /** Diske dayanikli oldugu bilinen defter kaydi. Belirsiz jeton icin `null`. */
    fun recordOf(token: String): LedgerRecord? {
        if (token.isBlank()) return null
        val guard = store.purchaseGuard
        return synchronized(guard.lock) {
            if (token in guard.uncertain) return@synchronized null
            decode(store.getString(ledgerKey(token), ""))
        }
    }

    /** Jeton eski listede ya da defterde (dayanikli) var mi. */
    fun isGranted(token: String): Boolean {
        if (token.isBlank()) return false
        return recordOf(token) != null || token in legacyTokens()
    }

    /** ESKI `iap_granted_tokens` (virgullu) — yalnizca OKUNUR, artik yazilmaz. */
    private fun legacyTokens(): Set<String> =
        store.getString(legacyTokensKey, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun grant(request: PurchaseGrantRequest): GrantResult {
        val guard = store.purchaseGuard
        return synchronized(guard.lock) { grantLocked(request, guard) }
    }

    private fun grantLocked(request: PurchaseGrantRequest, guard: PurchaseGuard): GrantResult {
        val token = request.purchaseToken
        if (token.isBlank() || request.productId.isBlank() ||
            FIELD_SEPARATOR in request.productId || request.coins <= 0
        ) {
            return GrantResult.Rejected(RejectReason.INVALID_REQUEST)
        }

        val key = ledgerKey(token)
        val raw = store.getString(key, "")
        val existing: LedgerRecord? =
            if (raw.isEmpty()) null else decode(raw) ?: return GrantResult.Rejected(RejectReason.CORRUPT_RECORD)
        val uncertain = guard.uncertain[token]

        if (existing != null) {
            if (existing.productId != request.productId || existing.coins != request.coins) {
                return GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH)
            }
            // Bellekte gorunen ama commit'i dogrulanamamis kayit "zaten odendi" SAYILMAZ.
            if (uncertain == null) return GrantResult.AlreadyGranted(existing)
            return repersist(request, key, existing, uncertain, guard)
        }

        // Kayit gorunmuyor: bellek geri alinmis (ya da yazim hic bellege girmemis).
        // Onceki basarisiz denemenin izi yok; taze odeme olarak devam.
        if (uncertain != null) guard.uncertain.remove(token)

        // Defter oncesi (eski) jeton: urun/miktar saklanmamisti; yalnizca ESKI coin
        // urunlerinden biri olabilir. Yeni bir urun kimligiyle gelirse tutarsizdir.
        if (token in legacyTokens()) {
            return if (StoreCatalog.kindOf(request.productId) == StoreCatalog.ProductKind.LEGACY_COINS) {
                GrantResult.AlreadyGranted(null)
            } else {
                GrantResult.Rejected(RejectReason.PRODUCT_OR_AMOUNT_MISMATCH)
            }
        }

        val hadBalance = store.hasKey(walletCoinsKey)
        val hadLifetime = store.hasKey(lifetimeCoinsKey)
        val balance = store.getInt(walletCoinsKey, 0).coerceAtLeast(0)
        val lifetime = store.getInt(lifetimeCoinsKey, 0).coerceAtLeast(0)

        val newBalance = try {
            Math.addExact(balance, request.coins)
        } catch (_: ArithmeticException) {
            return GrantResult.Rejected(RejectReason.WALLET_OVERFLOW)
        }
        val newLifetime = try {
            Math.addExact(lifetime, request.coins)
        } catch (_: ArithmeticException) {
            Int.MAX_VALUE // yalnizca raporlama: odemeyi reddetme
        }

        val record = LedgerRecord(request.productId, request.coins, clock())
        val batch = StoreBatch()
            .putInt(walletCoinsKey, newBalance)
            .putInt(lifetimeCoinsKey, newLifetime)
            .putString(key, encode(record))
            .putLong(KEY_COMMIT_SEQ, nextSeq())
        if (commitSafely(batch)) return GrantResult.Granted(request.coins, newBalance)

        // DOGRULANAMADI. Bellek yeni degerleri gosteriyor olabilir: geri al.
        guard.uncertain[token] = UncertainGrant(newBalance, newLifetime)
        val rollback = StoreBatch().remove(key)
        if (hadBalance) rollback.putInt(walletCoinsKey, balance) else rollback.remove(walletCoinsKey)
        if (hadLifetime) rollback.putInt(lifetimeCoinsKey, lifetime) else rollback.remove(lifetimeCoinsKey)
        rollback.putLong(KEY_COMMIT_SEQ, nextSeq())
        if (commitSafely(rollback)) guard.uncertain.remove(token)
        return GrantResult.PersistFailed
    }

    /**
     * Kayit bellekte GORUNUYOR ama jetonun commit'i dogrulanamamisti (geri alma
     * da uygulanamadi). Gorunum yazmaya calistigimiz degerlerle BIREBIR ayniysa
     * yeniden kalici yazariz; degilse (oyun bu arada cuzdani degistirdi) krediyi
     * kanitlayamayiz ve iddia etmeyiz.
     */
    private fun repersist(
        request: PurchaseGrantRequest,
        key: String,
        existing: LedgerRecord,
        uncertain: UncertainGrant,
        guard: PurchaseGuard,
    ): GrantResult {
        val balance = store.getInt(walletCoinsKey, 0).coerceAtLeast(0)
        val lifetime = store.getInt(lifetimeCoinsKey, 0).coerceAtLeast(0)
        if (balance != uncertain.afterBalance || lifetime != uncertain.afterLifetime) {
            return GrantResult.PersistFailed
        }
        val batch = StoreBatch()
            .putInt(walletCoinsKey, balance)
            .putInt(lifetimeCoinsKey, lifetime)
            .putString(key, encode(existing))
            // Degerler AYNI: artan sayac yazimin "degisiklik yok" diye atlanmasini onler.
            .putLong(KEY_COMMIT_SEQ, nextSeq())
        if (!commitSafely(batch)) return GrantResult.PersistFailed
        guard.uncertain.remove(request.purchaseToken)
        return GrantResult.Granted(existing.coins, balance)
    }

    private fun nextSeq(): Long = store.getLong(KEY_COMMIT_SEQ, 0L) + 1L

    private fun commitSafely(batch: StoreBatch): Boolean =
        try {
            store.commit(batch)
        } catch (_: Exception) {
            false
        }

    private fun encode(record: LedgerRecord): String =
        listOf(RECORD_TAG, record.productId, record.coins, record.grantedAtMillis)
            .joinToString(FIELD_SEPARATOR.toString())

    /** Bos/bozuk kayit `null` doner; cagiran bos ile bozugu AYIRMAK icin ham dizeye bakar. */
    private fun decode(raw: String): LedgerRecord? {
        if (raw.isEmpty()) return null
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 4 || parts[0] != RECORD_TAG) return null
        val productId = parts[1].takeIf { it.isNotBlank() } ?: return null
        val coins = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val at = parts[3].toLongOrNull() ?: return null
        return LedgerRecord(productId, coins, at)
    }
}

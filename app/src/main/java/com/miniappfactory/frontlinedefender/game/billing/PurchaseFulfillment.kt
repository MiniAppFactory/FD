package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog

/**
 * SATIN ALMA KARARI (saf) + UYGULAYICI (Android'siz, sahte portlarla JVM'de sinanir).
 *
 * `BillingManager` Play'den bir `Purchase` alir, [PurchaseInfo]ya cevirir ve
 * buraya verir. Coin nasil odenir, ne zaman tuketilir, neye dokunulmaz — HEPSI
 * burada; `BillingManager` yalnizca SDK'yi surer.
 *
 * ## DEGISMEZ KURALLAR
 *
 *  1. **PENDING ASLA odenmez/tuketilmez/onaylanmaz.** Turkiye'de nakit/havale
 *     `PENDING` gelir; `PURCHASED` olunca ayni jeton yeniden gelir ve o anda
 *     odenir (ON_RESUME yenilemesi / baglanti / elle kontrol).
 *  2. **Tam OLARAK BIR bilinen urun id'si.** Sifir, iki ya da bilinmeyen id ->
 *     dokunulmaz, tuketilmez, NEDENI LOGLANIR. (Eski kod bu durumda hicbir dala
 *     girmiyor ve hicbir sey yazmiyordu: ne odeme, ne tuketme, ne log.)
 *  3. **Tuketme yalnizca DAYANIKLI Granted / eslesen AlreadyGranted sonrasi.**
 *     [GrantResult.Rejected] ve [GrantResult.PersistFailed] tuketmez. Eski kod
 *     geri cagri `false` dondugunde "zaten odenmis" sayip KOSULSUZ tuketiyordu;
 *     `false` ayni zamanda "yazamadim" demek olabilirdi -> odenmemis ve tuketilmis
 *     bir satin alma, Play'in geri vermeyecegi kalici kayip.
 *  4. **Ayni jeton, farkli urun/miktar -> Rejected.** (Defterin isi.)
 *  5. **Adet KONTROLLU carpilir** (`Math.multiplyExact`); tasarsa Reject. Defter
 *     carpilmis miktari saklar.
 *  6. **Geri cagri istisna atarsa tuketilmez.**
 *  7. Bos jeton ya da coin <= 0 -> Reject (tuketme).
 *  8. `consume` -> `ITEM_NOT_OWNED` = zaten tuketilmis: zararsiz, loglanir.
 *  9. **ESKI id'ler eski miktarla** ([StoreCatalog.legacyFulfillment]); eski
 *     `remove_ads` yalnizca onaylanir (coin yok, tuketme yok).
 */
object PurchasePolicy {

    /** Saf karar. Ayni girdi -> ayni eylem; yan etki yok. */
    fun decide(info: PurchaseInfo): FulfillmentAction {
        val ids = info.productIds
        if (ids.size != 1) {
            return FulfillmentAction.LeaveUnknown(
                "purchase must carry exactly one product id but has ${ids.size}: $ids"
            )
        }
        val productId = ids.single()
        val kind = StoreCatalog.kindOf(productId)
            ?: return FulfillmentAction.LeaveUnknown("unknown product id '$productId'")

        when (info.state) {
            PurchaseState.PENDING -> return FulfillmentAction.LeavePending(productId)
            PurchaseState.UNSPECIFIED ->
                return FulfillmentAction.LeaveUnknown("purchase state UNSPECIFIED for '$productId'")
            PurchaseState.PURCHASED -> Unit
        }

        if (info.purchaseToken.isBlank()) {
            return FulfillmentAction.Reject("blank purchase token for '$productId'")
        }

        return when (kind) {
            StoreCatalog.ProductKind.LEGACY_REMOVE_ADS -> FulfillmentAction.AcknowledgeOnly(
                productId = productId,
                purchaseToken = info.purchaseToken,
                alreadyAcknowledged = info.acknowledged,
            )

            StoreCatalog.ProductKind.SALE_PACK, StoreCatalog.ProductKind.LEGACY_COINS -> {
                val unit = StoreCatalog.coinsOf(productId)
                    ?: return FulfillmentAction.Reject("no coin amount for '$productId'")
                if (unit <= 0) return FulfillmentAction.Reject("non-positive coin amount for '$productId'")
                if (info.quantity < 1) {
                    return FulfillmentAction.Reject("invalid quantity ${info.quantity} for '$productId'")
                }
                val total = try {
                    Math.multiplyExact(unit, info.quantity)
                } catch (_: ArithmeticException) {
                    return FulfillmentAction.Reject(
                        "coin amount overflow: $unit x ${info.quantity} for '$productId'"
                    )
                }
                FulfillmentAction.GrantAndConsume(
                    productId = productId,
                    purchaseToken = info.purchaseToken,
                    coins = total,
                    quantity = info.quantity,
                    legacy = kind == StoreCatalog.ProductKind.LEGACY_COINS,
                )
            }
        }
    }
}

/**
 * [PurchasePolicy]'nin kararini UYGULAR: defteri sorar, sonuca gore Play'e
 * tuketme/onay gonderir. Android yok — `grants` ve `finisher` enjekte edilir.
 *
 * Tek oturumda ayni jeton uc yoldan gelebilir (satin alma geri cagrisi,
 * ON_RESUME sorgusu, ITEM_ALREADY_OWNED kurtarmasi). Defter cift odemeyi
 * engeller; buradaki [consumesInFlight] ise ayni jetona ust uste iki `consume`
 * gonderilmesini engeller (ikincisi `ITEM_NOT_OWNED` doner; zararsiz ama
 * gurultu ve gereksiz ag).
 *
 * @param onConsumeResult tuketme tamamlaninca (asenkron) cagrilir — UI "tuketilmemis"
 *   sayacini gunceller. Sahte finisher senkron cagirabilir.
 */
class PurchaseFulfiller(
    private val grants: PurchaseGrantPort,
    private val finisher: PurchaseFinisher,
    private val log: (String) -> Unit = {},
    private val onConsumeResult: (purchaseToken: String, result: ConsumeResult) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private val consumesInFlight = HashSet<String>()

    /** Bir satin almayi ISLER. Istisna DISARI ATMAZ: istisna = tuketme yok. */
    fun process(info: PurchaseInfo): FulfillmentOutcome = try {
        execute(PurchasePolicy.decide(info))
    } catch (e: Exception) {
        log("fulfillment threw, purchase left unconsumed: ${e.javaClass.simpleName}: ${e.message}")
        FulfillmentOutcome.PersistFailed("exception: ${e.javaClass.simpleName}")
    }

    private fun execute(action: FulfillmentAction): FulfillmentOutcome = when (action) {
        is FulfillmentAction.LeavePending -> {
            log("purchase ${action.productId} is PENDING: not granted, not consumed, not acknowledged")
            FulfillmentOutcome.Pending(action.productId)
        }

        is FulfillmentAction.LeaveUnknown -> {
            log("purchase left untouched: ${action.reason}")
            FulfillmentOutcome.Unknown(action.reason)
        }

        is FulfillmentAction.Reject -> {
            log("purchase rejected, not consumed: ${action.reason}")
            FulfillmentOutcome.Rejected(action.reason)
        }

        is FulfillmentAction.AcknowledgeOnly -> {
            if (!action.alreadyAcknowledged) {
                finisher.acknowledge(action.purchaseToken) { result ->
                    if (result != AcknowledgeResult.ACKNOWLEDGED) {
                        log("legacy ${action.productId} could not be acknowledged (Play refunds unacknowledged purchases after 3 days)")
                    }
                }
            }
            FulfillmentOutcome.LegacyRemoveAds(acknowledgeRequested = !action.alreadyAcknowledged)
        }

        is FulfillmentAction.GrantAndConsume -> grantAndConsume(action)
    }

    private fun grantAndConsume(action: FulfillmentAction.GrantAndConsume): FulfillmentOutcome {
        val request = PurchaseGrantRequest(action.purchaseToken, action.productId, action.coins)
        return when (val result = grants.grant(request)) {
            is GrantResult.Granted -> {
                consumeOnce(action.purchaseToken)
                FulfillmentOutcome.Granted(action.productId, result.coins, result.newBalance, action.legacy)
            }

            is GrantResult.AlreadyGranted -> {
                log("purchase ${action.productId} already granted; consuming again")
                consumeOnce(action.purchaseToken)
                FulfillmentOutcome.AlreadyCredited(action.productId)
            }

            is GrantResult.Rejected -> {
                log("ledger rejected ${action.productId}: ${result.reason}; not consumed")
                FulfillmentOutcome.Rejected("ledger: ${result.reason}")
            }

            GrantResult.PersistFailed -> {
                log("could not persist grant for ${action.productId}; not consumed, will retry")
                FulfillmentOutcome.PersistFailed("commit not confirmed")
            }
        }
    }

    private fun consumeOnce(token: String) {
        val first = synchronized(lock) { consumesInFlight.add(token) }
        if (!first) {
            log("consume for this token is already in flight; skipped")
            return
        }
        try {
            finisher.consume(token) { result ->
                synchronized(lock) { consumesInFlight.remove(token) }
                when (result) {
                    ConsumeResult.CONSUMED -> Unit
                    ConsumeResult.ALREADY_CONSUMED -> log("consume: ITEM_NOT_OWNED = already consumed (harmless)")
                    ConsumeResult.FAILED -> log("consume failed; grant is durable, retried on next refresh")
                }
                onConsumeResult(token, result)
            }
        } catch (e: Exception) {
            synchronized(lock) { consumesInFlight.remove(token) }
            log("consume threw: ${e.javaClass.simpleName}: ${e.message}; retried on next refresh")
            onConsumeResult(token, ConsumeResult.FAILED)
        }
    }
}

package com.miniappfactory.frontlinedefender.game.billing

/**
 * SAF (Android'siz) satin alma tipleri — [PurchaseFulfillment], `SaveManager`in
 * defteri ve `BillingManager` ayni sozlesmeyi bunlar uzerinden konusur.
 *
 * Billing SDK sabitleri BURAYA SIZMAZ: [PurchaseState] Play'in `PurchaseState`
 * degerlerinin aynasidir ve tek cevirici `BillingManager`dedir. Boylece butun
 * karar matrisi JVM'de, cihaz/Play olmadan sinanir.
 */

/** Play'in bildirdigi satin alma durumu. */
enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/**
 * Bir Play `Purchase`inin karar icin gereken kesiti.
 *
 * @param quantity Play'in bildirdigi adet. ⚠ `Purchase.getQuantity()` bugun 1
 *   doner ama Console'da "cok adetli satin alma" acilirsa TEK jetonla N adet
 *   gelir; adet YOK SAYILIRSA oyuncu N paket parasi odeyip 1 paket alir.
 * @param acknowledged Play `isAcknowledged`.
 */
data class PurchaseInfo(
    val productIds: List<String>,
    val purchaseToken: String,
    val state: PurchaseState,
    val quantity: Int,
    val acknowledged: Boolean,
)

/**
 * Defterden istenen odeme.
 *
 * @param coins **SON** miktar — adetle carpilmis (kontrollu aritmetik). Defter
 *   carpilmis miktari saklar, boylece ayni jetonun tekrar gelisi ayni degerle
 *   karsilastirilir ve adet degisirse [GrantResult.Rejected] olur.
 */
data class PurchaseGrantRequest(
    val purchaseToken: String,
    val productId: String,
    val coins: Int,
)

/** Defterde saklanan, diske YAZILMIS odeme kaydi. */
data class LedgerRecord(
    val productId: String,
    val coins: Int,
    val grantedAtMillis: Long,
)

enum class RejectReason {
    /** Bos jeton / bos urun / coin <= 0. Hicbir sey degismedi. */
    INVALID_REQUEST,

    /** Ayni jeton FARKLI urun ya da FARKLI miktarla geldi. Kayit korunur. */
    PRODUCT_OR_AMOUNT_MISMATCH,

    /** Defter kaydi okunamiyor. Otomatik onarilmaz (odemenin ne oldugu bilinmiyor). */
    CORRUPT_RECORD,

    /** Bakiye `Int.MAX_VALUE`u asardi. Hicbir sey yazilmadi. */
    WALLET_OVERFLOW,
}

/**
 * Defterin AYRINTILI ve DAYANIKLI sonucu. **Yalniz [Granted] ve [AlreadyGranted]
 * `consume`a izin verir**; ikisi de "diskte esleyen bir hak var" demektir.
 */
sealed interface GrantResult {
    /**
     * Coin, cuzdan + defter + omur sayaci TEK atomik toplu yazimla eklendi ve
     * yazim `true` dondu (diske dayanikli). Bu cagrida krediler.
     */
    data class Granted(val coins: Int, val newBalance: Int) : GrantResult

    /**
     * Bu jeton icin ESLESEN (ayni urun, ayni miktar) bir hak zaten kayitli;
     * bu cagri HICBIR SEY kredilemedi.
     *
     * @param record eslesen defter kaydi. `null` YALNIZCA jeton eski
     *   `iap_granted_tokens` listesinde bulundugunda (defter oncesi odemeler:
     *   urun/miktar saklanmamisti, yalniz "odendi" bilgisi var).
     */
    data class AlreadyGranted(val record: LedgerRecord?) : GrantResult

    /** Guvenle kredilenemez ve KREDILENMEDI. Tuketme. Yeniden denemek ayni sonucu verir. */
    data class Rejected(val reason: RejectReason) : GrantResult

    /**
     * Yazim diske DAYANIKLI olarak dogrulanamadi (`commit()` false ya da istisna).
     * Cuzdan/defter bellekteki gorunumu geri alindi; bir sonraki denemede yeni
     * bir yazim yapilir. Tuketme.
     */
    data object PersistFailed : GrantResult
}

/** [GrantResult] uretici. `SaveManager` gercek uygulamadir; testler sahte kullanir. */
fun interface PurchaseGrantPort {
    fun grant(request: PurchaseGrantRequest): GrantResult
}

/** `consumeAsync` sonucu. */
enum class ConsumeResult {
    CONSUMED,

    /** `ITEM_NOT_OWNED`: Play zaten tuketilmis diyor — zararsiz. */
    ALREADY_CONSUMED,
    FAILED,
}

/** `acknowledgePurchase` sonucu. */
enum class AcknowledgeResult { ACKNOWLEDGED, FAILED }

/**
 * Play'e giden tamamlayici cagrilar. Gercek uygulama `BillingManager`da;
 * testler ve debug paneli kayit tutan sahte kullanir.
 */
interface PurchaseFinisher {
    fun consume(purchaseToken: String, onResult: (ConsumeResult) -> Unit)
    fun acknowledge(purchaseToken: String, onResult: (AcknowledgeResult) -> Unit)
}

/** Karar katmaninin cikti eylemi. Saf: hicbir yan etki yok. */
sealed interface FulfillmentAction {
    /** Coini ode; YALNIZ dayanikli Granted/AlreadyGranted sonrasi tuket. */
    data class GrantAndConsume(
        val productId: String,
        val purchaseToken: String,
        /** Adetle carpilmis son miktar. */
        val coins: Int,
        val quantity: Int,
        val legacy: Boolean,
    ) : FulfillmentAction

    /** ESKI `remove_ads`: yalnizca onayla. Coin yok, consume yok. */
    data class AcknowledgeOnly(
        val productId: String,
        val purchaseToken: String,
        val alreadyAcknowledged: Boolean,
    ) : FulfillmentAction

    /** PENDING: ne odenir ne tuketilir ne onaylanir; sonra `PURCHASED` olunca yeniden gelir. */
    data class LeavePending(val productId: String) : FulfillmentAction

    /** Bilinmeyen/anlasilamayan: dokunma (yeni surum isleyebilir) — nedenini logla. */
    data class LeaveUnknown(val reason: String) : FulfillmentAction

    /** Guvenli islenemez: tuketme, logla. */
    data class Reject(val reason: String) : FulfillmentAction
}

/** [PurchaseFulfiller]in gozlemlenebilir sonucu — UI olaylari ve testler bunu okur. */
sealed interface FulfillmentOutcome {
    /** Coin bu cagrida krediye girdi; tuketme istegi Play'e gonderildi. */
    data class Granted(
        val productId: String,
        val coins: Int,
        val newBalance: Int,
        val legacy: Boolean,
    ) : FulfillmentOutcome

    /** Hak zaten kayitliydi (tekrar gelen jeton): kredi YOK; tuketme yeniden denendi. */
    data class AlreadyCredited(val productId: String) : FulfillmentOutcome

    data class Pending(val productId: String) : FulfillmentOutcome

    /** Eski reklamsiz hakki goruldu (onay istegi gonderildi ya da zaten onayliydi). */
    data class LegacyRemoveAds(val acknowledgeRequested: Boolean) : FulfillmentOutcome

    data class Unknown(val reason: String) : FulfillmentOutcome

    /** Karar katmani ya da defter reddetti: dokunulmadi, tuketilmedi. */
    data class Rejected(val reason: String) : FulfillmentOutcome

    /** Yazim dayanikli dogrulanamadi ya da geri cagri istisna atti: tuketilmedi. */
    data class PersistFailed(val reason: String) : FulfillmentOutcome
}

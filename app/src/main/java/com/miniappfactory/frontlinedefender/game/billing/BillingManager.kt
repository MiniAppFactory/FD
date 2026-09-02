package com.miniappfactory.frontlinedefender.game.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PLAY BILLING KOPRUSU — coin paketleri ve "Reklamlari Kaldir" (2026-09-02).
 *
 * Kron-Drive'daki `billing/BillingManager` ile AYNI desen (oradaki 2026-09-01
 * `refresh()` duzeltmesi dahil): Android'e bagli her sey burada, karar/veri
 * tarafi saf Kotlin'de ([StoreCatalog]). Boylece paket dengesi JVM testiyle
 * kilitlenir.
 *
 * ## DEGISMEZ KURALLAR
 *
 * 1. **Coin ONCE yazilir, `consume` SONRA.** Tersi olursa tuketilmis ama
 *    yazilmamis bir satin alma KALICI olarak kaybolur — Play onu bir daha
 *    vermez.
 *
 *    ⚠ Bu kuralin eski son cumlesi ("tuketim yalnizca [onCoinsPurchased]
 *    `true` dondugunde yapilir") KODLA CELISIYORDU ve kod hakliydi: `consume`
 *    DONUS DEGERINE BAKILMADAN cagrilir. Sebep, yazimin basarisiz olmasi
 *    degil — `false` "zaten odenmis" demektir ve o durumda onceki oturumda
 *    YARIM KALMIS bir `consumeAsync` olabilir. Kosulsuz yeniden tuketim, o
 *    satin almayi tamamlayip urunu yeniden satin alinabilir yapan TEK yoldur;
 *    kosula baglamak, jetonu sonsuza kadar tuketilmemis birakirdi.
 *
 *    Yani sira garantisi ("once yaz, sonra tuket") GECERLI; tuketimin
 *    kosullu oldugu iddiasi YANLISTI.
 * 2. **Idempotenlik cagiranin isi.** `queryPurchasesAsync` her acilista
 *    tuketilmemis satin almalari yeniden getirir; ayni jetonun iki kez
 *    odenmesini engelleyen kayit `SaveManager.grantPurchasedCoins`tadir.
 * 3. **PENDING yutulmaz.** Turkiye'de nakit/havale odemeleri `PENDING` gelir.
 *    Ele alinmazsa oyuncu oder ve hicbir sey almaz; bu durumda satin alma
 *    SONRA `PURCHASED`a doner ve o anda odenir.
 * 4. **Tuketilmeyen urun `acknowledge` edilmeli.** Uc gun icinde
 *    onaylanmayan satin almayi Play OTOMATIK iade eder.
 * 5. **Fiyat metni koda yazilmaz.** Ekrandaki her fiyat
 *    `ProductDetails.oneTimePurchaseOfferDetails.formattedPrice`tan gelir.
 *
 * ## Sunucusuz dogrulama
 *
 * Bu oyunun sunucusu ve hesabi yok. Jeton dogrulamasi (Play Developer API)
 * backend ister; yok. Yalnizca `BillingClient`in dondurdugune guveniriz. Kabul
 * edilebilir: coin bakiyesi ZATEN yerel (`SharedPreferences`) ve rootlu cihazda
 * bugun de duzenlenebilir — sunucu dogrulamasi bugun korunmayan bir seyi
 * korumuyor olurdu.
 *
 * ## Nerede yasar
 *
 * FD'de ViewModel yok; `GameScreen` bunu `remember` ile kurar, `ON_RESUME`da
 * [refresh] cagirir ve `DisposableEffect` ile [endConnection] eder.
 */
class BillingManager(
    context: Context,
    /**
     * Coin yazimi. **`true` = yazildi.** Ayni jeton daha once odenmisse `false`
     * donmeli — ama `false` tuketimi ENGELLEMEZ (gerekce: sinif belgesi kural 1).
     */
    private val onCoinsPurchased: (purchaseToken: String, coins: Int) -> Boolean,
    /** "Reklamlari Kaldir" hakkinin son durumu — her kontrolde yazilir. */
    private val onRemoveAdsChanged: (purchased: Boolean) -> Unit,
) {

    /** Baglanti ayakta mi — magaza ekrani "baglaniyor" yazisi icin okur. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Urun id -> Play'den gelen detay (onemlisi `formattedPrice`). Bos olmasi
     * NORMAL: urunler Play Console'da henuz olusturulmamis olabilir; magaza
     * ekrani o satira "su an satin alinamiyor" yazar, satiri GIZLEMEZ.
     */
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    /** Satin alma akisi surerken magaza butonlari kilitlenir. */
    private val _purchaseInFlight = MutableStateFlow(false)
    val purchaseInFlight: StateFlow<Boolean> = _purchaseInFlight.asStateFlow()

    /** Elle baslatilan "satin almalari geri yukle" isleminin son durumu. */
    enum class RestoreState { IDLE, RUNNING, DONE, FAILED }

    private val _restoreState = MutableStateFlow(RestoreState.IDLE)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    /**
     * Baglanti kurulmadan once dokunulmus "satin almalari geri yukle" istegi.
     * Baglanti kurulunca kosar, baglanti basarisiz olursa `FAILED` olur.
     */
    private var pendingManualRestore = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _purchaseInFlight.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }

            // Kullanici vazgecti — hata degil, sessizce gecilir.
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> Log.w(TAG, "Satin alma basarisiz: ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        // Bekleyen satin almalar ACIKCA etkin. Billing 6+'da bu bayrak olmadan
        // `PENDING` hic gelmez ve nakit/havale ile odeyen oyuncu parasini verip
        // hicbir sey almaz.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /**
     * Baglantiyi kurar, duserse SINIRLI sayida yeniden dener. Sinirsiz dongu
     * pil yakar; Play Store guncellenirken baglanti duser ve yeniden
     * baglanmayan bir istemci magazayi oturum boyunca olu birakir.
     */
    fun connect(attempt: Int = 0) {
        if (client.isReady) {
            _ready.value = true
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _ready.value = true
                    queryProducts()
                    // Baglanti kurulmadan once dokunulmus "geri yukle" varsa
                    // ISTEK SIMDI KOSAR ve sonucu oyuncuya bildirilir.
                    if (pendingManualRestore) {
                        pendingManualRestore = false
                        restorePurchases(manuel = true)
                        return
                    }
                    // HER BAGLANTIDA: yarim kalan satin almalari tamamlar ve
                    // "Reklamlari Kaldir" hakkini geri yukler. Bu cagri olmadan
                    // "param gitti, urun gelmedi" destek talebi kacinilmaz.
                    restorePurchases()
                } else {
                    _ready.value = false
                    // Baglantinin KENDISI basarisiz: bekleyen elle istek artik
                    // gercekten basarisizdir ve oyuncuya oyle bildirilir.
                    if (pendingManualRestore) {
                        pendingManualRestore = false
                        _restoreState.value = RestoreState.FAILED
                    }
                    Log.w(TAG, "Billing baglanamadi: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _ready.value = false
                if (attempt < MAX_RECONNECT_ATTEMPTS) connect(attempt + 1)
            }
        })
    }

    fun endConnection() {
        _ready.value = false
        client.endConnection()
    }

    private fun queryProducts() {
        val urunler = StoreCatalog.allProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(urunler).build()
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Urun detaylari alinamadi: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            // Billing 8: callback duz liste degil `QueryProductDetailsResult`
            // doner. Bulunamayan urunler `unfetchedProductList`te gelir — Play
            // Console'da henuz olusturulmamis urun icin NORMAL durum.
            _products.value = queryResult.productDetailsList.associateBy { it.productId }
        }
    }

    /**
     * Play'deki mevcut satin almalari okur. Iki isi var, ikisi de kritik:
     *  1. **Yarim kalanlari tamamlar** — coin yazilmis ama tuketilmemis, ya da
     *     hic yazilmamis satin almalar burada ele alinir.
     *  2. **"Reklamlari Kaldir" hakkini geri yukler** — tuketilmeyen urun
     *     oldugu icin cihaz degisse de Play'den geri gelir. (Coin paketleri
     *     GELMEZ: tuketildikten sonra Play'de gorunmezler.)
     */
    fun restorePurchases(manuel: Boolean = false) {
        if (!client.isReady) {
            // ⚠ ESKIDEN BURADA DOGRUDAN `FAILED` YAZILIYORDU ve durum KILITLENIYORDU.
            //
            // Oyuncu magazayi acar acmaz "geri yukle"ye dokunursa baglanti henuz
            // kurulmamis olabilir. Eski kod bunu BASARISIZLIK olarak raporluyordu,
            // oysa hicbir sey denenmemisti. Daha kotusu, durum temizlenmiyordu:
            // `BillingManager` `remember(campaignProgress)` ile tutuldugu ve magaza
            // yalnizca bir `storeOpen` bayragiyla acilip kapandigi icin ekrani
            // kapatip acmak AYNI StateFlow'u geri okur. `refresh()` de temizleyemez
            // (o `manuel = false` ile cagirir). Yani hata mesaji, ikinci bir elle
            // deneme ya da surec olumune kadar ekranda kalirdi — ustelik satin alma
            // gercekten VARSA ayni ekranda "SATIN ALINDI" rozetiyle CELISEREK.
            //
            // Artik dokunus BEKLEMEYE alinir: baglanti kurulunca istek gercekten
            // kosar ve oyuncu OLAN sonucu gorur.
            if (manuel) {
                _restoreState.value = RestoreState.RUNNING
                pendingManualRestore = true
            }
            connect()
            return
        }
        if (manuel) _restoreState.value = RestoreState.RUNNING
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // HAK BURADA SILINMEZ. Play "hayir" demedi, "cevap veremiyorum"
                // dedi (cogunlukla ag yok). Bu dali sessizce `false` yazmaya
                // baglamak, cevrimdisi bir acilista odenmis hakki silmek olurdu.
                Log.w(TAG, "Geri yukleme basarisiz: ${result.debugMessage}")
                if (manuel) _restoreState.value = RestoreState.FAILED
                return@queryPurchasesAsync
            }
            val reklamsiz = purchases.any {
                it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    StoreCatalog.PRODUCT_REMOVE_ADS in it.products
            }
            onRemoveAdsChanged(reklamsiz)
            purchases.forEach { handlePurchase(it) }
            if (manuel) _restoreState.value = RestoreState.DONE
        }
    }

    /**
     * Uygulama on plana gelince cagrilir (`ON_RESUME`).
     *
     * Kron-Drive 2026-09-01 dersi: `restorePurchases` yalnizca ILK baglantida
     * kosuyordu; oyuncu `remove_ads` alip uygulamayi cevrimdisi acarsa hak
     * yazilamiyor ve surec omru boyunca BIR DAHA DENENMIYORDU — "odedim, hala
     * reklam goruyorum". Play'in kendi onerisi de bu: her on plana gelmede
     * satin almalar sorgulanir (arka planda tamamlanan `PENDING` odeme dahil).
     */
    fun refresh() {
        if (client.isReady) restorePurchases() else connect()
    }

    /** Magazadan tek bir urun icin satin alma akisini baslatir. */
    fun launchPurchase(activity: Activity, productId: String) {
        val details = _products.value[productId] ?: run {
            Log.w(TAG, "Urun detayi yok, satin alma baslatilamadi: $productId")
            return
        }
        if (_purchaseInFlight.value) return
        _purchaseInFlight.value = true
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseInFlight.value = false
            Log.w(TAG, "Satin alma akisi acilamadi: ${result.debugMessage}")
        }
    }

    /**
     * Tek bir satin almayi isler.
     *
     * `PENDING` satin alma RAHAT BIRAKILIR, hata sayilmaz: kullanici odemeyi
     * tamamlayinca durum `PURCHASED` olur ve bir sonraki [restorePurchases]te
     * odenir.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val pack = purchase.products.firstNotNullOfOrNull { StoreCatalog.packOf(it) }
        if (pack != null) {
            // ⚠ ADET (quantity) HESABA KATILIR.
            //
            // Play Console'da "cok adetli satin almaya izin ver" tek seferlik
            // urunlerde VARSAYILAN OLARAK KAPALI, yani bugun her `Purchase`
            // quantity = 1 ile gelir ve odeme dogrudur. Ama bu, ISTEMCININ
            // ZORLAYAMADIGI bir Console ayarina bagli sessiz bir tuzak: o
            // anahtar bir gun acilirsa Play TEK bir `Purchase` icinde N adet
            // ve TEK jeton dondurur. Sabit `pack.coins` odemek, oyuncunun N
            // paket parasi odeyip 1 paket almasi olurdu — ve jeton
            // `iap_granted_tokens`e yazildigi icin fark KALICI olarak kaybolur,
            // idempotenlik korumasi da telafiyi imkansiz kilardi.
            //
            // Jeton hala TEK idempotenlik anahtaridir; degisen yalnizca miktar.
            val adet = purchase.quantity.coerceAtLeast(1)
            // TUKETILEN: once yaz, sonra tuket (kural 1). `yazildi == false` ise
            // jeton zaten odenmis ama tuketim yarim kalmis olabilir — urunun
            // yeniden satin alinabilir olmasi icin yine tuketilir.
            val yazildi = onCoinsPurchased(purchase.purchaseToken, pack.coins * adet)
            if (!yazildi) Log.i(TAG, "Jeton zaten odenmis, yalnizca tuketiliyor: ${pack.id}")
            consume(purchase)
            return
        }

        if (StoreCatalog.PRODUCT_REMOVE_ADS in purchase.products) {
            // TUKETILMEYEN: tuketilmez, yalnizca onaylanir (kural 4).
            onRemoveAdsChanged(true)
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "Onaylanamadi (3 gun icinde iade riski): ${result.debugMessage}")
                    }
                }
            }
        }
    }

    private fun consume(purchase: Purchase) {
        client.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Tuketilemedi: ${result.debugMessage}")
            }
        }
    }

    private companion object {
        const val TAG = "FDBilling"

        /** Play Store guncellenirken dusen baglanti icin sinirli yeniden deneme. */
        const val MAX_RECONNECT_ATTEMPTS = 3
    }
}

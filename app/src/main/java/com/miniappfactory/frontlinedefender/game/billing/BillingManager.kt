package com.miniappfactory.frontlinedefender.game.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * PLAY BILLING KOPRUSU — coin paketleri (2026-09-02; dayaniklilik yeniden yazimi 2026-09-30).
 *
 * Android'e bagli olan HER SEY burada (SDK cagrilari, yasam dongusu); KARAR ve
 * DEFTER tarafi saf Kotlin'de: [PurchasePolicy]/[PurchaseFulfiller] (ne yapilir),
 * `SaveManager.fulfillPurchase` (kalici defter), [StoreSession] (baglanti/urun
 * durumu). Boylece butun odeme matrisi JVM testiyle kilitlenir.
 *
 * ## DEGISMEZ KURALLAR (ayrintisi [PurchasePolicy]de)
 *
 * 1. **Coin ONCE dayanikli yazilir, `consume` SONRA** — ve `consume` YALNIZCA
 *    dayanikli `Granted` ya da ESLESEN `AlreadyGranted` sonrasi. Eski surum geri
 *    cagri `false` dondugunde "zaten odenmis" sayip kosulsuz tuketiyordu; `false`
 *    ayni zamanda "yazamadim" demek olabildigi icin bu, odenmemis + tuketilmis =
 *    kalici kayip demekti.
 * 2. **PENDING yutulmaz, odenmez, tuketilmez, onaylanmaz.** `PURCHASED` olunca
 *    ayni jeton ON_RESUME/baglanti/elle kontrolle yeniden gelir.
 * 3. **Bilinmeyen urun id'si loglanir; asla odenmez/tuketilmez/onaylanmaz.**
 * 4. **Eski `coins_*` id'leri ESKI miktarla odenir; eski `remove_ads` yalnizca
 *    onaylanir** (Play onaylanmayan satin almayi 3 gun sonra otomatik iade eder).
 * 5. **Fiyat metni koda yazilmaz.** Ekrandaki her fiyat `ProductDetails`in
 *    `formattedPrice`indan gelir.
 * 6. **`restorePurchases` ASLA `iap_remove_ads = false` yazmaz.** Play "bu urunu
 *    gormuyorum" dediginde bunun iade mi, cevrimdisi mi, baska hesap mi oldugu
 *    ayirt edilemez; eski alicinin yapiskan isareti silinmez.
 * 7. **Coin paketleri Play'den "geri yuklenemez"**: tuketildikten sonra Play'de
 *    gorunmezler. "Geri yukle" yalnizca ODENMIS ama TESLIM EDILMEMIS (yarim kalan)
 *    satin almalari bulur; ekran metni bunu vaat etmemeli.
 *
 * ## Sunucusuz dogrulama
 *
 * Bu oyunun sunucusu ve hesabi yok; jeton dogrulamasi (Play Developer API)
 * backend ister. Yalnizca `BillingClient`in dondurduguna guveniriz. Coin bakiyesi
 * zaten yerel ve rootlu cihazda duzenlenebilir — sunucu dogrulamasi bugun
 * korunmayan bir seyi korumuyor olurdu.
 *
 * ## Nerede yasar
 *
 * FD'de ViewModel yok; `GameScreen` bunu `remember` ile kurar, `ON_RESUME`da
 * [refresh] cagirir ve `DisposableEffect` ile [endConnection] eder. Play geri
 * cagrilari ana is parcaciginda gelir; defter yazimi (`commit()`) da orada
 * kosar (kucuk dosya, ms).
 *
 * ## Dogrulanmadi
 *
 * Bu sinifin SDK'ya bagli kismi (baglanti, `launchBillingFlow`, `consumeAsync`)
 * JVM'de sinanamaz ve gercek Play'e/cihaza KARSI CALISTIRILMADI. Karar/defter
 * mantigi (PurchasePolicy, PurchaseFulfiller, PurchaseGrantLedger, StoreSession)
 * JVM testleriyle kilitlidir.
 */
class BillingManager(
    context: Context,
    /**
     * Dayanikli coin yazimi: **acik sonuc** ([GrantResult]). `Boolean` DEGIL — `false`
     * hem "zaten odenmis" hem "yazilamadi" demek olabiliyordu. `GameScreen` bunu
     * `SaveManager.fulfillPurchase` uzerinden kurar ve `Granted` sonrasi cuzdan
     * sahibini yeniler.
     */
    private val grantPurchase: (PurchaseGrantRequest) -> GrantResult,
    /**
     * ESKI "Reklamlari Kaldir" satin alimi goruldugunde `true` ile cagrilir. **`false`
     * ile ASLA cagrilmaz** (yapiskan eski-alici isareti). Reklamlar kalktiysa cagiran
     * bunu yok sayabilir.
     */
    private val onRemoveAdsChanged: (purchased: Boolean) -> Unit = {},
) {

    private val session = StoreSession()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    /** Baglanti durumu: CONNECTING / READY / UNAVAILABLE. */
    val connection: StateFlow<StoreConnection> = session.connection

    /** Urun detaylarinin durumu: LOADING / LOADED / FAILED. */
    val productsState: StateFlow<ProductsState> = session.productsState

    /** Baglanti kurulamadiysa tanilama metni (yerellestirilmez; UI [FailureReason] metnini kullanir). */
    val unavailableReason: StateFlow<String?> = session.unavailableReason

    /** Baglanti ayakta mi — eski ekranlar bunu okur. `connection == READY` ile aynidir. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Urun id -> Play'den gelen detay (onemlisi `formattedPrice`). YALNIZCA satistaki urunler.
     * Eksik satir NORMAL olabilir (Console'da henuz olusturulmamis); ekran o satira
     * "su an satin alinamiyor" yazar, satiri GIZLEMEZ.
     */
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    /** Satin alma akisi surerken magaza butonlari kilitlenir. */
    private val _purchaseInFlight = MutableStateFlow(false)
    val purchaseInFlight: StateFlow<Boolean> = _purchaseInFlight.asStateFlow()

    /**
     * Play'in son sorguda PENDING (odeme tamamlanmamis, ornegin nakit/havale) bildirdigi satin
     * alma sayisi. > 0 iken ekran "odeme onaylaninca coin otomatik gelir" notu gosterebilir.
     */
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** Elle baslatilan "satin almalari kontrol et" isleminin son durumu. */
    enum class RestoreState { IDLE, RUNNING, DONE, FAILED }

    private val _restoreState = MutableStateFlow(RestoreState.IDLE)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    /**
     * Tek seferlik olaylar (Granted / Pending / Cancelled / Failed / AlreadyCredited). Kanal
     * TAMPONLU: magaza ekrani kapaliyken gelen olay (ornegin arka planda tamamlanan odeme) ekran
     * acilinca bir kez gosterilir, sessizce KAYBOLMAZ. Tek toplayici varsayilir.
     */
    private val eventChannel = Channel<StoreEvent>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<StoreEvent> = eventChannel.receiveAsFlow()

    /**
     * Baglanti kurulmadan once dokunulmus "kontrol et" istegi. Baglanti kurulunca kosar,
     * baglanti basarisiz olursa `FAILED` olur.
     */
    private var pendingManualRestore = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _purchaseInFlight.value = false
        when (result.responseCode) {
            BillingResponseCode.OK -> handleInfos(
                infos = purchases.orEmpty().map { it.toInfo() },
                announce = true,
                fullQuery = false,
                fulfiller = fulfiller,
            )

            // Kullanici vazgecti — hata degil.
            BillingResponseCode.USER_CANCELED -> emit(StoreEvent.Cancelled)

            // Bu tuketilebilir urunun onceki alimi TUKETILMEMIS: kurtar (odenmemisse ode, tuket).
            BillingResponseCode.ITEM_ALREADY_OWNED -> recover(announce = true, manual = false)

            else -> {
                Log.w(TAG, "Satin alma basarisiz: ${describe(result)}")
                emit(StoreEvent.Failed(reasonOf(result), describe(result)))
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        // Bekleyen satin almalar ACIKCA etkin. Billing 6+'da bu bayrak olmadan `PENDING` hic
        // gelmez ve nakit/havale ile odeyen oyuncu parasini verip hicbir sey almaz.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Gercek Play'e tuketme/onay gonderen uygulayici. */
    private val realFinisher = object : PurchaseFinisher {
        override fun consume(purchaseToken: String, onResult: (ConsumeResult) -> Unit) {
            client.consumeAsync(
                ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
            ) { result, _ ->
                onResult(
                    when (result.responseCode) {
                        BillingResponseCode.OK -> ConsumeResult.CONSUMED
                        BillingResponseCode.ITEM_NOT_OWNED -> ConsumeResult.ALREADY_CONSUMED
                        else -> {
                            Log.w(TAG, "Tuketilemedi: ${describe(result)}")
                            ConsumeResult.FAILED
                        }
                    }
                )
            }
        }

        override fun acknowledge(purchaseToken: String, onResult: (AcknowledgeResult) -> Unit) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
            ) { result ->
                onResult(
                    if (result.responseCode == BillingResponseCode.OK) {
                        AcknowledgeResult.ACKNOWLEDGED
                    } else {
                        Log.w(TAG, "Onaylanamadi (3 gun icinde iade riski): ${describe(result)}")
                        AcknowledgeResult.FAILED
                    }
                )
            }
        }
    }

    private val fulfiller = PurchaseFulfiller(
        grants = PurchaseGrantPort { grantPurchase(it) },
        finisher = realFinisher,
        log = { Log.i(TAG, it) },
    )

    // ---------------------------------------------------------------- baglanti

    /**
     * Baglantiyi kurar. **Tek ucus:** zaten yoldaysa ikinci `startConnection` BASLATILMAZ
     * (soguk acilista `GameScreen` yasam dongusu gozlemcisi + acik `connect()` iki kez cagirir).
     * Duserse SINIRLI sayida ustel geri cekilmeyle yeniden dener.
     */
    fun connect() {
        if (closed) return
        if (client.isReady) {
            markReady()
            return
        }
        if (!session.beginConnect()) return
        _ready.value = false
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                if (result.responseCode == BillingResponseCode.OK) {
                    markReady()
                    ensureProducts()
                    // Baglanti kurulmadan once dokunulmus elle istek varsa SIMDI kosar.
                    val manual = pendingManualRestore
                    pendingManualRestore = false
                    // HER BAGLANTIDA: yarim kalan satin almalari tamamlar. Bu cagri olmadan
                    // "param gitti, coin gelmedi" destek talebi kacinilmaz.
                    recover(announce = manual, manual = manual)
                } else {
                    _ready.value = false
                    session.connectFailed(describe(result))
                    // Baglantinin KENDISI basarisiz: bekleyen elle istek artik gercekten basarisizdir.
                    if (pendingManualRestore) {
                        pendingManualRestore = false
                        _restoreState.value = RestoreState.FAILED
                    }
                    Log.w(TAG, "Billing baglanamadi: ${describe(result)}")
                }
            }

            override fun onBillingServiceDisconnected() {
                if (closed) return
                _ready.value = false
                val delayMs = session.disconnected()
                if (delayMs != null) mainHandler.postDelayed({ connect() }, delayMs)
            }
        })
    }

    private fun markReady() {
        session.connectSucceeded()
        _ready.value = true
    }

    fun endConnection() {
        closed = true
        session.close()
        mainHandler.removeCallbacksAndMessages(null)
        _ready.value = false
        client.endConnection()
    }

    // ---------------------------------------------------------------- urunler

    /**
     * Satistaki urunlerden biri eksikse (bos sonuc, ag hatasi, Console'da eksik urun) VE bir sorgu
     * yolda degilse yeniden sorar. `refresh()` ve magaza acilisi bunu cagirir; eskiden sorgu yalnizca
     * ilk baglantida bir kez calisiyor ve basarisiz olursa magaza oturum boyunca bos kaliyordu.
     */
    fun ensureProducts() {
        if (closed) return
        if (!client.isReady) {
            connect()
            return
        }
        if (session.connection.value != StoreConnection.READY) markReady()
        val haveAll = _products.value.keys.containsAll(StoreCatalog.allProductIds)
        if (session.beginProductQuery(haveAll)) queryProducts()
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
            if (closed) return@queryProductDetailsAsync
            if (result.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "Urun detaylari alinamadi: ${describe(result)}")
                session.productsFailed()
                return@queryProductDetailsAsync
            }
            // Billing 8: bulunamayan urunler `unfetchedProductList`te gelir — Play Console'da henuz
            // olusturulmamis urun icin NORMAL durum. Yalnizca SATISTAKI id'ler alinir.
            _products.value = queryResult.productDetailsList
                .filter { it.productId in StoreCatalog.allProductIds }
                .associateBy { it.productId }
            session.productsLoaded()
        }
    }

    // ---------------------------------------------------------------- kurtarma / yenileme

    /**
     * Play'deki mevcut satin almalari okur ve **yarim kalanlari tamamlar** (odenmis ama
     * tuketilmemis, ya da hic yazilmamis; arka planda tamamlanan PENDING dahil).
     *
     * ⚠ Coin paketleri Play'den "GERI YUKLENMEZ": tuketildikten sonra Play'de gorunmezler. Bu
     * cagri yalnizca teslim edilmemis satin almalari bulur. Ayrica ESKI `remove_ads`i onaylar ve
     * yapiskan isareti `true` yapar; **`false` ASLA yazilmaz** (bkz. sinif belgesi kural 6).
     *
     * @param manuel oyuncu elle istedi: durum [restoreState]e yazilir, sonuclar olay olarak bildirilir.
     */
    fun restorePurchases(manuel: Boolean = false) = recover(announce = manuel, manual = manuel)

    private fun recover(announce: Boolean, manual: Boolean) {
        if (closed) return
        if (!client.isReady) {
            // Oyuncu magazayi acar acmaz "kontrol et"e dokunursa baglanti henuz kurulmamis olabilir.
            // Bu BASARISIZLIK degildir (hicbir sey denenmedi): istek BEKLEMEYE alinir, baglanti
            // kurulunca gercekten kosar ve oyuncu OLAN sonucu gorur.
            if (manual) {
                _restoreState.value = RestoreState.RUNNING
                pendingManualRestore = true
            }
            connect()
            return
        }
        if (manual) _restoreState.value = RestoreState.RUNNING
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (closed) return@queryPurchasesAsync
            if (result.responseCode != BillingResponseCode.OK) {
                // Play "hayir" demedi, "cevap veremiyorum" dedi. Hicbir hak SILINMEZ.
                Log.w(TAG, "Satin almalar sorgulanamadi: ${describe(result)}")
                if (manual) _restoreState.value = RestoreState.FAILED
                return@queryPurchasesAsync
            }
            handleInfos(
                infos = purchases.map { it.toInfo() },
                announce = announce,
                fullQuery = true,
                fulfiller = fulfiller,
            )
            if (manual) _restoreState.value = RestoreState.DONE
        }
    }

    /**
     * Uygulama on plana gelince cagrilir (`ON_RESUME`). Kron-Drive 2026-09-01 dersi: satin almalar
     * yalnizca ilk baglantida sorgulanirsa cevrimdisi acilista yazilamayan hak ve arka planda
     * tamamlanan PENDING odeme surec omru boyunca bir daha denenmez. Eksik urunler de yeniden sorulur.
     */
    fun refresh() {
        if (closed) return
        if (client.isReady) {
            recover(announce = false, manual = false)
            ensureProducts()
        } else {
            connect()
        }
    }

    // ---------------------------------------------------------------- satin alma akisi

    /** Magazadan tek bir urun icin satin alma akisini baslatir. */
    fun launchPurchase(activity: Activity, productId: String) {
        if (closed) return
        if (productId !in StoreCatalog.allProductIds) {
            // Satista olmayan (ornegin eski) urun ASLA baslatilmaz.
            Log.w(TAG, "Satista olmayan urun istendi: $productId")
            emit(StoreEvent.Failed(FailureReason.PRODUCT_UNAVAILABLE, "product not on sale: $productId"))
            return
        }
        if (_purchaseInFlight.value) return
        if (!client.isReady) {
            emit(StoreEvent.Failed(FailureReason.NOT_READY, "not connected to Google Play"))
            connect()
            return
        }
        val details = _products.value[productId]
        if (details == null) {
            Log.w(TAG, "Urun detayi yok, satin alma baslatilamadi: $productId")
            emit(StoreEvent.Failed(FailureReason.PRODUCT_UNAVAILABLE, "no product details for $productId"))
            ensureProducts()
            return
        }
        _purchaseInFlight.value = true
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        // Billing 8: tek seferlik urunlerde secilen teklifin jetonu (varsa) verilmeli.
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { productParams.setOfferToken(it) }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        when (result.responseCode) {
            BillingResponseCode.OK -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _purchaseInFlight.value = false
                recover(announce = true, manual = false)
            }

            else -> {
                _purchaseInFlight.value = false
                Log.w(TAG, "Satin alma akisi acilamadi: ${describe(result)}")
                emit(StoreEvent.Failed(reasonOf(result), describe(result)))
            }
        }
    }

    // ---------------------------------------------------------------- isleme

    /**
     * Bir grup satin almayi [fulfiller]a verir ve sonuclari olaya cevirir.
     *
     * @param announce true = kullanici akisi (ya da elle/ITEM_ALREADY_OWNED kurtarma): tekrar gelen
     *   jeton, PENDING, bilinmeyen/reddedilen bildirilir. false = sessiz arka plan yenilemesi; yine
     *   de `Granted` (odeme arkadan tamamlandi) ve `SAVE_FAILED` (oyuncu para odedi, coin YAZILAMADI)
     *   HER ZAMAN bildirilir.
     * @param fullQuery true = `queryPurchasesAsync`in tam listesi: PENDING sayaci bundan guncellenir.
     */
    private fun handleInfos(
        infos: List<PurchaseInfo>,
        announce: Boolean,
        fullQuery: Boolean,
        fulfiller: PurchaseFulfiller,
    ) {
        var pending = 0
        for (info in infos) {
            try {
                when (val outcome = fulfiller.process(info)) {
                    is FulfillmentOutcome.Granted ->
                        emit(StoreEvent.Granted(outcome.productId, outcome.coins, outcome.legacy))

                    is FulfillmentOutcome.AlreadyCredited ->
                        if (announce) emit(StoreEvent.AlreadyCredited(outcome.productId))

                    is FulfillmentOutcome.Pending -> {
                        pending++
                        if (announce) emit(StoreEvent.Pending(outcome.productId))
                    }

                    is FulfillmentOutcome.LegacyRemoveAds -> onRemoveAdsChanged(true)

                    is FulfillmentOutcome.Unknown ->
                        if (announce) emit(StoreEvent.Failed(FailureReason.UNKNOWN_ITEM, outcome.reason))

                    is FulfillmentOutcome.Rejected ->
                        if (announce) emit(StoreEvent.Failed(FailureReason.REJECTED, outcome.reason))

                    is FulfillmentOutcome.PersistFailed ->
                        emit(StoreEvent.Failed(FailureReason.SAVE_FAILED, outcome.reason))
                }
            } catch (e: Exception) {
                // Bir satin alimin isleme hatasi digerlerini ya da uygulamayi DUSURMEZ; tuketilmemis kalir.
                Log.w(TAG, "Satin alma islenirken hata, tuketilmedi: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (fullQuery) _pendingCount.value = pending
    }

    private fun emit(event: StoreEvent) {
        eventChannel.trySend(event)
    }

    private fun Purchase.toInfo(): PurchaseInfo = PurchaseInfo(
        productIds = products,
        purchaseToken = purchaseToken,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNSPECIFIED
        },
        quantity = quantity,
        acknowledged = isAcknowledged,
    )

    private fun reasonOf(result: BillingResult): FailureReason = when (result.responseCode) {
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.NETWORK_ERROR -> FailureReason.NETWORK

        BillingResponseCode.BILLING_UNAVAILABLE,
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> FailureReason.BILLING_UNAVAILABLE

        BillingResponseCode.ITEM_UNAVAILABLE -> FailureReason.PRODUCT_UNAVAILABLE
        BillingResponseCode.DEVELOPER_ERROR -> FailureReason.DEVELOPER_ERROR
        else -> FailureReason.BILLING_ERROR
    }

    private fun describe(result: BillingResult): String =
        "code ${result.responseCode}: ${result.debugMessage}"

    // ---------------------------------------------------------------- DEBUG: sahte satin alma

    /**
     * YALNIZ DEBUG: sahte bir satin almayi GERCEK isleme hattindan ([PurchasePolicy] ->
     * [PurchaseFulfiller] -> `SaveManager.fulfillPurchase`) gecirir. Yalnizca Play'e giden
     * tuketme/onay sahtedir ([FakeBilling.consumedTokens]). Release'te ([FakeBilling.enabled]
     * `false`) hicbir sey yapmaz.
     */
    fun debugSubmit(info: PurchaseInfo) {
        if (!FakeBilling.enabled) return
        handleInfos(listOf(info), announce = true, fullQuery = false, fulfiller = debugFulfiller)
    }

    /** Debug paneli senaryosunu calistirir (bkz. [FakeBilling.Scenario]). */
    fun debugRun(scenario: FakeBilling.Scenario) {
        if (!FakeBilling.enabled) return
        when (scenario) {
            FakeBilling.Scenario.CANCEL -> emit(StoreEvent.Cancelled)
            FakeBilling.Scenario.ARM_PERSIST_FAILURE -> FakeBilling.armPersistFailure()
            else -> FakeBilling.infoFor(scenario)?.let { debugSubmit(it) }
        }
    }

    private val debugFulfiller: PurchaseFulfiller by lazy {
        FakeBilling.newDebugFulfiller(
            grants = PurchaseGrantPort { grantPurchase(it) },
            log = { Log.i(TAG, "[debug] $it") },
        )
    }

    private companion object {
        const val TAG = "FDBilling"
    }
}

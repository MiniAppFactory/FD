package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.BuildConfig
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog

/**
 * YALNIZ DEBUG — SAHTE SATIN ALMA (Play Console beklemeden test icin).
 *
 * Gercek akis ucu ayni anda saglaninca calisir: uygulama bir Play track'ine
 * yuklenmis, urunler Console'da olusturulmus, cihazdaki hesap "lisans
 * testcisi". Hicbiri kod tarafinda degil — yani magaza yazildigi gun test
 * edilemez. Oysa magazanin GERCEKTEN kirilgan kismi Play'de degil bizde.
 *
 * ## 2026-09-30: sahte akis da GERCEK islemci uzerinden gecer
 *
 * Eskiden debug paneli dogrudan `grantPurchasedCoins` cagiriyordu — yani
 * SAHTE bir yol test ediliyor, gercek karar hatti (PENDING/adet/eski id/
 * bilinmeyen urun/yazim basarisizligi) hic sinanmiyordu. Simdi her senaryo bir
 * [PurchaseInfo] uretir ve `BillingManager.debugSubmit` onu GERCEK
 * [PurchasePolicy] -> [PurchaseFulfiller] -> `SaveManager.fulfillPurchase`
 * hattindan gecirir. Yalnizca Play'e giden tuketme/onay sahtedir
 * ([recordingFinisher]); sahte jeton Play'de zaten yoktur.
 *
 * ## RELEASE'TE OLU KOD
 *
 * [enabled] `BuildConfig.DEBUG`e bagli; R8 release derlemesinde sabit `false`a
 * katlanir. Yine de `BillingManager.debugSubmit`/`debugRun` bayragi ACIKCA
 * kontrol eder — "zaten release'te calismaz" varsayimi bu depoda daha once
 * birkac kez kapatilan sessiz-yanlis desenidir.
 *
 * Uretilen jeton GERCEK bir Play jetonu DEGIL; bilerek `debug-` onekli.
 */
object FakeBilling {

    /** Sahte satin alma yalnizca debug derlemede acik. */
    val enabled: Boolean get() = BuildConfig.DEBUG

    /** Debug panelinin sundugu senaryolar. */
    enum class Scenario {
        /** Yeni jetonla en kucuk paketin normal satin alimi. */
        BUY,

        /** PENDING: odeme tamamlanmadi (nakit/havale). Hicbir sey odenmez/tuketilmez. */
        PENDING,

        /** Bekleyen satin alma PURCHASED oldu: AYNI jeton simdi bir kez odenir. */
        COMPLETE_PENDING,

        /** Kullanici Play penceresini kapatti. */
        CANCEL,

        /** Bir SONRAKI odemenin yazimi basarisiz olsun; ardindan [REPLAY_LAST] ile yeniden dene. */
        ARM_PERSIST_FAILURE,

        /** ESKI `coins_1200` urunu: ESKI miktarla (1.200) odenir. */
        LEGACY_COINS_1200,

        /** SON gonderilen satin almanin AYNISI (ayni jeton): bakiye DEGISMEMELI. */
        REPLAY_LAST,

        /** Adet 2: miktar 2 ile kontrollu carpilir ve defter carpilmis miktari saklar. */
        QUANTITY_2,
    }

    private var counter = 0

    /**
     * Bu SURECE ozgu onek.
     *
     * ⚠ 2026-09-02 — CIHAZDA GORULEN HATA. Onceki surum jetonu yalnizca
     * `debug-<urun>-<sayac>` olarak uretiyordu ve [counter] SUREC ICINDEYDI:
     * uygulama yeniden baslayinca sayac 0'a donuyor, ilk "sahte satin alma"
     * yine ayni jetonu uretiyordu. O jeton ZATEN odenmis oldugu icin defter
     * dogru sekilde reddediyor ve dugme HICBIR SEY yapmiyordu — geri bildirim
     * de yok. Bozuk olan TEST ARACIYDI.
     *
     * `nanoTime` yeterli: amac kriptografik benzersizlik degil, acilislar
     * arasinda CAKISMAMAK.
     */
    private val processTag: String by lazy { java.lang.Long.toString(System.nanoTime() % 1_000_000L, 36) }

    /**
     * Sahte satin alma jetonu uretir. HER CAGRIDA FARKLI — **uygulama yeniden
     * baslatildiktan sonra da**. Ayni satin almayi tekrar gondermek icin
     * [Scenario.REPLAY_LAST] ya da [lastToken] kullanilir.
     */
    fun newToken(productId: String): String {
        counter++
        return "debug-$productId-$processTag-$counter"
    }

    /** Son uretilen jeton — idempotenlik denemesi icin. */
    var lastToken: String? = null
        private set

    fun rememberToken(token: String) {
        lastToken = token
    }

    /**
     * Urunun verdigi BIRIM (adet=1) coin; eski coin urunleri ESKI miktarla; `remove_ads` ve
     * bilinmeyen urun icin 0.
     */
    fun coinsOf(productId: String): Int = StoreCatalog.coinsOf(productId) ?: 0

    // ---------------------------------------------------------------- senaryolar

    private var lastInfo: PurchaseInfo? = null
    private var pendingInfo: PurchaseInfo? = null

    private fun fresh(
        productId: String,
        state: PurchaseState = PurchaseState.PURCHASED,
        quantity: Int = 1,
    ): PurchaseInfo {
        val token = newToken(productId)
        rememberToken(token)
        return PurchaseInfo(
            productIds = listOf(productId),
            purchaseToken = token,
            state = state,
            quantity = quantity,
            acknowledged = false,
        ).also { lastInfo = it }
    }

    /**
     * Senaryonun uretecegi satin alma. Satin alma UYDURMAYAN senaryolar ([Scenario.CANCEL],
     * [Scenario.ARM_PERSIST_FAILURE]) ve on kosulu olmayanlar ([Scenario.COMPLETE_PENDING] icin
     * bekleyen yok, [Scenario.REPLAY_LAST] icin onceki yok) `null` doner.
     */
    fun infoFor(scenario: Scenario): PurchaseInfo? = when (scenario) {
        Scenario.BUY -> fresh(StoreCatalog.PACK_RECRUIT)
        Scenario.PENDING -> fresh(StoreCatalog.PACK_RECRUIT, state = PurchaseState.PENDING)
            .also { pendingInfo = it }
        Scenario.COMPLETE_PENDING -> pendingInfo?.copy(state = PurchaseState.PURCHASED)
            ?.also { lastInfo = it; pendingInfo = null }
        Scenario.LEGACY_COINS_1200 -> fresh(LEGACY_1200_ID)
        Scenario.QUANTITY_2 -> fresh(StoreCatalog.PACK_RECRUIT, quantity = 2)
        Scenario.REPLAY_LAST -> lastInfo
        Scenario.CANCEL, Scenario.ARM_PERSIST_FAILURE -> null
    }

    private const val LEGACY_1200_ID = "coins_1200"

    // ---------------------------------------------------------------- yazim basarisizligi

    private var persistFailuresArmed = 0

    /** Bir sonraki [count] odemenin defter yazimi "dogrulanamadi" (PersistFailed) donsun. */
    fun armPersistFailure(count: Int = 1) {
        persistFailuresArmed = count.coerceAtLeast(0)
    }

    /** Silahli basarisizlik varsa tuketir ve `true` doner. */
    fun takePersistFailure(): Boolean {
        if (persistFailuresArmed <= 0) return false
        persistFailuresArmed--
        return true
    }

    // ---------------------------------------------------------------- sahte Play tarafi

    private val consumed = ArrayList<String>()
    private val acknowledged = ArrayList<String>()

    /** Sahte Play'e "tuketildi" gonderilen jetonlar (debug panelinde gosterilebilir). */
    val consumedTokens: List<String> get() = synchronized(consumed) { consumed.toList() }

    /** Sahte Play'e "onaylandi" gonderilen jetonlar. */
    val acknowledgedTokens: List<String> get() = synchronized(acknowledged) { acknowledged.toList() }

    /**
     * Debug hattinin uygulayicisi: GERCEK [PurchaseFulfiller], yalnizca defter portu "yazim
     * basarisiz" silahini ([armPersistFailure]) ve Play tarafi sahte ([recordingFinisher]).
     * `BillingManager` ve JVM testleri AYNI kurulusu kullanir; boylece test edilen sey panelin
     * calistirdigi seydir.
     */
    fun newDebugFulfiller(grants: PurchaseGrantPort, log: (String) -> Unit = {}): PurchaseFulfiller =
        PurchaseFulfiller(
            grants = PurchaseGrantPort { request ->
                // Defter hic denenmeden "yazilamadi" doner; ayni satin almayi tekrar gondermek gercek
                // deftere gider ve bir kez odenir.
                if (takePersistFailure()) GrantResult.PersistFailed else grants.grant(request)
            },
            finisher = recordingFinisher,
            log = log,
        )

    /** Yalniz testler: surece ozgu senaryo durumunu sifirlar. */
    internal fun resetForTest() {
        lastInfo = null
        pendingInfo = null
        persistFailuresArmed = 0
        lastToken = null
        synchronized(consumed) { consumed.clear() }
        synchronized(acknowledged) { acknowledged.clear() }
    }

    /** Kayit tutan sahte uygulayici: her zaman basarili yanit verir. */
    val recordingFinisher: PurchaseFinisher = object : PurchaseFinisher {
        override fun consume(purchaseToken: String, onResult: (ConsumeResult) -> Unit) {
            synchronized(consumed) { consumed += purchaseToken }
            onResult(ConsumeResult.CONSUMED)
        }

        override fun acknowledge(purchaseToken: String, onResult: (AcknowledgeResult) -> Unit) {
            synchronized(acknowledged) { acknowledged += purchaseToken }
            onResult(AcknowledgeResult.ACKNOWLEDGED)
        }
    }
}

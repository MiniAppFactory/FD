package com.miniappfactory.frontlinedefender.game.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Faz 5 — oyunun reklamlarla konustugu TEK arayuz.
 *
 * Tasarim ilkesi (GDD §G.4, DECISIONS "Reklam doktrini"):
 * **Reklam bir kolayliktir, asla bir kapi degildir.** Bu yuzden arayuzun
 * hicbir fonksiyonu "basarisiz" olamaz: her cagri, reklam gosterilse de
 * gosterilmese de callback'i **tam olarak bir kez** cagirir ve oyun akisi
 * her dalda ayni sekilde devam eder.
 *
 * Cagri yerleri Faz 6'da baglanacak; hangi dosyanin hangi satirina ne
 * eklenecegi `docs/ADMOB_INTEGRATION.md` icinde satir seviyesinde yazili.
 */
interface AdHost {

    /**
     * Banner seridi cizilsin mi? "Reklamlari Kaldir" satin alindiysa (GDD §G.2/7)
     * veya riza yoksa false doner. UI bu deger false iken serit icin **hic yer
     * ayirmaz**.
     */
    val bannerAllowed: Boolean

    /** Ayarlar'da "Gizlilik Secenekleri" satiri gosterilecek mi (UMP zorunlulugu). */
    val privacyOptionsRequired: Boolean

    /** Uygulama acilisinda bir kez. SDK init + consent + on-yukleme. */
    fun initialize(activity: Activity)

    /** Sonuc ekrani gorunurken cagrilir — reklami sessizce on-yukler (GDD §G.4). */
    fun preload(context: Context)

    /** Yeni savas basladi. Savas-basi rewarded haklari sifirlanir. */
    fun onBattleStarted()

    /**
     * Savas SONUNA KADAR oynandi (zafer veya yenilgi). GDD §G.2/1 (ilk 3 savas
     * reklamsiz) ve §G.2/5 (yarida birakilan savas interstitial tetiklemez)
     * kurallarinin sayaci.
     */
    fun onBattleCompleted()

    /** Savas yarida birakildi (menuye cikis). Interstitial hakki DOGMAZ. */
    fun onBattleAbandoned()

    /**
     * Zorunlu gecis reklami. **Rewarded degil** — burada odul yok, oyuncu
     * sadece ilerler.
     *
     * [onProceed] **her durumda ve tam bir kez** cagrilir: reklam gosterildi,
     * gosterilemedi, frekans kapi kapali, riza yok, timeout — hepsinde ayni.
     * Cagiran taraf navigasyonu SADECE bu callback icinde yapar.
     */
    fun showInterstitial(activity: Activity, reason: InterstitialReason, onProceed: () -> Unit)

    /**
     * Rewarded nokta. [onResult] **her durumda ve tam bir kez** cagrilir.
     *
     * KRITIK: cagiran taraf, sonucu ne olursa olsun akisi **ayni sekilde**
     * surdurmek zorundadir; yalnizca odulun buyuklugu degisir
     * ([RewardedOutcome]).
     */
    fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardedResult) -> Unit
    )

    /**
     * Bu nokta su an teklif edilebilir mi? SADECE **buton gorunurlugu** icin;
     * false donmesi oyuncunun ilerlemesini engellemez, yalnizca odul teklifi
     * tukendigi anlamina gelir (ornegin R1 gunluk 3 hakkin hepsi kullanildi).
     *
     * Onemli: bu deger reklamin YUKLU olup olmamasina **bagli degildir**.
     * Reklam hazir olmadigi icin buton asla gizlenmez/pasiflenmez (GDD §G.4).
     *
     * [RewardedPlacement.BOOSTER] icin buton gorunurlugunun karari BU DEGIL,
     * ekonomi katmanindaki `boosterAllowed(...)`dir; buradaki tavan pratikte
     * hicbir zaman once kapanmaz (bkz. [AdPolicyConfig.BOOSTER_PER_BATTLE_CEILING]).
     */
    fun isRewardedOffered(placement: RewardedPlacement): Boolean

    /**
     * R1'in bugun kalan hakki (UI'da "3/gun" gostergesi icin).
     *
     * DIKKAT — [RewardedPlacement.BOOSTER] icin bu deger **oyuncuya
     * gosterilmez**: R4'un gunluk hakkinin sahibi ekonomi katmanidir
     * (`EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY`, kalici sayac). Buradan donen
     * sayi yalnizca savas-ici spam tavaninin kalanidir
     * ([AdPolicyConfig.BOOSTER_PER_BATTLE_CEILING]). Oyuncuya gosterilecek
     * sayi `BoosterState.adViewsLeftToday`'dir.
     */
    fun rewardedRemaining(placement: RewardedPlacement): Int

    /** Ayarlar > Reklam ayarlari. */
    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {})
}

/**
 * Interstitial'in hangi baglamdan istendigi. Politika kararini bu belirler —
 * cagiran taraf "gosterilsin mi" sorusunu KENDI cevaplamaz.
 */
enum class InterstitialReason {
    /** Sonuc ekranindan bolum secime donus (zafer VEYA yenilgi). Tek mesru nokta. */
    RESULT_TO_LEVEL_SELECT,

    /**
     * Boss bolumu (11, 22) KAZANILDI ve sonuc ekranindan cikiliyor.
     * GDD §G.2/6: zafer ani bozulmaz — politika bunu **her zaman** atlar,
     * hak bir sonraki donuse kayar.
     */
    BOSS_VICTORY_TO_LEVEL_SELECT
}

/**
 * GDD §G.3 UC nokta tanimliyordu; [BOOSTER] **dorduncu** noktadir ve ayri bir
 * karar olarak eklenmistir (Faz 10 ekonomi katmani, `game/economy/BoosterSystem.kt`).
 *
 * Dorduncu noktanin gerekcesi: `BoosterType.EMERGENCY_SUPPLY` **AD_ONLY** para
 * birimindedir — ucretli yolu YOKTUR, yani rewarded yerlesimi olmadan oyunda
 * **hic kullanilamaz** (ve bolum 2'de acilan ILK guclendiricidir). Diger iki
 * guclendirici (hava destegi, us tamiri) reklami yalnizca ucretli kullanim
 * tukendikten SONRAKI ek kullanim icin kullanir; yani reklam ucretli yolun
 * ikamesi degil uzantisidir ve ekonomi arbitraji yapisal olarak kapalidir.
 */
enum class RewardedPlacement {
    /**
     * R1 — Tedarik Talebi. Bolum secim ekraninin ALT SERIDI. +150 coin, 3/gun.
     *
     * Ayni odulun ikinci GIRIS NOKTASI icin bkz. [COIN_TOP_UP]; ikisi ayni
     * gunluk hakki ve ayni gunluk coin butcesini paylasir.
     */
    SUPPLY_DROP,

    /** R2 — Takviye. Yenilgi ekrani. Us cani 5, savas devam. Savas basina 1. */
    REINFORCEMENT,

    /** R3 — Cift Odeme. Zafer ekrani. Coin odulu x2. Savas basina 1. */
    DOUBLE_PAYOUT,

    /**
     * R4 — Guclendirici. Savas ICI.
     *
     * Digerlerinden farki: teklif oynanisin ORTASINDA gorunur. Bu yuzden
     * yerlesimin sozlesmesi daha sikidir:
     *  - Teklif yalnizca oyuncunun KENDI dokundugu guclendirici butonundan acilir;
     *    kendiliginden acilan hicbir savas-ici reklam yoktur.
     *  - Teklif acikken simulasyon durdurulur, kapaninca ayni noktadan devam eder
     *    (cagiran tarafin sorumlulugu — `GameScreen`).
     *  - Hicbir dal savasi bloklamaz: reklam gelmese de oyuncu savasa devam eder,
     *    yalnizca guclendiricinin verdigi etki degisir ([RewardedOutcome]).
     *
     * Gunluk hakkin sahibi bu katman DEGILDIR; bkz.
     * [AdPolicyConfig.BOOSTER_PER_BATTLE_CEILING].
     */
    BOOSTER,

    /**
     * R1b — COIN CIPI. Bolum secim ekraninin BASLIK SATIRI.
     *
     * ## Bu YENI BIR COIN MUSLUGU DEGILDIR
     * Odul yolu [SUPPLY_DROP] ile **birebir aynidir**: ikisi de
     * `AdRewardBridge.grantSupplyDrop` -> `CampaignProgressImpl.grantSupplyDrop`
     * cagirir, yani ayni `RequisitionState` uzerinden ayni gunluk tavanlara
     * tabidir:
     *
     *   - `EconomyConfig.R1_COIN_BUDGET_PER_DAY` = 450 coin/gun (KALICI sayac)
     *   - `EconomyConfig.R1_VIEWS_PER_DAY` = 3 dolu gosterim/gun
     *
     * Reklam katmanindaki hak sayaci da PAYLASILIR (bkz.
     * [InMemoryRewardedQuotaStore]): bu yerlesimden yapilan bir gosterim
     * [SUPPLY_DROP]'un kalan hakkini da dusurur. Iki ayri kova olsaydi ucak
     * modunda serit 3 x 50, cip 3 x 50 fallback verir ve gunluk hak fiilen
     * ikiye katlanirdi — ekonominin 450 tavani yine tutardi ama reklam katmani
     * ekonomiyle CELISIR hale gelirdi ("hakkin var" deyip 0 coin odemek).
     *
     * ## O zaman neden AYRI bir enum degeri
     * Tek sebep **analitik**: `placement.name` dogrudan olay etiketine gider
     * (`RewardedAdManager.FORMAT` + `analytics.adRequest/adImpression`). Ayni
     * odulun iki giris noktasi var ve hangisinin gercekten gosterim urettigi
     * ancak boyle olculebilir. Aksi halde ikisi tek ortalamanin arkasinda
     * kaybolurdu — `AdIdsConsistencyTest`in interstitial/rewanded birimleri
     * icin uyguladigi ayni gerekce.
     *
     * ## Sozlesme
     *  - Teklif yalnizca oyuncunun coin cipine KENDI dokunmasiyla acilir;
     *    kendiliginden acilan hicbir teklif yoktur.
     *  - Buton reklam hazir olmadigi icin ASLA pasiflesmez (GDD §G.4).
     *  - Teklif tukendiginde (gunluk hak veya butce bitti) cip **bakiyeyi
     *    gostermeye devam eder**, yalnizca "+" isareti kaybolur: hicbir bilgi
     *    ve hicbir ilerleme yolu kapanmaz.
     */
    COIN_TOP_UP
}

/**
 * Rewarded sonucu. UC deger var, ikisi de akisi ayni sekilde surdurur:
 * fark **yalnizca odul buyuklugunde**.
 */
enum class RewardedOutcome {
    /** SDK gercekten "odul kazanildi" dedi. Tam odul. Gunluk/savas hakki tuketilir. */
    FULL_REWARD,

    /**
     * Reklam yok / yuklenemedi / timeout / oyuncu kapatti / riza yok.
     * **Azaltilmis odul verilir** (R1: +50, R2: savas yine devam, R3: x1).
     * R1 ve R3'te gunluk/savas hakki **TUKETILMEZ**.
     */
    REDUCED_REWARD,

    /**
     * Teklif zaten tukenmis (R1 gunluk 3 hak bitti, R2/R3 bu savasta
     * kullanildi). Odul verilmez — ama akis yine bloklanmaz, UI bu durumda
     * butonu hic gostermemis olmalidir ([AdHost.isRewardedOffered]).
     */
    UNAVAILABLE
}

/** Analitik ve teshis icin: azaltilmis odule neden dusuldu? */
enum class AdFallbackReason {
    NONE,
    NO_CONSENT,
    NOT_CONFIGURED,
    LOAD_FAILED,
    LOAD_TIMEOUT,
    SHOW_FAILED,
    DISMISSED_WITHOUT_REWARD,
    ALREADY_IN_FLIGHT,
    QUOTA_EXHAUSTED,
    /** Ekonomi arbitrajina karsi gunluk fallback tavani doldu (bkz. AdPolicy). */
    FALLBACK_CAP_REACHED
}

data class RewardedResult(
    val placement: RewardedPlacement,
    val outcome: RewardedOutcome,
    val reason: AdFallbackReason = AdFallbackReason.NONE
) {
    /** Kisayol: cagiran taraf en azindan taban davranisi uygulamali mi? */
    val grantsSomething: Boolean
        get() = outcome == RewardedOutcome.FULL_REWARD || outcome == RewardedOutcome.REDUCED_REWARD
}

/**
 * Reklamsiz AdHost. Birim/Compose testleri, ekran onizlemeleri ve
 * "Reklamlari Kaldir" satin alinmis oyuncu icin.
 *
 * Davranisi kasitli olarak gercek akisin **en kotu senaryosuna** esittir
 * (hicbir reklam gostermez ama hicbir yeri de bloklamaz). Bir cagri yeri
 * NoOpAdHost ile dogru calisiyorsa, no-fill ile de dogru calisir.
 */
class NoOpAdHost(
    override val bannerAllowed: Boolean = false,
    /** Testte "teklif tukendi" dalini de zorlayabilmek icin. */
    private val offerRewarded: Boolean = true,
    private val quota: RewardedQuotaStore = InMemoryRewardedQuotaStore()
) : AdHost {

    override val privacyOptionsRequired: Boolean = false

    /** Testlerin dogrulayabilmesi icin gorulen cagrilarin kaydi. */
    val interstitialRequests = mutableListOf<InterstitialReason>()
    val rewardedRequests = mutableListOf<RewardedPlacement>()

    override fun initialize(activity: Activity) = Unit
    override fun preload(context: Context) = Unit
    override fun onBattleStarted() = quota.onBattleStarted()
    override fun onBattleCompleted() = Unit
    override fun onBattleAbandoned() = Unit

    override fun showInterstitial(
        activity: Activity,
        reason: InterstitialReason,
        onProceed: () -> Unit
    ) {
        interstitialRequests += reason
        onProceed()
    }

    override fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardedResult) -> Unit
    ) {
        rewardedRequests += placement
        val offered = offerRewarded && quota.isOffered(placement)
        onResult(
            if (offered) {
                RewardedResult(placement, RewardedOutcome.REDUCED_REWARD, AdFallbackReason.NOT_CONFIGURED)
            } else {
                RewardedResult(placement, RewardedOutcome.UNAVAILABLE, AdFallbackReason.QUOTA_EXHAUSTED)
            }
        )
    }

    override fun isRewardedOffered(placement: RewardedPlacement): Boolean =
        offerRewarded && quota.isOffered(placement)

    override fun rewardedRemaining(placement: RewardedPlacement): Int = quota.remaining(placement)

    override fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) = onDismissed()
}

/**
 * Compose cagri yerleri icin: tam ekran reklam gostermek Activity ister.
 * `LocalContext.current.findActivity()` guvenli kullanim; null donerse
 * cagiran taraf reklamsiz dala duser (crash YOK).
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

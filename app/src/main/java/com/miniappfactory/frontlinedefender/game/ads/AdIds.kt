package com.miniappfactory.frontlinedefender.game.ads

import com.miniappfactory.frontlinedefender.BuildConfig

/**
 * Faz 5 — reklam kimliklerinin TEK kaynagi.
 *
 * DECISIONS.md "Reklam doktrini": ilk APK'da **yalnizca Google'in herkese acik
 * TEST kimlikleri** kullanilir. Gercek (production) kimlikler AYRI bir karardir
 * ve kullanici onayi gerektirir — bu yuzden asagidaki PRODUCTION_* sabitleri
 * bilincli olarak BOS birakildi.
 *
 * Gercek kimlikler geldiginde yapilacak is:
 *   1. PRODUCTION_* sabitlerini doldur (koda gomme yerine tercihen
 *      local.properties -> buildConfigField; bkz. GDD §G.5).
 *   2. [USE_TEST_ADS] degerini `BuildConfig.DEBUG` yap.
 *   3. Cihaz kimligini [developerTestDeviceIds] listesine ekle (asagiya bak).
 *
 * `USE_TEST_ADS` sabiti bilincli olarak `true` — `BuildConfig.DEBUG` DEGIL.
 * Sebep: bugun release build'de gostericek gercek bir kimlik yok; `BuildConfig.DEBUG`
 * yazsak release APK bos ad unit id ile SDK'ya gider ve her istek hata dondurur.
 */
object AdIds {

    /**
     * ⚠ 2026-08-21: gercek kimlikler geldi, sabit `true` -> `BuildConfig.DEBUG`.
     *
     * DEBUG BUILD'DE TEST REKLAMI ZORUNLU ve bu bir tercih degil, HESAP
     * GUVENLIGI: gelistirici kendi canli reklamina tiklarsa AdMob bunu gecersiz
     * trafik sayar ve yaptirimi hesap askiya alma/kapatmaya kadar gider. Bu
     * satir, cihazda test eden kisinin asla gercek reklam gormemesini garanti
     * eder.
     *
     * Yani gercek kimlikler YALNIZCA release build'de okunur.
     */
    val USE_TEST_ADS: Boolean = BuildConfig.DEBUG

    // ---------------------------------------------------------------------
    // Google'in resmi test kimlikleri (herkese acik, dokumante, guvenli):
    // https://developers.google.com/admob/android/test-ads
    // Bunlar her zaman doldurulur ve her zaman fill verir — no-fill senaryosunu
    // test etmek icin ucak modu kullanilir (bkz. docs/ADMOB_INTEGRATION.md §7).
    // ---------------------------------------------------------------------
    internal const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // ---------------------------------------------------------------------
    // GERCEK KIMLIKLER (AdMob, 2026-08-21).
    //
    // Bunlar SIR DEGIL: her yayinlanmis APK'nin icinde acik metin olarak durur
    // ve AdMob'un kendi dokumantasyonu koda gomulmelerini bekler. Depo kuralinin
    // yasakladigi sey (anahtar, keystore sifresi) bu degil.
    //
    // Yalnizca release build'de okunurlar (bkz. USE_TEST_ADS).
    // ---------------------------------------------------------------------
    const val PRODUCTION_APP_ID: String = "ca-app-pub-8582550349019790~1660929933"

    /**
     * BANNER YOK ve bos olmasi DOGRU: banner bu oyunda kapali
     * (`AdPolicyConfig.BANNER_ENABLED = false`) cunku ekran yatay ve dar,
     * serit oynanis alanini yiyordu. Banner acilirsa once AdMob'da birim
     * olusturulmali.
     */
    private const val PRODUCTION_BANNER_AD_UNIT_ID: String = ""

    internal const val PRODUCTION_INTERSTITIAL_AD_UNIT_ID: String =
        "ca-app-pub-8582550349019790/4138814268"
    internal const val PRODUCTION_REWARDED_AD_UNIT_ID: String =
        "ca-app-pub-8582550349019790/2977931367"

    /**
     * R4 Guclendirici icin AYRI bir rewarded birimi — henuz YOK.
     *
     * Neden ayri bir sabit: savas-ici guclendirici reklaminin eCPM'i ve
     * tamamlanma orani menu-ici R1'den yapisal olarak farklidir; ayni birime
     * karistirilirsa iki yerlesimin performansi tek bir ortalamanin arkasinda
     * kaybolur ve hangisinin gelir getirdigi olculemez. AdMob'da bu yerlesim
     * icin ayri bir "Rewarded" ad unit acilmali (onerilen ad: "FD Booster R4").
     *
     * BOS oldugu surece [rewardedAdUnitId] paylasilan rewarded birimine duser.
     *
     * ⚠ 2026-08-21 NOTU: AdMob'da bir "Rewarded Interstitial" birimi acildi
     * (.../1791852529) ama BURAYA KONMADI ve konmamali — o AYRI BIR FORMAT.
     * Rewarded interstitial farkli bir yukleyici sinifi kullanir
     * (`RewardedInterstitialAd`), oyunun rewarded akisi ise `RewardedAd`
     * bekliyor; kimligi buraya yazmak calisma zamaninda yukleme hatasi verirdi.
     *
     * R4'u ayirmak icin AdMob'da format olarak **Rewarded** secilmis yeni bir
     * birim gerekiyor. O acilana kadar bu alan bos kalir ve R4, R1/R2/R3 ile
     * ayni birimi kullanir — kimlik UYDURULMAZ.
     */
    private const val PRODUCTION_REWARDED_BOOSTER_AD_UNIT_ID: String = ""

    /**
     * AndroidManifest'teki `com.google.android.gms.ads.APPLICATION_ID`
     * meta-data'sinin **ayni** degeri. Manifest degeri derleme zamaninda
     * sabitlenir; buradaki kopya yalnizca dogrulama/log amaclidir.
     */
    val manifestAppId: String get() = if (USE_TEST_ADS) TEST_APP_ID else PRODUCTION_APP_ID

    fun bannerAdUnitId(): String =
        if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else PRODUCTION_BANNER_AD_UNIT_ID

    fun interstitialAdUnitId(): String =
        if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else PRODUCTION_INTERSTITIAL_AD_UNIT_ID

    fun rewardedAdUnitId(): String =
        if (USE_TEST_ADS) TEST_REWARDED_AD_UNIT_ID else PRODUCTION_REWARDED_AD_UNIT_ID

    /**
     * Yerlesime ozel rewarded birimi.
     *
     * Bugun TUM yerlesimler ayni Google test birimini kullanir (test kimlikleri
     * yerlesim ayrimi yapmaz). Gercek kimliklere gecildiginde yalnizca bu
     * fonksiyonun icindeki esleme buyur; cagri yerleri degismez.
     *
     * Kimligi olmayan bir yerlesim paylasilan rewarded birimine duser; bos
     * kimlikle SDK'ya asla gidilmez ([isConfigured]).
     */
    fun rewardedAdUnitId(placement: RewardedPlacement): String {
        if (USE_TEST_ADS) return TEST_REWARDED_AD_UNIT_ID
        val specific = when (placement) {
            RewardedPlacement.BOOSTER -> PRODUCTION_REWARDED_BOOSTER_AD_UNIT_ID
            RewardedPlacement.SUPPLY_DROP,
            // COIN_TOP_UP, SUPPLY_DROP ile AYNI odulun ikinci giris noktasidir;
            // ayri bir AdMob birimi ISTEMEZ. Ayrim analitik tarafinda
            // (`placement.name`) zaten var.
            RewardedPlacement.COIN_TOP_UP,
            RewardedPlacement.REINFORCEMENT,
            RewardedPlacement.DOUBLE_PAYOUT -> ""
        }
        return if (specific.isNotBlank()) specific else PRODUCTION_REWARDED_AD_UNIT_ID
    }

    /** Kimlik bos ise reklam istegi hic yapilmaz — SDK'yi bos id ile cagirmayiz. */
    fun isConfigured(adUnitId: String): Boolean = adUnitId.isNotBlank()

    /**
     * Gelistirici/test cihazlari. Bu listedeki cihazlara RELEASE build'de bile
     * her zaman Google'in guvenli test reklamlari gosterilir; boylece ekibin
     * kendi reklamina tiklamasi (invalid traffic) AdMob hesabini riske atmaz.
     *
     * DIKKAT — bu kimlik UYGULAMA BAZLIDIR, cihaz bazli DEGIL. Ayni Galaxy S22
     * Ultra'da olculdu (2026-08-13): Kaboom "F83812BB...", Kron Drive
     * "B5BD61FF..." bildirdi. Yani **bu uygulamadan okunan** kimlik yazilmali;
     * baska bir uygulamadan alinan kimlik hicbir ise yaramaz.
     *
     * Kimlik nasil okunur (uygulamada ilk reklam istegi yapildiktan sonra):
     *   adb logcat | grep setTestDeviceIds
     * Cikti: "Use RequestConfiguration.Builder().setTestDeviceIds(
     *         Arrays.asList("XXXX")) to get test ads on this device."
     *
     * Bugun BOS: USE_TEST_ADS=true oldugu surece tum cihazlar zaten test
     * reklami aliyor, listeye ihtiyac yok. Gercek kimliklere gecilen anda
     * Galaxy S8 (SM-G950F) ve S22 Ultra kimlikleri BU UYGULAMADAN okunup
     * buraya yazilmali — yoksa ilk gercek tiklamada politika ihlali riski var.
     */
    val developerTestDeviceIds: List<String> = listOf(
        // Samsung Galaxy S8 (SM-G950F) — birincil cihaz testi telefonu.
        //
        // BU UYGULAMADAN okundu (2026-08-22, imzali release build, adb logcat).
        // Kaboom'un kimligi (F83812BB...) buraya YAZILAMAZ ve yazilmadi: ayni
        // cihazda bile her uygulama farkli kimlik bildiriyor (Boom-Blocks
        // AdIds.kt'deki olculmus not: Kaboom F83812BB..., Kron Drive B5BD61FF...).
        //
        // Nasil okundu: release APK kuruldu, uygulama acildi, reklam ON-YUKLEME
        // istegi logcat'e su satiri dusurdu — HICBIR REKLAM GOSTERILMEDI ve
        // hicbir seye TIKLANMADI, yani gecersiz trafik riski olusmadi:
        //   Use RequestConfiguration.Builder().setTestDeviceIds(...)
        "4EC2D32786F16937AF9963145EA0E233",
        // Galaxy S22 Ultra icin kimlik HENUZ YOK. Ayni adim o cihazda
        // tekrarlanmali; Kaboom'daki S22 kimligi bu uygulamada calismaz.
    )
}

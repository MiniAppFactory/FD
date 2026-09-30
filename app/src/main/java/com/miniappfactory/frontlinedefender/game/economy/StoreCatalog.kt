package com.miniappfactory.frontlinedefender.game.economy

/**
 * COIN MAGAZASI — satistaki kalemlerin TEK KAYNAGI (2026-09-02; katalog 2026-09-30'da yenilendi).
 *
 * Saf Kotlin, Android importu YOK ([EconomyConfig] ile ayni sozlesme): fiyat/deger
 * dengesi JVM testiyle kilitlenir. Play Billing'e bagli hicbir sey burada
 * durmaz; kopru `billing/` paketindedir.
 *
 * ## ⚠ FIYAT METNI BURADA DEGIL
 *
 * Bu katalog yalnizca **urun kimligini** ve **verilen coin miktarini** tutar.
 * Oyuncuya gosterilen fiyat ("₺49,99") HER ZAMAN Play'den gelir
 * (`ProductDetails.formattedPrice`). Ulke, kur ve Play Console fiyat kademesi
 * degisir; koda yazilan fiyat bir gun mutlaka yalan soyler. [CoinPack.usdReference]
 * YALNIZCA denge testleri icindir, ekrana cikmaz; ekranda ne fiyat ne de
 * fiyattan turetilmis bir yuzde ("%20 daha cok") gosterilir — ikisi de Play'in
 * yerel fiyatiyla celisebilir. Oyuncuyu paketler arasinda ayirt eden tek sey
 * verilen **coin miktaridir** (her satirda gorunur) ve rutbe adi ([PackTier]).
 *
 * ## Iki AYRI harita — ESKI id YENI miktarla YORUMLANMAZ
 *
 * 2026-09-30'da satis katalogu bes YENI urune tasindi (`fd_coins_*`). Eski
 * `coins_*` urunleri Play Console'da hala var olabilir ve bir oyuncu onlari
 * ZATEN satin almis olabilir; Play bu satin almalari (tuketilmemisse) bir
 * sonraki sorguda yeniden getirir. Bunlar [legacyFulfillment] ile **ESKI
 * miktarlariyla** odenir (eski 0,99$ paketi 1.200 coindi; yeni 0,99$ paketi
 * 1.000 — eski id'yi yeni tabloyla okumak oyuncudan 200 coin calmak olurdu).
 *
 *  * [coinPacks] — SATISTA. Ekrana yalnizca bunlar cikar; `queryProductDetails`
 *    yalnizca bunlari sorar ([allProductIds]).
 *  * [legacyFulfillment] — SATISTA DEGIL. Yalnizca Play "bu hesapta bu eski urun
 *    var" dediginde kullanilir. Ekrana cikmaz, sorgulanmaz.
 *  * [PRODUCT_REMOVE_ADS] — SATISTAN KALDIRILDI (oyundan reklam kalkti). Eski
 *    alicilarin satin almasi yalnizca `acknowledge` edilir: 3 gun icinde
 *    onaylanmayan satin almayi Play OTOMATIK iade eder, yani onaylamamak
 *    oyuncunun parasini geri vermek olurdu. Coin YOK, consume YOK.
 *
 * ## Paketlerin kalibrasyonu
 *
 * Cipa: kampanyanin TEK SEFERLIK coin giderlerinin toplami —
 * [EconomyConfig.TOTAL_LOCK_COST] + [EconomyConfig.TREE_TOTAL_COST] +
 * [EconomyConfig.PRESTIGE_TOTAL_COST] (bugun 23.105 + 19.600 + 38.800 =
 * 81.505). Sayi burada YAZILMAZ, [oneTimeSinkTotal] ile hesaplanir; emiciler
 * buyurse cipa kendiliginden kayar ve `StoreCatalogTest` yeni degerle denetler.
 * Yinelenen emiciler (Elit Sevk, Us Tamiri, Hava Destegi) toplama GIRMEZ.
 *
 * Iki kural var, ikisini de test kilitler:
 *
 *  1. **coin/para artan olmali.** Buyuk paket her zaman daha iyi deger verir;
 *     aksi halde paket listesi oyuncuyu cezalandirir.
 *  2. **Tek paket oyunu bitirememeli.** En buyuk paket toplamin %50'sinin
 *     altinda kalir — satin alan oyuncunun onunde oynayacak bir sey kalmali.
 *
 * ## Enflasyon yasagi ile iliskisi
 *
 * [EconomyConfig]'teki "enflasyon yasagi" KAZANILAN coin'in musluklarini
 * sinirlar. Satin alinan coin bir musluk degil, oyuncunun kendi parasiyla
 * actigi bir giristir. Satin alma `lifetimeEarned`e YAZILMAZ — o sayac
 * oynanisla kazanilan coin'i olcer; satin alma ayri bir sayacta tutulur
 * (`SaveManager.lifetimePurchasedCoins`).
 */
object StoreCatalog {

    /**
     * Oyuncuya gorunen rutbe adi ailesi. [code] KOD etiketidir (test tag'i,
     * analitik); oyuncuya gorunen ad `strings_store.xml`den gelir
     * (`store_tier_<code kucuk harf>`), yani buradaki enum Android'e baglanmaz.
     */
    enum class PackTier(val code: String) {
        RECRUIT("RECRUIT"),
        SERGEANT("SERGEANT"),
        CAPTAIN("CAPTAIN"),
        COLONEL("COLONEL"),
        GENERAL("GENERAL"),
    }

    /**
     * Bir coin paketi.
     *
     * @param id Play Console'daki urun id'si. DEGISTIRILEMEZ: yayimlanan bir
     *   urun id'si Play'de ne silinir ne yeniden kullanilir.
     * @param coins satin almada verilen coin.
     * @param usdReference YALNIZCA denge testleri icin referans fiyat. Ekrana
     *   cikmaz (sinif belgesine bak).
     * @param tier oyuncuya gorunen rutbe ailesi.
     */
    data class CoinPack(
        val id: String,
        val coins: Int,
        val usdReference: Double,
        val tier: PackTier,
    ) {
        init {
            require(coins > 0) { "paket coin'i pozitif olmali: $coins" }
            require(usdReference > 0.0) { "referans fiyat pozitif olmali: $usdReference" }
        }

        /** Dolar basina coin — paket siralamasinin dogrulugu bundan olculur. */
        val coinsPerUsd: Double get() = coins / usdReference
    }

    // Urun id'leri. Bu dizeler Play Console'da BIREBIR ayni olusturulmali.
    // Adlar rutbe ailesidir; miktar id'nin icinde de yazar (testle kilitli).
    const val PACK_RECRUIT = "fd_coins_1000"
    const val PACK_SERGEANT = "fd_coins_2200"
    const val PACK_CAPTAIN = "fd_coins_3600"
    const val PACK_COLONEL = "fd_coins_5200"
    const val PACK_GENERAL = "fd_coins_7000"

    /**
     * ⚠ SATISTAN KALDIRILDI — yalnizca ESKI alicilar icin. Bkz. sinif belgesi.
     * Ad geriye uyumluluk icin korundu (`BillingManager` yalniz onaylar, `SaveManager` isareti tutar).
     */
    const val PRODUCT_REMOVE_ADS = "remove_ads"

    /**
     * Paketler UCUZDAN PAHALIYA. Ekrandaki sira da aynidir.
     *
     * Kademeler BILEREK sifir hacimde comert: oyunun henuz kitlesi yok, fiyat
     * her durumda tahmin. Kapali testten sonra Play Console donusum verisiyle
     * yeniden fiyatlanir. Fiyat degisir, urun id'si ve miktar degismez.
     */
    val coinPacks: List<CoinPack> = listOf(
        CoinPack(PACK_RECRUIT, coins = 1_000, usdReference = 0.99, tier = PackTier.RECRUIT),
        CoinPack(PACK_SERGEANT, coins = 2_200, usdReference = 1.99, tier = PackTier.SERGEANT),
        CoinPack(PACK_CAPTAIN, coins = 3_600, usdReference = 2.99, tier = PackTier.CAPTAIN),
        CoinPack(PACK_COLONEL, coins = 5_200, usdReference = 3.99, tier = PackTier.COLONEL),
        CoinPack(PACK_GENERAL, coins = 7_000, usdReference = 4.99, tier = PackTier.GENERAL),
    )

    /**
     * ESKI (artik satilmayan) coin urunleri ve **odendikleri ESKI miktarlar**.
     *
     * SATIS LISTESINE GIRMEZ, `queryProductDetails`a GITMEZ. Play bir hesapta
     * bu urunlerden birini tuketilmemis olarak getirirse `billing/PurchaseFulfillment.kt`
     * bu haritayla ode ve tuketir. Bu miktarlar DEGISTIRILEMEZ: oyuncu o gun
     * bu miktara para odedi (`StoreCatalogTest.legacyGrantsEqualTheOriginalAmounts`).
     */
    val legacyFulfillment: Map<String, Int> = linkedMapOf(
        "coins_1200" to 1_200,
        "coins_4000" to 4_000,
        "coins_7400" to 7_400,
        "coins_16000" to 16_000,
        "coins_25000" to 25_000,
    )

    /** Eski coin urun id'leri. */
    val legacyCoinProductIds: List<String> get() = legacyFulfillment.keys.toList()

    /** Yalnizca `acknowledge` edilen eski urunler (coin YOK, consume YOK). */
    val legacyAcknowledgeOnlyIds: List<String> = listOf(PRODUCT_REMOVE_ADS)

    /** Tuketilen urun id'leri (SATISTAKI) — Billing tarafinda `consume` edilir. */
    val consumableIds: List<String> get() = coinPacks.map { it.id }

    /**
     * Play'den SORGULANAN ve ekrana cikan id'ler = yalnizca satistakiler.
     * Eski id'ler ve `remove_ads` BURADA DEGIL (bkz. sinif belgesi).
     */
    val allProductIds: List<String> get() = consumableIds

    /** Urunun nasil ele alinacagi — `billing/PurchaseFulfillment.kt` bunun uzerine karar verir. */
    enum class ProductKind {
        /** Satistaki yeni coin paketi. */
        SALE_PACK,

        /** Eski coin urunu — eski miktarla odenir, tuketilir. */
        LEGACY_COINS,

        /** Eski reklamsiz urunu — yalnizca onaylanir. */
        LEGACY_REMOVE_ADS,
    }

    /** Bilinmeyen id icin `null` — bilinmeyen urun ASLA odenmez/tuketilmez/onaylanmaz. */
    fun kindOf(productId: String): ProductKind? = when {
        coinPacks.any { it.id == productId } -> ProductKind.SALE_PACK
        productId in legacyFulfillment -> ProductKind.LEGACY_COINS
        productId in legacyAcknowledgeOnlyIds -> ProductKind.LEGACY_REMOVE_ADS
        else -> null
    }

    /**
     * Bir urun id'sinin BIRIM (adet=1) coin karsiligi: satistaki paket icin
     * [CoinPack.coins], eski coin urunu icin ESKI miktar; baska her sey icin
     * (bilinmeyen, `remove_ads`) `null`.
     */
    fun coinsOf(productId: String): Int? =
        coinPacks.firstOrNull { it.id == productId }?.coins ?: legacyFulfillment[productId]

    /** SATISTAKI paket. Eski id'ler ve `remove_ads` icin `null` (bkz. [coinsOf]). */
    fun packOf(productId: String): CoinPack? = coinPacks.firstOrNull { it.id == productId }

    /** En kucuk paketin coin degeri — magazanin tanitim metni ve testler "en dusuk giris" olarak okur. */
    val smallestPackCoins: Int get() = coinPacks.minOf { it.coins }

    val largestPackCoins: Int get() = coinPacks.maxOf { it.coins }

    /**
     * Kampanyanin TEK SEFERLIK coin giderleri: 55 kilit + meta agac + 7 prestij
     * nisani. Elit bilet ve Us Tamiri/Hava Destegi TEKRARLANABILIR emiciler,
     * toplama girmez — "oyunu bitirmek" onlarsiz da mumkun.
     */
    val oneTimeSinkTotal: Int
        get() = EconomyConfig.TOTAL_LOCK_COST +
            EconomyConfig.TREE_TOTAL_COST +
            EconomyConfig.PRESTIGE_TOTAL_COST

    /** En buyuk paketin tek seferlik giderlere orani (kural 2'nin olcusu). */
    val largestPackShareOfSinks: Double get() = largestPackCoins.toDouble() / oneTimeSinkTotal
}

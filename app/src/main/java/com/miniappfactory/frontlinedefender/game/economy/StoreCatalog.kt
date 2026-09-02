package com.miniappfactory.frontlinedefender.game.economy

/**
 * COIN MAGAZASI — satistaki kalemlerin TEK KAYNAGI (2026-09-02).
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
 * degisir; koda yazilan fiyat bir gun mutlaka yalan soyler. Asagidaki
 * [CoinPack.usdReference] YALNIZCA denge testleri icindir, ekrana cikmaz.
 *
 * ## Paketlerin kalibrasyonu (2026-09-02)
 *
 * Cipa: kampanyanin TEK SEFERLIK coin giderlerinin toplami —
 * [EconomyConfig.TOTAL_LOCK_COST] + [EconomyConfig.TREE_TOTAL_COST] +
 * [EconomyConfig.PRESTIGE_TOTAL_COST] (bugun 23.105 + 19.600 + 38.800 =
 * 81.505). Sayi burada YAZILMAZ, [oneTimeSinkTotal] ile hesaplanir; emiciler
 * buyurse cipa kendiliginden kayar ve `StoreCatalogTest` yeni degerle denetler.
 * (Prestij 2026-09-02'de 19.900 -> 38.800 cikti; gerekce EconomyConfig'te.)
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
 * [EconomyConfig]'teki "enflasyon yasagi" (R1 gunluk butcesi, tekrar odulu
 * tabani) KAZANILAN coin'in musluklarini sinirlar. Satin alinan coin bir musluk
 * degil, oyuncunun kendi parasiyla actigi bir giristir; rezerv kurali ve
 * soft-lock garantisi ona da aynen uygulanir (coin FAZLASI hicbir kapiyi
 * kapatamaz, yalnizca acar). Satin alma `lifetimeEarned`e YAZILMAZ — o sayac
 * oynanisla kazanilan coin'i olcer ve ekonomi raporlari onu okur; satin alma
 * ayri bir sayacta tutulur (`SaveManager.lifetimePurchasedCoins`).
 */
object StoreCatalog {

    /**
     * Bir coin paketi.
     *
     * @param id Play Console'daki urun id'si. DEGISTIRILEMEZ: yayimlanan bir
     *   urun id'si Play'de ne silinir ne yeniden kullanilir.
     * @param coins satin almada verilen coin.
     * @param usdReference YALNIZCA denge testleri icin referans fiyat. Ekrana
     *   cikmaz (sinif belgesine bak).
     */
    data class CoinPack(
        val id: String,
        val coins: Int,
        val usdReference: Double,
    ) {
        init {
            require(coins > 0) { "paket coin'i pozitif olmali: $coins" }
            require(usdReference > 0.0) { "referans fiyat pozitif olmali: $usdReference" }
        }

        /** Dolar basina coin — paket siralamasinin dogrulugu bundan olculur. */
        val coinsPerUsd: Double get() = coins / usdReference
    }

    // Urun id'leri. Bu dizeler Play Console'da BIREBIR ayni olusturulmali.
    const val PACK_RECRUIT = "coins_1200"
    const val PACK_SERGEANT = "coins_4000"
    const val PACK_CAPTAIN = "coins_7400"
    const val PACK_COLONEL = "coins_16000"
    const val PACK_GENERAL = "coins_25000"

    /**
     * Tuketilmeyen urun: bolum gecis interstitial'i kalkar. **Rewarded KALIR** —
     * rewarded bir reklam degil, oyuncunun kendi actigi bir coin muslugudur;
     * onu kapatmak para odeyeni cezalandirirdi. Banner bu oyunda zaten kapali
     * (`AdPolicyConfig.BANNER_ENABLED = false`), yani bu urunun kaldirdigi tek
     * yuzey interstitial'dir ve magaza metni bunu ACIKCA soyler.
     */
    const val PRODUCT_REMOVE_ADS = "remove_ads"

    /**
     * Paketler UCUZDAN PAHALIYA. Ekrandaki sira da aynidir — oyuncu en dusuk
     * riskli secenegi once gorur.
     *
     * Fiyat kademeleri BILEREK 14,99'da kesildi (Kron Drive'daki 2026-09-01
     * karariyla ayni gerekce): oyunun henuz kitlesi yok, fiyat her durumda
     * tahmin; sifir hacimde comert olmak bedelsiz, ilk alicilara (muhtemelen
     * tanidiklar) acgozlu gorunmek pahali. Kapali testten sonra Play Console
     * donusum verisiyle yeniden fiyatlanir. Fiyat degisir, urun id'si degismez.
     */
    val coinPacks: List<CoinPack> = listOf(
        CoinPack(PACK_RECRUIT, coins = 1_200, usdReference = 0.99),
        CoinPack(PACK_SERGEANT, coins = 4_000, usdReference = 2.99),
        CoinPack(PACK_CAPTAIN, coins = 7_400, usdReference = 4.99),
        CoinPack(PACK_COLONEL, coins = 16_000, usdReference = 9.99),
        CoinPack(PACK_GENERAL, coins = 25_000, usdReference = 14.99),
    )

    /** Tuketilen urun id'leri — Billing tarafinda `consume` edilir. */
    val consumableIds: List<String> get() = coinPacks.map { it.id }

    /** Tuketilmeyen urun id'leri — `acknowledge` edilir, `consume` EDILMEZ. */
    val nonConsumableIds: List<String> = listOf(PRODUCT_REMOVE_ADS)

    val allProductIds: List<String> get() = consumableIds + nonConsumableIds

    fun packOf(productId: String): CoinPack? = coinPacks.firstOrNull { it.id == productId }

    /** En kucuk paketin coin degeri — magazanin tanitim metni ve testler "en dusuk giris" olarak okur. */
    val smallestPackCoins: Int get() = coinPacks.minOf { it.coins }

    val largestPackCoins: Int get() = coinPacks.maxOf { it.coins }

    /**
     * Kampanyanin TEK SEFERLIK coin giderleri: 55 kilit + meta agac + 5 prestij
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

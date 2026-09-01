package com.miniappfactory.frontlinedefender.game.billing

import com.miniappfactory.frontlinedefender.BuildConfig
import com.miniappfactory.frontlinedefender.game.economy.StoreCatalog

/**
 * YALNIZ DEBUG — SAHTE SATIN ALMA (Play Console beklemeden test icin).
 *
 * Gercek akis ucu ayni anda saglaninca calisir: uygulama bir Play track'ine
 * yuklenmis, urunler Console'da olusturulmus, cihazdaki hesap "lisans
 * testcisi". Hicbiri kod tarafinda degil — yani magaza yazildigi gun test
 * edilemez. Oysa magazanin GERCEKTEN kirilgan kismi Play'de degil bizde:
 *
 *  * coin `SaveManager`a yaziliyor mu,
 *  * ayni satin alma IKI kez odeniyor mu (`grantPurchasedCoins` idempotent mi),
 *  * `remove_ads` interstitial'i kapatirken rewarded'i **kapatmiyor** mu,
 *  * ekranin "yukleniyor / alinamaz / alindi" durumlari dogru mu.
 *
 * Bunlarin hepsi Play'e dokunmadan dogrulanir. Bu dosya tam olarak onu yapar.
 *
 * ## RELEASE'TE OLU KOD
 *
 * [enabled] `BuildConfig.DEBUG`e bagli; R8 release derlemesinde sabit `false`a
 * katlanir. Yine de magaza ekrani bayragi ACIKCA kontrol eder — "zaten
 * release'te calismaz" varsayimi bu depoda daha once birkac kez kapatilan
 * sessiz-yanlis desenidir.
 *
 * Uretilen jeton GERCEK bir Play jetonu DEGIL; bilerek `debug-` onekli.
 */
object FakeBilling {

    /** Sahte satin alma yalnizca debug derlemede acik. */
    val enabled: Boolean get() = BuildConfig.DEBUG

    private var counter = 0

    /**
     * Bu SURECE ozgu onek.
     *
     * ⚠ 2026-09-02 — CIHAZDA GORULEN HATA. Onceki surum jetonu yalnizca
     * `debug-<urun>-<sayac>` olarak uretiyordu ve [counter] SUREC ICINDEYDI:
     * uygulama yeniden baslayinca sayac 0'a donuyor, ilk "sahte satin alma"
     * yine `debug-coins_1200-1` uretiyordu. O jeton ZATEN odenmis oldugu icin
     * `SaveManager.grantPurchasedCoins` dogru sekilde `false` donuyor ve
     * dugme HICBIR SEY YAPMIYORDU — hicbir geri bildirim de vermeden.
     *
     * Yani idempotenlik korumasi calisiyordu; bozuk olan TEST ARACIYDI. Bu,
     * bu deponun "sessizce yanlis" desenidir: gelistirici dugmeye basar,
     * bakiye degismez ve bunun bir HATA mi yoksa KORUMA mi oldugunu
     * ayirt edemez.
     *
     * `nanoTime` yeterli: amac kriptografik benzersizlik degil, aciliş
     * arasinda CAKISMAMAK. Ayni surecte artan [counter] ile birlikte ayni
     * milisaniyede iki cagri da farkli jeton uretir.
     */
    private val processTag: String by lazy { java.lang.Long.toString(System.nanoTime() % 1_000_000L, 36) }

    /**
     * Sahte satin alma jetonu uretir. HER CAGRIDA FARKLI — **uygulama yeniden
     * baslatildiktan sonra da**. Idempotenlik testi icin ayni jetonu ikinci kez
     * gondermek gerekirse [lastToken] kullanilir; boylece "ayni satin alma iki
     * kez odeniyor mu" sorusu kod degistirmeden ekrandan denenir.
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

    /** Urunun verdigi coin; tuketilmeyen urun icin 0. */
    fun coinsOf(productId: String): Int = StoreCatalog.packOf(productId)?.coins ?: 0
}

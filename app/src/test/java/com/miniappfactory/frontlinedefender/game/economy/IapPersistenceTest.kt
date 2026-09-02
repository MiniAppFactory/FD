package com.miniappfactory.frontlinedefender.game.economy

import com.miniappfactory.frontlinedefender.game.data.InMemoryKeyValueStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * =============================================================================
 * IAP KALICILIGI — GERCEK PARA, DOLAYISIYLA SOZLESME
 * =============================================================================
 *
 * ⚠ NEDEN SONRADAN YAZILDI. Coin paketleri ve "Reklamlari Kaldir" 2026-09-02'de
 * yayina girdi (v54) ve o commit'in MANSET GARANTILERI — idempotenlik, odenmis
 * hakkin sifirlamada korunmasi, satin alinan coinin oynanis kazancindan ayri
 * sayilmasi — **hicbir testle korunmuyordu**. Uc garanti de yalnizca KDoc'ta
 * yaziliydi; bu depoda "kod var, cagiran yok" ile ayni aileden bir kusur:
 * "kural yazili, kilidi yok".
 *
 * Idempotenlik cihazda ELLE dogrulandi (bakiye 70 -> 1.270, ayni jeton tekrar
 * gonderilince 1.270'te kaldi) ama elle dogrulama bir kilit degildir.
 *
 * Buradaki her test, PARA KAYBIYLA SONUCLANABILECEK bir yolu kapatir.
 */
class IapPersistenceTest {

    private fun save() = SaveManager(InMemoryKeyValueStore())

    // =========================================================================
    // 1. IDEMPOTENLIK — ayni satin alma IKI kez odenemez
    // =========================================================================

    /**
     * `queryPurchasesAsync` HER acilista tuketilmemis satin almalari yeniden
     * getirir. Kayit olmasaydi, bir kez odenen paket her acilista tekrar
     * yazilirdi — oyuncu bir kez oder, sinirsiz coin alirdi.
     */
    @Test
    fun theSamePurchaseTokenIsPaidOutExactlyOnce() {
        val s = save()
        val basePlusStart = s.loadWallet().coins

        assertTrue("ilk yazim basarisiz", s.grantPurchasedCoins("tok-A", 1_200))
        assertEquals(basePlusStart + 1_200, s.loadWallet().coins)

        // Ayni jeton: false donmeli ve bakiye DEGISMEMELI.
        assertFalse("ayni jeton ikinci kez odendi", s.grantPurchasedCoins("tok-A", 1_200))
        assertEquals(basePlusStart + 1_200, s.loadWallet().coins)

        // Miktar farkli olsa bile jeton ayni: yine odenmez. (Kotu niyetli bir
        // cagri veya bir hata, ayni jetonu buyuk bir miktarla yeniden
        // gonderemesin.)
        assertFalse(s.grantPurchasedCoins("tok-A", 25_000))
        assertEquals(basePlusStart + 1_200, s.loadWallet().coins)

        // FARKLI jeton normal sekilde odenir.
        assertTrue(s.grantPurchasedCoins("tok-B", 4_000))
        assertEquals(basePlusStart + 5_200, s.loadWallet().coins)
    }

    @Test
    fun blankTokenOrNonPositiveCoinsAreRefusedWithoutTouchingTheWallet() {
        val s = save()
        val before = s.loadWallet().coins
        assertFalse(s.grantPurchasedCoins("", 1_200))
        assertFalse(s.grantPurchasedCoins("   ", 1_200))
        assertFalse(s.grantPurchasedCoins("tok", 0))
        assertFalse(s.grantPurchasedCoins("tok", -500))
        assertEquals(before, s.loadWallet().coins)
        assertEquals(0, s.lifetimePurchasedCoins)
    }

    // =========================================================================
    // 2. SIFIRLAMA — odenmis hak silinemez
    // =========================================================================

    /**
     * SERT KURAL: ilerlemesini sifirlayan oyuncu **parasini kaybetmez**.
     *
     * Ve daha da onemlisi: odenmis jeton listesi silinseydi, Play tuketilmemis
     * bir paketi ikinci kez dondurdugunde oyuncu bir kez odeyip IKI kez coin
     * alirdi. Yani bu koruma yalnizca adalet degil, EKONOMI butunlugu.
     */
    @Test
    fun resetProgressPreservesEveryIapKey() {
        val s = save()
        s.grantPurchasedCoins("tok-A", 1_200)
        s.adsRemoved = true
        s.prestigeRank = 3

        s.resetProgress()

        assertTrue("odenmis reklamsiz hakki silinmis", s.adsRemoved)
        assertEquals("satin alinan coin sayaci silinmis", 1_200, s.lifetimePurchasedCoins)
        assertTrue("odenmis jeton kaydi silinmis", s.isPurchaseTokenGranted("tok-A"))
        // Sifirlama ILERLEMEYI goturur — bunlar korunmaz.
        assertEquals(0, s.prestigeRank)
        assertEquals(0, s.loadWallet().coins)
    }

    /**
     * Sifirlamadan SONRA ayni jeton yine odenmemeli: bakiye 0'a dondu ama
     * "bu satin alma odendi" gercegi degismedi. Aksi halde sifirlama, sinirsiz
     * coin uretmenin yolu olurdu (sifirla -> Play ayni paketi geri getirir ->
     * tekrar odenir).
     */
    @Test
    fun aResetDoesNotTurnAnAlreadyPaidPurchaseIntoANewOne() {
        val s = save()
        s.grantPurchasedCoins("tok-A", 1_200)
        s.resetProgress()
        assertEquals(0, s.loadWallet().coins)

        assertFalse("sifirlama sonrasi ayni jeton yeniden odendi", s.grantPurchasedCoins("tok-A", 1_200))
        assertEquals(0, s.loadWallet().coins)
    }

    // =========================================================================
    // 3. SATIN ALINAN COIN, OYNANIS KAZANCI DEGILDIR
    // =========================================================================

    /**
     * `lifetimeEarned` OYNANISLA kazanilan coini olcer ve ekonomi raporlari
     * (`CoinLedgerTest`, ECONOMY_ANALYSIS tablolari) onu okur. Satin alma oraya
     * yazilsaydi, para harcayan bir oyuncunun kaydi ekonominin "musluklar ne
     * kadar veriyor" olcumunu kirletirdi — ve kampanya dengesi yanlis bir
     * tabana gore ayarlanirdi.
     *
     * Bu yuzden `grantPurchasedCoins` cuzdani `PlayerWallet.credited` ile DEGIL
     * dogrudan yazar. Bu test o ayrimin kasitli oldugunu kilitler.
     */
    @Test
    fun purchasedCoinsAreCountedSeparatelyFromGameplayEarnings() {
        val s = save()
        val before = s.loadWallet()
        s.grantPurchasedCoins("tok-A", 1_200)
        val after = s.loadWallet()

        assertEquals("bakiye artmali", before.coins + 1_200, after.coins)
        assertEquals(
            "satin alma lifetimeEarned'e yazilmis — ekonomi olcumu kirlenir",
            before.lifetimeEarned, after.lifetimeEarned,
        )
        assertEquals("ayri sayac yazilmamis", 1_200, s.lifetimePurchasedCoins)

        // Oynanis kazanci ise lifetimeEarned'i artirmali (karsit ornek):
        s.saveWallet(after.credited(300))
        assertEquals(before.lifetimeEarned + 300, s.loadWallet().lifetimeEarned)
        assertEquals("oynanis kazanci satin alma sayacina yazilmis", 1_200, s.lifetimePurchasedCoins)
    }

    @Test
    fun lifetimePurchasedCoinsAccumulatesAcrossPurchases() {
        val s = save()
        s.grantPurchasedCoins("a", 1_200)
        s.grantPurchasedCoins("b", 4_000)
        s.grantPurchasedCoins("a", 1_200) // yok sayilir
        assertEquals(5_200, s.lifetimePurchasedCoins)
    }

    // =========================================================================
    // 4. REKLAMSIZ BAYRAGI — iki yone de yazilabilmeli
    // =========================================================================

    /**
     * Bayrak `false` YAZILABILIR olmali: Play'de iade edilen bir satin almada
     * `queryPurchasesAsync` artik o urunu dondurmez ve `restorePurchases`
     * `onRemoveAdsChanged(false)` cagirir. Yalnizca `true` yazabilen bir alan,
     * iade edilmis bir hakki sonsuza kadar acik birakirdi.
     *
     * (Cihazda gozlemlendi: debug sahte bayragi, uygulama yeniden acilip Play'e
     * baglaninca `false`a dondu — Play sahte satin almayi bilmiyor.)
     */
    @Test
    fun adsRemovedCanBeWrittenInBothDirections() {
        val s = save()
        assertFalse("varsayilan true olamaz", s.adsRemoved)
        s.adsRemoved = true
        assertTrue(s.adsRemoved)
        s.adsRemoved = false
        assertFalse("iade edilen hak geri alinamiyor", s.adsRemoved)
    }

    // =========================================================================
    // 5. BOZUK/ESKI KAYIT — cokmez
    // =========================================================================

    /**
     * IAP anahtarlari 2026-09-02'de eklendi; ondan onceki her kayitta YOKTUR.
     * Eski kayitla acilis, varsayilanlara dusmeli ve hicbir sey kirmamalidir.
     */
    @Test
    fun aSaveWrittenBeforeIapExistedOpensWithSafeDefaults() {
        val store = InMemoryKeyValueStore()
        store.putInt("eco_coins", 4_200)
        store.putString("eco_cleared_levels", "1,2,3")
        val s = SaveManager(store)

        assertFalse(s.adsRemoved)
        assertEquals(0, s.lifetimePurchasedCoins)
        assertFalse(s.isPurchaseTokenGranted("herhangi"))
        assertEquals("eski bakiye korunmali", 4_200, s.loadWallet().coins)

        // Ve ilk satin alma normal calismali.
        assertTrue(s.grantPurchasedCoins("tok", 1_200))
        assertEquals(5_400, s.loadWallet().coins)
    }

    /**
     * Jeton listesi virgullu bir dize. Bozuk girdi (bos alanlar, bosluk,
     * bastaki/sondaki virgul) cokme uretmemeli ve gecerli jetonlari
     * kaybetmemeli — `SaveManager`in geri kalaninin "cop degere toleransli"
     * kodlama/cozme kuralinin ayni uygulamasi.
     */
    @Test
    fun corruptTokenListIsToleratedWithoutLosingRealTokens() {
        val store = InMemoryKeyValueStore()
        store.putString("iap_granted_tokens", ",, tok-A , ,,tok-B,")
        val s = SaveManager(store)

        assertTrue(s.isPurchaseTokenGranted("tok-A"))
        assertTrue(s.isPurchaseTokenGranted("tok-B"))
        assertFalse(s.isPurchaseTokenGranted(""))
        assertFalse(s.isPurchaseTokenGranted("tok-C"))

        // Bozuk liste uzerine yazim da calismali.
        assertFalse("bozuk listedeki gercek jeton yeniden odendi", s.grantPurchasedCoins("tok-A", 1_200))
        assertTrue(s.grantPurchasedCoins("tok-C", 1_200))
        assertTrue(s.isPurchaseTokenGranted("tok-A"))
    }

    // =========================================================================
    // 6. KATALOG <-> KALICILIK BAGI
    // =========================================================================

    /**
     * Katalogdaki her paket gercekten odenebilmeli ve miktari birebir yazilmali.
     * Bu, `StoreCatalog` ile `SaveManager` arasindaki tek sozlesmedir; biri
     * digerinden habersiz degistirilirse burasi patlar.
     */
    @Test
    fun everyCatalogPackCanBePaidOutExactlyOnceForItsExactAmount() {
        val s = save()
        var expected = s.loadWallet().coins
        StoreCatalog.coinPacks.forEachIndexed { i, pack ->
            val token = "tok-$i-${pack.id}"
            assertTrue("${pack.id} odenemedi", s.grantPurchasedCoins(token, pack.coins))
            expected += pack.coins
            assertEquals("${pack.id} yanlis miktar yazdi", expected, s.loadWallet().coins)
            assertFalse("${pack.id} ikinci kez odendi", s.grantPurchasedCoins(token, pack.coins))
            assertEquals(expected, s.loadWallet().coins)
        }
        assertEquals(StoreCatalog.coinPacks.sumOf { it.coins }, s.lifetimePurchasedCoins)
    }

    // =========================================================================
    // 7. GUNLUK GUCLENDIRICI-REKLAM SAYACI — artik KALICI
    // =========================================================================

    /**
     * 2026-09-02'ye kadar bu sayac yalnizca bellekteydi ve her acilista
     * sifirlaniyordu. Iki sonucu vardi:
     *  1. `BOOSTER_AD_VIEWS_PER_DAY` "reklam yorgunlugu tavani" olarak
     *     belgeliydi ama uygulamayi kapatip acmak onu sifirliyordu.
     *  2. Hava Destegi'nin YENI coin yolunu acan TEK kapi bu sayacti; hic
     *     dolmadigi icin IAP ile satilan coinin o kalemdeki harcama yolu OLU idi.
     */
    @Test
    fun theDailyBoosterAdCounterSurvivesAProcessRestart() {
        val store = InMemoryKeyValueStore()
        SaveManager(store).boosterAdViewsToday = 7
        // "Yeniden baslatma": AYNI depo uzerinde YENI bir SaveManager.
        assertEquals(7, SaveManager(store).boosterAdViewsToday)
    }

    @Test
    fun theDailyBoosterAdCounterIsClearedOnDayRollover() {
        val s = save()
        s.boosterAdViewsToday = 9
        s.resetDailyCounters()
        assertEquals(0, s.boosterAdViewsToday)
    }

    /**
     * Gunluk sayac ILERLEMEDIR, odenmis hak degil: `resetProgress` onu
     * goturmeli (IAP anahtarlarinin aksine).
     */
    @Test
    fun resetProgressClearsTheDailyBoosterAdCounterButNotThePaidEntitlement() {
        val s = save()
        s.boosterAdViewsToday = 5
        s.adsRemoved = true
        s.resetProgress()
        assertEquals(0, s.boosterAdViewsToday)
        assertTrue("odenmis hak silinmis", s.adsRemoved)
    }

    /** Tuketilmeyen urun bir coin paketi DEGIL — yanlislikla coin yazmamali. */
    @Test
    fun theRemoveAdsProductIsNotACoinPack() {
        assertNotEquals(
            "remove_ads bir coin paketi olarak cozulmus",
            null, StoreCatalog.packOf(StoreCatalog.PACK_RECRUIT),
        )
        assertEquals(0, com.miniappfactory.frontlinedefender.game.billing.FakeBilling
            .coinsOf(StoreCatalog.PRODUCT_REMOVE_ADS))
    }
}

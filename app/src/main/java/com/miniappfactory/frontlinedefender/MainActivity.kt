package com.miniappfactory.frontlinedefender

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.miniappfactory.frontlinedefender.game.ads.AdMobAdHost
import com.miniappfactory.frontlinedefender.game.ads.SaveManagerAdProgressStore
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.ui.BiomeBackgroundCache
import com.miniappfactory.frontlinedefender.game.ui.GameScreen
import com.miniappfactory.frontlinedefender.game.ui.LocalAdHost
import com.miniappfactory.frontlinedefender.ui.theme.FrontlineDefenderTheme

class MainActivity : ComponentActivity() {

    /**
     * Faz 5 — reklam katmaninin SAHIBI Activity'dir, Composable degil.
     *
     * Sebep: UMP riza formu ve tam ekran reklamlar Activity ister, ve
     * `MobileAds.initialize` uygulama basina **bir kez** cagrilmalidir.
     * Composable icinde `remember` edilseydi her yapilandirma degisikliginde
     * (donme, dil, karanlik mod) yeni bir host uretme riski olurdu.
     */
    /**
     * Reklam sayaci KALICI depoya baglandi (2026-08-22).
     *
     * Eskiden `AdMobAdHost()` varsayilan `InMemoryAdProgressStore`u
     * kullaniyordu ve sayac her acilista sifirlaniyordu — yani
     * `ONBOARDING_FREE_BATTLES` (3) ve `FIRST_SESSION_WARMUP_MS` (3 dk) HER
     * OTURUMDA bastan uygulaniyordu. Cihaz raporu: *"level 1 bitirdim, next
     * level dedigimde reklam gelmedi."*
     *
     * `SaveManager` burada `by lazy`: `Context` Activity olusturulurken hazir
     * degil, `onCreate`ta hazir. `adHost` da lazy olmak zorunda cunku ona
     * bagimli.
     */
    private val saveManager by lazy { SaveManager(applicationContext) }

    private val adHost by lazy {
        AdMobAdHost(progressStore = SaveManagerAdProgressStore(saveManager))
    }

    /**
     * Faz 14 — ARKA PLANDA BITMAP IADESI.
     *
     * `BiomeBackgroundCache` bolum arka planini decode + recolor edilmis
     * halde tutar: olculen **7,92 MiB** (docs/PERFORMANCE_REPORT.md).
     * `clear()` tanimliydi ve test ediliyordu ama uretimde HIC cagrilmiyordu,
     * yani uygulama arka plandayken bu bellek elde kaliyordu. Birincil test
     * cihazi Galaxy S8 / API 24 — projenin en dar bellek butcesi; bu, sistemin
     * uygulamayi oldurme olasiligini dogrudan artiriyordu.
     *
     * ⚠ NEDEN YALNIZCA BURADA: `returnToMainMenu()` / `returnToLevelSelect()`
     * icinde temizlemek CAZIP ama YANLIS. Oyuncu "tekrar dene" derse ayni
     * harita yeniden decode + recolor edilir (gece biyomunda 137 ms+), yani
     * bellek kazanci dogrudan bolum acilis gecikmesine cevrilir.
     * `TRIM_MEMORY_UI_HIDDEN` esigi tam olarak "kullanici artik gormuyor"
     * demektir; geri donulurse yeniden uretmek serbesttir.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) BiomeBackgroundCache.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // KENARDAN KENARA + GERCEK INSET YONETIMI.
        //
        // ONCEKI HATA: `enableEdgeToEdge()` TEK BASINA cagriliyordu. O cagri
        // yalnizca "sistem cubuklarinin arkasina ciz" der; icerigi cubuklardan
        // KORUMAZ. Sonuc: durum/gezinme cubuklari HUD'in USTUNDE kaliyordu.
        // targetSdk 36'da bu davranis Android 15+ cihazlarda ZORUNLU, yani
        // enableEdgeToEdge'i silmek de bir cozum degil — insetleri uygulamak
        // gerekiyor (asagida, `windowInsetsPadding(safeDrawing)`).
        //
        // Oyun `sensorLandscape`: cubuklar YANLARDA olusur. Yani duzeltilmemis
        // halde kaybedilen sey ust seritten birkac piksel degil, gezinme
        // cubugunun/kesigin altinda kalan HUD kenari ve dokunulamayan sag/sol
        // seritti. `safeDrawing` dort kenari da (durum + gezinme + ekran kesigi)
        // kapsar, bu yuzden yatay-ozel bir hesap yazilmadi.
        //
        // Cubuk simgeleri ACIK renk olsun diye iki cubuk da `dark` stiliyle
        // seffaf: oyunun zemini koyu (SleekDarkBg), varsayilan otomatik secim
        // acik zemin varsayip koyu simge cizebiliyordu.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // TAM EKRAN (immersive) — SISTEM CUBUKLARI GIZLENIR.
        //
        // ## Neden bu, "insetleri kaldiralim" DEGIL
        // Yukaridaki `windowInsetsPadding(safeDrawing)` gercek bir hatayi
        // kapatti: cubuklar HUD'in USTUNE biniyordu (cihazda goruldu — saat
        // "DALGA 1/6" rozetinin, wifi/pil simgeleri "1x" dugmesinin uzerine
        // dusuyordu ve ikisi de okunmuyordu). O padding'i geri almak, kazanilan
        // yerin bedelini OKUNAMAYAN HUD olarak oderdi.
        //
        // Cubuklari GIZLEMEK ise ayni yeri kazandirir ve carpismayi geri
        // getirmez: gizli cubugun ineti sifirdir, yani `safeDrawing` kendi
        // kendine kuculur ve `MainActivity` disindaki hicbir dosya bu karardan
        // haberdar olmak zorunda kalmaz. Ekran KESIGI insetleri ise `safeDrawing`
        // icinde DURMAYA devam eder — kesik gizlenemez, HUD onun altina
        // girmemeli.
        //
        // ## Neden yatay oyunda kazanc buyuk
        // Oyun `sensorLandscape` ve yuzey ~360 dp yuksekliginde. Durum cubugu
        // tek basina bunun ~%7'si; yanda gezinme cubugu da var. Harita
        // `GameCanvas` icinde FIT + letterbox yerlesiyor, yani kaybedilen her
        // dp yalnizca tepeden kirpmiyor, haritanin TAMAMINI kucultup yanlarda
        // siyah bant aciyor.
        //
        // ## BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Cubuklar yok olmaz, ERISILEBILIR kalir: kenardan kaydirinca gecici
        // olarak gelir ve kendiliginden tekrar gizlenir. Oyuncu saate/pile
        // bakabilir, geri gidebilir. Kalici gizleme (`BEHAVIOR_DEFAULT`)
        // kullanilmadi cunku gezinme cubugunu kalici gizlemek geri hareketini
        // ogrenilmesi gereken bir sirra cevirir.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // UMP riza + SDK baslatma + on-yukleme. Oyunu BLOKLAMAZ: bu cagri
        // aninda doner, sonuc callback ile gelir ve `setContent` beklemez.
        // Riza alinamazsa reklam istenmez, oyun reklamsiz tam oynanir
        // (offline oynanabilir bir oyundur).
        adHost.initialize(this)

        setContent {
            FrontlineDefenderTheme {
                // Surface TUM PENCEREYI kaplar ve koyu zemini cubuklarin
                // ARKASINA da boyar; inset bosluklarinda pencere zemini (acik
                // tema varsayilani beyaz olabilir) gorunmesin diye.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // INSET KAPISI BURADA, `GameScreen`'in DISINDA.
                    //
                    // NEDEN SAVAS ALANI BOZULMUYOR: `GameScreen` HUD'in OLCULEN
                    // yuksekligini `topInsetPx` olarak `GameCanvas`'a veriyor ve
                    // motor oynanis dikdortgenini ona gore kuruyor. Bu hesabin
                    // tamami GameScreen'in KENDI yerel koordinat uzayinda ve
                    // GORELI: "HUD ne kadar yer kapladi" sorusunu soruyor,
                    // "ekranin tepesi nerede" sorusunu degil.
                    //
                    // Buradaki padding, GameScreen'e verilen kutuyu kucultur ve
                    // kaydirir; kutunun ICINDEKI HUD-savas alani iliskisini
                    // degistirmez. HUD yine kutunun tepesine oturur, olculur ve
                    // ayni piksel degeri asagi aktarilir. Yani savas alani
                    // HUD'a gore ayni yerde kalir; yalnizca kutunun tamami
                    // guvenli alana cekilir. GameScreen.kt'ye ve GameCanvas'a
                    // TEK SATIR dokunulmadi.
                    //
                    // Sistem cubugu genisligi 0 olan cihazlarda (cogu tam ekran
                    // oyun modu, gomme cubuklu tabletler) padding 0 dp'dir, yani
                    // mevcut duzen birebir korunur — degisiklik yalnizca
                    // gercekten cubuk/kesik olan cihazlarda gorunur.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        // Faz 13 — ODUL KOPRUSU ARTIK BURADAN GECMIYOR.
                        //
                        // Eskiden bu cagri `rewardBridge = LoggingAdRewardBridge`
                        // veriyordu: oyuncuya "reklam izle, +150 coin al" deniyor
                        // ve hicbir sey verilmiyordu. Yerine gercek kopru
                        // (`EconomyAdRewardBridge`) gecti ve onu `GameScreen`
                        // kuruyor — adapte ettigi ekonomi nesnesinin ve
                        // carpilacak taban odulun sahibi orasi. Activity reklam
                        // HOST'unun sahibi olmaya devam ediyor (SDK + UMP + tam
                        // ekran cagrilari).
                        //
                        // `LocalAdHost`: ayni host'u AYARLAR EKRANINA tasir.
                        // Ayarlari acan composable'lar (`MainMenuOverlay`,
                        // `PauseMenuModal`) `AdHost` parametresi almiyor ve
                        // almalari `GameScreen.kt` icindeki cagri yerlerini
                        // degistirmeyi gerektirirdi. Saglayici olmadan ayarlar
                        // ekrani `NoOpAdHost` gorur ve "Gizlilik Secenekleri"
                        // satiri hic cizilmez — yani bu saglayici, UMP satirinin
                        // CALISMASININ sarti.
                        CompositionLocalProvider(LocalAdHost provides adHost) {
                            GameScreen(adHost = adHost)
                        }
                    }
                }
            }
        }
    }

    /**
     * TAM EKRANI GERI AL — bu override OLMADAN kazanc TEK SEFERLIK olur.
     *
     * `hide()` kalici bir mod degil, tek seferlik bir emirdir. Pencere odagi
     * kaybedip geri aldiginda sistem cubuklari GERI GELIR. Bu oyunda odagin
     * kaybedildigi yer istisna degil KURAL:
     *
     *   - her odullu/gecis reklami (tam ekran, ayri Activity),
     *   - UMP riza formu,
     *   - bildirim panelini acip kapatma,
     *   - uygulama degistirip geri donme.
     *
     * Yani bu satirlar olmasaydi ilk reklamdan sonra cubuklar temelli geri
     * gelir ve oyuncu "duzeltme calismiyor" derdi — depodaki "kod var, cagiran
     * yok" hatasinin odak-olayli akrabasi.
     *
     * `hasFocus` kapisi bilincli: odak YOKKEN gizlemeye calismak (ornegin
     * reklam onde iken) reklamin kendi pencere ayarlariyla yarisir.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        // Bekleyen timeout handler'lari ve on-yuklenmis reklam referanslari
        // birakilir; aksi halde Activity sizar.
        adHost.dispose()
        super.onDestroy()
    }
}

package com.miniappfactory.frontlinedefender.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.miniappfactory.frontlinedefender.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import androidx.compose.ui.unit.dp
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.audio.rememberHaptics
import com.miniappfactory.frontlinedefender.game.billing.BillingManager
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.ContinueDecision
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.LevelClearResult
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import kotlinx.coroutines.isActive

@Composable
fun GameScreen() {
    val context = LocalContext.current

    val saveManager = remember { SaveManager(context) }
    val audioManager = remember { AudioManager(context) }
    // Motora verilen sey SAF KOTLIN arayuz (`HapticsFeedback`); `HapticsManager`
    // onu uyguluyor. Surec basina tek ornek oldugu icin `remember`in anahtarsiz
    // olmasi guvenli: yeniden besteleme yeni bir motor uretmez.
    val haptics = rememberHaptics()
    val gameEngine = remember { GameEngine(saveManager, audioManager, haptics) }

    val gameState by gameEngine.gameState.collectAsState()

    // MUZIK SAHNESI. `AudioManager` kendi `init` blogunda MENU ile basliyor,
    // yani acilista muzik zaten var; buradaki etki muzigi oyun durumuna
    // BAGLAR. Onceden gecis ses efektlerinden cikarsaniyordu ve savasi
    // bitirmeden cikan oyuncuda (duraklama -> bolum secme) ne VICTORY ne
    // DEFEAT caldigi icin savas muzigi harita ekraninda calmaya devam
    // ediyordu. Ayni parca tekrar verildiginde `setMusicScene` hicbir sey
    // yapmadigi icin bu etki her durum degisiminde kosulsuz calisabilir.
    LaunchedEffect(gameState) {
        musicSceneFor(gameState)?.let(audioManager::setMusicScene)
    }

    // Faz 3 — SES YASAM DONGUSU.
    //
    // SoundPool ORNEK BELLEGI TUTAR; release() cagrilmazsa sizar (skill'in en
    // sik bulunan ses hatasi). Reklam da bir lifecycle olayidir: interstitial
    // acilinca ON_PAUSE gelir -> autoPause() ile calan sesler susar ve
    // simulasyon durur; kapaninca ON_RESUME ile ses kanallari geri gelir.
    // Oyun kasitli olarak PAUSED kalir, oyuncu devam tusuna kendi basar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, audioManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    gameEngine.pauseForLifecycle()
                    audioManager.onPause()
                }
                Lifecycle.Event.ON_RESUME -> audioManager.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioManager.onPause()
            audioManager.release()
        }
    }

    // Faz 2 — KARE INVALIDATION KANCASI.
    //
    // Sorun: GameEngine'in towers/enemies/projectiles/visualEffects listeleri duz
    // mutableListOf(). Compose snapshot state olmadiklari icin motor onlari
    // degistirdiginde GameCanvas'in draw lambda'si HIC gecersiz kilinmiyordu ->
    // HUD sayaclari akiyor ama savas alani donmus kaliyordu.
    //
    // Cozum: her karede artan bir sayac. GameCanvas bu sayaci YALNIZCA draw
    // lambda'sinin icinde okur; boylece her karede sadece CIZIM fazi gecersiz
    // olur, GameScreen/HUD/TowerBuildBar RECOMPOSE OLMAZ. Burada `by` ile
    // okumuyoruz -- okusaydik GameScreen saniyede 60 kez recompose olurdu.
    val frameTick = remember { mutableIntStateOf(0) }

    // 60FPS Game Loop using lockstep VSYNC withFrameNanos
    LaunchedEffect(Unit) {
        var lastTimeNanos = System.nanoTime()
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val dt = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastTimeNanos = frameTimeNanos
                gameEngine.tick(dt)
                frameTick.intValue++
            }
        }
    }

    // Faz 3: ust HUD seridi OPAK. Yuksekligi olculur ve oynanis alani bunun
    // altina alinir; aksi halde haritanin en ustteki iki build pad'i kalici
    // olarak yarim gorunur (cihazda dogrulandi).
    val density = LocalDensity.current
    var hudInsetPx by remember {
        mutableFloatStateOf(with(density) { GameConfig.HUD_TOP_INSET_DP.dp.toPx() })
    }

    // Faz 4 — kampanya ilerlemesi. Yildizlar SaveManager uzerinden KALICI;
    // coin bakiyesi ve coin ile kilit acma su an BELLEK-ICI (ekonomi ajani
    // kalici implementasyonu baglayacak, `CampaignProgress` sozlesmesi hazir).
    // Faz 9: KALICI ekonomi. `InMemoryCampaignProgress` iskelesi kaldirildi —
    // coin bakiyesi, kilit durumu, meta yukseltmeler ve gorevler artik
    // SaveManager uzerinden surece dayanikli. `CampaignProgressImpl` ayni
    // `CampaignProgress` sozlesmesini uyguluyor, ustune ekonomi API'si ekliyor
    // (onLevelCleared / grantSupplyDrop / gorevler).
    val campaignProgress = remember(saveManager) { CampaignProgressImpl(saveManager) }

    /** Son bolum sonucu — zafer ekrani coin odul dokumunu bundan okur. */
    var lastClearResult by remember { mutableStateOf<LevelClearResult?>(null) }

    /**
     * Zafer ekranindaki "SONRAKI BOLUM" butonunun hedefi; `null` ise buton
     * GOSTERILMEZ.
     *
     * Iki durumda null olur: kampanya bitti (son bolum) veya siradaki bolum
     * hala kilitli. Ikincisi bilincli — kilitli bolume goturen bir buton
     * oyuncuyu "yeterli coinin yok" duvarina carptirirdi; zafer aninin
     * yapmamasi gereken tam olarak budur. O durumda tek buton kalir ve
     * oyuncu haritaya doner, kilidi orada gorur.
     */
    val nextPlayableLevel: Int? = run {
        val candidate = gameEngine.levelSpec.levelId + 1
        if (gameState == GameState.VICTORY &&
            candidate <= EconomyConfig.CAMPAIGN_LEVELS &&
            campaignProgress.isUnlocked(candidate)
        ) {
            candidate
        } else {
            null
        }
    }

    /** Cephanelik (meta yukseltme dukkani) acik mi — LEVEL_SELECT ustunde katman. */
    var shopOpen by remember { mutableStateOf(false) }

    // IKMAL DEPOSU (IAP, 2026-09-02) — Cephanelik'in USTUNDE katman: oyuncu
    // Cephanelik'ten girer, geri deyince Cephanelik'e doner (yigin korunur).
    var storeOpen by remember { mutableStateOf(false) }

    // Play Billing koprusu. FD'de ViewModel yok; burada `remember` ile kurulur,
    // her ON_RESUME'da `refresh()` (cevrimdisi acilista yazilamayan hak ve arka
    // planda tamamlanan PENDING odeme icin — Kron-Drive 2026-09-01 dersi) ve
    // kompozisyon dusunce `endConnection()`. Coin yazimi ve reklamsiz bayragi
    // ekonomiye gider; reklam katmani `SaveManagerAdProgressStore` uzerinden
    // ayni SharedPreferences dosyasini okur.
    val billing = remember(campaignProgress) {
        BillingManager(
            context = context,
            // Dayanikli defter + ACIK sonuc (GrantResult); cuzdan sahibi yenilenir.
            grantPurchase = campaignProgress::grantPurchase,
            onRemoveAdsChanged = { campaignProgress.noteLegacyAdFreePurchase() },
        )
    }
    val billingLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(billing, billingLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) billing.refresh()
        }
        billingLifecycleOwner.lifecycle.addObserver(observer)
        billing.connect()
        onDispose {
            billingLifecycleOwner.lifecycle.removeObserver(observer)
            billing.endConnection()
        }
    }
    val launchPurchase: (String) -> Unit = { productId ->
        context.findActivity()?.let { billing.launchPurchase(it, productId) }
    }

    // ILERLEMEYI SIFIRLAMA — IKI ADIM, TEK YER.
    //
    // `SaveManager.resetProgress()` yalnizca DEPOYU siler. `CampaignProgressImpl`
    // cuzdani/agaci/gorevleri kurulumda bir kez okur ve bu nesne hic yeniden
    // kurulmaz (`remember(saveManager)`, `saveManager` da anahtarsiz `remember`).
    // Tazeleme olmadan: ekran silinmis veriyi gostermeye devam eder ve ilk coin
    // hareketi (bolum bitirme, yukseltme, elit bilet, gorev odulu) bellekteki
    // ESKI cuzdani silinmis kaydin uzerine geri yazar.
    val resetProgressAndReload: () -> Unit = {
        saveManager.resetProgress()
        campaignProgress.reloadFromStore()
    }

    // ANA MENUDEN acilan gorev paneli. `LevelSelectScreen` kendi
    // `missionsOpen` durumunu ICINDE tutuyor ve o oyle KALIYOR — iki ekranin
    // durumunu tek degiskende birlestirmek, bolum secimden menuye donuldugunde
    // panelin kendiliginden acilmasina yol acardi.
    var menuMissionsOpen by remember { mutableStateOf(false) }

    // Faz 10: YILDIZ ARBITRAJINI KAPATAN baglanti.
    //
    // "Us Tamiri" guclendiricisi kaybedilen us canini geri veriyor. Yildiz
    // kalan can YUZDESINDEN hesaplandigi icin, baglanti olmadan oyuncu
    // guclendiriciyle daha YUKSEK yildiz (ve daha fazla coin) satin alabilirdi.
    // `starHealthFor` tamir edilen cani yildiz hesabindan duser: tamir
    // HAYATTA KALMA satin alir, yildiz/coin asla.
    //
    // Motordaki seam'in varsayilani kimlik fonksiyonu — bu satir olmadan
    // davranis Faz 9 ile birebir ayni, yani sessizce bozulmaz, sadece
    // arbitraj acik kalirdi.
    LaunchedEffect(campaignProgress) {
        gameEngine.starHealthAdjuster = campaignProgress::starHealthFor
    }

    // Faz 12 — GUCLENDIRICI SAYAClARININ SAVAS BASINA SIFIRLANMASI.
    //
    // `gameState`i dinlemek YETMEZ: PAUSED -> "yeniden basla" ve DEFEAT ->
    // "tekrar dene" akislarinda PREPARATION degeri TEKRAR gelir, yani akista
    // gozlenebilir bir degisim olmaz ve yeni savas kacirilirdi — guclendirici
    // haklari onceki savastan sessizce devrederdi. `battleEpoch` her
    // `startNewGame()` cagrisinda artar, dolayisiyla her savasi tam olarak bir
    // kez yakalar. Gunluk reklam sayaci `beginBattle` icinde TASINIR.
    val battleEpoch by gameEngine.battleEpoch.collectAsState()
    LaunchedEffect(battleEpoch) {
        campaignProgress.beginBattle(gameEngine.levelSpec.levelId)

        // Faz 15 — OLCUM TABANI. Iki alan icin "olculmedi" ile "olculdu ve
        // sifir/1x" ayrimini SAVAS BASINDA kurmak zorundayiz; `beginBattle`
        // raporu UNREPORTED'a cekiyor ve o durumda iki beceri gorevi de
        // hicbir zaman hak edilemez.
        //
        //  - `noteSellTrackingActive()` -> towersSold = 0. "Hic satmadi"
        //    ancak boyle ISPATLANIR; olculmemis bir alan `d_s_no_sell`i
        //    kazandirmaz (ve kazandirmamali — satan oyuncu 120 coin alirdi).
        //  - `noteGameSpeed(...)` -> clearedAtDoubleSpeed = false. Motor her
        //    `startNewGame`de hizi 1x'e cekiyor, yani bu taban dogru; HUD'daki
        //    dugme 2x'e cikardigi anda deger true'ya kilitlenir.
        campaignProgress.noteSellTrackingActive()
        campaignProgress.noteGameSpeed(gameEngine.gameSpeed.value)
    }

    /** Savas gercekten devam ediyor mu? Dalga aralarindaki PREPARATION'i saymaz. */
    var battleActive by remember { mutableStateOf(false) }

    // ⛔ GERI TUSU OYUNDAN CIKARIYORDU.
    //
    // Savasin ortasinda telefonun geri tusuna basmak uygulamayi kapatip ana
    // ekrana atiyordu — hicbir uyari yok, ilerleme kaydedilmemis. Sebep basit:
    // `MissionsScreen`, `SettingsScreen` ve `UpgradeShopScreen` kendi
    // `BackHandler`larini tasiyordu ama SAVAS EKRANI hicbirini tasimiyordu,
    // yani geri tusu sistemin varsayilanina (Activity'yi kapat) dusuyordu.
    //
    // Android'de geri tusu "bir adim geri" demektir; oyunun her ekraninda bir
    // karsiligi olmali:
    //  · Savas (hazirlik/dalga) -> DURAKLAT. Oyuncunun aradigi sey zaten bu;
    //    duraklatma menusu cikma/bastan alma/ana menuyu de icinde tasiyor.
    //  · Duraklatma acikken -> DEVAM ET. Geri tusu modali kapatir, ikinci bir
    //    "cikmak istiyor musun" katmani acmaz.
    //  · Zafer/yenilgi -> BOLUM SECIME don. Modalin BOLUM SEC butonuyla ayni
    //    yer; sonuc ekraninda geri tusuyla oyundan atilmak sasirtici.
    //  · Bolum secimi -> ANA MENU.
    //
    // ANA MENUDE KAPALI (`enabled = false`): oyunun kok ekranindan geri tusu
    // GERCEKTEN cikmali. Aksi halde oyuncu uygulamadan hic cikamazdi ve bu,
    // duzeltmenin kendisinden daha kotu bir hata olurdu.
    //
    // Ust ustelik acilan ekranlar (gorevler, ayarlar, cephanelik) kendi
    // `BackHandler`larini SONRA kaydeder ve dispatcher son kaydedileni once
    // calistirir; yani bu handler onlarin ustune binmez.
    BackHandler(enabled = gameState != GameState.MAIN_MENU) {
        when (gameState) {
            GameState.PREPARATION, GameState.WAVE_RUNNING, GameState.PAUSED ->
                gameEngine.togglePause()
            GameState.VICTORY, GameState.DEFEAT -> gameEngine.returnToLevelSelect()
            GameState.LEVEL_SELECT -> gameEngine.returnToMainMenu()
            GameState.MAIN_MENU -> Unit
        }
    }

    // Savas yasam dongusu. `battleActive`, zafer coin odulunun ve bolum kaydinin savas basina
    // YALNIZCA BIR KEZ yazilmasini saglayan nobettir: recomposition odulu tekrarlamaz ve
    // savas oynanmadan VICTORY'ye dusen bir durum (ornegin ekran yeniden kurulumu) odul yazmaz.
    // Yenilgi + devam ayni savasi surdurur; `battleActive` DEFEAT'te bilincli olarak true kalir,
    // boylece devamla kazanilan zafer de tam bir kez odenir.
    LaunchedEffect(gameState) {
        when (gameState) {
            GameState.PREPARATION -> battleActive = true
            GameState.VICTORY -> if (battleActive) {
                battleActive = false
                // Faz 9: COIN ODULU BURADA yatirilir.
                //
                // Faz 13: `livesLeft` artik ham `lives.value` DEGIL,
                // meta-arindirilmis `victoryStarHealth`. Ham deger meta
                // yukseltme bonusunu (Tahkimat: +10 can) iceriyordu ama payda
                // taban 20'de kaliyordu — Tahkimat'li oyuncu 12 sizintiyla
                // 3 yildiz, 10 sizintiyla Kusursuz Savunma (+80 coin)
                // aliyordu. Ayni duzeltme motorun kendi yildiz hesabinda da
                // var; iki taraf AYNI girdiyi kullaniyor.
                // Faz 15 — SAVAS RAPORU BURADA AYRICA GECILMEZ: savas boyunca
                // `BattleTelemetry` cagrilari olcumu ekonominin KENDI
                // `battleReport` alaninda biriktirdi; `onLevelCleared` once o
                // birikimi dalga tablosundan turetilen tabanla birlestiriyor,
                // sonra tek seferde goreve isliyor.
                if (gameEngine.isEliteMode.value) {
                    // ELIT ZAFER — ayri muhasebe (ECONOMY_ANALYSIS C):
                    // coin YOK, yildiz YOK, gorev sayaci OYNANMAZ; tek kalici
                    // etki elit zafer sayaci. `lastClearResult` null kalir,
                    // yani zafer modali coin satiri cizmez.
                    campaignProgress.onEliteCleared(gameEngine.levelSpec.levelId)
                    lastClearResult = null
                } else {
                    lastClearResult = campaignProgress.onLevelCleared(
                        levelId = gameEngine.levelSpec.levelId,
                        livesLeft = gameEngine.victoryStarHealth,
                        maxLives = gameEngine.levelSpec.maxBaseLives
                    )
                }
            }
            GameState.MAIN_MENU, GameState.LEVEL_SELECT -> {
                battleActive = false
                // Guclendiriciler SAVASA KAPSAMLIDIR, stoklanmaz. Zafer/yenilgi
                // dallarinda DEGIL burada kapatiliyor: `onLevelCleared` yildizi
                // `starHealthFor` uzerinden hesapliyor ve o da savas
                // guclendirici durumuna bakiyor — erken kapatmak Us Tamiri'nin
                // yildiz notrlugunu sessizce devre disi birakirdi.
                campaignProgress.endBattle()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (gameState) {
            GameState.MAIN_MENU -> {
                MainMenuOverlay(
                    gameEngine = gameEngine,
                    onStartGame = { gameEngine.openLevelSelect() },
                    // CEPHANELIK ve GOREVLER artik ANA MENUDEN de
                    // acilabiliyor. Ikisi de YENI EKRAN DEGIL —
                    // bolum secim ekranindan zaten ulasilan aynı iki
                    // panel, ayni durum degiskenleriyle. Tek degisen
                    // giris noktasi sayisi.
                    onOpenArmory = { shopOpen = true },
                    onOpenMissions = { menuMissionsOpen = true },
                    // REAKTIF prestij rank'i. `MainMenuOverlay` bunu
                    // eskiden `SaveManager`dan okuyordu ve satin alma
                    // sonrasi cip ANCAK yeniden baslatmada cikiyordu
                    // (2026-09-02 cihaz bulgusu).
                    prestigeRank = campaignProgress.prestigeRank,
                    onResetProgress = resetProgressAndReload
                )

                // Iki panel de TAM EKRAN katman ve menunun USTUNDE: acikken
                // "HAREKATI BASLAT"a kazara basilamaz.
                if (shopOpen) {
                    UpgradeShopScreen(
                        progress = campaignProgress,
                        onBack = { shopOpen = false },
                        onOpenStore = { storeOpen = true }
                    )
                }
                if (storeOpen) {
                    StoreScreen(
                        progress = campaignProgress,
                        billing = billing,
                        onBack = { storeOpen = false },
                        onPurchase = launchPurchase
                    )
                }
                if (menuMissionsOpen) {
                    MissionsScreen(
                        progress = campaignProgress,
                        onClose = { menuMissionsOpen = false }
                    )
                }
            }
            GameState.LEVEL_SELECT -> {
                LevelSelectScreen(
                    progress = campaignProgress,
                    // `elite = false` ACIK: parametresiz varsayilan
                    // "mevcut bayrak"tir (retry sozlesmesi icin) ve
                    // buradan gelen her baslatma NORMALDIR.
                    onPlayLevel = { levelNo -> gameEngine.startNewGame(levelNo, elite = false) },
                    // ELIT SEVK: bilet ekonomide ODENDIKTEN sonra
                    // motor elit bayrakla baslatilir. Bilet denemeye
                    // degil SAVASA kesilir: retry ayni bilete dahil.
                    onPlayElite = { levelNo ->
                        if (campaignProgress.buyEliteTicket(levelNo)) {
                            gameEngine.startNewGame(levelNo, elite = true)
                        }
                    },
                    onBack = { gameEngine.returnToMainMenu() },
                    // CEPHANELIK girisi bolum secim ekraninin BASLIK SATIRINDA.
                    // Eskiden serbest bir katmandi ve yatayda (360 dp yukseklik)
                    // bolum kartlarinin USTUNE biniyordu.
                    onOpenArmory = { shopOpen = true },
                    // Faz 20 — PERDE ACILIS KARTI baglantisi. Kalicilik `SaveManager`in
                    // var olan dizeyle anahtarlanan tek atislik bayrak API'si uzerinden
                    // yuruyor (`act_intro_1..5`).
                    actIntroStore = remember(saveManager) {
                        SaveManagerActIntroStore(saveManager)
                    },
                    // COIN CIPI = IKMAL DEPOSU KAPISI (satin alma). Reklamla coin kazanma
                    // teklifi YOKTUR; cip yalnizca depoyu acar.
                    onOpenStore = { storeOpen = true }
                )

                // Cephanelik TAM EKRAN katman: acikken bolum kartlari erisilemez olur,
                // boylece kazara sevk olmaz.
                if (shopOpen) {
                    UpgradeShopScreen(
                        progress = campaignProgress,
                        onBack = { shopOpen = false },
                        onOpenStore = { storeOpen = true }
                    )
                }
                if (storeOpen) {
                    StoreScreen(
                        progress = campaignProgress,
                        billing = billing,
                        onBack = { storeOpen = false },
                        onPurchase = launchPurchase
                    )
                }
            }
            else -> {
                // Interactive 2D Canvas Battlefield
                GameCanvas(
                    gameEngine = gameEngine,
                    frameTick = frameTick,
                    topInsetPx = hudInsetPx,
                    modifier = Modifier.fillMaxSize()
                )

                // Top HUD Header
                HUDOverlay(
                    gameEngine = gameEngine,
                    onOpenPauseMenu = { gameEngine.togglePause() },
                    telemetry = campaignProgress,
                    modifier = Modifier.onSizeChanged { hudInsetPx = it.height.toFloat() }
                )

                // Bottom Build Drawer when build spot is selected
                TowerBuildBar(
                    gameEngine = gameEngine,
                    telemetry = campaignProgress,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // BOLUM DEGISTIRICILERI — reddedilen insanin SEBEBI.
                // Cekmecenin USTUNDE ve onun disinda cizilir: cekmecenin
                // olculen yuksekligi `GameCanvas`in secim hayaletine capa
                // oluyor, seridi iceri koymak o capayi kaydirirdi.
                BuildRejectionStrip(
                    gameEngine = gameEngine,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Bottom Inspector Drawer when tower is selected
                SelectedTowerInspector(
                    gameEngine = gameEngine,
                    telemetry = campaignProgress,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Faz 12 — guclendirici rayi. Sag kenarda, alta 72 dp capali:
                // ustteki iki cekmece (63 dp / 56 dp) acilinca ray YERINDEN
                // OYNAMAZ ve en sagdaki build pad'in dokunma dairesini ortmez.
                BoosterRail(
                    gameEngine = gameEngine,
                    progress = campaignProgress
                )

                // ILK OTURUM OGRETICISI (docs/FUN_AUDIT.md 1. madde).
                //
                // Katman SIRASI bilincli: savas alaninin, HUD'un ve iki alt
                // cekmecenin USTUNDE, butun modallarin ALTINDA. Boylece vurgu
                // halkasi oynanis yuzeyinde gorunur ama duraklatma/zafer/
                // yenilgi ekranlarinin onune GECEMEZ.
                //
                // Katman girdi YUTMAZ (tek tiklanabilir dugumu GEC cipi),
                // oyunu DURDURMAZ ve yalnizca bolum 1'in ILK oynanisinda
                // kosar — karar SaveManager.tutorialSeen bayraginda.
                TutorialOverlay(
                    gameEngine = gameEngine,
                    saveManager = saveManager,
                    hudInsetPx = hudInsetPx
                )

                // KILIT ACILMA IPUCLARI (docs/FUN_AUDIT.md — "4 kule = 4 rol
                // ama oyuncu bunlarin hicbirini ogrenemiyor").
                //
                // Ogreticinin BITTIGI yerden devam eder: bolum 1 dongusu
                // ogretir, bunlar ROLLERI ogretir (Cannon / Frost / Fuze
                // kilitleri acildiginda ve ilk zirhli dusman geldiginde).
                //
                // Neden TutorialOverlay'in HEMEN ALTINDA cizilir: ikisi ayni
                // konumu (HUD'un alti) ve ayni cip yerlesimini paylasiyor.
                // Ayni anda ASLA cizilemezler — ipucu motoru ogretici
                // kosarken kendini kapatir (HintSignals.tutorialArmed) — ama
                // sira yine de belirli olmali; hazirlik fazinda cikan ipucu
                // hicbir kosulda ogretici vurgusunun ALTINDA kalmaz.
                //
                // Bu katman da girdi YUTMAZ (tek tiklanabilir dugumu ANLADIM
                // cipi) ve oyunu DURDURMAZ.
                UnlockHintStrip(
                    gameEngine = gameEngine,
                    saveManager = saveManager,
                    hudInsetPx = hudInsetPx
                )

                // SAVAS EKRANINDA REKLAM YOK: oynanis yuzeyi hicbir teklif katmani
                // tarafindan kaydirilmaz/kucultulmez.

                // Overlay Modals
                when (gameState) {
                    // Faz 4: "MAIN MENU" butonlari eskiden startNewGame() cagiriyordu,
                    // yani menuye donmek yerine ayni bolumu bastan baslatiyordu.
                    // Artik gercek bir cikis var.
                    GameState.PAUSED -> PauseMenuModal(
                        gameEngine = gameEngine,
                        onResume = { gameEngine.togglePause() },
                        onRestart = { gameEngine.startNewGame() },
                        onMainMenu = { gameEngine.returnToLevelSelect() },
                        onResetProgress = resetProgressAndReload
                    )
                    GameState.VICTORY -> VictoryModal(
                        gameEngine = gameEngine,
                        clearResult = lastClearResult,
                        nextLevelId = nextPlayableLevel,
                        onNextLevel = nextPlayableLevel?.let { next ->
                            { gameEngine.startNewGame(next) }
                        },
                        onLevelSelect = { gameEngine.returnToLevelSelect() }
                    )
                    GameState.DEFEAT -> {
                        val defeatLevel = gameEngine.levelSpec.levelId
                        val decision = campaignProgress.continueDecision(defeatLevel)
                        DefeatModal(
                            gameEngine = gameEngine,
                            // RETRY: ucretsiz ve HER ZAMAN acik. "Bir kere daha deneyeyim"
                            // dongusu hicbir bedelle cezalandirilmaz.
                            onRetry = { gameEngine.startNewGame() },
                            onMainMenu = { gameEngine.returnToLevelSelect() },
                            // DEVAM (coin): karar ve bakiye ekonomiden gelir; ekran kendi
                            // karsilastirmasini yapmaz. Kill switch kapaliysa satir yok.
                            continueOffer = if (decision is ContinueDecision.Disabled) {
                                null
                            } else {
                                ContinueOffer(decision, campaignProgress.coins)
                            },
                            // Bedel ve etki TAM BIR KEZ: `continueAfterDefeat` once ekonomiyi
                            // kontrol eder, motoru surdurur, ANCAK motor surdurduyse bedeli
                            // dusurur. Ikinci dokunus hicbir sey degistirmez.
                            onContinue = {
                                campaignProgress.continueAfterDefeat(
                                    defeatLevel,
                                    gameEngine::reinforceAfterDefeat
                                )
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * Oyun durumu -> muzik yatagi. `null` = "parcayi DEGISTIRME".
 *
 * Bu esleme neden UI katmaninda: `AudioManager` dosya/kanal seviyesinde
 * calisir ve `GameState` gibi bir oynanis kavramini TANIMAMALI (audio -> engine
 * bagimliligi olusurdu). `GameScreen` ikisini de zaten biliyor, dogru yer burasi.
 *
 * PAUSED BILINCLI OLARAK `null`: duraklama menusu acilinca menu parcasina gecmek
 * hos gorunur ama devam edildiginde savas parcasi BASTAN baslar. Duraklama bir
 * sahne degisimi degil, ayni sahnenin askiya alinmasidir; uygulama arka plana
 * giderse zaten [AudioManager.onPause] muzigi duraklatir.
 *
 * PREPARATION MENU parcasini calar: oyuncu kule yerlestirirken sakin ton,
 * ilk dalgayla birlikte savas tonu devralir. Bu, dalga baslangicini duyulur
 * bir olay haline getirir.
 */
internal fun musicSceneFor(state: GameState): AudioManager.MusicTrack? = when (state) {
    GameState.WAVE_RUNNING -> AudioManager.MusicTrack.BATTLE
    GameState.MAIN_MENU,
    GameState.LEVEL_SELECT,
    GameState.PREPARATION,
    GameState.VICTORY,
    GameState.DEFEAT -> AudioManager.MusicTrack.MENU
    GameState.PAUSED -> null
}

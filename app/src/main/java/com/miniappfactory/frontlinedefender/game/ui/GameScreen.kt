package com.miniappfactory.frontlinedefender.game.ui

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
import com.miniappfactory.frontlinedefender.game.ads.AdHost
import com.miniappfactory.frontlinedefender.game.ads.AdPolicyConfig
import com.miniappfactory.frontlinedefender.game.ads.AdRewardBridge
import com.miniappfactory.frontlinedefender.game.ads.BannerAdSlot
import com.miniappfactory.frontlinedefender.game.ads.ConsentManager
import com.miniappfactory.frontlinedefender.game.ads.InterstitialReason
import com.miniappfactory.frontlinedefender.game.ads.EconomyAdRewardBridge
import com.miniappfactory.frontlinedefender.game.ads.LoggingAdRewardBridge
import com.miniappfactory.frontlinedefender.game.ads.NoOpAdHost
import com.miniappfactory.frontlinedefender.game.ads.RewardedOfferSheet
import com.miniappfactory.frontlinedefender.game.ads.RewardedPlacement
import com.miniappfactory.frontlinedefender.game.ads.SupplyDropBar
import com.miniappfactory.frontlinedefender.game.ads.applyDoublePayout
import com.miniappfactory.frontlinedefender.game.ads.applyReinforcement
import com.miniappfactory.frontlinedefender.game.ads.applySupplyDrop
import com.miniappfactory.frontlinedefender.game.ads.findActivity
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.audio.rememberHaptics
import com.miniappfactory.frontlinedefender.game.economy.BoosterType
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.LevelClearResult
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Sonuc modalindan cikis + interstitial. Navigasyon SADECE reklam akisi
 * bittiginde yapilir, ama reklam gelmese de MUTLAKA yapilir.
 */
private data class PendingBattleExit(
    val reason: InterstitialReason,
    val navigate: () -> Unit
)

@Composable
fun GameScreen(
    /**
     * Faz 5. Varsayilan [NoOpAdHost]: onizlemeler ve testler reklam SDK'sina
     * hic dokunmaz ve **en kotu senaryoyu** (hicbir reklam yok) yasar. Gercek
     * host'u `MainActivity` verir — SDK'nin ve rizanin sahibi orasidir.
     */
    adHost: AdHost = NoOpAdHost(),
    /**
     * Faz 13 — odul koprusu OVERRIDE'i. **Uretimde `null` birakilir**: bu ekran
     * o zaman ekonomiye bagli gercek kopruyu ([EconomyAdRewardBridge]) kendisi
     * kurar.
     *
     * Neden `MainActivity` enjekte etmiyor: koprunun adapte ettigi iki sey de
     * — kalici ekonomi nesnesi ([CampaignProgressImpl]) ve R3'un carpacagi
     * taban odul ([LevelClearResult]) — bu ekranin sahipligindedir. Activity'nin
     * kopruyu uretmesi, ekonominin sahipligini de Activity'ye tasimak demekti.
     * Reklam *host*'unun sahibi hala Activity'dir (SDK + UMP + tam ekran).
     *
     * Bir deger verilirse (ornegin [LoggingAdRewardBridge]) ekonomi baglanmaz;
     * bu yalnizca test/onizleme icindir.
     */
    rewardBridge: AdRewardBridge? = null
) {
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

    /**
     * Son bolum sonucu — R3 Cift Odeme odulu bunun uzerine EK katman koyar.
     * Taban odul reklamdan ONCE yatirilir (GDD G.4), bu yuzden burada tutuyoruz.
     */
    var lastClearResult by remember { mutableStateOf<LevelClearResult?>(null) }

    /**
     * Faz 13 — ODUL KOPRUSU. Bundan onceki hali `LoggingAdRewardBridge` idi:
     * oyuncuya "reklam izle, +150 coin al" deniyor ve **hicbir sey verilmiyordu**.
     *
     * Kopru burada kuruluyor cunku adapte ettigi iki sey de burada: kalici
     * ekonomi ([campaignProgress]) ve R3'un carpacagi taban odul dokumu
     * ([lastClearResult] — zafer aninda, reklamdan ONCE yatirilmis olan).
     * Miktarlarin sahibi tamamen ekonomidir; reklam katmani yalnizca sonucu
     * bildirir (bkz. `AdRewardBridge` KDoc'u).
     *
     * `remember` anahtari yalnizca [campaignProgress] ve [gameEngine]: taban
     * odul ve motor durumu LAMBDA ile okunur, yani her zaferde/karede kopru
     * yeniden kurulmaz ama her zaman GUNCEL degeri gorur.
     */
    val economyRewardBridge = remember(campaignProgress, gameEngine) {
        EconomyAdRewardBridge(
            progress = campaignProgress,
            lastClearResult = { lastClearResult },
            // R2 Takviye: motor artik yenilgiden cikip savasi kaldigi dalgadan
            // surdurebiliyor (`GameEngine.reinforceAfterDefeat`), bu yuzden
            // teklif ARTIK GOSTERILEBILIR. Donen 0 = uygulanamadi; kopru o
            // durumda odulu "verilmedi" sayar ve oyuncu yine yenilgi modalini
            // gorur — akis hicbir dalda kilitlenmez.
            resumeBattle = gameEngine::reinforceAfterDefeat,
        )
    }

    /** Etkin kopru: uretimde ekonomiye bagli olan, testte disaridan verilen. */
    val activeRewardBridge = rewardBridge ?: economyRewardBridge

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
    }

    /** Reklam yolu istenen guclendirici; sheet bunun uzerinden acilir. */
    var boosterAdRequest by remember { mutableStateOf<BoosterType?>(null) }

    // ----------------------------------------------------------------------
    // Faz 5 — REKLAM CAGRI YERLERI
    //
    // Yerlesim (DECISIONS "Reklam doktrini" + GDD §G.1):
    //   banner        -> YALNIZCA MAIN_MENU ve LEVEL_SELECT, ekranin ALT KENARI
    //   interstitial  -> YALNIZCA sonuc modali KAPANDIKTAN sonra, bolum secime
    //                    donuste (zafer VEYA yenilgi). RETRY'de YOK.
    //   rewarded      -> R1 bolum secim, R3 zafer, R2 yenilgi (motor API'si bekliyor)
    //   savas ekrani  -> HICBIR reklam yuzeyi yok
    // ----------------------------------------------------------------------
    val activity = remember(context) { context.findActivity() }
    val appContext = remember(context) { context.applicationContext }

    // Riza akisini BURADA topluyoruz: UMP formu kapandiginda GameScreen
    // recompose olur ve `adHost.bannerAllowed` yeniden okunur. Aksi halde
    // banner ilk acilista hic gelmez (@Volatile alan recomposition tetiklemez).
    val canRequestAds by ConsentManager.canRequestAdsFlow.collectAsState()
    // `BANNER_ENABLED` en basta: kapatildiginda riza durumu ve SDK hic
    // sorgulanmaz. Gerekce AdPolicyConfig.BANNER_ENABLED KDoc'unda
    // (yatayda 360 dp ekranin %14'unu yiyordu).
    val bannerEnabled = AdPolicyConfig.BANNER_ENABLED && canRequestAds && adHost.bannerAllowed

    /** Savas gercekten devam ediyor mu? Dalga aralarindaki PREPARATION'i saymaz. */
    var battleActive by remember { mutableStateOf(false) }

    /** Rewarded hak sayaci degisti -> teklif satirlari yeniden okunsun. */
    var rewardTick by remember { mutableIntStateOf(0) }

    var doublePayoutOfferOpen by remember { mutableStateOf(false) }
    var reinforcementOfferOpen by remember { mutableStateOf(false) }
    var supplyDropOfferOpen by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<PendingBattleExit?>(null) }

    // Savas yasam dongusu -> frekans politikasinin sayaclari.
    LaunchedEffect(gameState) {
        when (gameState) {
            GameState.PREPARATION -> if (!battleActive) {
                battleActive = true
                adHost.onBattleStarted()
                rewardTick++
            }
            GameState.VICTORY -> if (battleActive) {
                battleActive = false
                // Interstitial hakki BURADA dogar (GDD §G.2/5): savas sonuna
                // kadar oynandi. Yarida birakilan savas tetiklemez.
                adHost.onBattleCompleted()
                // Sonuc ekrani gorunurken sessiz on-yukleme (GDD §G.4).
                adHost.preload(appContext)
                // Faz 9: COIN ODULU BURADA yatirilir — reklamdan ONCE
                // (GDD G.4). `battleActive` bayragi bu blogun savas basina
                // YALNIZCA BIR KEZ kosmasini garantiler; recomposition
                // odulu tekrarlamaz.
                //
                // Faz 13: `livesLeft` artik ham `lives.value` DEGIL,
                // meta-arindirilmis `victoryStarHealth`. Ham deger meta
                // yukseltme bonusunu (Tahkimat: +10 can) iceriyordu ama payda
                // taban 20'de kaliyordu — Tahkimat'li oyuncu 12 sizintiyla
                // 3 yildiz, 10 sizintiyla Kusursuz Savunma (+80 coin)
                // aliyordu. Ayni duzeltme motorun kendi yildiz hesabinda da
                // var; iki taraf AYNI girdiyi kullaniyor.
                lastClearResult = campaignProgress.onLevelCleared(
                    levelId = gameEngine.levelSpec.levelId,
                    livesLeft = gameEngine.victoryStarHealth,
                    maxLives = gameEngine.levelSpec.maxBaseLives
                )
                doublePayoutOfferOpen =
                    adHost.isRewardedOffered(RewardedPlacement.DOUBLE_PAYOUT)
                rewardTick++
            }
            GameState.DEFEAT -> if (battleActive) {
                // R2 yalnizca kopru gercekten takviye uygulayabildiginde
                // teklif edilir; calismayan bir odul teklif edilmez.
                val offerReinforcement = activeRewardBridge.reinforcementSupported &&
                    adHost.isRewardedOffered(RewardedPlacement.REINFORCEMENT)
                reinforcementOfferOpen = offerReinforcement
                // KRITIK: teklif acilacaksa savas HENUZ BITMEDI. Takviye kabul
                // edilirse ayni savas kaldigi dalgadan surer, yani
                // `onBattleCompleted()` burada cagrilirsa tek savas iki kez
                // sayilir VE — daha kotusu — savas PREPARATION'a dondugunde
                // yukaridaki dal `onBattleStarted()` cagirip savas-basi
                // rewarded hakkini SIFIRLAR, yani sinirsiz takviye acilir.
                // `battleActive` bilincli olarak true birakiliyor; savas-sonu
                // muhasebesi teklif kapandiktan SONRA yapiliyor (asagida,
                // R2 sheet'inin onDismiss'inde).
                if (!offerReinforcement) {
                    battleActive = false
                    adHost.onBattleCompleted()
                    adHost.preload(appContext)
                }
                rewardTick++
            }
            GameState.MAIN_MENU, GameState.LEVEL_SELECT -> {
                if (battleActive) {
                    battleActive = false
                    // Yarida birakildi: interstitial hakki DOGMAZ.
                    adHost.onBattleAbandoned()
                }
                // Guclendiriciler SAVASA KAPSAMLIDIR, stoklanmaz. Zafer/yenilgi
                // dallarinda DEGIL burada kapatiliyor: `onLevelCleared` yildizi
                // `starHealthFor` uzerinden hesapliyor ve o da savas
                // guclendirici durumuna bakiyor — erken kapatmak Us Tamiri'nin
                // yildiz notrlugunu sessizce devre disi birakirdi.
                campaignProgress.endBattle()
                adHost.preload(appContext)
            }
            else -> {}
        }
    }

    // Sonuc modali kapandiktan SONRA interstitial, sonra navigasyon.
    val exit = pendingExit
    LaunchedEffect(exit) {
        if (exit == null) return@LaunchedEffect
        // Modal bu kare ile birlikte kaldirildi (pendingExit != null iken
        // cizilmiyor). Bir kare bekleyip reklamin modalin USTUNE acilmasini
        // kesin olarak engelliyoruz.
        withFrameNanos { }

        var done = false
        val proceed = {
            if (!done) {
                done = true
                exit.navigate()
                pendingExit = null
            }
        }

        val act = activity
        if (act == null) {
            proceed()
        } else {
            adHost.showInterstitial(act, exit.reason) { proceed() }
            // Son savunma: SDK callback'i hic gelmezse oyuncu modalsiz bir
            // savas alaninda kilitlenirdi. Hedef ekran her iki yolda da ayni
            // oldugu icin bu supap hicbir seyi bozmaz.
            delay(AdPolicyConfig.EXIT_WATCHDOG_MS)
            proceed()
        }
    }

    /**
     * Faz 12 — REKLAM YOLUYLA GUCLENDIRICI. **SIRA KRITIKTIR.**
     *
     * Reklam acilinca `ON_PAUSE` -> [GameEngine.pauseForLifecycle] gelir ve oyun
     * BILINCLI olarak PAUSED kalir ("oyuncu devam tusuna kendi basar"). Ama
     * [GameEngine.applyBoosterActivation] `acceptsBattlefieldInput()` kapisina
     * bakiyor ve PAUSED'da `false` donuyor — oysa
     * [CampaignProgressImpl.activateBooster] kullanimi ve **gunluk reklam
     * hakkini o noktada ZATEN harcamis** olurdu. Yani naif sira "reklami izle,
     * hicbir sey alma" demek olurdu.
     *
     * Bu yuzden: once oyun devam ettirilir, kapi DOGRULANIR, ancak ondan sonra
     * ekonomi katmani cagrilir. Hak, uygulanacagi kesinlesmeden tuketilmez.
     */
    fun applyBoosterAd(type: BoosterType, granted: Boolean): String {
        val name = context.getString(boosterNameRes(type))
        if (!granted) return context.getString(R.string.booster_ad_unavailable)

        if (gameEngine.gameState.value == GameState.PAUSED) gameEngine.togglePause()
        if (!gameEngine.acceptsBattlefieldInput()) {
            // Basarisiz dalda da mesaj okunur: yukarida devam ettirdigimiz
            // oyunu geri duraklat, yoksa "islem basarisiz" yazisini okurken
            // savas akmaya devam eder.
            val blocked = gameEngine.gameState.value
            if (blocked == GameState.PREPARATION || blocked == GameState.WAVE_RUNNING) {
                gameEngine.togglePause()
            }
            return context.getString(R.string.booster_ad_failed, name)
        }

        val activation = campaignProgress.activateBooster(
            type = type,
            viaAd = true,
            supplyOnHand = gameEngine.gold.value,
            baseHealth = gameEngine.lives.value,
            maxBaseHealth = gameEngine.maxLives,
            // Reklam yolu da hedef kontrolune TABIDIR. Bu satir olmadan
            // varsayilan `ENEMY_COUNT_UNKNOWN` gecerdi ve bos sahada reklamla
            // alinan hava destegi savas basina tek EK hakki ile gunluk 4
            // reklam hakkindan birini HICBIR SEYE yakardi — ustelik oyuncu
            // bedelini reklam izleyerek zaten odemis olurdu.
            enemiesOnField = gameEngine.enemies.size
        )
        val message = if (gameEngine.applyBoosterActivation(activation)) {
            context.getString(R.string.booster_ad_applied, name)
        } else {
            context.getString(R.string.booster_ad_failed, name)
        }

        // SONUC OKUNURKEN SIMULASYON DURUR.
        //
        // Yukarida oyunu devam ettirmek ZORUNDAYDIK (`acceptsBattlefieldInput`
        // kapisi PAUSED'da false doner ve hak yanardi), ama `RewardedOfferSheet`
        // RESULT fazinda EKRANDA KALIR. Bu satir olmadan oyuncu "Hava destegi
        // devrede" mesajini okurken dusmanlar ilerliyor ve us cani gidiyordu —
        // yani odulunu okumanin bedeli can oluyordu.
        //
        // Ayni desen takviye teklifinde (R2) zaten uygulanmis; burada eksikti.
        // `onDismiss` "PAUSED ise togglePause" yaptigi ve idempotent oldugu
        // icin sheet kapaninca oyun kaldigi yerden devam eder.
        val stateNow = gameEngine.gameState.value
        if (stateNow == GameState.PREPARATION || stateNow == GameState.WAVE_RUNNING) {
            gameEngine.togglePause()
        }
        return message
    }

    /** Boss zaferi (bolum 11/22) politika tarafinda korunur — GDD §G.2/6. */
    fun victoryExitReason(): InterstitialReason =
        if (gameEngine.levelSpec.levelId in AdPolicyConfig.BOSS_LEVEL_IDS) {
            InterstitialReason.BOSS_VICTORY_TO_LEVEL_SELECT
        } else {
            InterstitialReason.RESULT_TO_LEVEL_SELECT
        }

    Box(modifier = Modifier.fillMaxSize()) {
        when (gameState) {
            GameState.MAIN_MENU -> {
                // Banner ALT KENARDA ve Column icinde: no-fill'de 0dp'ye
                // cokmesi ustteki menunun yerini DEGISTIRMEZ (bindirme yok,
                // yani parmagin altinda duzen kaymasi da yok).
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        MainMenuOverlay(
                            gameEngine = gameEngine,
                            onStartGame = { gameEngine.openLevelSelect() }
                        )
                    }
                    BannerAdSlot(enabled = bannerEnabled)
                }
            }
            GameState.LEVEL_SELECT -> {
                // GORUNEN SAYININ SAHIBI EKONOMI (Faz 13).
                //
                // Eskiden ikisi de `adHost`tan okunuyordu; oradaki sayac
                // BELLEK-ICI, ekonomininki KALICI. Uygulamayi kapatip acan
                // oyuncuya ekran "3 hak" diyor, ekonomi hakki dolu gorup
                // 150 yerine 50 coin oduyordu. Ekonomi sayaci reklam
                // katmanininkine her zaman esit veya ondan kucuk oldugu icin
                // ("consume" yalnizca dolu odulde olur, ekonomi ustune bir de
                // yeniden baslatmalari hatirlar) kapi HER ZAMAN once ekonomide
                // kapanir — yani oyuncu hicbir zaman "hakkin var" deyip
                // reddedilen bir butona basmaz.
                val supplyRemaining = campaignProgress.supplyDropViewsLeftToday
                val supplyOffered = supplyRemaining > 0 &&
                    campaignProgress.supplyDropBudgetLeftToday > 0 &&
                    adHost.isRewardedOffered(RewardedPlacement.SUPPLY_DROP)

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        LevelSelectScreen(
                            progress = campaignProgress,
                            onPlayLevel = { levelNo -> gameEngine.startNewGame(levelNo) },
                            onBack = { gameEngine.returnToMainMenu() }
                        )
                        // CEPHANELIK girisi — coin'in gidecek gorunur yeri.
                        // Sag ust kosede, coin bakiyesinin hemen ALTINDA:
                        // "param var" ile "harcayacak yer" yan yana duruyor.
                        Text(
                            text = stringResource(R.string.shop_open),
                            color = SleekGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 62.dp, end = 20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x334C7A2E))
                                .clickable { shopOpen = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("open_armory")
                        )
                    }
                    // R1 seridi banner ile bolum kartlari ARASINDA: hicbir
                    // oynanis/navigasyon butonu reklama bitisik durmaz.
                    SupplyDropBar(
                        offered = supplyOffered,
                        remaining = supplyRemaining,
                        onRequest = { supplyDropOfferOpen = true }
                    )
                    BannerAdSlot(enabled = bannerEnabled, guardGapDp = 12.dp)
                }

                // Cephanelik TAM EKRAN katman: acikken bolum kartlari ve R1
                // seridi erisilemez olur, boylece kazara sevk/reklam olmaz.
                if (shopOpen) {
                    UpgradeShopScreen(
                        progress = campaignProgress,
                        onBack = { shopOpen = false }
                    )
                }

                if (supplyDropOfferOpen) {
                    RewardedOfferSheet(
                        adHost = adHost,
                        placement = RewardedPlacement.SUPPLY_DROP,
                        title = stringResource(R.string.ad_sheet_supply_title),
                        body = stringResource(
                            R.string.ad_sheet_supply_body,
                            AdPolicyConfig.SUPPLY_DROP_FULL_COIN,
                            AdPolicyConfig.SUPPLY_DROP_REDUCED_COIN
                        ),
                        remainingLabel = stringResource(
                            R.string.ad_sheet_supply_remaining,
                            supplyRemaining,
                            AdPolicyConfig.SUPPLY_DROP_DAILY_LIMIT
                        ),
                        applyResult = { applySupplyDrop(context, it, activeRewardBridge) },
                        onDismiss = {
                            supplyDropOfferOpen = false
                            rewardTick++
                        }
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
                    modifier = Modifier.onSizeChanged { hudInsetPx = it.height.toFloat() }
                )

                // Bottom Build Drawer when build spot is selected
                TowerBuildBar(
                    gameEngine = gameEngine,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Bottom Inspector Drawer when tower is selected
                SelectedTowerInspector(
                    gameEngine = gameEngine,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Faz 12 — guclendirici rayi. Sag kenarda, alta 72 dp capali:
                // ustteki iki cekmece (63 dp / 56 dp) acilinca ray YERINDEN
                // OYNAMAZ ve en sagdaki build pad'in dokunma dairesini ortmez.
                BoosterRail(
                    gameEngine = gameEngine,
                    progress = campaignProgress,
                    onAdBoosterRequested = { type ->
                        // Teklif ekrani acikken savas AKMAYA DEVAM ETMEMELI:
                        // scrim dokunusu yutuyor ama simulasyon durmuyordu,
                        // yani oyuncu teklifi okurken us cani gidiyordu.
                        if (gameEngine.gameState.value != GameState.PAUSED) {
                            gameEngine.togglePause()
                        }
                        boosterAdRequest = type
                    }
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

                val adBooster = boosterAdRequest
                if (adBooster != null) {
                    RewardedOfferSheet(
                        adHost = adHost,
                        placement = RewardedPlacement.BOOSTER,
                        title = stringResource(R.string.ad_sheet_booster_title),
                        body = stringResource(
                            R.string.ad_sheet_booster_body,
                            stringResource(boosterNameRes(adBooster))
                        ),
                        remainingLabel = stringResource(
                            R.string.ad_sheet_booster_remaining,
                            campaignProgress.boosterAdViewsLeftToday,
                            EconomyConfig.BOOSTER_AD_VIEWS_PER_DAY
                        ),
                        // Reklam gelmemesi oyuncuyu CEZALANDIRMAZ (ev kurali:
                        // no-fill ilerlemeyi kilitlemez). Ust sinir zaten
                        // ekonomi katmaninin KALICI gunluk sayacidir.
                        applyResult = { applyBoosterAd(adBooster, it.grantsSomething) },
                        onDismiss = {
                            boosterAdRequest = null
                            // `applyBoosterAd` basarili yolda oyunu zaten
                            // devam ettirdi; bu yalnizca iptal/no-fill dalini
                            // toparlar ve iki kez cagrilmasi guvenlidir.
                            if (gameEngine.gameState.value == GameState.PAUSED) {
                                gameEngine.togglePause()
                            }
                            rewardTick++
                        }
                    )
                }

                // SAVAS EKRANINDA BANNER YOK (DECISIONS bağlayıcı) — oynanis
                // yuzeyi hicbir reklam tarafindan kaydirilmaz/kucultulmez.

                // Overlay Modals
                // pendingExit != null iken modal cizilmez: interstitial
                // modalin USTUNE acilmaz, arkasinda da kalmaz.
                if (pendingExit == null) {
                    if (reinforcementOfferOpen) {
                        // R2 — TAKVIYE. `when (gameState)` DISINDA ve butun
                        // modallarin ONUNDE ciziliyor.
                        //
                        // Neden disarida: takviye kabul edilince motor DEFEAT'ten
                        // cikip PREPARATION'a doner. Sheet `GameState.DEFEAT`
                        // dalinin icinde kalsaydi tam o anda yok olurdu; sonuc
                        // mesaji ("takviye indi") hic gorunmez, `onDismiss` hic
                        // cagrilmaz ve ertelenmis savas-sonu muhasebesi asili
                        // kalirdi.
                        RewardedOfferSheet(
                            adHost = adHost,
                            placement = RewardedPlacement.REINFORCEMENT,
                            title = stringResource(R.string.ad_sheet_reinforce_title),
                            body = stringResource(
                                R.string.ad_sheet_reinforce_body,
                                AdPolicyConfig.REINFORCEMENT_LIVES
                            ),
                            applyResult = { result ->
                                val message = applyReinforcement(context, result, activeRewardBridge)
                                // Takviye uygulandiysa motor PREPARATION'a dondu
                                // ve hazirlik sayaci AKMAYA BASLADI — oyuncu ise
                                // hala sonuc mesajini okuyor. Guclendirici
                                // teklifindeki desenin aynisi: teklif ekrani
                                // acikken simulasyon durur.
                                if (gameEngine.gameState.value == GameState.PREPARATION) {
                                    gameEngine.togglePause()
                                }
                                message
                            },
                            onDismiss = {
                                reinforcementOfferOpen = false
                                if (gameEngine.gameState.value == GameState.PAUSED) {
                                    // Takviye uygulandi: ayni savas kaldigi
                                    // dalgadan devam eder. Savas-sonu muhasebesi
                                    // YAPILMAZ, cunku savas bitmedi.
                                    gameEngine.togglePause()
                                } else if (battleActive) {
                                    // Takviye yok (vazgecildi veya motor
                                    // uygulayamadi): savas gercekten bitti ve
                                    // ertelenmis muhasebe simdi yapilir.
                                    battleActive = false
                                    adHost.onBattleCompleted()
                                    adHost.preload(appContext)
                                }
                                rewardTick++
                            }
                        )
                    } else {
                        when (gameState) {
                            // Faz 4: "MAIN MENU" butonlari eskiden startNewGame() cagiriyordu,
                            // yani menuye donmek yerine ayni bolumu bastan baslatiyordu.
                            // Artik gercek bir cikis var.
                            // Guclendirici teklifi acikken oyun BIZIM tarafimizdan
                            // duraklatildi; duraklatma menusu de acilirsa scrim'in
                            // altinda birikir ve teklif kapaninca bir kare goz
                            // kirpar. Oyuncunun istedigi duraklatma degil, tekliftir.
                            GameState.PAUSED -> if (boosterAdRequest == null) {
                                PauseMenuModal(
                                    gameEngine = gameEngine,
                                    onResume = { gameEngine.togglePause() },
                                    onRestart = { gameEngine.startNewGame() },
                                    // Yarida birakma: interstitial YOK (GDD §G.2/5).
                                    onMainMenu = { gameEngine.returnToLevelSelect() }
                                )
                            }
                            GameState.VICTORY -> {
                                if (doublePayoutOfferOpen) {
                                    // R3 — sonuc modalinin ONUNDE, ustune bindirmeden.
                                    RewardedOfferSheet(
                                        adHost = adHost,
                                        placement = RewardedPlacement.DOUBLE_PAYOUT,
                                        title = stringResource(R.string.ad_sheet_double_title),
                                        body = stringResource(R.string.ad_sheet_double_body),
                                        applyResult = { applyDoublePayout(context, it, activeRewardBridge) },
                                        onDismiss = {
                                            doublePayoutOfferOpen = false
                                            rewardTick++
                                        }
                                    )
                                } else {
                                    VictoryModal(
                                        gameEngine = gameEngine,
                                        clearResult = lastClearResult,
                                        nextLevelId = nextPlayableLevel,
                                        onNextLevel = nextPlayableLevel?.let { next ->
                                            {
                                                pendingExit = PendingBattleExit(victoryExitReason()) {
                                                    gameEngine.startNewGame(next)
                                                }
                                            }
                                        },
                                        onLevelSelect = {
                                            pendingExit = PendingBattleExit(victoryExitReason()) {
                                                gameEngine.returnToLevelSelect()
                                            }
                                        }
                                    )
                                }
                            }
                            GameState.DEFEAT -> DefeatModal(
                                gameEngine = gameEngine,
                                // RETRY: interstitial YOK. "Bir kere daha
                                // deneyeyim" dongusu reklamla cezalandirilmaz
                                // (GDD §G.1: savas BASLAMADAN interstitial yok).
                                onRetry = { gameEngine.startNewGame() },
                                onMainMenu = {
                                    pendingExit = PendingBattleExit(
                                        InterstitialReason.RESULT_TO_LEVEL_SELECT
                                    ) { gameEngine.returnToLevelSelect() }
                                }
                            )
                            else -> {}
                        }
                    }
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

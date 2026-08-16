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
import com.miniappfactory.frontlinedefender.game.economy.CampaignProgressImpl
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
    /** Faz 5. Ekonomi katmani baglandiginda burasi degisir; baska hicbir yer degismez. */
    rewardBridge: AdRewardBridge = LoggingAdRewardBridge
) {
    val context = LocalContext.current

    val saveManager = remember { SaveManager(context) }
    val audioManager = remember { AudioManager(context) }
    val gameEngine = remember { GameEngine(saveManager, audioManager) }

    val gameState by gameEngine.gameState.collectAsState()

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
    val bannerEnabled = canRequestAds && adHost.bannerAllowed

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
            GameState.VICTORY, GameState.DEFEAT -> if (battleActive) {
                battleActive = false
                // Interstitial hakki BURADA dogar (GDD §G.2/5): savas sonuna
                // kadar oynandi. Yarida birakilan savas tetiklemez.
                adHost.onBattleCompleted()
                // Sonuc ekrani gorunurken sessiz on-yukleme (GDD §G.4).
                adHost.preload(appContext)
                if (gameState == GameState.VICTORY) {
                    // Faz 9: COIN ODULU BURADA yatirilir — reklamdan ONCE
                    // (GDD G.4). `battleActive` bayragi bu blogun savas basina
                    // YALNIZCA BIR KEZ kosmasini garantiler; recomposition
                    // odulu tekrarlamaz.
                    lastClearResult = campaignProgress.onLevelCleared(
                        levelId = gameEngine.levelSpec.levelId,
                        livesLeft = gameEngine.lives.value,
                        maxLives = gameEngine.levelSpec.maxBaseLives
                    )
                    doublePayoutOfferOpen =
                        adHost.isRewardedOffered(RewardedPlacement.DOUBLE_PAYOUT)
                } else {
                    // R2 yalnizca motor gercekten takviye uygulayabildiginde
                    // teklif edilir; calismayan bir odul teklif edilmez.
                    reinforcementOfferOpen = rewardBridge.reinforcementSupported &&
                        adHost.isRewardedOffered(RewardedPlacement.REINFORCEMENT)
                }
                rewardTick++
            }
            GameState.MAIN_MENU, GameState.LEVEL_SELECT -> {
                if (battleActive) {
                    battleActive = false
                    // Yarida birakildi: interstitial hakki DOGMAZ.
                    adHost.onBattleAbandoned()
                }
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
                val supplyOffered = remember(rewardTick) {
                    adHost.isRewardedOffered(RewardedPlacement.SUPPLY_DROP)
                }
                val supplyRemaining = remember(rewardTick) {
                    adHost.rewardedRemaining(RewardedPlacement.SUPPLY_DROP)
                }

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
                        applyResult = { applySupplyDrop(context, it, rewardBridge) },
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

                // SAVAS EKRANINDA BANNER YOK (DECISIONS bağlayıcı) — oynanis
                // yuzeyi hicbir reklam tarafindan kaydirilmaz/kucultulmez.

                // Overlay Modals
                // pendingExit != null iken modal cizilmez: interstitial
                // modalin USTUNE acilmaz, arkasinda da kalmaz.
                if (pendingExit == null) {
                    when (gameState) {
                        // Faz 4: "MAIN MENU" butonlari eskiden startNewGame() cagiriyordu,
                        // yani menuye donmek yerine ayni bolumu bastan baslatiyordu.
                        // Artik gercek bir cikis var.
                        GameState.PAUSED -> {
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
                                    applyResult = { applyDoublePayout(context, it, rewardBridge) },
                                    onDismiss = {
                                        doublePayoutOfferOpen = false
                                        rewardTick++
                                    }
                                )
                            } else {
                                VictoryModal(
                                    gameEngine = gameEngine,
                                    onReplay = {
                                        pendingExit = PendingBattleExit(victoryExitReason()) {
                                            gameEngine.returnToLevelSelect()
                                        }
                                    }
                                )
                            }
                        }
                        GameState.DEFEAT -> {
                            if (reinforcementOfferOpen) {
                                // R2 — bugun ULASILMAZ dal: rewardBridge
                                // .reinforcementSupported false. Motor API'si
                                // eklendiginde tek satirla acilir
                                // (docs/ADMOB_INTEGRATION.md §6).
                                RewardedOfferSheet(
                                    adHost = adHost,
                                    placement = RewardedPlacement.REINFORCEMENT,
                                    title = stringResource(R.string.ad_sheet_reinforce_title),
                                    body = stringResource(
                                        R.string.ad_sheet_reinforce_body,
                                        AdPolicyConfig.REINFORCEMENT_LIVES
                                    ),
                                    applyResult = { applyReinforcement(context, it, rewardBridge) },
                                    onDismiss = {
                                        reinforcementOfferOpen = false
                                        rewardTick++
                                    }
                                )
                            } else {
                                DefeatModal(
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
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

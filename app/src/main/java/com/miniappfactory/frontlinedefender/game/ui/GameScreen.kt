package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import kotlinx.coroutines.isActive

@Composable
fun GameScreen() {
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
    val campaignProgress = remember(saveManager) { InMemoryCampaignProgress(saveManager) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (gameState) {
            GameState.MAIN_MENU -> {
                // MAIN_MENU -> LEVEL_SELECT -> BATTLE akisi.
                MainMenuOverlay(
                    gameEngine = gameEngine,
                    onStartGame = { gameEngine.openLevelSelect() }
                )
            }
            GameState.LEVEL_SELECT -> {
                LevelSelectScreen(
                    progress = campaignProgress,
                    onPlayLevel = { levelNo -> gameEngine.startNewGame(levelNo) },
                    onBack = { gameEngine.returnToMainMenu() }
                )
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

                // Overlay Modals
                when (gameState) {
                    // Faz 4: "MAIN MENU" butonlari eskiden startNewGame() cagiriyordu,
                    // yani menuye donmek yerine ayni bolumu bastan baslatiyordu.
                    // Artik gercek bir cikis var.
                    GameState.PAUSED -> {
                        PauseMenuModal(
                            gameEngine = gameEngine,
                            onResume = { gameEngine.togglePause() },
                            onRestart = { gameEngine.startNewGame() },
                            onMainMenu = { gameEngine.returnToLevelSelect() }
                        )
                    }
                    GameState.VICTORY -> {
                        VictoryModal(
                            gameEngine = gameEngine,
                            onReplay = { gameEngine.returnToLevelSelect() }
                        )
                    }
                    GameState.DEFEAT -> {
                        DefeatModal(
                            gameEngine = gameEngine,
                            onRetry = { gameEngine.startNewGame() },
                            onMainMenu = { gameEngine.returnToLevelSelect() }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

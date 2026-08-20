package com.miniappfactory.frontlinedefender.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miniappfactory.frontlinedefender.R
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.economy.BoosterCurrency
import com.miniappfactory.frontlinedefender.game.economy.BoosterType
import com.miniappfactory.frontlinedefender.game.economy.EconomyConfig
import com.miniappfactory.frontlinedefender.game.economy.boosterPrice
import com.miniappfactory.frontlinedefender.game.economy.boostersAvailableAt
import com.miniappfactory.frontlinedefender.game.economy.emergencySupplyAmount
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.engine.GameState
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.model.TowerEntity
import com.miniappfactory.frontlinedefender.ui.theme.SleekBorderLight
import com.miniappfactory.frontlinedefender.ui.theme.SleekGold
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceCard
import com.miniappfactory.frontlinedefender.ui.theme.SleekSurfaceHeader
import com.miniappfactory.frontlinedefender.ui.theme.SleekTextAccent
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================================
// ILK OTURUM OGRETICISI — SAF MANTIK
// =====================================================================================

/**
 * Ogreticinin adimlari. **SIRA BAGLAYICI**: [TutorialFlow] bir sonraki adima
 * bu listedeki siraya gore gecer.
 */
enum class TutorialStep {
    /** Isaretli build pad'e dokun. */
    SELECT_PAD,

    /** Acilan insa cubugundan Gatling kur. */
    BUILD_TOWER,

    /** Kurulan kulenin menzil halkasini goster (girdi ISTEMEZ, kendi ilerler). */
    SHOW_RANGE,

    /** Dalgayi baslat. */
    START_WAVE,

    /** Oldurulen dusman Tedarik verir. */
    SUPPLY_GROWS
}

/** Ogreticinin nasil bittigi. Yalnizca terminal iki deger KALICI yazilir. */
enum class TutorialOutcome {
    /** Devam ediyor. */
    RUNNING,

    /** Butun adimlar gecildi veya bolum kazanildi -> bir daha gosterilmez. */
    COMPLETED,

    /** Oyuncu "GEC" dedi -> bir daha gosterilmez. */
    SKIPPED,

    /**
     * Savas yarida kaldi (yenilgi / bolum secime donus). **Kalici yazilmaz**:
     * oyuncu ogrenmeden ciktiysa tekrar denemesinde ogretici yine karsilar.
     */
    ABORTED
}

/**
 * Ogreticinin motordan okudugu HER SEY. Compose ve Android'e bagimsiz oldugu
 * icin [TutorialFlow] duz JVM testinde kosar (Robolectric YOK).
 */
data class TutorialSignals(
    val gameState: GameState,
    val padSelected: Boolean,
    val towerCount: Int,
    val supply: Int
)

/**
 * Ogreticinin o andaki durumu.
 *
 * @param supplyAtStepEntry Adima girildigi andaki Tedarik. [TutorialStep.SUPPLY_GROWS]
 *   "bu degerin USTUNE cikti mi" diye bakar; mutlak bir esik kullansaydi
 *   meta yukseltmeli oyuncuda yanlis anda tetiklenirdi.
 */
data class TutorialState(
    val step: TutorialStep,
    val elapsedSeconds: Float = 0f,
    val supplyAtStepEntry: Int = 0,
    val outcome: TutorialOutcome = TutorialOutcome.RUNNING
) {
    val running: Boolean get() = outcome == TutorialOutcome.RUNNING
}

/**
 * ---------------------------------------------------------------------------
 * ILK OTURUM ONBOARDING — durum makinesi ve yerlesim geometrisi
 * ---------------------------------------------------------------------------
 * docs/FUN_AUDIT.md 1. madde: oyunda hic ogretici yoktu ve yeni oyuncu ~40.
 * saniyede kayboluyordu. Bu nesne o akisin **saf** yarisidir; cizim
 * [TutorialOverlay] composable'inda.
 *
 * BOLUM 1'IN GERCEGI — akis buna gore kuruldu, genel gecer bir "kule dizme"
 * ogreticisi DEGIL:
 *
 * 1. Baslangic Tedariki **80**, Gatling **60**. Yani oyuncunun TEK kule
 *    hakki var ve yanlis yere kurmak bolumu bitirir. Bu yuzden ogretici
 *    "istedigin yere kur" DEMEZ, belirli bir pad'i isaretler.
 *
 * 2. Bolum 1'de 10 pad'in 5'i menzil disi oldugu icin GIZLI
 *    (`GameConfig.OUT_OF_RANGE_PADS[1] = {1,3,5,7,9}`), yani sahada
 *    **5 pad** var: 2, 4, 6, 8, 10.
 *
 * 3. Isaretlenen pad **[HIGHLIGHT_PAD_ID] = 2**. Gerekce: bolum 1'de
 *    catallanma kapali (`ALT_ROUTE_FIRST_LEVEL = 9`), yani tek aktif rota
 *    var ve pad 2 o rotaya **121 ref-px** uzakta — Gatling'in 150 menzilinin
 *    rahat icinde. Ayrica gorunur pad'ler icinde rota uzerinde **en erken**
 *    olani, dolayisiyla tek kule en cok atisi yapar. Konumu (%31 x, %58 y)
 *    alt %25 bas parmak seridinin ve `TowerBuildBar` cekmecesinin (~63 dp)
 *    disinda, sag kenardaki `BoosterRail`'in de disinda: ne parmak ne cekmece
 *    vurguyu orter.
 *
 * 4. Bolum 1'de yalnizca Gatling acik (Cannon 3, Frost 5, Fuze 7). "4 kule =
 *    4 rol" dersi burada VERILEMEZ; bu ogretici **dongusu** ogretir
 *    (mevzi -> kule -> menzil -> dalga -> ekonomi). Zirh/rol dersi ayri bir
 *    is: Cannon'in acildigi bolum 3 ve Fuze'nin acildigi bolum 7.
 *
 * OYUNCU KILITLENMEZ:
 * · Her adim "tatmin" kosuluyla biter ve kosullar ILERI ATLAMAYA acik —
 *   oyuncu dalgayi 1. adimda baslatirsa 2 ve 3 sessizce dusar.
 * · Isaretli pad DISINDA bir pad secmek de 1. adimi bitirir.
 * · Ogretici oyunu DURDURMAZ; hazirlik sayaci (10 sn) akmaya devam eder.
 * · [TutorialStep.SHOW_RANGE] girdi istemez, [RANGE_STEP_SECONDS] sonunda
 *   kendi ilerler — yani zorunlu bekleme tek yerde ve kisa.
 */
object TutorialFlow {

    /** Ogreticinin kostugu TEK bolum. */
    const val TUTORIAL_LEVEL_ID = 1

    /** Isaretlenen build pad. Gerekce icin sinif KDoc'una bak. */
    const val HIGHLIGHT_PAD_ID = 2

    /** [TutorialStep.SHOW_RANGE] kendi kendine ilerleme suresi. */
    const val RANGE_STEP_SECONDS = 3.5f

    /**
     * [TutorialStep.SUPPLY_GROWS] icin ust sinir. Dusman oldurulmese bile
     * ogretici sahada ASILI KALMAZ; sizinti da bir sonuctur ve ogretici o
     * anda oyuncunun onune serit koymaya devam etmemelidir.
     */
    const val SUPPLY_STEP_TIMEOUT_SECONDS = 12f

    val STEPS: List<TutorialStep> = TutorialStep.values().toList()

    val FIRST_STEP: TutorialStep = STEPS.first()

    // ---- Yerlesim sabitleri (dp) ------------------------------------------
    //
    // Bunlar BASKA composable'larin olculerini yansitir; o dosyalar baska
    // ajanlarin sahipligindedir ve degistirilemez. Bu yuzden konum
    // TAHMINDIR ve her adim "hedefe dokunmak" yerine "sonucu gormek"
    // uzerinden bittigi icin sapma hicbir adimi KILITLEYEMEZ.

    /** `TowerBuildBar` cekmecesinin olculen yuksekligi (~132 px @ 2.1x). */
    const val BUILD_BAR_HEIGHT_DP = 63f

    /** `TowerBuildBar` Row'unun yatay padding'i. */
    const val BUILD_BAR_H_PADDING_DP = 12f

    /** Kartlar arasi bosluk. */
    const val BUILD_CARD_GAP_DP = 8f

    /** Cekmecedeki kapatma IconButton'i. */
    const val BUILD_CLOSE_BUTTON_DP = 36f

    /** Kule karti sayisi = `GameConfig.TowerType` sayisi. */
    const val BUILD_CARD_COUNT = 4

    /** HUD'un ALT kenarindan yukari bakan okun ucuna kadar bosluk. */
    const val HUD_ARROW_GAP_DP = 6f

    /** HUD sag grubunda "BASLAT" butonunun sag kenardan yaklasik uzakligi. */
    const val HUD_START_WAVE_INSET_DP = 150f

    /** HUD sol grubunda Tedarik rozetinin sol kenardan yaklasik uzakligi. */
    const val HUD_SUPPLY_INSET_DP = 165f

    /** Pad vurgu halkasinin yaricapi. */
    const val PAD_RING_DP = 40f

    /** Ok boyu. */
    const val ARROW_LENGTH_DP = 18f

    /**
     * Bu savasta ogretici kosar mi? Savas basina **bir kez** sorulur
     * (`GameScreen` bunu `battleEpoch` ile hatirlar).
     */
    fun shouldStart(levelId: Int, alreadySeen: Boolean): Boolean =
        levelId == TUTORIAL_LEVEL_ID && !alreadySeen

    /**
     * Ogretici katmani su anda CIZILEBILIR mi?
     *
     * Modal cikan her durumda (duraklatma, zafer, yenilgi, bolum secim)
     * `false`: ogretici serit hicbir zaman bir modalin ustune binmez.
     */
    fun isOverlayVisible(gameState: GameState): Boolean =
        gameState == GameState.PREPARATION || gameState == GameState.WAVE_RUNNING

    /** Savasin basindaki durum. */
    fun start(supply: Int): TutorialState =
        TutorialState(step = FIRST_STEP, supplyAtStepEntry = supply)

    /** "GEC" butonu. Adim ne olursa olsun ogretici KALICI olarak kapanir. */
    fun skip(state: TutorialState): TutorialState =
        if (state.running) state.copy(outcome = TutorialOutcome.SKIPPED) else state

    /**
     * Bir adim TAMAMLANDI mi?
     *
     * Her kosul "ileri atlamaya" acik yazildi: oyuncu adimlari sirasiz
     * yaparsa (ornegin hic kule kurmadan dalgayi baslatirsa) aradaki adimlar
     * ayni karede dusler ve ogretici oyuncunun GERCEKTEN bulundugu yere
     * hizalanir. Bu, "oyuncuyu kilitleme" kuralinin mantik tarafidir.
     */
    fun isSatisfied(step: TutorialStep, state: TutorialState, signals: TutorialSignals): Boolean {
        val waveRunning = signals.gameState == GameState.WAVE_RUNNING
        return when (step) {
            // Isaretli pad SART DEGIL: herhangi bir pad secimi adimi bitirir.
            TutorialStep.SELECT_PAD ->
                signals.padSelected || signals.towerCount > 0 || waveRunning

            TutorialStep.BUILD_TOWER ->
                signals.towerCount > 0 || waveRunning

            // Tek "zaman ile biten" adim. Dalga baslarsa hemen duser.
            TutorialStep.SHOW_RANGE ->
                state.elapsedSeconds >= RANGE_STEP_SECONDS || waveRunning

            // Dalga NASIL basladigi onemli degil: buton da 10 sn'lik hazirlik
            // sayacinin dolmasi da ayni sonucu verir.
            TutorialStep.START_WAVE -> waveRunning

            TutorialStep.SUPPLY_GROWS ->
                signals.supply > state.supplyAtStepEntry ||
                    state.elapsedSeconds >= SUPPLY_STEP_TIMEOUT_SECONDS
        }
    }

    /**
     * Bir kare ilerlet.
     *
     * @param deltaSeconds Kare suresi. Negatif deger yok sayilir.
     */
    fun update(
        state: TutorialState,
        signals: TutorialSignals,
        deltaSeconds: Float
    ): TutorialState {
        if (!state.running) return state

        when (signals.gameState) {
            // Bolum kazanildi: oyuncu dongunun tamamini gordu.
            GameState.VICTORY -> return state.copy(outcome = TutorialOutcome.COMPLETED)

            // Yarida kalan savas KALICI yazilmaz; tekrar denemede geri gelir.
            GameState.DEFEAT,
            GameState.MAIN_MENU,
            GameState.LEVEL_SELECT -> return state.copy(outcome = TutorialOutcome.ABORTED)

            // Duraklatmada sayac DONAR. Aksi halde duraklatma menusunun
            // arkasinda SHOW_RANGE suresi dolar ve oyuncu adimi hic gormez.
            GameState.PAUSED -> return state

            else -> Unit
        }

        var next = state.copy(
            elapsedSeconds = state.elapsedSeconds + deltaSeconds.coerceAtLeast(0f)
        )

        // Birden fazla adim ayni karede tatmin olabilir (bkz. [isSatisfied]).
        // Dongu adim sayisiyla sinirli: kosullardan biri bir gun kendisiyle
        // celisse bile burasi SONSUZ DONMEZ.
        var guard = 0
        while (guard++ <= STEPS.size && isSatisfied(next.step, next, signals)) {
            val following = STEPS.getOrNull(STEPS.indexOf(next.step) + 1)
                ?: return next.copy(outcome = TutorialOutcome.COMPLETED)
            next = next.copy(
                step = following,
                elapsedSeconds = 0f,
                supplyAtStepEntry = signals.supply
            )
        }
        return next
    }

    // =================================================================================
    // Yerlesim geometrisi — saf, bu yuzden test edilebilir
    // =================================================================================

    /**
     * `TowerBuildBar` icindeki ILK kartin (Gatling) yatay merkezi.
     *
     * Cubuk 4 esit agirlikli kart + 36 dp kapatma butonu, aralarinda 8 dp
     * bosluk, Row'un iki yaninda 12 dp padding. Yani 5 cocuk -> 4 bosluk.
     */
    fun firstBuildCardCenterX(widthPx: Float, density: Float): Float {
        if (widthPx <= 0f || density <= 0f) return 0f
        val padding = BUILD_BAR_H_PADDING_DP * density
        val gaps = BUILD_CARD_GAP_DP * density * BUILD_CARD_COUNT
        val close = BUILD_CLOSE_BUTTON_DP * density
        val cardWidth =
            ((widthPx - 2f * padding - gaps - close) / BUILD_CARD_COUNT).coerceAtLeast(1f)
        return (padding + cardWidth / 2f).coerceIn(0f, widthPx)
    }

    /** `TowerBuildBar` cekmecesinin UST kenari; asagi bakan ok buraya dayanir. */
    fun buildBarTopY(heightPx: Float, density: Float): Float =
        (heightPx - BUILD_BAR_HEIGHT_DP * density).coerceIn(0f, heightPx)

    /**
     * HUD sagindaki "BASLAT" butonunun yaklasik merkezi.
     *
     * Sagdan sola dizilim: 16 dp padding · duraklat 36 dp · 10 dp · hiz 52 dp ·
     * 10 dp · BASLAT. Toplam ~150 dp. Dar ekranda ekranin disina cikmamasi
     * icin sag yariya kirpilir.
     */
    fun startWaveAnchorX(widthPx: Float, density: Float): Float {
        if (widthPx <= 0f) return 0f
        val lower = widthPx * 0.55f
        val upper = (widthPx - 24f * density).coerceAtLeast(lower)
        return (widthPx - HUD_START_WAVE_INSET_DP * density).coerceIn(lower, upper)
    }

    /**
     * HUD solundaki Tedarik rozetinin yaklasik merkezi.
     *
     * Soldan saga: 16 dp padding · dalga rozeti ~100 dp · 10 dp · Tedarik
     * rozeti. Genis ekranda bile sol yaride kalir.
     */
    fun supplyAnchorX(widthPx: Float, density: Float): Float {
        if (widthPx <= 0f) return 0f
        val lower = 24f * density
        val upper = (widthPx * 0.45f).coerceAtLeast(lower)
        return (HUD_SUPPLY_INSET_DP * density).coerceIn(lower, upper)
    }
}

// =====================================================================================
// Cizim modeli
// =====================================================================================

/** Vurgunun turu. Renk TEK ayrim kanali degil: her tur farkli SEKIL cizer. */
enum class TutorialCue {
    /** Dolu cift halka + disa acilan dalgacik. Dokunulacak hedef. */
    RING,

    /** Kesikli halka. Gercek menzil yaricapi; motorun yesil halkasindan ayrilir. */
    DASHED_RING,

    /** Yukari bakan ok — hedef HUD seridinde. */
    ARROW_UP,

    /** Asagi bakan ok — hedef alttaki insa cekmecesinde. */
    ARROW_DOWN
}

/** Tek bir vurgunun ekran uzerindeki yeri. */
data class TutorialAnchor(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val cue: TutorialCue
)

// =====================================================================================
// Compose katmani
// =====================================================================================

/**
 * ---------------------------------------------------------------------------
 * ILK OTURUM OGRETICISI — savas ekrani katmani
 * ---------------------------------------------------------------------------
 * `GameScreen` icinde savas alaninin USTUNDE, modallarin ALTINDA cizilir.
 *
 * TASARIM KURALLARI
 * -----------------
 * 1. **Girdi YUTMAZ.** Cizim yuzeyi `pointerInput` TASIMAZ, yani dokunuslar
 *    altindaki `GameCanvas`'a gecer. Tiklanabilir tek dugum "GEC" cipidir.
 *    Yanlis yere dokunmak oyunu bozmaz, ogreticiyi de bozmaz.
 *
 * 2. **Oyunu DURDURMAZ.** Hazirlik sayaci akar; oyuncu dilerse dalgayi
 *    ogreticinin 1. adimindayken bile baslatabilir (bkz. [TutorialFlow]).
 *
 * 3. **Karartma YOK.** Scrim hem oynanisi karartir hem de "kilitlendin"
 *    mesaji verir. Yerine: nabiz atan altin halka, dalgacik ve ok. Her
 *    halkanin altinda koyu bir taban halkasi var — acik zeminde de DEGER
 *    kontrasti kalir, ayrim tek basina renge birakilmaz.
 *
 * 4. **Serit HUD'un ALTINDA.** Alt %25 bas parmak seridi ve orada acilan
 *    cekmeceler (`TowerBuildBar` / `SelectedTowerInspector` / `BoosterRail`)
 *    kritik metin icin kullanilamaz; parmak da orayi orter. Serit
 *    olculen HUD yuksekliginin hemen altina capalanir.
 *
 * 5. **"GEC" her adimda AYNI yerde** (seridin sag ucu), en az 48x48 dp
 *    dokunma hedefi. Alt seride konamazdi: adim 1 ve 2'de orayi insa
 *    cekmecesi kapatiyor. Yatay oyunda sag ust bolge bas parmak yayina
 *    yakindir ve "gec" zorunlu degil, IKINCIL bir eylemdir.
 *
 * 6. **Tek satir metin.** Uzun blok okunmuyor; ustelik 5 dile cevrilecek.
 *    Serit `AutoShrinkText` kullanir: Turkce karsilik uzun geldiginde metin
 *    KIRPILMAZ, punto kucultur.
 *
 * 7. **Sakin animasyon.** Flas/strobe yok; nabiz 1,2 sn'lik sinus, kesikli
 *    halka yavas doner. Sarsinti uretmez.
 *
 * @param hudInsetPx `GameScreen`'in olctugu HUD yuksekligi. Hem serit hem de
 *   yukari bakan oklar buna capalanir.
 */
@Composable
fun TutorialOverlay(
    gameEngine: GameEngine,
    saveManager: SaveManager,
    hudInsetPx: Float,
    modifier: Modifier = Modifier
) {
    val battleEpoch by gameEngine.battleEpoch.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()

    // Savas basina TEK karar. `battleEpoch` `startNewGame()`in EN SONUNDA
    // artiyor, yani bu okuma her zaman kurulumu bitmis bir savasi gorur.
    val armed = remember(battleEpoch) {
        TutorialFlow.shouldStart(
            levelId = gameEngine.levelSpec.levelId,
            alreadySeen = saveManager.tutorialSeen
        )
    }

    // Durum makinesi Compose state'i DEGIL: `elapsedSeconds` her karede
    // degisiyor ve state olsaydi butun katman saniyede 60 kez recompose
    // olurdu. Disariya yalnizca ADIM yayinlaniyor (asagida).
    val machine = remember(battleEpoch) {
        TutorialMachine(TutorialFlow.start(gameEngine.gold.value))
    }
    var visibleStep by remember(battleEpoch) {
        mutableStateOf(if (armed) TutorialFlow.FIRST_STEP else null)
    }

    LaunchedEffect(battleEpoch, armed) {
        if (!armed) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && machine.state.running) {
            withFrameNanos { now ->
                val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = now
                machine.state = TutorialFlow.update(machine.state, gameEngine.signals(), dt)
                val step = if (machine.state.running) machine.state.step else null
                // Yalnizca DEGISINCE yaz: recomposition adim basina bir kez.
                if (step != visibleStep) visibleStep = step
            }
        }
        // Kalicilik TEK yerde. "GEC" de dahil butun cikislar bu dongunun
        // sonunda toplanir, yani bayrak iki farkli yoldan yazilmaz.
        when (machine.state.outcome) {
            TutorialOutcome.COMPLETED -> saveManager.markTutorialCompleted()
            TutorialOutcome.SKIPPED -> saveManager.markTutorialSkipped()
            // ABORTED / RUNNING: yazilmaz, ogretici tekrar denemede geri gelir.
            else -> Unit
        }
    }

    val step = visibleStep
    if (!armed || step == null || !TutorialFlow.isOverlayVisible(gameState)) return

    val density = LocalDensity.current
    val gatlingCost = remember {
        GameConfig.TOWER_SPECS[GameConfig.TowerType.MACHINE_GUN]?.buildCost ?: 0
    }

    // Nabiz. `pulse.value` YALNIZCA cizim lambda'sinin icinde okunuyor:
    // boylece her karede sadece CIZIM fazi gecersiz olur, katman recompose
    // olmaz (GameCanvas'taki `frameTick` deseninin aynisi).
    val transition = rememberInfiniteTransition(label = "tutorial")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tutorial_pulse"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // DIKKAT: burada `pointerInput` YOK. Compose isabet testini yalnizca
        // girdi modifier'i tasiyan dugumlere yapar, dolayisiyla dokunuslar
        // altindaki savas alanina duser. Bu satirin ihlali "oyuncuyu
        // kilitleme" kuralini sessizce bozar.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val phase = pulse.value
            val anchor = resolveAnchor(
                step = step,
                engine = gameEngine,
                densityScale = density.density,
                hudInsetPx = hudInsetPx
            ) ?: return@Canvas
            drawTutorialCue(anchor, phase, density.density)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    top = with(density) { hudInsetPx.toDp() } + 10.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // `weight(1f, fill = false)`: serit icerigi kadar yer kaplar ama
            // ARTAN alandan fazlasini ASLA alamaz -> "GEC" cipi hicbir dilde
            // ekran disina itilemez.
            TutorialCaption(
                text = captionFor(step, gatlingCost),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
            )
            TutorialSkipChip(
                onSkip = {
                    machine.state = TutorialFlow.skip(machine.state)
                    visibleStep = null
                }
            )
        }
    }
}

/**
 * Compose state'i OLMAYAN durum tasiyici.
 *
 * Amaci tek: kare basina degisen [TutorialState]'i recomposition'dan
 * ayirmak. `mutableStateOf` kullanilsaydi `elapsedSeconds` saniyede 60 kez
 * degistigi icin butun katman yeniden bestelenirdi.
 */
private class TutorialMachine(var state: TutorialState)

/** Motorun o andaki okunusu. Tek yerde toplaniyor ki testteki sinyalle ayni kalsin. */
private fun GameEngine.signals(): TutorialSignals = TutorialSignals(
    gameState = gameState.value,
    padSelected = selectedBuildSpot.value != null,
    towerCount = towers.size,
    supply = gold.value
)

@Composable
private fun captionFor(step: TutorialStep, buildCost: Int): String = when (step) {
    TutorialStep.SELECT_PAD -> stringResource(R.string.tutorial_step_select_pad)
    TutorialStep.BUILD_TOWER -> stringResource(R.string.tutorial_step_build_tower, buildCost)
    TutorialStep.SHOW_RANGE -> stringResource(R.string.tutorial_step_show_range)
    TutorialStep.START_WAVE -> stringResource(R.string.tutorial_step_start_wave)
    TutorialStep.SUPPLY_GROWS -> stringResource(R.string.tutorial_step_supply)
}

/**
 * Tek satirlik yonerge.
 *
 * TASMA: `AutoShrinkText` metni KIRPMAZ, puntoyu kucultur. Turkce karsilik
 * Ingilizceden ~%20-35 uzun; en uzun satir "Her olen dusman Tedarik verir".
 */
@Composable
private fun TutorialCaption(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SleekSurfaceHeader.copy(alpha = 0.94f))
            .border(1.dp, SleekGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("tutorial_caption")
    ) {
        AutoShrinkText(
            text = text,
            color = SleekTextAccent,
            maxFontSize = 14.sp,
            minFontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * "GEC" cipi. Her adimda AYNI yerde ve en az 48x48 dp — Material dokunma
 * hedefi alt siniri.
 */
@Composable
private fun TutorialSkipChip(onSkip: () -> Unit) {
    val description = stringResource(R.string.tutorial_skip_desc)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SleekSurfaceCard)
            .border(1.dp, SleekBorderLight, RoundedCornerShape(24.dp))
            .clickable { onSkip() }
            .padding(horizontal = 18.dp)
            .semantics { contentDescription = description }
            .testTag("tutorial_skip"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.tutorial_skip),
            color = SleekTextAccent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

/**
 * Adimi ekran uzerindeki bir noktaya baglar.
 *
 * Hedefi bulunamayan adim `null` doner ve o karede HICBIR SEY cizilmez —
 * yanlis yerde bir halka cizmektense hic cizmemek dogrudur, cunku metin
 * seridi zaten ne yapilacagini soyluyor.
 */
private fun DrawScope.resolveAnchor(
    step: TutorialStep,
    engine: GameEngine,
    densityScale: Float,
    hudInsetPx: Float
): TutorialAnchor? {
    val hudArrowY = hudInsetPx + TutorialFlow.HUD_ARROW_GAP_DP * densityScale

    // Isaretlenen pad. Id bulunamazsa (yedek haritaya dusulduyse) ilk gorunur
    // pad'e duser: ogretici sessizce bos ekrana isaret etmez.
    fun padAnchor(): TutorialAnchor? =
        (engine.scaledBuildSpots.firstOrNull { it.id == TutorialFlow.HIGHLIGHT_PAD_ID }
            ?: engine.scaledBuildSpots.firstOrNull())
            ?.let {
                TutorialAnchor(
                    x = it.normX,
                    y = it.normY,
                    radiusPx = TutorialFlow.PAD_RING_DP * densityScale,
                    cue = TutorialCue.RING
                )
            }

    return when (step) {
        TutorialStep.SELECT_PAD -> padAnchor()

        // Cekmece ACIKSA asagi bakan ok kartlari gosterir. Oyuncu bu adimda
        // pad secimini bozarsa (bos alana dokunmak cekmeceyi kapatir) ok bos
        // bir seride isaret ederdi; o durumda vurgu pad'e GERI doner. Adim
        // makinesi geri sarmaz, yalnizca VURGU oyuncunun gordugu ekrana
        // hizalanir.
        TutorialStep.BUILD_TOWER ->
            if (engine.selectedBuildSpot.value == null && engine.towers.isEmpty()) {
                padAnchor()
            } else {
                TutorialAnchor(
                    x = TutorialFlow.firstBuildCardCenterX(size.width, densityScale),
                    y = TutorialFlow.buildBarTopY(size.height, densityScale),
                    radiusPx = TutorialFlow.ARROW_LENGTH_DP * densityScale,
                    cue = TutorialCue.ARROW_DOWN
                )
            }

        TutorialStep.SHOW_RANGE -> {
            // `GameEngine.buildTower` yeni kuleyi ZATEN seciyor, yani motorun
            // yesil menzil halkasi bu adimda gorunur durumda. Buradaki kesikli
            // halka onun uzerine binmez, AYNI yaricapta farkli bir SEKIL cizer.
            engine.towers.firstOrNull()?.let { tower ->
                TutorialAnchor(
                    x = tower.posX,
                    y = tower.posY,
                    radiusPx = tower.rangePx(engine.renderScale),
                    cue = TutorialCue.DASHED_RING
                )
            }
        }

        TutorialStep.START_WAVE -> TutorialAnchor(
            x = TutorialFlow.startWaveAnchorX(size.width, densityScale),
            y = hudArrowY,
            radiusPx = TutorialFlow.ARROW_LENGTH_DP * densityScale,
            cue = TutorialCue.ARROW_UP
        )

        TutorialStep.SUPPLY_GROWS -> TutorialAnchor(
            x = TutorialFlow.supplyAnchorX(size.width, densityScale),
            y = hudArrowY,
            radiusPx = TutorialFlow.ARROW_LENGTH_DP * densityScale,
            cue = TutorialCue.ARROW_UP
        )
    }
}

/** @param phase 0..1 arasi dongusel nabiz fazi. */
private fun DrawScope.drawTutorialCue(anchor: TutorialAnchor, phase: Float, densityScale: Float) {
    val center = Offset(anchor.x, anchor.y)
    val wave = sin(phase * 2f * PI.toFloat())
    val unit = densityScale.coerceAtLeast(1f)

    when (anchor.cue) {
        TutorialCue.RING -> {
            val radius = anchor.radiusPx * (1f + 0.05f * wave)
            // Koyu taban: acik zeminde de halka kaybolmaz (deger kontrasti).
            drawCircle(
                color = Color(0x99000000),
                radius = radius,
                center = center,
                style = Stroke(width = 5f * unit)
            )
            drawCircle(
                color = SleekGold,
                radius = radius,
                center = center,
                style = Stroke(width = 3f * unit)
            )
            // Ikinci ic halka: renkten bagimsiz, SEKILLE ayirt edilir.
            drawCircle(
                color = SleekGold,
                radius = radius * 0.78f,
                center = center,
                alpha = 0.45f,
                style = Stroke(width = 1.5f * unit)
            )
            // "Buraya dokun" dalgaciklari — iki halka, yarim faz kaydirmali.
            for (index in 0..1) {
                val t = (phase + index * 0.5f) % 1f
                drawCircle(
                    color = SleekGold,
                    radius = anchor.radiusPx * (0.55f + t * 0.85f),
                    center = center,
                    alpha = (1f - t) * 0.5f,
                    style = Stroke(width = 2f * unit)
                )
            }
        }

        TutorialCue.DASHED_RING -> {
            // Kesikler faz ile kayiyor: donen bir menzil halkasi. Yanip
            // sonme YOK, sadece hareket.
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(14f * unit, 10f * unit),
                phase * 24f * unit
            )
            drawCircle(
                color = Color(0x99000000),
                radius = anchor.radiusPx,
                center = center,
                style = Stroke(width = 5f * unit, pathEffect = dash)
            )
            drawCircle(
                color = SleekGold,
                radius = anchor.radiusPx,
                center = center,
                alpha = 0.9f,
                style = Stroke(width = 3f * unit, pathEffect = dash)
            )
        }

        TutorialCue.ARROW_UP -> drawArrow(center, anchor.radiusPx, unit, pointingUp = true, wave = wave)

        TutorialCue.ARROW_DOWN -> drawArrow(center, anchor.radiusPx, unit, pointingUp = false, wave = wave)
    }
}

/**
 * Hedefe bakan ucgen ok. Ekseni boyunca hafifce salinir (isaret hareketi);
 * [wave] nabiz sinusudur.
 */
private fun DrawScope.drawArrow(
    tip: Offset,
    lengthPx: Float,
    unit: Float,
    pointingUp: Boolean,
    wave: Float
) {
    val direction = if (pointingUp) -1f else 1f
    val bob = 5f * unit * wave * direction
    val tipY = tip.y + bob
    val baseY = tipY - direction * lengthPx
    val halfWidth = lengthPx * 0.55f

    val path = Path().apply {
        moveTo(tip.x, tipY)
        lineTo(tip.x - halfWidth, baseY)
        lineTo(tip.x + halfWidth, baseY)
        close()
    }
    // Koyu kontur once: ok her zemin uzerinde okunur kalir.
    drawPath(path, color = Color(0xAA000000), style = Stroke(width = 5f * unit))
    drawPath(path, color = SleekGold, alpha = 0.95f)
}

// =====================================================================================
// =====================================================================================
// KILIT ACILMA IPUCLARI — SAF MANTIK
// =====================================================================================
// =====================================================================================
//
// NEDEN AYRI BIR SISTEM DEGIL, AYNI DOSYA
// ---------------------------------------
// Yukaridaki [TutorialFlow] ILK OTURUM ogreticisidir: bolum 1'de bir kez kosar,
// **dongusu** ogretir (mevzi -> kule -> menzil -> dalga -> ekonomi) ve bittiginde
// bir daha gorunmez. Kendi KDoc'unda yazdigi gibi "4 kule = 4 rol" dersi orada
// VERILEMEZ, cunku bolum 1'de yalnizca Gatling aciktir.
//
// Asagidaki [HintFlow] o eksigi kapatir ve ayni dosyada durur cunku ayni
// sozlesmeleri paylasir: girdi yutmayan katman, oyunu durdurmayan tek satirlik
// serit, kalici "bir kez goster" bayragi, saf/test edilebilir durum makinesi.
// Iki akis AYNI ANDA cizilemez ([HintSignals.tutorialArmed] kapisi).
//
// DERSIN ICERIGI — docs/FUN_AUDIT.md merkezi sikayeti
// ---------------------------------------------------
// "4 kule gercekten 4 ayri rol. Zirhsiz hedefte Gatling 43,8 DPS / Cannon 18,4;
//  TANKTA Gatling 6,1 / Fuze 26,6 — 7 kat fark. AMA oyuncu bunlarin hicbirini
//  ogrenemiyor."
// Yani derinlik VAR ve GORUNMEZ. Ipuclari o gorunmezligi kaldirir.
//
// UC KURAL
// --------
// 1. **Bolum NUMARASINA baglanmaz.** Kampanya 55 bolum ve L1-L22 su anda yeniden
//    sekilleniyor. Tetikleyici [GameConfig.isTowerUnlocked] ilk kez `true`
//    dondugu ANdir; bolum tablosu degisse de ipucu dogru yerde kalir.
// 2. **Sayi gosterir, sifat kullanmaz.** "Fuze zirha karsi guclu" demez;
//    "Agir Tank: Gatling 6,1 DPS - Fuze Rampasi 26,6" der. Butun sayilar
//    [HintFacts] uzerinden `TOWER_SPECS`/`ENEMY_SPECS`'ten TURETILIR — denge
//    degisirse ipucu kendiliginden guncellenir, yalan soyleyemez.
// 3. **Dalga onizleme seridiyle CELISMEZ, onu ACIKLAR.** `WavePreviewBar` zaten
//    "3 zirhli geliyor" diyor; ipucu "zirhli ne demek" sorusunu cevaplar ve
//    ayni hazirlik fazinda, onun hemen ALTINDA cikar.

/**
 * Kule/dusman sayilarini ipucu metnine ceviren TEK yer.
 *
 * Formul `GameEngine.applyDamageToEnemy` + `onProjectileImpact`'in aynasidir ve
 * `BalanceConsistencyTest.effectiveDps(cluster = 1)` ile ayni sonucu verir:
 *
 *  - Cannon (splash > 0)      -> hasar x `splashVulnerability`, ZIRHI BYPASS EDER
 *                                (DECISIONS B2)
 *  - Frost  (slowPulse > 0)   -> hasar x (1 - zirh), zirh TAM etkili
 *  - digeri                   -> hasar x (1 - zirh x (1 - `armorPierce`))
 *
 * Neden **kademe 1**: ipucu kule KILIDI acildigi anda cikiyor, yani oyuncunun
 * o an insa edebilecegi tek kademe budur. Kademe 3 sayisini gostermek, oyuncunun
 * 12. bolumden once goremeyecegi bir vaat olurdu.
 */
object HintFacts {

    /** Ipuclarinin konustugu kademe. Gerekce icin sinif KDoc'una bak. */
    const val REFERENCE_TIER = 1

    /**
     * Tek hedefe karsi surekli hasar (hasar/sn). Tanimsiz tur veya bozuk
     * (fireRate <= 0) tablo icin 0 doner — ipucu cizilmez, motor cokmez.
     */
    fun dps(
        tower: GameConfig.TowerType,
        enemy: GameConfig.EnemyType,
        tier: Int = REFERENCE_TIER
    ): Float {
        val spec = GameConfig.TOWER_SPECS[tower] ?: return 0f
        val target = GameConfig.ENEMY_SPECS[enemy] ?: return 0f
        val step = spec.tier(tier)
        if (step.fireRate <= 0f) return 0f

        val perShot = when {
            spec.splashRadius > 0f -> step.damage * target.splashVulnerability
            spec.slowPulseRadius > 0f -> step.damage * (1f - target.armor.coerceIn(0f, 1f))
            else -> {
                val armorLeft = (target.armor * (1f - spec.armorPierce)).coerceIn(0f, 1f)
                step.damage * (1f - armorLeft)
            }
        }
        return perShot / step.fireRate
    }

    /** Frost'un yavaslatma yuzdesi. Motor `hiz x (1 - slowFactor)` isletiyor. */
    fun slowPercent(tower: GameConfig.TowerType): Int =
        ((GameConfig.TOWER_SPECS[tower]?.slowFactor ?: 0f).coerceIn(0f, 0.99f) * 100f).roundToInt()

    /**
     * Yavaslatilan dusman menzilde KAC KAT uzun kalir.
     *
     * Kombonun tamami bu tek sayida: hiz `(1 - slowFactor)` katina duserse ayni
     * mesafe `1 / (1 - slowFactor)` katinda alinir, yani menzildeki HER kule o
     * kadar uzun ates eder. Frost'un "hasar vermeyen kule neden 100 Tedarik"
     * sorusunun sayisal cevabi.
     */
    fun rangeTimeMultiplier(tower: GameConfig.TowerType): Float {
        val factor = (GameConfig.TOWER_SPECS[tower]?.slowFactor ?: 0f).coerceIn(0f, 0.95f)
        return 1f / (1f - factor)
    }

    /** Zirh > 0 olan her tip. `ENEMY_SPECS`'ten TURETILIR, elle listelenmez. */
    fun isArmored(enemy: GameConfig.EnemyType): Boolean = armorOf(enemy) > 0f

    fun armorOf(enemy: GameConfig.EnemyType): Float =
        GameConfig.ENEMY_SPECS[enemy]?.armor ?: 0f

    /**
     * Kademe merdiveninin HAM hasar/sn degeri — hedeften BAGIMSIZ.
     *
     * [dps] her zaman bir dusman ister cunku zirh hesaba girer. Yukseltme dersi
     * ise "bu kule ne kadar buyuyor" sorusunu sorar; oraya bir dusman adi
     * karistirmak iki degiskeni ayni cumlede oynatirdi ve ustelik ipucunun
     * o bolumde CIKMAYAN bir dusmandan bahsetme riskini geri getirirdi
     * (bkz. HintTeachesPresentEnemyTest). `TowerTier.dps` ile ayni formul.
     */
    fun rawDps(tower: GameConfig.TowerType, tier: Int): Float {
        val spec = GameConfig.TOWER_SPECS[tower] ?: return 0f
        val step = spec.tier(tier)
        return if (step.fireRate <= 0f) 0f else step.damage / step.fireRate
    }

    /**
     * Dusmanin tam cani. Hedefleme dersi bunu gosterir: "EN GUCLU" modunun ne
     * anlama geldigi ancak iki can yan yana konunca okunur.
     */
    fun maxHp(enemy: GameConfig.EnemyType): Int =
        (GameConfig.ENEMY_SPECS[enemy]?.maxHp ?: 0f).roundToInt()
}

/**
 * Tek seferlik baglamsal ipuclari.
 *
 * SIRA = ONCELIK. Ayni hazirlik fazinda birden fazla ipucu uygun olursa
 * [HintFlow] bu listedeki ILKINI gosterir, digerleri SONRAKI hazirlik fazlarina
 * kayar (bkz. [HintState.servedWaveIndex]). Siralama geri bildirim
 * hiyerarsisidir: **kritik uyari > ilerleme**.
 *
 * 1. [ARMOR_INTRO] — su an sahaya cikan bir TEHDIT. Oyuncunun kulesi az sonra
 *    ise yaramayacak; en once bunu bilmeli.
 * 2. [MISSILE_ROLE] — denetimin en buyuk sayisal farki (tankta 6,1'e karsi 26,6).
 * 3. [CANNON_ROLE] — zirhin ikinci cevabi; patlama zirhi BYPASS eder.
 * 4. [FROST_ROLE] — hasar vermeyen destek; en az acil olan, en son.
 * 5. [UPGRADE_INTRO] — "insa mi, yukseltme mi" ekonominin ikinci karari.
 * 6. [TARGETING_INTRO] — kule zaten atis yapiyor; SIRAYI degistirmek ince ayar.
 * 7. [BOOSTER_INTRO] — savas ici tuketilebilir; oynanisin zorunlu parcasi degil.
 * 8. [SELL_INTRO] — en son, cunku en yikici eylem. Bkz. kendi KDoc'u.
 *
 * ---------------------------------------------------------------------------
 * 2026-08-20 — EKSIK DORT MEKANIK (docs/FUN_AUDIT_2.md (a))
 * ---------------------------------------------------------------------------
 * Denetim: satma, hedefleme modu, guclendiriciler ve yukseltme HICBIR YERDE
 * ogretilmiyordu. `WaveDefinitions.kt` icinde "L6'da hedefleme modlari
 * tanitilir" yazan bir yorum vardi ama tanitan bir UI yoktu — olu yorum.
 *
 * Dordu de ayni sozlesmeyle buraya eklendi ve **hicbiri bolum numarasina
 * baglanmadi**: tetikleyici oyuncunun sahada ULASTIGI durumdur (yukseltme
 * odenebilir hale geldi, ray'de guclendirici belirdi, dalga karisik geldi,
 * insa edecek yer/tedarik kalmadi). Kampanya yeniden sekillenirse ders
 * kendiliginden dogru yere tasinir.
 *
 * Dordu de oncelik listesinin ALTINA kondu: bir kule kilidinin acilmasi
 * zamana duyarli bir HABERDIR (oyuncu o kuleyi hemen kurmak ister), yonetim
 * dersleri ise her hazirlik fazinda ayni derecede gecerlidir. Cakismada haber
 * once verilir, ders bir sonraki fazda cikar.
 *
 * @param saveId `SaveManager`'daki kalici anahtarin govdesi. **ASLA
 *   degistirilmez**: degisirse ipucu eski kayitlarda yeniden gorunur. Enum
 *   adindan turetilmedi tam da bu yuzden — enum adi bir refactor'da degisebilir,
 *   kalici anahtar degisemez.
 */
enum class UnlockHint(val saveId: String) {

    /** Ilk zirhli dusman geliyor: "kursun zirha islemez". */
    ARMOR_INTRO("armor_intro"),

    /** Fuze acildi: ZIRH DELER. Denetimin en kritik dersi. */
    MISSILE_ROLE("missile_role"),

    /** Cannon acildi: alan hasari zirhi BYPASS eder (DECISIONS B2). */
    CANNON_ROLE("cannon_role"),

    /** Frost acildi: hasar VERMEZ, yavaslatir. */
    FROST_ROLE("frost_role"),

    /**
     * **YUKSELTME.** Tetikleyici: sahadaki bir kulenin bir sonraki kademesi
     * ACIK ve o an ODENEBILIR ([HintFlow.upgradeCandidate]).
     *
     * Neden "odenebilir" kosulu var: yukseltme paneli yalnizca kuleye
     * dokununca acilir, yani ders ancak oyuncu O ANDA uygulayabiliyorsa
     * yapiskan olur. Odeyemeyecegi bir rakami gostermek, `BoosterRail`'in
     * "karsilanamayan secenegi sebebiyle goster" kuralinin aksine, burada
     * yalnizca gurultu uretir: ipucu bir KEZ cikar ve kaybolur, buton gibi
     * ekranda kalmaz.
     */
    UPGRADE_INTRO("upgrade_intro"),

    /**
     * **HEDEFLEME MODU.** Tetikleyici: sahada en az bir kule VAR ve siradaki
     * dalga en az IKI farkli dusman tipi getiriyor ([HintSignals.incomingEnemyTypes]).
     *
     * Tek tipli bir dalgada "kule once kime atsin" diye bir soru YOKTUR; ders
     * o zaman bir cevabi olmayan soruyu sorar. Karisik dalga ise sorunun
     * kendisidir ve `WavePreviewBar` onu ayni hazirlik fazinda zaten
     * gosteriyor: serit "ne geliyor", ipucu "sen ne yapabilirsin" der.
     */
    TARGETING_INTRO("targeting_intro"),

    /**
     * **GUCLENDIRICILER.** Tetikleyici: `boostersAvailableAt(levelId)` bos
     * degil, yani `BoosterRail` GERCEKTEN en az bir buton ciziyor
     * ([BoosterRail] ile AYNI fonksiyon okunur, ayri bir kural yazilmaz).
     *
     * Denetimin sikayeti tam olarak buydu: uc guclendirici var, biri reklamla
     * besleniyor ve ray'e hicbir sey isaret etmiyor. Bu yuzden bu ipucu
     * seridin yaninda ray'in ALT-SAG kosesine bir halka da cizer.
     */
    BOOSTER_INTRO("booster_intro"),

    /**
     * **SATMA.** Tetikleyici: sahada EN AZ IKI kule var VE su anda hicbir kule
     * insa edilemiyor ([HintSignals.buildBlocked]) — yer, tavan ya da Tedarik
     * bitmis.
     *
     * Iki kosul da zorunlu:
     * · **Insa tikandi** olmadan satis bir cozum degil, sadece bir kayiptir
     *   (`salvageRate` her zaman 1,0'in altinda). Motorun kendi yorumu da
     *   ayni: "MEVZI TAVANI — satis yer acar".
     * · **En az iki kule** olmadan ders ZARARLIDIR: tek kulesi olan yeni
     *   oyuncuya "satabilirsin" demek, savunmasini bosaltmasina davettir.
     *
     * Oncelik listesinin en altinda olmasinin sebebi de budur: geri alinmasi
     * en pahali eylemi en son ogretiyoruz.
     */
    SELL_INTRO("sell_intro");

    /**
     * Bu ipucunu acan kule; **kule kilidine bagli OLMAYAN** ipuclari icin
     * `null`. Tetikleyici bolum NUMARASI degil, bu kulenin kilidinin
     * acilmasidir.
     *
     * `null` donen her ipucunun tetikleyicisi [HintFlow.isTriggered] icinde
     * ACIKCA yazilidir; bu alan yalnizca "kilitli her kulenin bir rol dersi
     * var mi" sorusunu cevaplar (UnlockHintFlowTest).
     */
    val unlockTower: GameConfig.TowerType?
        get() = when (this) {
            MISSILE_ROLE -> GameConfig.TowerType.ANTI_ARMOR
            CANNON_ROLE -> GameConfig.TowerType.CANNON
            FROST_ROLE -> GameConfig.TowerType.SLOW
            ARMOR_INTRO,
            UPGRADE_INTRO,
            TARGETING_INTRO,
            BOOSTER_INTRO,
            SELL_INTRO -> null
        }
}

/**
 * Ipucu motorunun okudugu HER SEY. Compose ve Android'e bagimsiz oldugu icin
 * [HintFlow] duz JVM testinde kosar (Robolectric YOK).
 *
 * @param tutorialArmed ilk oturum ogreticisi bu savasta kosuyor mu. `true` ise
 *   HICBIR ipucu cizilmez: ayni anda iki ogretici serit ust uste binerdi ve
 *   "ayni anda en fazla bir mesaj" kurali kirilirdi.
 * @param levelEnemyTypes bu BOLUMUN butun dalgalarindaki dusman tipleri. Ipucu
 *   ornek dusmanini buradan secer; oyuncunun HIC gormedigi bir dusmanla ders
 *   vermek ogretmez, kafa karistirir.
 * @param incomingArmoredTypes SIRADAKI dalgadaki zirhli tipler. [UnlockHint.ARMOR_INTRO]
 *   tetikleyicisi ve ornek dusmani. `WavePreviewBar` ayni dalgayi gosterdigi
 *   icin serit ile ipucu ayni seyden bahseder.
 * @param incomingEnemyTypes SIRADAKI dalgadaki TUM tipler. [UnlockHint.TARGETING_INTRO]
 *   tetikleyicisi: "kule once kime atsin" sorusu ancak birden fazla tip
 *   geliyorsa vardir.
 * @param fieldTowers su anda sahada duran kulelerin ozeti. Yukseltme ve satis
 *   dersleri yalnizca oyuncunun GERCEKTEN sahip oldugu bir kuleden bahsedebilir;
 *   varsayimsal bir kule uzerinden ders vermek, dusman tarafinda tam bir gun
 *   once duzeltilen hatanin ekonomi tarafindaki esidi olurdu.
 * @param supply o andaki Tedarik. Yukseltme dersinin "odenebilir mi" kosulu.
 * @param buildBlocked su anda HICBIR kule kurulamiyor mu (bos mevzi yok, mevzi
 *   tavani doldu ya da Tedarik yetmiyor). [UnlockHint.SELL_INTRO] tetikleyicisi.
 */
data class HintSignals(
    val gameState: GameState,
    val levelId: Int,
    val waveIndex: Int,
    val tutorialArmed: Boolean,
    val levelEnemyTypes: Set<GameConfig.EnemyType> = emptySet(),
    val incomingArmoredTypes: Set<GameConfig.EnemyType> = emptySet(),
    val incomingEnemyTypes: Set<GameConfig.EnemyType> = emptySet(),
    val fieldTowers: List<HintTowerSnapshot> = emptyList(),
    val supply: Int = 0,
    val buildBlocked: Boolean = false
)

/**
 * Sahadaki tek bir kulenin ipucuna giren OZETI.
 *
 * Neden `TowerEntity` degil de ozet: [HintFlow] saf kalmali ve testte
 * Compose/motor kurmadan kosmali. Ozet, panelin (`SelectedTowerInspector`)
 * gosterdigi ayni turetilmis degerleri tasir — yani ipucu ile panel AYNI
 * sayiyi soyler. `TowerEntity.upgradeCost` / `sellValue` getter'lari tek
 * kaynaktir; burada hicbir sey yeniden hesaplanmaz.
 *
 * @param towerId sahadaki kuleyi geri bulmak icin. Vurgu halkasi kulenin O
 *   ANKI konumuna cizilecek ve konum her karede degisebilir (cihaz donusu,
 *   yeniden olcekleme), bu yuzden koordinat DEGIL kimlik tasiniyor.
 * @param tier kulenin su anki kademesi (1 tabanli).
 * @param upgradeCost bir sonraki kademenin bedeli; son kademede `null`.
 * @param sellValue satista geri gelen Tedarik.
 * @param salvagePercent geri donus yuzdesi (meta yukseltmeyle 70..90 arasi).
 */
data class HintTowerSnapshot(
    val towerId: String,
    val type: GameConfig.TowerType,
    val tier: Int,
    val upgradeCost: Int?,
    val sellValue: Int,
    val salvagePercent: Int
)

/**
 * Ipucu motorunun durumu.
 *
 * @param seen KALICI olarak gosterilmis ipuclari. Baslangicta `SaveManager`'dan
 *   okunur, buyudugunde geri yazilir. Yalnizca BUYUR.
 * @param shownSeconds aktif ipucunun ekranda kaldigi sure.
 * @param servedWaveIndex bu savasta en son ipucu gosterilen dalga. Ayni hazirlik
 *   fazinda IKINCI bir ipucu cikmasini engeller: iki ders arka arkaya okunmaz
 *   ve ikisi de kaybolur.
 */
data class HintState(
    val seen: Set<UnlockHint> = emptySet(),
    val active: UnlockHint? = null,
    val shownSeconds: Float = 0f,
    val servedWaveIndex: Int? = null
)

/**
 * ---------------------------------------------------------------------------
 * KILIT ACILMA IPUCLARI — durum makinesi
 * ---------------------------------------------------------------------------
 * [TutorialFlow] ile ayni sozlesme: saf, Compose'suz, kare kare ilerleyen.
 *
 * ZAMANLAMA KARARLARI
 * -------------------
 * - **Yalnizca HAZIRLIK fazinda.** Savas gercek zamanli akiyor; dalga sirasinda
 *   metin okumak oynanistan ceker ve okunmaz. Hazirlik fazi zaten bir KARAR
 *   anidir (dalga onizleme seridi de oradadir) — ders icin dogal an.
 * - **Duraklatmada DONAR.** Duraklatma menusunun arkasinda [VISIBLE_SECONDS]
 *   dolarsa oyuncu ipucunu hic gormeden kaybederdi.
 * - **[MIN_READ_SECONDS] okuma esigi.** Ipucu ekranda bu sureden AZ kaldiysa
 *   kalici yazilmaz. Oyuncu seridi gordugu an "BASLAT"a basarsa ders harcanmis
 *   sayilmaz; sonraki hazirlik fazinda geri gelir.
 * - **Kapatmak esigi ATLAR.** Acikca kapatan oyuncu okudugunu bildirmistir;
 *   o ipucu bir daha gorunmez.
 *
 * OYUNCUYU HICBIR SEKILDE ENGELLEMEZ: serit girdi yutmaz, oyunu durdurmaz,
 * hazirlik sayacini uzatmaz ve dalga baslayinca kendiliginden kaybolur.
 */
object HintFlow {

    /** Serit ekranda en fazla bu kadar kalir. Hazirlik fazi 10 sn. */
    const val VISIBLE_SECONDS = 7f

    /** Kalici "gosterildi" yazmak icin gereken en az ekranda kalma suresi. */
    const val MIN_READ_SECONDS = 1.5f

    /** Oncelik sirasi = enum sirasi. Gerekce [UnlockHint] KDoc'unda. */
    val PRIORITY: List<UnlockHint> = UnlockHint.values().toList()

    // ---------------------------------------------------------------------
    // Ornek dusman adaylari.
    //
    // Sira OGRETME DEGERINE gore: listenin ilk uygun (bu bolumde GERCEKTEN
    // ciken) elemani secilir, hicbiri yoksa listenin ilkine dusulur. Boylece
    // kampanya tablosu degisse bile ipucu oyuncunun gordugu bir dusmandan
    // bahseder.
    // ---------------------------------------------------------------------

    /** Fuze: en agir hedef once. Tankta fark 4 kattan buyuk. */
    private val MISSILE_TARGETS = listOf(
        GameConfig.EnemyType.TANK,
        GameConfig.EnemyType.COMMAND_TANK,
        GameConfig.EnemyType.ARMORED_VEHICLE,
        GameConfig.EnemyType.SHIELDED_TROOPER
    )

    /**
     * Cannon: KALKANLI ER once. Tasarim geregi `splashVulnerability = 1.6` ile
     * Cannon'un ozel hedefidir (DECISIONS B1/B2) ve Cannon'in acildigi bolum
     * civarinda sahada olma ihtimali tanktan yuksektir.
     */
    private val CANNON_TARGETS = listOf(
        GameConfig.EnemyType.SHIELDED_TROOPER,
        GameConfig.EnemyType.ARMORED_VEHICLE,
        GameConfig.EnemyType.TANK,
        GameConfig.EnemyType.COMMAND_TANK
    )

    /** Zirh dersinin "once boyleydi" tarafi: zirhsiz taban hedef. */
    private val SOFT_TARGETS = listOf(
        GameConfig.EnemyType.INFANTRY,
        GameConfig.EnemyType.FAST_SOLDIER
    )

    /** Savasin basindaki durum. [seen] kalici kayittan gelir. */
    fun start(seen: Set<UnlockHint>): HintState = HintState(seen = seen)

    /** Kalici bayraklarin okunmasi. Tek yerde, testle ayni yol. */
    fun seenFrom(saveManager: SaveManager): Set<UnlockHint> =
        PRIORITY.filterTo(LinkedHashSet()) { saveManager.isHintSeen(it.saveId) }

    /**
     * Gosterilecek ipucu KALMADI mi? `true` ise [UnlockHintStrip] kare dongusunu
     * tamamen birakir — ipuclarini bitirmis oyuncu (yani bir sure sonra HERKES)
     * icin kare basina sifir is.
     */
    fun isExhausted(state: HintState): Boolean =
        state.active == null && state.seen.containsAll(PRIORITY)

    /**
     * Serit su anda CIZILEBILIR mi? Yalnizca hazirlik fazi: modal cikan hicbir
     * durumda (duraklatma, zafer, yenilgi) ve dalga akarken cizilmez.
     */
    fun isOverlayVisible(gameState: GameState): Boolean =
        gameState == GameState.PREPARATION

    /**
     * Bu ipucunun anlatacagi ornek dusman BU BOLUMDE sahaya cikiyor mu?
     *
     * Kule acilmasi tek basina yeterli bir kosul DEGIL: kule L3'te acilabilir
     * ama dersin ornegi L5'e kadar gelmeyebilir. Bu ayrimin olmamasi,
     * oyuncunun gordugu ilk ipucunun hic karsilasmadigi bir birimi anlatmasina
     * yol aciyordu (bkz. [pick]).
     */
    private fun hasTeachableExample(hint: UnlockHint, signals: HintSignals): Boolean =
        when (hint) {
            UnlockHint.MISSILE_ROLE -> MISSILE_TARGETS.any { it in signals.levelEnemyTypes }
            UnlockHint.CANNON_ROLE -> CANNON_TARGETS.any { it in signals.levelEnemyTypes }
            // Digerleri ornegini zirhsiz tabandan alir (piyade her bolumde var)
            // ya da zaten sahadaki zirhliyi kosul kosar.
            else -> true
        }

    /**
     * Bu ipucunun KOSULU sagladi mi? **Hicbir dalda bolum numarasi yok** —
     * her dal bir OLAYI sorar.
     *
     * Kule dersleri icin iki kosul birlikte: kule ACILMIS olacak VE dersin
     * ornegi bu bolumde gercekten cikacak. Ikincisi olmadan ipucu "gorulduye"
     * yazilir ve dogru an geldiginde bir daha hic cikmazdi.
     *
     * Yonetim dersleri (yukseltme / hedefleme / guclendirici / satis) ayni
     * mantigi ekonomi tarafinda kurar: her biri, ilgili mekanigin O ANDA
     * GERCEKTEN kullanilabilir oldugu duruma bakar. Gerekceler [UnlockHint]
     * icindeki tek tek KDoc'larda.
     */
    fun isTriggered(hint: UnlockHint, signals: HintSignals): Boolean = when (hint) {
        UnlockHint.ARMOR_INTRO -> signals.incomingArmoredTypes.isNotEmpty()

        UnlockHint.MISSILE_ROLE,
        UnlockHint.CANNON_ROLE,
        UnlockHint.FROST_ROLE -> {
            val tower = hint.unlockTower
            tower != null &&
                GameConfig.isTowerUnlocked(tower, signals.levelId) &&
                hasTeachableExample(hint, signals)
        }

        // Yukseltme O AN odenebiliyor mu (kule sahada, kademe acik, Tedarik yeter).
        UnlockHint.UPGRADE_INTRO -> upgradeCandidate(signals) != null

        // Kule var VE dalga karisik: "once kime" sorusunun bir cevabi var.
        UnlockHint.TARGETING_INTRO ->
            signals.fieldTowers.isNotEmpty() && signals.incomingEnemyTypes.size >= 2

        // Ray GERCEKTEN buton ciziyor mu.
        UnlockHint.BOOSTER_INTRO -> firstBooster(signals.levelId) != null

        // Insa tikandi VE feda edilebilecek yedek kule var.
        UnlockHint.SELL_INTRO -> signals.buildBlocked && sellCandidate(signals) != null
    }

    // ---------------------------------------------------------------------
    // Yonetim derslerinin aday secimi — saf, bu yuzden test edilebilir
    // ---------------------------------------------------------------------

    /**
     * Yukseltme dersinin konusacagi kule: **su an odenebilen EN UCUZ** adim.
     *
     * En ucuz olani secmek bilincli — ders bir EYLEM oneriyor ve oyuncunun
     * kasasini bosaltmayan oneri, hazirlik fazinin geri kalanini (yeni kule
     * kurmak) bozmaz. Esitlikte kimlige gore siralanir: ayni kare iki kez
     * cizildiginde ipucu farkli kuleyi gostermez.
     */
    fun upgradeCandidate(signals: HintSignals): HintTowerSnapshot? =
        signals.fieldTowers
            .filter { snapshot ->
                val cost = snapshot.upgradeCost
                cost != null && cost <= signals.supply
            }
            .minWithOrNull(compareBy({ it.upgradeCost ?: Int.MAX_VALUE }, { it.towerId }))

    /**
     * Satis dersinin konusacagi kule: **en az yatirim yapilmis** olan.
     *
     * Iki sebep: (1) ders bir ornek gosterir, bir emir vermez — en ucuz kuleyi
     * ornek almak en az yikici olanidir; (2) `sellValue` yatirimla dogru
     * orantili oldugu icin en dusuk yatirimli kule, oyuncunun geri alamayacagi
     * en kucuk karari temsil eder.
     *
     * `null` doner: sahada ikiden az kule varsa. Gerekce [UnlockHint.SELL_INTRO].
     */
    fun sellCandidate(signals: HintSignals): HintTowerSnapshot? {
        if (signals.fieldTowers.size < 2) return null
        return signals.fieldTowers.minWithOrNull(compareBy({ it.sellValue }, { it.towerId }))
    }

    /**
     * Bu bolumde ray'de belirEN ILK guclendirici.
     *
     * `boostersAvailableAt` `BoosterRail`'in okudugu AYNI fonksiyondur; ayri
     * bir "hangi bolumde hangisi acik" kurali yazilmadi, cunku iki yerde iki
     * kural bu depoda daha once sessiz uyusmazlik uretti. Kill switch de
     * ([BoosterType.enabled]) oradan gelir: kapali guclendirici ne ray'de
     * cizilir ne de ders konusu olur.
     *
     * Aralik disi bolum icin `null`: [boosterPrice] gecersiz bolumde
     * `require` atar ve bir ipucu motoru asla cokme sebebi olamaz.
     */
    fun firstBooster(levelId: Int): BoosterType? {
        if (levelId !in 1..EconomyConfig.CAMPAIGN_LEVELS) return null
        return boostersAvailableAt(levelId).minByOrNull { it.unlockLevel }
    }

    /**
     * Sahadaki bir kuleyi ipucu ozetine cevirir.
     *
     * Butun turetilmis degerler `TowerEntity`nin KENDI getter'larindan gelir;
     * burada tek bir formul bile tekrar edilmez. Aksi halde panel ile ipucu bir
     * gun farkli sayi soylerdi ve bu dosyanin gecmisinde (yildiz formulu,
     * kademe kilidi) tam olarak bu tur sessiz uyusmazliklar cikti.
     */
    fun snapshotOf(tower: TowerEntity): HintTowerSnapshot = HintTowerSnapshot(
        towerId = tower.id,
        type = tower.type,
        tier = tower.level,
        upgradeCost = tower.upgradeCost,
        sellValue = tower.sellValue,
        salvagePercent = (tower.salvageRate.coerceIn(0f, 1f) * 100f).roundToInt()
    )

    // ---------------------------------------------------------------------
    // `BoosterRail` yerlesim aynasi (dp)
    //
    // Bu sabitler BASKA bir composable'in olculerini yansitir ve o dosya baska
    // bir is kolunun elindedir. [TutorialFlow] yerlesim sabitlerindeki ayni
    // sozlesme burada da gecerli: konum TAHMINDIR ve ipucu hicbir yerde
    // "halkaya dokun" kosuluyla ilerlemedigi icin sapma HICBIR SEYI kilitlemez
    // — en kotu ihtimalle halka butonun birkac dp yanina duser.
    // ---------------------------------------------------------------------

    /** `BoosterRail.WIDE_BAND_DP` — genis bant esigi. */
    const val RAIL_WIDE_BAND_DP = 680f

    /** Ray butonunun kenari (genis / dar bant). */
    const val RAIL_BUTTON_WIDE_DP = 56f
    const val RAIL_BUTTON_NARROW_DP = 48f

    /** Rayin sag kenardan uzakligi (genis / dar bant). */
    const val RAIL_END_WIDE_DP = 10f
    const val RAIL_END_NARROW_DP = 8f

    /** Rayin alt kenardan uzakligi; `TowerBuildBar` cekmecesinin ustunde kalir. */
    const val RAIL_BOTTOM_DP = 72f

    /** Ray butonunun kenari — bant esigine gore. */
    private fun railButtonDp(widthPx: Float, density: Float): Float {
        if (density <= 0f) return RAIL_BUTTON_NARROW_DP
        val widthDp = widthPx / density
        return if (widthDp >= RAIL_WIDE_BAND_DP) RAIL_BUTTON_WIDE_DP else RAIL_BUTTON_NARROW_DP
    }

    /**
     * Ray'in EN ALTTAKI butonunun merkezi (x).
     *
     * `BoosterRail` butonlari `unlockLevel` AZALAN sirada dizer, yani en altta
     * duran buton ilk acilan guclendiricidir — [firstBooster] ile ayni sey.
     * Ders o butonu anlattigi icin halka da onun uzerine biner.
     */
    fun boosterRailAnchorX(widthPx: Float, density: Float): Float {
        if (widthPx <= 0f || density <= 0f) return 0f
        val button = railButtonDp(widthPx, density)
        val end = if (button == RAIL_BUTTON_WIDE_DP) RAIL_END_WIDE_DP else RAIL_END_NARROW_DP
        return (widthPx - (end + button / 2f) * density).coerceIn(0f, widthPx)
    }

    /** Ray'in EN ALTTAKI butonunun merkezi (y). */
    fun boosterRailAnchorY(widthPx: Float, heightPx: Float, density: Float): Float {
        if (heightPx <= 0f || density <= 0f) return 0f
        val button = railButtonDp(widthPx, density)
        return (heightPx - (RAIL_BOTTOM_DP + button / 2f) * density).coerceIn(0f, heightPx)
    }

    /** Ortam ipucu gostermeye musait mi? (faz + ogretici cakismasi) */
    fun isShowable(signals: HintSignals): Boolean =
        isOverlayVisible(signals.gameState) && !signals.tutorialArmed

    /** Simdi gosterilecek ipucu. Ayni hazirlik fazinda IKINCI ipucu vermez. */
    fun nextHint(state: HintState, signals: HintSignals): UnlockHint? {
        if (!isShowable(signals)) return null
        if (state.servedWaveIndex == signals.waveIndex) return null
        return PRIORITY.firstOrNull { it !in state.seen && isTriggered(it, signals) }
    }

    /**
     * Bir kare ilerlet.
     *
     * @param deltaSeconds kare suresi; negatif deger yok sayilir.
     */
    fun update(state: HintState, signals: HintSignals, deltaSeconds: Float): HintState {
        // Duraklatmada sayac DONAR (bkz. sinif KDoc'u).
        if (signals.gameState == GameState.PAUSED) return state

        val active = state.active
        if (active != null) {
            val shown = state.shownSeconds + deltaSeconds.coerceAtLeast(0f)
            val done = !isShowable(signals) || shown >= VISIBLE_SECONDS
            return if (done) {
                retire(state.copy(shownSeconds = shown))
            } else {
                state.copy(shownSeconds = shown)
            }
        }

        val next = nextHint(state, signals) ?: return state
        return state.copy(active = next, shownSeconds = 0f, servedWaveIndex = signals.waveIndex)
    }

    /**
     * Oyuncu "ANLADIM" dedi. Okuma esigine BAKILMAZ: acikca kapatilan ipucu
     * kalici olarak kapanir.
     */
    fun dismiss(state: HintState): HintState {
        val active = state.active ?: return state
        return state.copy(active = null, shownSeconds = 0f, seen = state.seen + active)
    }

    /** Suresi dolan / fazi biten ipucu. Kalici yazim [MIN_READ_SECONDS]'e bagli. */
    private fun retire(state: HintState): HintState {
        val active = state.active ?: return state
        val seen = if (state.shownSeconds >= MIN_READ_SECONDS) state.seen + active else state.seen
        return state.copy(active = null, shownSeconds = 0f, seen = seen)
    }

    // =================================================================================
    // Dalga okumasi — motora DOKUNMADAN, WavePreview'in public API'si uzerinden
    // =================================================================================

    /**
     * Bu bolumun butun dalgalarinda gecen dusman tipleri.
     *
     * `WaveDefinitions` DOGRUDAN okunmuyor (o dosya baska bir is kolunun
     * elinde); [WavePreview.forWave] zaten ayni tabloyu savunmali bicimde
     * cozuyor ve bilinmeyen bolum/indeks icin `null` donuyor.
     */
    fun levelEnemyTypes(levelId: Int, totalWaves: Int): Set<GameConfig.EnemyType> {
        if (totalWaves <= 0) return emptySet()
        val types = LinkedHashSet<GameConfig.EnemyType>()
        for (index in 0 until totalWaves) {
            WavePreview.forWave(levelId, index)?.entries?.forEach { types += it.type }
        }
        return types
    }

    /** Siradaki dalgadaki ZIRHLI tipler. `WavePreviewBar`'in rozetiyle ayni kaynak. */
    fun armoredTypesInWave(levelId: Int, waveIndex: Int): Set<GameConfig.EnemyType> {
        val entries = WavePreview.forWave(levelId, waveIndex)?.entries ?: return emptySet()
        val armored = LinkedHashSet<GameConfig.EnemyType>()
        for (entry in entries) if (entry.armored) armored += entry.type
        return armored
    }

    /**
     * Siradaki dalgadaki TUM tipler — hedefleme dersinin tetigi ve ornekleri.
     * Yine `WavePreview` uzerinden okunuyor: `WaveDefinitions` baska bir is
     * kolunun elinde ve ayni tabloyu iki yerden okumak gerekmiyor.
     */
    fun enemyTypesInWave(levelId: Int, waveIndex: Int): Set<GameConfig.EnemyType> {
        val entries = WavePreview.forWave(levelId, waveIndex)?.entries ?: return emptySet()
        return entries.mapTo(LinkedHashSet()) { it.type }
    }

    // =================================================================================
    // Metin verisi — saf, bu yuzden test edilebilir
    // =================================================================================

    /**
     * Zirhli ornek: **en kalin zirh**. Esitlikte enum sirasi (deterministik).
     * En kalin zirh en buyuk sayisal farki gosterir, ders en okunur olur.
     */
    private fun armoredSample(signals: HintSignals): GameConfig.EnemyType? {
        val pool = signals.incomingArmoredTypes.ifEmpty {
            signals.levelEnemyTypes.filterTo(LinkedHashSet()) { HintFacts.isArmored(it) }
        }
        return pool.minWithOrNull(compareBy({ -HintFacts.armorOf(it) }, { it.ordinal }))
    }

    /** Zirhsiz taban ornegi. Bolumde yoksa piyadeye dusulur (her zaman vardir). */
    private fun softSample(signals: HintSignals): GameConfig.EnemyType =
        SOFT_TARGETS.firstOrNull { it in signals.levelEnemyTypes } ?: SOFT_TARGETS.first()

    /**
     * Aday listesinden bu bolumde GERCEKTEN cikan ilk tip.
     *
     * ⛔ ESKIDEN `?: candidates.first()` ILE LISTENIN ILKINE DUSUYORDU ve bu,
     * dosyanin kendi sozlesmesini ("oyuncunun HIC gormedigi bir dusmanla ders
     * vermek ogretmez, kafa karistirir") ihlal ediyordu. Somut sonuc: L3'te Top
     * acilir, `CANNON_TARGETS`'in ilki Kalkanli Er'dir, ama L3'un butun
     * dalgalari yalnizca piyade + hizli er — Kalkanli Er ilk kez L9'da cikar.
     * Yani oyuncunun hayatindaki ILK baglamsal ipucu, alti bolum sonra
     * gorecegi bir birimi anlatiyordu.
     *
     * Artik `null` donuyor; ipucu o bolumde CIZILMEZ ama GORULDU DE
     * SAYILMAZ — [isTriggered] ornegin varligini kosul kostugu icin ayni ipucu
     * ornek sahaya ilk ciktiginda kendiliginden gelir. Yani ders kaybolmuyor,
     * ERTELENIYOR.
     */
    private fun pick(
        candidates: List<GameConfig.EnemyType>,
        signals: HintSignals
    ): GameConfig.EnemyType? =
        candidates.firstOrNull { it in signals.levelEnemyTypes }

    /**
     * Ipucunun ekranda gosterecegi TUM veri. Metnin kendisi degil: sayilar,
     * kule ve dusman TURLERI. Cevrilebilir sablon `res/values/strings_hints.xml`
     * icinde; boylece sayilarin dogrulugu Compose olmadan test edilir.
     *
     * @return veri turetilemiyorsa `null` (ornegin ortada hic zirhli yoksa).
     *   O karede serit CIZILMEZ; yarim bir ders vermektense hic vermemek dogru.
     */
    fun copyFor(hint: UnlockHint, signals: HintSignals): HintCopy? = when (hint) {
        UnlockHint.ARMOR_INTRO -> {
            val armored = armoredSample(signals)
            val soft = softSample(signals)
            val gatling = GameConfig.TowerType.MACHINE_GUN
            if (armored == null) {
                null
            } else {
                HintCopy.ArmorContrast(
                    hint = hint,
                    tower = gatling,
                    softEnemy = soft,
                    softDps = HintFacts.dps(gatling, soft),
                    armoredEnemy = armored,
                    armoredDps = HintFacts.dps(gatling, armored)
                )
            }
        }

        UnlockHint.MISSILE_ROLE ->
            matchup(hint, GameConfig.TowerType.ANTI_ARMOR, MISSILE_TARGETS, signals)

        UnlockHint.CANNON_ROLE ->
            matchup(hint, GameConfig.TowerType.CANNON, CANNON_TARGETS, signals)

        UnlockHint.FROST_ROLE -> {
            val tower = GameConfig.TowerType.SLOW
            HintCopy.SupportRole(
                hint = hint,
                tower = tower,
                // Frost'un hasari zirha TABIDIR; zirhsiz hedefe karsi olcmek
                // kulenin en IYI halini gosterir ve "yine de neredeyse sifir"
                // mesaji boylece abartisiz olur.
                dps = HintFacts.dps(tower, softSample(signals)),
                slowPercent = HintFacts.slowPercent(tower),
                rangeTimeMultiplier = HintFacts.rangeTimeMultiplier(tower)
            )
        }

        UnlockHint.UPGRADE_INTRO -> {
            val snapshot = upgradeCandidate(signals)
            val cost = snapshot?.upgradeCost
            if (snapshot == null || cost == null) {
                null
            } else {
                HintCopy.UpgradeStep(
                    hint = hint,
                    towerId = snapshot.towerId,
                    tower = snapshot.type,
                    // HAM DPS: hedefsiz, cunku yukseltme kulenin kendi
                    // buyumesidir. Zirhli/zirhsiz ayrimi ayri bir derstir
                    // ([ARMOR_INTRO]) ve iki dersi tek satira sikistirmak
                    // ikisini de okunmaz yapardi.
                    currentDps = HintFacts.rawDps(snapshot.type, snapshot.tier),
                    nextDps = HintFacts.rawDps(snapshot.type, snapshot.tier + 1),
                    cost = cost
                )
            }
        }

        UnlockHint.TARGETING_INTRO -> targetingCopy(hint, signals)

        UnlockHint.BOOSTER_INTRO -> boosterCopy(hint, signals)

        UnlockHint.SELL_INTRO -> sellCandidate(signals)?.let { snapshot ->
            HintCopy.SellRefund(
                hint = hint,
                towerId = snapshot.towerId,
                tower = snapshot.type,
                refund = snapshot.sellValue,
                salvagePercent = snapshot.salvagePercent
            )
        }
    }

    /**
     * Hedefleme dersi: siradaki dalganin **en can'li** ve **en can'sizi**.
     *
     * Ucu ucuna iki sayi gostermek yeterli — "EN GUCLU" modunun ne demek
     * oldugu ancak bu iki sayinin arasindaki mesafe gorulunce anlasilir.
     * Ornekler SIRADAKI dalgadan seciliyor, yani `WavePreviewBar`'da o anda
     * ekranda duran kartlardan; oyuncunun gormedigi bir birim adi gecemez.
     *
     * `null` doner: dalgada iki ayri tip yoksa ya da ikisi AYNI cana sahipse
     * (fark okunmaz, ders bos kalir).
     */
    private fun targetingCopy(hint: UnlockHint, signals: HintSignals): HintCopy? {
        val anchor = signals.fieldTowers.firstOrNull() ?: return null
        val sorted = signals.incomingEnemyTypes
            .sortedWith(compareBy({ -HintFacts.maxHp(it) }, { it.ordinal }))
        if (sorted.size < 2) return null
        val strongest = sorted.first()
        val weakest = sorted.last()
        if (HintFacts.maxHp(strongest) == HintFacts.maxHp(weakest)) return null
        return HintCopy.TargetingChoice(
            hint = hint,
            towerId = anchor.towerId,
            strongEnemy = strongest,
            strongHp = HintFacts.maxHp(strongest),
            weakEnemy = weakest,
            weakHp = HintFacts.maxHp(weakest)
        )
    }

    /**
     * Guclendirici dersi: ray'de beliren ILK guclendiricinin ADI, ETKISI ve
     * BEDELI.
     *
     * Uc alan da ayni kartta olmali (booster secim UX'i kurali: fiyat, etki ve
     * karsilanabilirlik ayni yerde). Etki ve bedel `EconomyConfig` ile
     * `BoosterSystem`'in saf fonksiyonlarindan TURETILIR — fiyat tablosu
     * degisirse ipucu kendiliginden guncellenir.
     */
    private fun boosterCopy(hint: UnlockHint, signals: HintSignals): HintCopy? {
        val booster = firstBooster(signals.levelId) ?: return null
        val level = signals.levelId
        val effect = when (booster) {
            BoosterType.EMERGENCY_SUPPLY ->
                HintBoosterEffect.SUPPLY to emergencySupplyAmount(level)

            BoosterType.AIR_SUPPORT ->
                HintBoosterEffect.DAMAGE to
                    (EconomyConfig.AIR_SUPPORT_DAMAGE_FRACTION * 100.0).roundToInt()

            BoosterType.BASE_REPAIR ->
                HintBoosterEffect.REPAIR to
                    (EconomyConfig.BASE_REPAIR_HEALTH_RATIO * 100.0).roundToInt()
        }
        val cost = when (booster.currency) {
            BoosterCurrency.AD_ONLY -> HintBoosterCost.AD to 0
            BoosterCurrency.SUPPLY -> HintBoosterCost.SUPPLY to boosterPrice(booster, level)
            BoosterCurrency.COIN -> HintBoosterCost.COIN to boosterPrice(booster, level)
        }
        return HintCopy.BoosterIntro(
            hint = hint,
            booster = booster,
            effect = effect.first,
            effectAmount = effect.second,
            cost = cost.first,
            price = cost.second
        )
    }

    /**
     * Ayni hedef, iki kule: eski (Gatling) ve yeni acilan.
     *
     * Ornek dusman bu bolumde cikmiyorsa `null` doner — sinifin geri kalaninda
     * oldugu gibi, "yarim bir ders vermektense hic vermemek dogru".
     */
    private fun matchup(
        hint: UnlockHint,
        newTower: GameConfig.TowerType,
        candidates: List<GameConfig.EnemyType>,
        signals: HintSignals
    ): HintCopy? {
        val enemy = pick(candidates, signals) ?: return null
        val oldTower = GameConfig.TowerType.MACHINE_GUN
        return HintCopy.TowerMatchup(
            hint = hint,
            enemy = enemy,
            oldTower = oldTower,
            oldDps = HintFacts.dps(oldTower, enemy),
            newTower = newTower,
            newDps = HintFacts.dps(newTower, enemy)
        )
    }
}

/** [HintCopy.BoosterIntro] etkisinin BIRIMI. Metin sablonunu bu secer. */
enum class HintBoosterEffect {
    /** Aninda Tedarik (Acil Tedarik). */
    SUPPLY,

    /** Ekrandaki herkese yuzde hasar (Hava Destegi). */
    DAMAGE,

    /** Us caninin yuzdesi (Us Tamiri). */
    REPAIR
}

/** [HintCopy.BoosterIntro] bedelinin BIRIMI. Metin sablonunu bu secer. */
enum class HintBoosterCost {
    /** Ucretli yol yok; rewarded reklamla alinir. */
    AD,

    /** Savas ici Tedarik. */
    SUPPLY,

    /** Kalici Coin. */
    COIN
}

/**
 * Bir ipucunun EKRANDAKI icerigi — sayilar ve turler, metin degil.
 *
 * Yedi bicim var cunku yedi farkli ders anlatiliyor ve hepsini tek bir
 * sablona sikistirmak metni uzatirdi (tek satir butcesi 68 karakter):
 *  - [ArmorContrast]   — ayni kule, IKI hedef ("kursun zirha islemez")
 *  - [TowerMatchup]    — ayni hedef, IKI kule ("bunun cevabi bu kule")
 *  - [SupportRole]     — karsilastirma yok; kulenin kendi mekanigi
 *  - [UpgradeStep]     — ayni kule, IKI kademe ("bu kadar buyuyor, bu kadara")
 *  - [TargetingChoice] — ayni dalga, IKI hedef ("once hangisi")
 *  - [BoosterIntro]    — ad + etki + bedel, tek kartta
 *  - [SellRefund]      — geri donen Tedarik ve orani
 *
 * [towerId] tasiyan bicimler, seridin yaninda SAHADAKI kuleye bir halka da
 * cizdirir: dersin gectigi panel (`SelectedTowerInspector`) yalnizca kuleye
 * dokununca acilir, yani metin tek basina "nereye" sorusunu cevaplayamaz.
 */
sealed interface HintCopy {

    val hint: UnlockHint

    /**
     * Vurgu halkasinin cizilecegi kule; halka istemeyen bicimlerde `null`.
     * Koordinat DEGIL kimlik: kule konumu her karede degisebilir.
     */
    val towerId: String? get() = null

    /** Zirh dersi: [tower] iki farkli hedefte kac DPS yapiyor. */
    data class ArmorContrast(
        override val hint: UnlockHint,
        val tower: GameConfig.TowerType,
        val softEnemy: GameConfig.EnemyType,
        val softDps: Float,
        val armoredEnemy: GameConfig.EnemyType,
        val armoredDps: Float
    ) : HintCopy

    /** Rol dersi: [enemy] hedefinde [oldTower] ve [newTower] kac DPS yapiyor. */
    data class TowerMatchup(
        override val hint: UnlockHint,
        val enemy: GameConfig.EnemyType,
        val oldTower: GameConfig.TowerType,
        val oldDps: Float,
        val newTower: GameConfig.TowerType,
        val newDps: Float
    ) : HintCopy

    /** Destek dersi: hasar yok; yavaslatma ve menzilde kalma suresi. */
    data class SupportRole(
        override val hint: UnlockHint,
        val tower: GameConfig.TowerType,
        val dps: Float,
        val slowPercent: Int,
        val rangeTimeMultiplier: Float
    ) : HintCopy

    /**
     * Yukseltme dersi: ayni kule, iki kademe.
     *
     * DPS **hedefsizdir** ([HintFacts.rawDps]): yukseltme kulenin kendi
     * buyumesidir ve araya bir dusman adi koymak hem satiri uzatir hem de
     * ikinci bir degiskeni ayni cumlede oynatir.
     */
    data class UpgradeStep(
        override val hint: UnlockHint,
        override val towerId: String,
        val tower: GameConfig.TowerType,
        val currentDps: Float,
        val nextDps: Float,
        val cost: Int
    ) : HintCopy

    /** Hedefleme dersi: siradaki dalganin en can'li ve en can'siz hedefi. */
    data class TargetingChoice(
        override val hint: UnlockHint,
        override val towerId: String,
        val strongEnemy: GameConfig.EnemyType,
        val strongHp: Int,
        val weakEnemy: GameConfig.EnemyType,
        val weakHp: Int
    ) : HintCopy

    /**
     * Guclendirici dersi: ad + etki + bedel.
     *
     * [towerId] YOK — bu dersin hedefi sahadaki bir kule degil, sag alttaki
     * `BoosterRail`. Halka oraya cizilir ([HintFlow.boosterRailAnchorX]).
     */
    data class BoosterIntro(
        override val hint: UnlockHint,
        val booster: BoosterType,
        val effect: HintBoosterEffect,
        val effectAmount: Int,
        val cost: HintBoosterCost,
        val price: Int
    ) : HintCopy

    /** Satis dersi: geri donen Tedarik ve orani. */
    data class SellRefund(
        override val hint: UnlockHint,
        override val towerId: String,
        val tower: GameConfig.TowerType,
        val refund: Int,
        val salvagePercent: Int
    ) : HintCopy
}

// =====================================================================================
// Kilit acilma ipuclari — Compose katmani
// =====================================================================================

/**
 * ---------------------------------------------------------------------------
 * KILIT ACILMA IPUCU SERIDI — savas ekrani katmani
 * ---------------------------------------------------------------------------
 * `GameScreen` icinde [TutorialOverlay] ile AYNI kusakta cizilir: savas
 * alaninin ve HUD'un USTUNDE, butun modallarin ALTINDA.
 *
 * TASARIM KURALLARI (ogreticiyle ORTAK, bilincli olarak ayni)
 * -----------------------------------------------------------
 * 1. **Girdi YUTMAZ.** Katman `pointerInput` TASIMAZ; tiklanabilir tek dugum
 *    "ANLADIM" cipidir. Serit acikken oyuncu mevzi secebilir, kule kurabilir,
 *    dalgayi baslatabilir.
 * 2. **Oyunu DURDURMAZ, MODAL DEGILDIR.** Savas gercek zamanli akiyor; hazirlik
 *    sayaci serit yuzunden uzamaz ve serit kendiliginden kaybolur
 *    ([HintFlow.VISIBLE_SECONDS]).
 * 3. **Karartma YOK.** Scrim "kilitlendin" mesaji verir.
 * 4. **Tek satir.** Uzun blok okunmuyor; ustelik 5 dile cevrilecek. Serit
 *    `AutoShrinkText` kullanir: uzun ceviri KIRPILMAZ, punto kucultur.
 * 5. **Alt %25'e girmez.** Bas parmak seridi ve orada acilan cekmeceler
 *    (`TowerBuildBar` / `SelectedTowerInspector` / `BoosterRail`) kritik metin
 *    tasiyamaz; parmak da orayi orter. Serit HUD'un HEMEN ALTINA capalanir.
 * 6. **Dalga onizlemesiyle CELISMEZ.** `WavePreviewBar` HUD satirinin
 *    ORTASINDADIR ve zirh rozetini o gosterir. Serit onun tam ALTINDA cikar,
 *    ayni hazirlik fazinda: rozet "ne geliyor", serit "bu ne demek" der.
 * 7. **"ANLADIM" her ipucunda AYNI yerde** (seridin sag ucu), en az 48x48 dp
 *    dokunma hedefi — ogreticideki "GEC" cipiyle AYNI konum, ayni olcu.
 *    Iki serit ayni anda cizilemedigi icin bu iki cip carpismaz.
 *
 * ERISILEBILIRLIK
 * ---------------
 * Ayrim tek basina RENGE birakilmaz: her ipucu kisa bir METIN rozetiyle
 * (ZIRH / YENI) baslar. Flas, sarsinti veya animasyon YOKTUR — serit sabittir,
 * "hareket azaltma" ayarindan etkilenecek bir sey uretmez.
 *
 * @param hudInsetPx `GameScreen`'in olctugu HUD yuksekligi; serit buna capalanir.
 */
@Composable
fun UnlockHintStrip(
    gameEngine: GameEngine,
    saveManager: SaveManager,
    hudInsetPx: Float,
    modifier: Modifier = Modifier
) {
    val battleEpoch by gameEngine.battleEpoch.collectAsState()
    val gameState by gameEngine.gameState.collectAsState()
    val levelId by gameEngine.currentLevelId.collectAsState()
    val waveIndex by gameEngine.currentWaveIndex.collectAsState()
    val totalWaves by gameEngine.totalWaves.collectAsState()

    // Dalga tablosu okumalari ONBELLEKLENIR: kare dongusunde HIC is yapilmaz.
    // Bolum basina bir kez / dalga basina bir kez cozulur.
    val levelEnemyTypes = remember(levelId, totalWaves) {
        HintFlow.levelEnemyTypes(levelId, totalWaves)
    }
    val incomingArmored = remember(levelId, waveIndex) {
        HintFlow.armoredTypesInWave(levelId, waveIndex)
    }
    val incomingTypes = remember(levelId, waveIndex) {
        HintFlow.enemyTypesInWave(levelId, waveIndex)
    }
    val tutorialArmed = remember(battleEpoch, levelId) {
        TutorialFlow.shouldStart(levelId, saveManager.tutorialSeen)
    }

    // Durum makinesi Compose state'i DEGIL: `shownSeconds` her karede degisiyor
    // ve state olsaydi butun katman saniyede 60 kez recompose olurdu. Disariya
    // yalnizca AKTIF IPUCU yayinlaniyor (asagida) — ipucu basina bir recompose.
    val machine = remember(battleEpoch) {
        HintMachine(HintFlow.start(HintFlow.seenFrom(saveManager)))
    }
    var visibleHint by remember(battleEpoch) { mutableStateOf<UnlockHint?>(null) }

    // Sinyaller her karede TAZE okunmali ama `LaunchedEffect` yalnizca savas
    // basina bir kez kurulmali; aksi halde dalga degisiminde dongu yeniden
    // baslar ve `shownSeconds` sifirlanirdi. `rememberUpdatedState` tam olarak
    // bu ayrimi saglar.
    // SAHA DURUMU her karede TAZE okunur. `towers` duz bir liste (StateFlow
    // degil), yani Compose'a okutulamaz; ama bu lambda kare dongusunun ICINDE
    // cagriliyor, dolayisiyla recomposition'a hic dokunmadan guncel kaliyor.
    // Liste birkac elemanli oldugu icin kare basina maliyet olculemez
    // seviyededir ve butun ipuclari gorulduyse dongu zaten hic kurulmaz
    // (`HintFlow.isExhausted`).
    val signalsProvider by rememberUpdatedState {
        HintSignals(
            gameState = gameEngine.gameState.value,
            levelId = gameEngine.currentLevelId.value,
            waveIndex = gameEngine.currentWaveIndex.value,
            tutorialArmed = tutorialArmed,
            levelEnemyTypes = levelEnemyTypes,
            incomingArmoredTypes = incomingArmored,
            incomingEnemyTypes = incomingTypes,
            fieldTowers = gameEngine.towers.map { HintFlow.snapshotOf(it) },
            supply = gameEngine.gold.value,
            buildBlocked = gameEngine.noTowerCanBeBuiltNow()
        )
    }

    LaunchedEffect(battleEpoch) {
        var lastFrameNanos = withFrameNanos { it }
        // Butun ipuclari gorulduyse dongu HIC kurulmaz / hemen biter: uzun
        // vadede oyuncularin cogunda bu katman kare basina sifir is yapar.
        while (isActive && !HintFlow.isExhausted(machine.state)) {
            withFrameNanos { now ->
                val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = now

                val before = machine.state
                val after = HintFlow.update(before, signalsProvider(), dt)
                machine.state = after
                persistNewlySeenHints(saveManager, before, after)

                // Yalnizca DEGISINCE yaz: recomposition ipucu basina bir kez.
                if (after.active != visibleHint) visibleHint = after.active
            }
        }
    }

    val hint = visibleHint ?: return
    if (!HintFlow.isOverlayVisible(gameState)) return

    // Veri turetilemezse (ornegin bolumde hic zirhli yok) HICBIR SEY cizilmez.
    // Yarim bir ders vermektense hic vermemek dogru.
    val copy = remember(hint, levelEnemyTypes, incomingArmored) {
        HintFlow.copyFor(hint, signalsProvider())
    } ?: return

    val density = LocalDensity.current

    // Halka NABZI. Yalnizca isaret gerektiren ipuclarinda donuyor; digerlerinde
    // hic okunmadigi icin cizim fazi bile gecersiz olmaz.
    val transition = rememberInfiniteTransition(label = "hint")
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hint_pulse"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // DIKKAT: burada `pointerInput` YOK. Compose isabet testini yalnizca
        // girdi modifier'i tasiyan dugumlere yapar, dolayisiyla dokunuslar
        // altindaki savas alanina duser.
        //
        // ISARET KATMANI (2026-08-20). Yonetim dersleri metinle ANLATILAMAZ,
        // cunku anlattiklari yuzey ekranda GORUNMUYOR: yukseltme/satis/
        // hedefleme yalnizca kuleye dokununca acilan `SelectedTowerInspector`
        // icindedir. Denetimin guclendirici sikayeti de birebir buydu —
        // "BoosterRail'e hicbir sey isaret etmiyor". Halka ogreticideki
        // [TutorialCue.RING] ile AYNI sekildir ve orada "dokunulacak hedef"
        // anlamina gelir; oyuncu ikinci bir gorsel dil ogrenmez.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val anchor = resolveHintAnchor(copy, gameEngine, density.density) ?: return@Canvas
            drawTutorialCue(anchor, pulse.value, density.density)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    top = with(density) { hudInsetPx.toDp() } + 10.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // `weight(1f, fill = false)`: serit icerigi kadar yer kaplar ama
            // ARTAN alandan fazlasini ASLA alamaz -> "ANLADIM" cipi hicbir
            // dilde ekran disina itilemez.
            HintCaption(
                copy = copy,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
            )
            HintDismissChip(
                onDismiss = {
                    val before = machine.state
                    val after = HintFlow.dismiss(before)
                    machine.state = after
                    persistNewlySeenHints(saveManager, before, after)
                    visibleHint = null
                }
            )
        }
    }
}

/**
 * Compose state'i OLMAYAN durum tasiyici — [TutorialMachine] ile ayni gerekce:
 * kare basina degisen [HintState]'i recomposition'dan ayirmak.
 */
private class HintMachine(var state: HintState)

/**
 * Su anda HICBIR kule kurulamiyor mu? [UnlockHint.SELL_INTRO] tetikleyicisi.
 *
 * Iki ayri tikanma birlestiriliyor cunku oyuncu icin ikisi de ayni sonuca
 * varir ("insa edemiyorum") ve ikisinin de cozumu satistir:
 *  · **Bos mevzi yok** — `buildRejectionFor` bunu BILMEZ; pad doluluğu
 *    motorun insa denemesinde degil, mevzi seciminde ele alinir.
 *  · **Motorun kendi reddi** — mevzi tavani, kadro kisiti ya da Tedarik.
 *    Kural burada TEKRAR YAZILMIYOR; motorun tek karar noktasi soruluyor.
 *
 * Yalnizca BU BOLUMDE ACIK kuleler sorulur: kilitli bir kulenin reddi
 * "insa tikandi" demek degildir, o kule zaten hicbir zaman kurulamaz.
 */
private fun GameEngine.noTowerCanBeBuiltNow(): Boolean {
    val spots = scaledBuildSpots
    if (spots.isEmpty()) return false
    val occupied = towers.mapTo(HashSet()) { it.buildSpotId }
    if (spots.none { it.id !in occupied }) return true
    val unlocked = GameConfig.unlockedTowers(currentLevelId.value)
    if (unlocked.isEmpty()) return false
    return unlocked.none { buildRejectionFor(it) == null }
}

/**
 * Ipucunun isaret ettigi ekran noktasi; isaret istemeyen ipuclarinda `null`
 * (o karede hicbir sey cizilmez, serit metni tek basina yeter).
 *
 * Kule hedefli derslerde koordinat KULENIN KENDISINDEN okunur: ozet yalnizca
 * kimlik tasir, cunku konum cihaz donusunde ve yeniden olceklemede degisir
 * (`GameEngine.updateMapDimensions` kule konumlarini yeniden yaziyor). Kule
 * bir sekilde bulunamazsa (savas bitti, kule satildi) `null` doner —
 * ogreticideki "yanlis yerde halka cizmektense hic cizme" kuralinin aynisi.
 */
private fun DrawScope.resolveHintAnchor(
    copy: HintCopy,
    engine: GameEngine,
    densityScale: Float
): TutorialAnchor? {
    if (copy is HintCopy.BoosterIntro) {
        return TutorialAnchor(
            x = HintFlow.boosterRailAnchorX(size.width, densityScale),
            y = HintFlow.boosterRailAnchorY(size.width, size.height, densityScale),
            radiusPx = TutorialFlow.PAD_RING_DP * densityScale,
            cue = TutorialCue.RING
        )
    }
    val towerId = copy.towerId ?: return null
    val tower = engine.towers.firstOrNull { it.id == towerId } ?: return null
    return TutorialAnchor(
        x = tower.posX,
        y = tower.posY,
        radiusPx = TutorialFlow.PAD_RING_DP * densityScale,
        cue = TutorialCue.RING
    )
}

/**
 * Yeni "gosterildi" bayraklarini kalici yazar.
 *
 * [HintState.seen] yalnizca BUYUDUGU icin fark almak guvenlidir ve ayni ipucu
 * iki kez yazilmaz. Yazim savasin SONUNDA degil OLDUGU ANDA yapiliyor:
 * oyuncu ipucunu okuduktan sonra bolumden cikarsa ders harcanmis sayilir,
 * aksi halde ayni serit her denemede yeniden cikardi.
 */
private fun persistNewlySeenHints(saveManager: SaveManager, before: HintState, after: HintState) {
    if (after.seen.size == before.seen.size) return
    for (hint in after.seen) {
        if (hint !in before.seen) saveManager.markHintSeen(hint.saveId)
    }
}

/**
 * Tek satirlik ipucu seridi: kisa METIN rozeti + karsilastirmali satir.
 *
 * TASMA: `AutoShrinkText` metni KIRPMAZ, puntoyu 13 sp'den 10 sp'ye kadar
 * kucultur. Butce ve turetilisi `res/values/strings_hints.xml` basliginda,
 * kilidi `UnlockHintStringsTest` icinde.
 */
@Composable
private fun HintCaption(copy: HintCopy, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SleekSurfaceHeader.copy(alpha = 0.94f))
            .border(1.dp, SleekGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("hint_caption"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rozet: ipucunun NEDEN ciktigini tek kelimeyle soyler. Renk tek ayrim
        // kanali olmasin diye ayrim METINDE, arka plan yalnizca destekler.
        Text(
            text = stringResource(hintTagRes(copy)),
            color = SleekGold,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SleekGold.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("hint_tag")
        )
        Spacer(Modifier.width(8.dp))
        AutoShrinkText(
            text = hintBody(copy),
            color = SleekTextAccent,
            maxFontSize = 13.sp,
            minFontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * "ANLADIM" cipi. Her ipucunda AYNI yerde ve en az 48x48 dp — Material dokunma
 * hedefi alt siniri. "X" degil onay kelimesi: oyuncu ipucunu reddetmiyor,
 * okudugunu bildiriyor ve o ipucu bir daha gorunmuyor.
 */
@Composable
private fun HintDismissChip(onDismiss: () -> Unit) {
    val description = stringResource(R.string.hint_dismiss_desc)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SleekSurfaceCard)
            .border(1.dp, SleekBorderLight, RoundedCornerShape(24.dp))
            .clickable { onDismiss() }
            .padding(horizontal = 18.dp)
            .semantics { contentDescription = description }
            .testTag("hint_dismiss"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.hint_dismiss),
            color = SleekTextAccent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

/**
 * Rozet metni: ipucunun NEDEN ciktigini tek kelimeyle soyler.
 *
 * Tehdit mi (ZIRH), firsat mi (YENI), yoksa bir YONETIM dersi mi. Yonetim
 * dersleri ayri rozetler kullanir cunku "YENI" onlarda yalan olurdu: satis ve
 * yukseltme oyunun ilk dakikasindan beri vardi, oyuncu onlari GORMEDI.
 * Rozetler en fazla 7 karakter — govde butcesinin turetilisi
 * `strings_hints.xml` basliginda.
 */
private fun hintTagRes(copy: HintCopy): Int = when (copy) {
    is HintCopy.ArmorContrast -> R.string.hint_tag_armor
    is HintCopy.TowerMatchup,
    is HintCopy.SupportRole -> R.string.hint_tag_new
    is HintCopy.UpgradeStep -> R.string.hint_tag_upgrade
    is HintCopy.TargetingChoice -> R.string.hint_tag_target
    is HintCopy.BoosterIntro -> R.string.hint_tag_booster
    is HintCopy.SellRefund -> R.string.hint_tag_sell
}

/**
 * Serit metni.
 *
 * SAYILAR BURADA HESAPLANMAZ: hepsi [HintCopy] icinde hazir gelir ve
 * `GameConfig` tablolarindan turetilmistir. Burada yapilan tek is, cevrilebilir
 * sabloni doldurmaktir — ondalik ayraci (26,6 / 26.6) cihazin diline gore
 * Android tarafindan secilir.
 */
@Composable
private fun hintBody(copy: HintCopy): String = when (copy) {
    is HintCopy.ArmorContrast -> stringResource(
        R.string.hint_armor_intro,
        stringResource(copy.tower.nameRes()),
        stringResource(copy.softEnemy.nameRes()),
        copy.softDps,
        stringResource(copy.armoredEnemy.nameRes()),
        copy.armoredDps
    )

    is HintCopy.TowerMatchup -> stringResource(
        R.string.hint_tower_matchup,
        stringResource(copy.enemy.nameRes()),
        stringResource(copy.oldTower.nameRes()),
        copy.oldDps,
        stringResource(copy.newTower.nameRes()),
        copy.newDps
    )

    is HintCopy.SupportRole -> stringResource(
        R.string.hint_frost_role,
        stringResource(copy.tower.nameRes()),
        copy.dps,
        copy.slowPercent,
        copy.rangeTimeMultiplier
    )

    is HintCopy.UpgradeStep -> stringResource(
        R.string.hint_upgrade_intro,
        stringResource(copy.tower.nameRes()),
        copy.currentDps,
        copy.nextDps,
        copy.cost
    )

    // Hedefleme modu adi ARGUMAN olarak geliyor ve `SelectedTowerInspector`'in
    // butonunda yazan METNIN AYNISI (`inspector_target_strongest`). Ipucu
    // oyuncuya panelde arayacagi kelimeyi verir, es anlamlisini degil.
    is HintCopy.TargetingChoice -> stringResource(
        R.string.hint_targeting_intro,
        stringResource(copy.strongEnemy.nameRes()),
        copy.strongHp,
        stringResource(copy.weakEnemy.nameRes()),
        copy.weakHp,
        stringResource(GameConfig.TargetingMode.STRONGEST.labelRes())
    )

    is HintCopy.BoosterIntro -> stringResource(
        R.string.hint_booster_intro,
        stringResource(copy.booster.hintNameRes()),
        boosterEffectText(copy),
        boosterCostText(copy)
    )

    is HintCopy.SellRefund -> stringResource(
        R.string.hint_sell_intro,
        stringResource(copy.tower.nameRes()),
        copy.refund,
        copy.salvagePercent
    )
}

/**
 * Guclendirici adi.
 *
 * `BoosterRail.kt` ayni eslemeyi PRIVATE tasiyor ve o dosya baska bir is
 * kolunun elinde; aynisi burada tekrarlaniyor ama ANAHTARLAR ayni, yani
 * oyuncu iki yerde ayni adi gorur. Kopyalanan sey bir kural degil, bir
 * kaynak referansi — ikisi ayrisirsa `UnlockHintStringsTest` bunu gormez,
 * ama ayrisabilecek bir DEGER de yok.
 */
@androidx.annotation.StringRes
private fun BoosterType.hintNameRes(): Int = when (this) {
    BoosterType.EMERGENCY_SUPPLY -> R.string.booster_emergency_supply_name
    BoosterType.AIR_SUPPORT -> R.string.booster_air_support_name
    BoosterType.BASE_REPAIR -> R.string.booster_base_repair_name
}

/** Guclendiricinin ETKISI — birimi tipe gore degisir, sablonu da oyle. */
@Composable
private fun boosterEffectText(copy: HintCopy.BoosterIntro): String = when (copy.effect) {
    HintBoosterEffect.SUPPLY ->
        stringResource(R.string.hint_booster_effect_supply, copy.effectAmount)

    HintBoosterEffect.DAMAGE ->
        stringResource(R.string.hint_booster_effect_damage, copy.effectAmount)

    HintBoosterEffect.REPAIR ->
        stringResource(R.string.hint_booster_effect_repair, copy.effectAmount)
}

/**
 * Guclendiricinin BEDELI. Reklam yolu ayri bir sablon: "0 Tedarik" demek
 * teknik olarak dogru ama oyuncuya hicbir sey soylemez; ogrenmesi gereken
 * sey butona basinca kisa bir reklam acilacagidir.
 */
@Composable
private fun boosterCostText(copy: HintCopy.BoosterIntro): String = when (copy.cost) {
    HintBoosterCost.AD -> stringResource(R.string.hint_booster_cost_ad)
    HintBoosterCost.SUPPLY -> stringResource(R.string.hint_booster_cost_supply, copy.price)
    HintBoosterCost.COIN -> stringResource(R.string.hint_booster_cost_coin, copy.price)
}

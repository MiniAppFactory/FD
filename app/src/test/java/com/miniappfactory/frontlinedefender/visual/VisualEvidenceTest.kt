package com.miniappfactory.frontlinedefender.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import com.miniappfactory.frontlinedefender.game.model.GameConfig
import com.miniappfactory.frontlinedefender.game.ui.ActIntro
import com.miniappfactory.frontlinedefender.game.ui.ActIntroOverlay
import com.miniappfactory.frontlinedefender.game.ui.MapLoadingOverlay
import com.miniappfactory.frontlinedefender.game.ui.UnlockConfirmOverlay
import com.miniappfactory.frontlinedefender.game.ui.VictoryModal
import com.miniappfactory.frontlinedefender.ui.theme.FrontlineDefenderTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * CIHAZSIZ GORSEL KANIT (FD-023/024/025).
 *
 * GERCEK uretim composable'larini cihaz/emulator OLMADAN 740x360 dp TR olarak
 * PNG'ye raster'lar ve comms/visual-evidence altina yazar. Codex'in kalan
 * gorsel/akis kabulu icin: zafer boss karti, coin onay (yeterli/yetersiz),
 * act-intro ve harita Loading/Failed durumlari.
 *
 * YONTEM — NEDEN captureToImage DEGIL: Compose `captureToImage()` Robolectric'te
 * `WindowCapture.forceRedraw` -> gercek redraw callback'i beklediginden
 * ComposeTimeoutException veriyor (bu makinede dogrulandi). Bunun yerine
 * `@GraphicsMode(NATIVE)` altinda host View dogrudan bir `Canvas`'a cizilir
 * (`View.draw`), ki bu Robolectric'in Skia tabanli native runtime'iyle GERCEK
 * piksel uretir ve hipervizor/cihaz GEREKTIRMEZ.
 *
 * NOT: Bu GORSEL+AKIS (yerlesim/metin/renk) kanitidir; gercek CIHAZ performansi
 * degildir ve oyle sunulmaz.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "tr-rTR-w740dp-h360dp-land-xxhdpi")
class VisualEvidenceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var audio: AudioManager
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        audio = AudioManager(context)
        audio.isSoundEnabled = false
        engine = GameEngine(SaveManager(context), audio)
    }

    @After
    fun tearDown() {
        audio.release()
    }

    // ---- HARITA DURUMLARI (FD-018/019) ----------------------------------

    @Test
    fun mapFailedOverlay() = render("map_failed_tr.png") {
        MapLoadingOverlay(failed = true, onRetry = {}, onLevelSelect = {})
    }

    @Test
    fun mapLoadingOverlay() = render("map_loading_tr.png") {
        MapLoadingOverlay(failed = false)
    }

    // ---- ZAFER BOSS DONUS KARTI (FD-005) --------------------------------

    /** Zafer paneli (tam) — 740x360'ta boss karti kaydirmanin ALTINDA kalir. */
    @Test
    fun victoryBossCard_L11_panelTop() {
        engine.startNewGame(11) // L11 = Act I boss; elite=false -> boss donus karti cizilir
        org.junit.Assert.assertEquals("L11 yuklenmeli", 11, engine.levelSpec.levelId)
        setVictory()
        // Kart GERCEKTEN composed (yalniz kaydirma disinda): varligi kanitla.
        composeRule.onNodeWithTag("victory_boss_card").assertExists()
        capture("victory_L11_panel_top_tr.png")
    }

    /** Ayni panel, boss DONUS kartina kaydirilmis — kartin okunurlugu gorunur. */
    @Test
    fun victoryBossCard_L11_scrolledToCard() {
        engine.startNewGame(11)
        setVictory()
        composeRule.onNodeWithTag("victory_boss_card").performScrollTo()
        composeRule.waitForIdle()
        capture("victory_L11_boss_card_tr.png")
    }

    private fun setVictory() {
        composeRule.setContent {
            FrontlineDefenderTheme {
                Box(Modifier.fillMaxSize()) {
                    VictoryModal(
                        gameEngine = engine,
                        clearResult = null,
                        nextLevelId = 12,
                        onNextLevel = {},
                        onLevelSelect = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // ---- COIN KILIT ACMA ONAYI (FD-008) ---------------------------------

    @Test
    fun unlockConfirm_sufficientCoins() = render("coin_unlock_sufficient_tr.png") {
        UnlockConfirmOverlay(
            spec = GameConfig.levelSpec(8),
            balance = 9_999, // yeterli -> onay aktif
            onCancel = {},
            onConfirm = {},
        )
    }

    @Test
    fun unlockConfirm_insufficientCoins() = render("coin_unlock_insufficient_tr.png") {
        UnlockConfirmOverlay(
            spec = GameConfig.levelSpec(8),
            balance = 0, // yetersiz -> onay pasif + eksik miktar
            onCancel = {},
            onConfirm = {},
        )
    }

    // ---- PERDE ACILIS KARTI (act-intro, STORY) --------------------------

    @Test
    fun actIntro_actII() = render("act_intro_II_tr.png") {
        ActIntroOverlay(act = ActIntro.actOf(12), onDismiss = {})
    }

    // =====================================================================

    private fun render(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            FrontlineDefenderTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.waitForIdle()
        capture(name)
    }

    /** Host View'i (o anki kare) dogrudan Canvas'a cizip PNG yazar. */
    private fun capture(name: String) {
        val view: View = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        val file = save(bitmap, name)
        assertTrue("PNG yazilmali: $file", file.isFile && file.length() > 0)
    }

    private fun save(bitmap: Bitmap, name: String): File {
        val dir = evidenceDir().apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("VISUAL-EVIDENCE: ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
        return file
    }

    /** comms/visual-evidence — proje kokunun altinda, source'un DISINDA. */
    private fun evidenceDir(): File = listOf(
        File("../../comms/visual-evidence"), // cwd = source/app
        File("../comms/visual-evidence"),    // cwd = source
        File("C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence"),
    ).firstOrNull { it.parentFile?.exists() == true } ?: File(
        "C:/Users/bhdre/APPDeveloper/projects/Frontline Defender/comms/visual-evidence"
    )
}

package com.miniappfactory.frontlinedefender.game.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.miniappfactory.frontlinedefender.game.audio.AudioManager
import com.miniappfactory.frontlinedefender.game.data.SaveManager
import com.miniappfactory.frontlinedefender.game.engine.GameEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * ===========================================================================
 * DURAKLATMA MENUSU — SANAT DILI, DOKUNMA HEDEFI VE DAVRANIS SOZLESMESI
 * ===========================================================================
 *
 * `PauseMenuModal` Material `Button` listesinden `ArtHeaderPlate` +
 * `ArtPrimaryButton`/`ArtSecondaryButton` 2x2 izgarasina tasindi. Bu test ne
 * KILITLER:
 *
 *  1. dort `pause_*_button` etiketi de var, tiklanabilir ve GORUNUR,
 *  2. her buton >= 44 dp dokunma tabaninda VE komsu hedefler arasi >= 8 dp,
 *  3. her buton YALNIZCA kendi geri cagrisini bir kez tetikler,
 *  4. AYARLAR ayni modala doner ve oynanisi DEVAM ETTIRMEZ (buton + geri tusu),
 *  5. modal dikey butceye (ekran - 2 x dis bosluk) sigar ve butonlar kaydirma
 *     alaninin DISINDA kalir,
 *  6. modal kapsayicisina Material `Button` geri konmaz (kaynak taramasi).
 *
 * ## NE KANITLAMIYOR (dogrudan soylenmeli)
 *
 * Robolectric `graphicsMode` LEGACY: metin genisligini OLCMEZ. Bu testler
 * GEOMETRIYI (boyut, konum, sigma) ve DAVRANISI kanitlar; "en uzun TR etiketi
 * butona sigiyor mu" ve "S8'de butonlar metinli cizildi mi" sorularina cevap
 * VERMEZ. Ikisi de cihaz/ekran goruntusu gerektirir (ayrintida:
 * `AutoShrinkText` yalnizca punto kuculterek tasmayi cozer, kirpmaz).
 */
@RunWith(RobolectricTestRunner::class)
// Referans cihaz: Galaxy S8 yatay, 740x360 dp. `qualifiers` olmadan Robolectric'in
// varsayilan ekrani 452 dp'den DAR kalir ve modal sessizce kirpilir
// (`ArtButtonTouchTargetTest` ayni tuzagi 39 dp olcumuyle yasadi).
@Config(sdk = [33], qualifiers = "w740dp-h360dp-land-xxhdpi")
class PauseMenuModalTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var audio: AudioManager
    private lateinit var saveManager: SaveManager
    private lateinit var engine: GameEngine

    private var resumed = 0
    private var restarted = 0
    private var mainMenu = 0

    private val actionTags = listOf(
        "pause_restart_button",
        "pause_main_menu_button",
        "pause_settings_button",
        "pause_resume_button"
    )

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        audio = AudioManager(ctx)
        audio.isSoundEnabled = false
        saveManager = SaveManager(ctx)
        engine = GameEngine(saveManager, audio)
        engine.updateMapDimensions(2220f, 1080f, 96f)
        resumed = 0
        restarted = 0
        mainMenu = 0
    }

    @After
    fun tearDown() {
        audio.release()
    }

    private fun showModal() {
        composeRule.setContent {
            PauseMenuModal(
                gameEngine = engine,
                onResume = { resumed++ },
                onRestart = { restarted++ },
                onMainMenu = { mainMenu++ }
            )
        }
    }

    private fun boundsDp(tag: String): DpRect {
        val r = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) {
            DpRect(r.left.toDp(), r.top.toDp(), r.right.toDp(), r.bottom.toDp())
        }
    }

    private fun rootHeightDp(): Float {
        val r = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) { r.height.toDp().value }
    }

    // =========================================================================
    // 1. Dort etiket de var, tiklanabilir, GORUNUR
    // =========================================================================

    @Test
    fun allFourActionsAndTheTitlePlateExistAreClickableAndDisplayed() {
        showModal()

        actionTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHasClickAction().assertIsDisplayed()
        }
        composeRule.onNodeWithTag("pause_title_plate").assertIsDisplayed()
    }

    // =========================================================================
    // 2. Dokunma hedefi: >= 44 dp, komsu hedefler arasi >= 8 dp
    // =========================================================================

    private fun assertTouchGeometry(screen: String) {
        val heights = mutableListOf<String>()
        actionTags.forEach { tag ->
            val b = boundsDp(tag)
            heights += "$tag=%.1fx%.1f".format(b.width.value, b.height.value)
            assertTrue(
                "[$screen] $tag ${b.width}x${b.height} — 44 dp dokunma tabaninin altinda. " +
                    "PauseModalMaxWidth dusuruldu mu, sanatin en-boy orani mi degisti?",
                b.width >= 44.dp && b.height >= 44.dp
            )
        }
        println("[$screen] dokunma hedefleri: ${heights.joinToString()}")

        val restart = boundsDp("pause_restart_button")
        val menu = boundsDp("pause_main_menu_button")
        val settings = boundsDp("pause_settings_button")
        val resume = boundsDp("pause_resume_button")

        // Yatay komsular ve dikey komsular. Yanlis tiklama = ilerleme kaybi
        // (BASTAN AL / ANA MENU yikici), bu yuzden bosluk kozmetik degil.
        val gaps = mapOf(
            "restart|menu (yatay)" to (menu.left - restart.right),
            "settings|resume (yatay)" to (resume.left - settings.right),
            "restart/settings (dikey)" to (settings.top - restart.bottom),
            "menu/resume (dikey)" to (resume.top - menu.bottom)
        )
        gaps.forEach { (pair, gap) ->
            assertTrue(
                "[$screen] $pair arasi $gap — komsu hedefler arasi en az 8 dp olmali",
                gap >= 8.dp
            )
        }
    }

    /** Referans: 740x360 dp yatay (compact modu, ic genislik 452 dp). */
    @Test
    fun everyActionClearsTheTouchFloorOnTheReferenceScreen() {
        showModal()
        // Olcumun DOGRU soruyu sordugunu dogrula: harness gercekten 360 dp mi?
        assertEquals("harness ekran yuksekligi", 360f, rootHeightDp(), 1f)
        assertTouchGeometry("740x360")
    }

    /** Uzun ekran: compact degil (>= 420 dp) -> ferah dolgu, ic genislik 432 dp (EN DAR durum). */
    @Test
    @Config(sdk = [33], qualifiers = "w1024dp-h768dp-land-xxhdpi")
    fun everyActionClearsTheTouchFloorOnATabletWithRoomyPadding() {
        showModal()
        assertEquals("harness ekran yuksekligi", 768f, rootHeightDp(), 1f)
        assertTouchGeometry("1024x768")

        // En dar ic genislikte (432 dp) izgara hucresi `PauseModalInnerWidth`la
        // tutarli: modal 480 dp'yi asamaz.
        val resume = boundsDp("pause_resume_button")
        val restart = boundsDp("pause_restart_button")
        val gridWidth = resume.right - restart.left
        assertEquals(
            "izgara genisligi PauseModalInnerWidth'e esit olmali",
            PauseModalInnerWidth.value, gridWidth.value, 1f
        )
    }

    /** Dar-kisa yatay ekran (480x320): modal ekrana gore daralir, taban yine tutmali. */
    @Test
    @Config(sdk = [33], qualifiers = "w480dp-h320dp-land-xxhdpi")
    fun everyActionClearsTheTouchFloorOnASmallLandscapeScreen() {
        showModal()
        assertEquals("harness ekran yuksekligi", 320f, rootHeightDp(), 1f)
        assertTouchGeometry("480x320")
    }

    // =========================================================================
    // 3. Her buton YALNIZCA kendi geri cagrisini BIR kez tetikler
    // =========================================================================

    @Test
    fun eachActionFiresOnlyItsOwnCallbackExactlyOnce() {
        showModal()

        fun counts() = listOf(resumed, restarted, mainMenu)

        // (etiket, beklenen artan indeks: 0=onResume 1=onRestart 2=onMainMenu)
        listOf(
            "pause_resume_button" to 0,
            "pause_restart_button" to 1,
            "pause_main_menu_button" to 2
        ).forEach { (tag, index) ->
            val before = counts()
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            val expected = before.toMutableList().also { it[index] += 1 }
            assertEquals(
                "$tag: yalnizca $index numarali geri cagri bir kez artmali " +
                    "(onResume, onRestart, onMainMenu)",
                expected, counts()
            )
        }
    }

    // =========================================================================
    // 4. AYARLAR: ayni modala doner, oynanisi DEVAM ETTIRMEZ
    // =========================================================================

    @Test
    fun settingsOpensTheOverlayAndTheCloseButtonReturnsToTheModalWithoutResuming() {
        showModal()
        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()

        composeRule.onNodeWithTag("pause_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_close_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("pause_panel").assertIsDisplayed()
        assertEquals("AYARLAR yolu oynanisi devam ettirmemeli", listOf(0, 0, 0), listOf(resumed, restarted, mainMenu))
    }

    @Test
    fun systemBackClosesOnlyTheSettingsOverlayAndKeepsThePauseModal() {
        showModal()

        composeRule.onNodeWithTag("pause_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("pause_panel").assertIsDisplayed()
        assertEquals("geri tusu oynanisi devam ettirmemeli", listOf(0, 0, 0), listOf(resumed, restarted, mainMenu))
    }

    // =========================================================================
    // 5. Dikey butce: ekran - 2 x dis bosluk; butonlar kaydirma alaninin DISINDA
    // =========================================================================

    @Test
    fun theModalFitsTheVerticalBudgetAndNoActionNeedsScrolling() {
        showModal()

        val screen = rootHeightDp()
        assertEquals("harness ekran yuksekligi", 360f, screen, 1f)
        val budget = screen - PauseModalOuterPadding.value * 2 // 360 - 24 = 336
        val panel = boundsDp("pause_panel")
        println(
            "[740x360] panel yuksekligi = %.1f dp (butce %.1f dp), panel genisligi = %.1f dp".format(
                panel.height.value, budget, panel.width.value
            )
        )

        assertTrue(
            "panel ${panel.height} — dikey butce $budget dp'i asiyor",
            panel.height.value <= budget + 0.5f
        )
        assertTrue(
            "panel ${panel.width} — PauseModalMaxWidth'i asiyor",
            panel.width.value <= PauseModalMaxWidth.value + 0.5f
        )

        // Panel ekranin icinde.
        assertTrue("panel ustten tasiyor", panel.top.value >= -0.5f)
        assertTrue("panel alttan tasiyor", panel.bottom.value <= screen + 0.5f)

        // Butonlar kaydirma GEREKTIRMEDEN panelin icinde ve ekranda.
        actionTags.forEach { tag ->
            val b = boundsDp(tag)
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            assertTrue("$tag panelin altina tasiyor", b.bottom <= panel.bottom)
            assertTrue("$tag panelin ustune tasiyor", b.top >= panel.top)
            assertTrue("$tag ekran disina tasiyor", b.bottom.value <= screen && b.top.value >= 0f)
        }

        // Baslik plakasi butonlarla CAKISMAZ (kaydirma alaninin kirpmasi yok).
        val plate = boundsDp("pause_title_plate")
        val firstRowTop = minOf(boundsDp("pause_restart_button").top, boundsDp("pause_main_menu_button").top)
        assertTrue("baslik plakasi ilk buton satirina biniyor", plate.bottom <= firstRowTop)
        assertTrue("baslik plakasi panelden tasiyor", plate.top >= panel.top && plate.bottom <= panel.bottom)
    }

    // =========================================================================
    // 6. Modal kapsayicisina Material `Button` geri konmaz
    // =========================================================================

    /**
     * SANAT ile ayni kapsayicidaki Material `Surface` butonlari Galaxy S8 /
     * API 24'te METINSIZ cizildi (HANDOVER §1c — dort denemeyle olculdu, kok
     * neden bilinmiyor). JVM testleri bunu YAKALAYAMAZ, o yuzden kural kaynak
     * duzeyinde kilitli: `PauseMenuModal` govdesinde yalnizca sanat butonlari.
     *
     * Tarama `fun PauseMenuModal(`ten bir sonraki `@Composable`a (ya da dosya
     * sonuna) kadar; yorumlar taranmaz.
     */
    @Test
    fun thePauseModalContainerHoldsNoMaterialButtons() {
        val candidates = listOf(
            File("src/main/java/com/miniappfactory/frontlinedefender/game/ui/GameDialogs.kt"),
            File("app/src/main/java/com/miniappfactory/frontlinedefender/game/ui/GameDialogs.kt")
        )
        val source = candidates.firstOrNull { it.isFile }?.readText()
            ?: error("GameDialogs.kt bulunamadi. Denenen: ${candidates.map { it.absolutePath }}")

        val start = source.indexOf("fun PauseMenuModal(")
        assertTrue("PauseMenuModal bulunamadi", start >= 0)
        val nextComposable = source.indexOf("@Composable", start)
        val body = source.substring(start, if (nextComposable < 0) source.length else nextComposable)

        val code = body.lines().joinToString("\n") { it.substringBefore("//") }

        // `ArtPrimaryButton(` / `ArtSecondaryButton(` serbest; ciplak `Button(`,
        // `OutlinedButton(`, `TextButton(`, `IconButton(` ... degil.
        val material = Regex("""(?<![A-Za-z])(Filled\w*Button|Outlined\w*Button|Elevated\w*Button|Icon\w*Button|Text\w*Button|Button)\s*\(""")
            .findAll(code).map { it.value }.toList()
        assertTrue(
            "PauseMenuModal kapsayicisinda Material buton var: $material. Galaxy S8'de " +
                "sanatla ayni kapsayicida METINSIZ cizildi — ArtPrimaryButton/ArtSecondaryButton kullanin.",
            material.isEmpty()
        )

        // Ve yeni yapinin gercekten sanat butonlari kullandigini da kilitle
        // (bos govde "Material yok" testini sahte yesile cevirirdi).
        assertEquals(1, Regex("""ArtPrimaryButton\s*\(""").findAll(code).count())
        assertEquals(3, Regex("""ArtSecondaryButton\s*\(""").findAll(code).count())
        assertTrue("baslik plakasi yok", code.contains("ArtHeaderPlate("))
    }
}
